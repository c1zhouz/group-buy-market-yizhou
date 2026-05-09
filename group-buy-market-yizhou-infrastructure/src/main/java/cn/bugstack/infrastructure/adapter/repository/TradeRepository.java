package cn.bugstack.infrastructure.adapter.repository;

import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
import cn.bugstack.domain.trade.model.aggregate.GroupBuyOrderAggregate;
import cn.bugstack.domain.trade.model.aggregate.GroupBuyTeamSettlementAggregate;
import cn.bugstack.domain.trade.model.entity.*;
import cn.bugstack.domain.trade.model.valobj.GroupBuyProgressVO;
import cn.bugstack.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import cn.bugstack.infrastructure.dao.IGroupBuyActivityDao;
import cn.bugstack.infrastructure.dao.IGroupBuyOrderDao;
import cn.bugstack.infrastructure.dao.IGroupBuyOrderListDao;
import cn.bugstack.infrastructure.dao.INotifyTaskDao;
import cn.bugstack.infrastructure.dao.po.GroupBuyActivity;
import cn.bugstack.infrastructure.dao.po.GroupBuyOrder;
import cn.bugstack.infrastructure.dao.po.GroupBuyOrderList;
import cn.bugstack.infrastructure.dao.po.NotifyTask;
import cn.bugstack.infrastructure.dcc.DCCService;
import cn.bugstack.infrastructure.redis.IRedisService;
import cn.bugstack.types.common.Constants;
import cn.bugstack.types.enums.ActivityStatusEnumVO;
import cn.bugstack.types.enums.GroupBuyOrderEnumVO;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RScript;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;

/**
 * @description 交易仓储服务
 */
@Slf4j
@Repository
public class TradeRepository implements ITradeRepository {

    private static final String LUA_LOCK_OCCUPANCY = "lua/lock_market_pay_order.lua";
    private static final String LUA_RELEASE_OCCUPANCY = "lua/release_market_pay_order.lua";

    @Resource
    private IGroupBuyActivityDao groupBuyActivityDao;
    @Resource
    private IGroupBuyOrderDao groupBuyOrderDao;
    @Resource
    private IGroupBuyOrderListDao groupBuyOrderListDao;
    @Resource
    private INotifyTaskDao notifyTaskDao;
    @Resource
    private DCCService dccService;
    @Resource
    private IRedisService redisService;

    @Override
    public MarketPayOrderEntity queryMarketPayOrderEntityByOutTradeNo(String userId, String outTradeNo) {
        GroupBuyOrderList groupBuyOrderListReq = new GroupBuyOrderList();
        groupBuyOrderListReq.setUserId(userId);
        groupBuyOrderListReq.setOutTradeNo(outTradeNo);
        GroupBuyOrderList groupBuyOrderListRes = groupBuyOrderListDao.queryGroupBuyOrderRecordByOutTradeNo(groupBuyOrderListReq);
        if (null == groupBuyOrderListRes) return null;

        return MarketPayOrderEntity.builder()
                .teamId(groupBuyOrderListRes.getTeamId())
                .orderId(groupBuyOrderListRes.getOrderId())
                .deductionPrice(groupBuyOrderListRes.getDeductionPrice())
                .tradeOrderStatusEnumVO(TradeOrderStatusEnumVO.valueOf(groupBuyOrderListRes.getStatus()))
                .build();
    }

    @Override
    public MarketPayOrderEntity queryNoPayMarketPayOrder(String userId, Long activityId, String goodsId) {
        GroupBuyOrderList groupBuyOrderListReq = new GroupBuyOrderList();
        groupBuyOrderListReq.setUserId(userId);
        groupBuyOrderListReq.setActivityId(activityId);
        groupBuyOrderListReq.setGoodsId(goodsId);
        GroupBuyOrderList groupBuyOrderListRes = groupBuyOrderListDao.queryNoPayOrderByUserActivityGoods(groupBuyOrderListReq);
        if (null == groupBuyOrderListRes) return null;

        return MarketPayOrderEntity.builder()
                .teamId(groupBuyOrderListRes.getTeamId())
                .orderId(groupBuyOrderListRes.getOrderId())
                .deductionPrice(groupBuyOrderListRes.getDeductionPrice())
                .tradeOrderStatusEnumVO(TradeOrderStatusEnumVO.valueOf(groupBuyOrderListRes.getStatus()))
                .build();
    }

    @Transactional(timeout = 500)
    @Override
    public MarketPayOrderEntity lockMarketPayOrder(GroupBuyOrderAggregate groupBuyOrderAggregate) {
        // 聚合对象信息
        UserEntity userEntity = groupBuyOrderAggregate.getUserEntity();
        PayActivityEntity payActivityEntity = groupBuyOrderAggregate.getPayActivityEntity();
        PayDiscountEntity payDiscountEntity = groupBuyOrderAggregate.getPayDiscountEntity();
        Integer userTakeOrderCount = groupBuyOrderAggregate.getUserTakeOrderCount();

        // 判断是否有团 - teamId 为空 - 新团、为不空 - 老团
        String teamId = payActivityEntity.getTeamId();
        boolean luaOccupancyLocked = false;
        if (StringUtils.isBlank(teamId)) {
            // Build a longer unique team id to avoid collisions across historical records.
            teamId = generateUniqueTeamId();
            // 日期处理
            Date currentDate = new Date();
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(currentDate);
            calendar.add(Calendar.MINUTE, payActivityEntity.getValidTime());

            // 构建拼团订单
            GroupBuyOrder groupBuyOrder = GroupBuyOrder.builder()
                    .teamId(teamId)
                    .activityId(payActivityEntity.getActivityId())
                    .source(payDiscountEntity.getSource())
                    .channel(payDiscountEntity.getChannel())
                    .originalPrice(payDiscountEntity.getOriginalPrice())
                    .deductionPrice(payDiscountEntity.getDeductionPrice())
                    .payPrice(payDiscountEntity.getPayPrice())
                    .targetCount(payActivityEntity.getTargetCount())
                    .completeCount(0)
                    .lockCount(1)
                    .validStartTime(currentDate)
                    .validEndTime(calendar.getTime())
                    .notifyUrl(payDiscountEntity.getNotifyUrl())
                    .build();

            // 写入记录
            groupBuyOrderDao.insert(groupBuyOrder);
        } else {
            // 老团先执行 Redis Lua 原子占位，避免高并发超卖
            if (dccService.isTradeLockLuaSwitch()) {
                GroupBuyOrder groupBuyOrder = groupBuyOrderDao.queryGroupBuyTeamByTeamId(teamId);
                if (null == groupBuyOrder || null == groupBuyOrder.getTargetCount() || null == groupBuyOrder.getLockCount()) {
                    throw new AppException(ResponseCode.E0005);
                }

                int lockResult = lockTeamOccupancyByLua(teamId, payActivityEntity.getActivityId(), payDiscountEntity.getOutTradeNo(), groupBuyOrder.getLockCount(), groupBuyOrder.getTargetCount());
                boolean bypassLuaResultWithDb = false;

                // Self-heal stale Redis lock counters caused by historical script failures.
                if (2 == lockResult) {
                    GroupBuyOrder latestOrder = groupBuyOrderDao.queryGroupBuyTeamByTeamId(teamId);
                    boolean dbStillHasSeat = null != latestOrder
                            && Objects.equals(latestOrder.getStatus(), 0)
                            && null != latestOrder.getValidEndTime()
                            && latestOrder.getValidEndTime().after(new Date())
                            && null != latestOrder.getTargetCount()
                            && null != latestOrder.getLockCount()
                            && latestOrder.getLockCount() < latestOrder.getTargetCount();
                    if (dbStillHasSeat) {
                        resetTeamOccupancyCache(teamId, payActivityEntity.getActivityId());
                        lockResult = lockTeamOccupancyByLua(teamId, payActivityEntity.getActivityId(), payDiscountEntity.getOutTradeNo(), latestOrder.getLockCount(), latestOrder.getTargetCount());
                        // Redis may still return stale no-seat; trust DB CAS update as final source of truth.
                        if (2 == lockResult) {
                            bypassLuaResultWithDb = true;
                            log.warn("Lua占位返回无名额但DB仍有名额，降级为DB更新判定 teamId:{} activityId:{} userId:{}",
                                    teamId, payActivityEntity.getActivityId(), userEntity.getUserId());
                        }
                    }
                }
                // 2: 名额不足，直接拦截
                if (2 == lockResult && !bypassLuaResultWithDb) {
                    throw new AppException(ResponseCode.E0005);
                }
                // 1: 幂等命中，交给后续订单唯一约束兜底，避免重复扣减
                luaOccupancyLocked = 0 == lockResult;
            }

            // 更新记录 - 如果更新记录不等于1，则表示拼团已满，抛出异常
            int updateAddTargetCount = groupBuyOrderDao.updateAddLockCount(teamId);
            if (1 != updateAddTargetCount) {
                if (luaOccupancyLocked) {
                    releaseTeamOccupancyByLua(teamId, payActivityEntity.getActivityId(), payDiscountEntity.getOutTradeNo());
                }
                throw new AppException(ResponseCode.E0005);
            }
        }

        // 使用 RandomStringUtils.randomNumeric 替代公司里使用的雪花算法UUID
        String orderId = RandomStringUtils.randomNumeric(12);
        GroupBuyOrderList groupBuyOrderListReq = GroupBuyOrderList.builder()
                .userId(userEntity.getUserId())
                .teamId(teamId)
                .orderId(orderId)
                .activityId(payActivityEntity.getActivityId())
                .startTime(payActivityEntity.getStartTime())
                .endTime(payActivityEntity.getEndTime())
                .goodsId(payDiscountEntity.getGoodsId())
                .source(payDiscountEntity.getSource())
                .channel(payDiscountEntity.getChannel())
                .originalPrice(payDiscountEntity.getOriginalPrice())
                .deductionPrice(payDiscountEntity.getDeductionPrice())
                .status(TradeOrderStatusEnumVO.CREATE.getCode())
                .outTradeNo(payDiscountEntity.getOutTradeNo())
                // 构建 bizId 唯一值；活动id_用户id_参与次数累加
                .bizId(payActivityEntity.getActivityId() + Constants.UNDERLINE + userEntity.getUserId() + Constants.UNDERLINE + (userTakeOrderCount + 1))
                .build();
        try {
            // 写入拼团记录
            groupBuyOrderListDao.insert(groupBuyOrderListReq);
        } catch (DuplicateKeyException e) {
            if (StringUtils.isNotBlank(payActivityEntity.getTeamId()) && luaOccupancyLocked) {
                releaseTeamOccupancyByLua(payActivityEntity.getTeamId(), payActivityEntity.getActivityId(), payDiscountEntity.getOutTradeNo());
                groupBuyOrderDao.updateSubtractionLockCount(payActivityEntity.getTeamId());
            }
            throw new AppException(ResponseCode.INDEX_EXCEPTION);
        } catch (RuntimeException e) {
            if (StringUtils.isNotBlank(payActivityEntity.getTeamId()) && luaOccupancyLocked) {
                releaseTeamOccupancyByLua(payActivityEntity.getTeamId(), payActivityEntity.getActivityId(), payDiscountEntity.getOutTradeNo());
                groupBuyOrderDao.updateSubtractionLockCount(payActivityEntity.getTeamId());
            }
            throw e;
        }

        return MarketPayOrderEntity.builder()
                .orderId(orderId)
                .deductionPrice(payDiscountEntity.getDeductionPrice())
                .tradeOrderStatusEnumVO(TradeOrderStatusEnumVO.CREATE)
                .build();
    }

    @Transactional(timeout = 500)
    @Override
    public boolean cancelNoPayMarketPayOrder(String userId, String outTradeNo) {
        GroupBuyOrderList groupBuyOrderList = groupBuyOrderListDao.queryGroupBuyOrderByOutTradeNo(userId, outTradeNo);
        if (null == groupBuyOrderList) {
            return true;
        }

        if (!Objects.equals(groupBuyOrderList.getStatus(), TradeOrderStatusEnumVO.CREATE.getCode())) {
            return true;
        }

        int closeOrderCount = groupBuyOrderListDao.updateOrderStatus2CLOSE(userId, outTradeNo);
        if (1 != closeOrderCount) {
            GroupBuyOrderList latestOrder = groupBuyOrderListDao.queryGroupBuyOrderByOutTradeNo(userId, outTradeNo);
            if (null == latestOrder || !Objects.equals(latestOrder.getStatus(), TradeOrderStatusEnumVO.CREATE.getCode())) {
                return true;
            }
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }

        int subtractionLockCount = groupBuyOrderDao.updateSubtractionLockCount(groupBuyOrderList.getTeamId());
        if (1 != subtractionLockCount) {
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }

        if (dccService.isTradeLockLuaSwitch()) {
            releaseTeamOccupancyByLua(groupBuyOrderList.getTeamId(), groupBuyOrderList.getActivityId(), outTradeNo);
        }

        groupBuyOrderDao.updateOrderStatus2CLOSEIfEmpty(groupBuyOrderList.getTeamId());
        return true;
    }

    @Override
    public GroupBuyProgressVO queryGroupBuyProgress(String teamId) {
        GroupBuyOrder groupBuyOrder = groupBuyOrderDao.queryGroupBuyProgress(teamId);
        if (null == groupBuyOrder) return null;
        return GroupBuyProgressVO.builder()
                .completeCount(groupBuyOrder.getCompleteCount())
                .targetCount(groupBuyOrder.getTargetCount())
                .lockCount(groupBuyOrder.getLockCount())
                .build();
    }

    @Override
    public GroupBuyActivityEntity queryGroupBuyActivityEntityByActivityId(Long activityId) {
        GroupBuyActivity groupBuyActivity = groupBuyActivityDao.queryGroupBuyActivityByActivityId(activityId);
        return GroupBuyActivityEntity.builder()
                .activityId(groupBuyActivity.getActivityId())
                .activityName(groupBuyActivity.getActivityName())
                .discountId(groupBuyActivity.getDiscountId())
                .groupType(groupBuyActivity.getGroupType())
                .takeLimitCount(groupBuyActivity.getTakeLimitCount())
                .target(groupBuyActivity.getTarget())
                .validTime(groupBuyActivity.getValidTime())
                .status(ActivityStatusEnumVO.valueOf(groupBuyActivity.getStatus()))
                .startTime(groupBuyActivity.getStartTime())
                .endTime(groupBuyActivity.getEndTime())
                .tagId(groupBuyActivity.getTagId())
                .tagScope(groupBuyActivity.getTagScope())
                .build();
    }

    @Override
    public Integer queryOrderCountByGoodsId(String userId, String goodsId) {
        GroupBuyOrderList groupBuyOrderListReq = new GroupBuyOrderList();
        groupBuyOrderListReq.setUserId(userId);
        groupBuyOrderListReq.setGoodsId(goodsId);
        return groupBuyOrderListDao.queryOrderCountByGoodsId(groupBuyOrderListReq);
    }

    @Override
    public GroupBuyTeamEntity queryGroupBuyTeamByTeamId(String teamId) {
        GroupBuyOrder groupBuyOrder = groupBuyOrderDao.queryGroupBuyTeamByTeamId(teamId);
        return GroupBuyTeamEntity.builder()
                .teamId(groupBuyOrder.getTeamId())
                .activityId(groupBuyOrder.getActivityId())
                .targetCount(groupBuyOrder.getTargetCount())
                .completeCount(groupBuyOrder.getCompleteCount())
                .lockCount(groupBuyOrder.getLockCount())
                .status(GroupBuyOrderEnumVO.valueOf(groupBuyOrder.getStatus()))
                .validStartTime(groupBuyOrder.getValidStartTime())
                .validEndTime(groupBuyOrder.getValidEndTime())
                .notifyUrl(groupBuyOrder.getNotifyUrl())
                .build();
    }

    @Transactional(timeout = 500)
    @Override
    public boolean settlementMarketPayOrder(GroupBuyTeamSettlementAggregate groupBuyTeamSettlementAggregate) {

        UserEntity userEntity = groupBuyTeamSettlementAggregate.getUserEntity();
        GroupBuyTeamEntity groupBuyTeamEntity = groupBuyTeamSettlementAggregate.getGroupBuyTeamEntity();
        TradePaySuccessEntity tradePaySuccessEntity = groupBuyTeamSettlementAggregate.getTradePaySuccessEntity();

        // 1. 更新拼团订单明细状态
        GroupBuyOrderList groupBuyOrderListReq = new GroupBuyOrderList();
        groupBuyOrderListReq.setUserId(userEntity.getUserId());
        groupBuyOrderListReq.setOutTradeNo(tradePaySuccessEntity.getOutTradeNo());
        groupBuyOrderListReq.setOutTradeTime(tradePaySuccessEntity.getOutTradeTime());

        int updateOrderListStatusCount = groupBuyOrderListDao.updateOrderStatus2COMPLETE(groupBuyOrderListReq);
        if (1 != updateOrderListStatusCount) {
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }

        // 2. 更新拼团达成数量
        int updateAddCount = groupBuyOrderDao.updateAddCompleteCount(groupBuyTeamEntity.getTeamId());
        if (1 != updateAddCount) {
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }

        // 3. 更新拼团完成状态
        if (groupBuyTeamEntity.getTargetCount() - groupBuyTeamEntity.getCompleteCount() == 1) {
            int updateOrderStatusCount = groupBuyOrderDao.updateOrderStatus2COMPLETE(groupBuyTeamEntity.getTeamId());
            if (1 != updateOrderStatusCount) {
                throw new AppException(ResponseCode.UPDATE_ZERO);
            }

            // 查询拼团交易完成外部单号列表
            List<String> outTradeNoList = groupBuyOrderListDao.queryGroupBuyCompleteOrderOutTradeNoListByTeamId(groupBuyTeamEntity.getTeamId());

            // 拼团完成写入回调任务记录
            NotifyTask notifyTask = new NotifyTask();
            notifyTask.setActivityId(groupBuyTeamEntity.getActivityId());
            notifyTask.setTeamId(groupBuyTeamEntity.getTeamId());
            notifyTask.setNotifyUrl(groupBuyTeamEntity.getNotifyUrl());
            notifyTask.setNotifyCount(0);
            notifyTask.setNotifyStatus(0);

            notifyTask.setParameterJson(JSON.toJSONString(new HashMap<String, Object>() {{
                put("teamId", groupBuyTeamEntity.getTeamId());
                put("outTradeNoList", outTradeNoList);
            }}));

            notifyTaskDao.insert(notifyTask);

            return true;
        }

        return false;
    }

    @Override
    public boolean isSCBlackIntercept(String source, String channel) {
        return dccService.isSCBlackIntercept(source, channel);
    }

    @Override
    public List<NotifyTaskEntity> queryUnExecutedNotifyTaskList() {
        List<NotifyTask> notifyTaskList = notifyTaskDao.queryUnExecutedNotifyTaskList();
        if (notifyTaskList.isEmpty()) return new ArrayList<>();

        List<NotifyTaskEntity> notifyTaskEntities = new ArrayList<>();
        for (NotifyTask notifyTask : notifyTaskList) {

            NotifyTaskEntity notifyTaskEntity = NotifyTaskEntity.builder()
                    .teamId(notifyTask.getTeamId())
                    .notifyUrl(notifyTask.getNotifyUrl())
                    .notifyCount(notifyTask.getNotifyCount())
                    .parameterJson(notifyTask.getParameterJson())
                    .build();

            notifyTaskEntities.add(notifyTaskEntity);
        }

        return notifyTaskEntities;
    }

    @Override
    public List<NotifyTaskEntity> queryUnExecutedNotifyTaskList(String teamId) {
        NotifyTask notifyTask = notifyTaskDao.queryUnExecutedNotifyTaskByTeamId(teamId);
        if (null == notifyTask) return new ArrayList<>();
        return Collections.singletonList(NotifyTaskEntity.builder()
                .teamId(notifyTask.getTeamId())
                .notifyUrl(notifyTask.getNotifyUrl())
                .notifyCount(notifyTask.getNotifyCount())
                .parameterJson(notifyTask.getParameterJson())
                .build());
    }

    @Override
    public int updateNotifyTaskStatusSuccess(String teamId) {
        return notifyTaskDao.updateNotifyTaskStatusSuccess(teamId);
    }

    @Override
    public int updateNotifyTaskStatusError(String teamId) {
        return notifyTaskDao.updateNotifyTaskStatusError(teamId);
    }

    @Override
    public int updateNotifyTaskStatusRetry(String teamId) {
        return notifyTaskDao.updateNotifyTaskStatusRetry(teamId);
    }

    private int lockTeamOccupancyByLua(String teamId, Long activityId, String outTradeNo, int initLockCount, int targetCount) {
        String lockScript = readScript(LUA_LOCK_OCCUPANCY);
        String teamLockKey = buildTeamLockKey(teamId, activityId);
        String teamIdempotentKey = buildTeamIdempotentKey(teamId, activityId);
        Long luaResult = redisService.eval(
                lockScript,
                RScript.ReturnType.INTEGER,
                Arrays.asList(teamLockKey, teamIdempotentKey),
                outTradeNo,
                targetCount,
                dccService.getTradeLockLuaTtlSeconds(),
                initLockCount
        );
        return null == luaResult ? 2 : luaResult.intValue();
    }

    private void releaseTeamOccupancyByLua(String teamId, Long activityId, String outTradeNo) {
        String releaseScript = readScript(LUA_RELEASE_OCCUPANCY);
        redisService.eval(
                releaseScript,
                RScript.ReturnType.INTEGER,
                Arrays.asList(buildTeamLockKey(teamId, activityId), buildTeamIdempotentKey(teamId, activityId)),
                outTradeNo
        );
    }

    private String buildTeamLockKey(String teamId, Long activityId) {
        return dccService.getTradeLockLuaKeyPrefix() + Constants.UNDERLINE + "team" + Constants.UNDERLINE + activityId + Constants.UNDERLINE + teamId;
    }

    private String buildTeamIdempotentKey(String teamId, Long activityId) {
        return dccService.getTradeLockLuaKeyPrefix() + Constants.UNDERLINE + "idempotent" + Constants.UNDERLINE + activityId + Constants.UNDERLINE + teamId;
    }

    private void resetTeamOccupancyCache(String teamId, Long activityId) {
        redisService.remove(buildTeamLockKey(teamId, activityId));
        redisService.remove(buildTeamIdempotentKey(teamId, activityId));
    }

    private String readScript(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "Lua script load failed: " + path);
        }
    }

    private String generateUniqueTeamId() {
        // team_id column is length-limited in DB; keep 8-digit id and retry for uniqueness.
        for (int i = 0; i < 20; i++) {
            String candidate = RandomStringUtils.randomNumeric(8);
            GroupBuyOrder existed = groupBuyOrderDao.queryGroupBuyTeamByTeamId(candidate);
            if (null == existed) {
                return candidate;
            }
        }
        throw new AppException(ResponseCode.UN_ERROR.getCode(), "生成拼团编号失败，请稍后重试");
    }

}
