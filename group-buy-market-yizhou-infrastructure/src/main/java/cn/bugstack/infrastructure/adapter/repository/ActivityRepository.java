package cn.bugstack.infrastructure.adapter.repository;

import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
import cn.bugstack.domain.activity.model.entity.AdminProductEntity;
import cn.bugstack.domain.activity.model.entity.AdminProductListEntity;
import cn.bugstack.domain.activity.model.entity.DefaultProductInitializeEntity;
import cn.bugstack.domain.activity.model.entity.MarketProductItemEntity;
import cn.bugstack.domain.activity.model.entity.UserGroupBuyOrderDetailEntity;
import cn.bugstack.domain.activity.model.valobj.*;
import cn.bugstack.infrastructure.dao.*;
import cn.bugstack.infrastructure.dao.po.*;
import cn.bugstack.infrastructure.dcc.DCCService;
import cn.bugstack.infrastructure.redis.IRedisService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBitSet;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @description 活动仓储
 */
@Slf4j
@Repository
public class ActivityRepository implements IActivityRepository {

    private static final int DEFAULT_PRODUCT_STOCK = 100;
    private static final int COMPLETE_ORDER_STATUS = 1;

    @Resource
    private IGroupBuyActivityDao groupBuyActivityDao;
    @Resource
    private IGroupBuyDiscountDao groupBuyDiscountDao;
    @Resource
    private ISCSkuActivityDao skuActivityDao;
    @Resource
    private ISkuDao skuDao;
    @Resource
    private IRedisService redisService;
    @Resource
    private DCCService dccService;
    @Resource
    private IGroupBuyOrderDao groupBuyOrderDao;
    @Resource
    private IGroupBuyOrderListDao groupBuyOrderListDao;
    @Resource
    private SkuDefaultService skuDefaultService;

    @Override
    public GroupBuyActivityDiscountVO queryGroupBuyActivityDiscountVO(Long activityId) {
        GroupBuyActivity groupBuyActivityRes = groupBuyActivityDao.queryValidGroupBuyActivityId(activityId);
        if (null == groupBuyActivityRes) return null;

        String discountId = groupBuyActivityRes.getDiscountId();

        GroupBuyDiscount groupBuyDiscountRes = groupBuyDiscountDao.queryGroupBuyActivityDiscountByDiscountId(discountId);
        if (null == groupBuyDiscountRes) return null;

        GroupBuyActivityDiscountVO.GroupBuyDiscount groupBuyDiscount = GroupBuyActivityDiscountVO.GroupBuyDiscount.builder()
                .discountName(groupBuyDiscountRes.getDiscountName())
                .discountDesc(groupBuyDiscountRes.getDiscountDesc())
                .discountType(DiscountTypeEnum.get(groupBuyDiscountRes.getDiscountType()))
                .marketPlan(groupBuyDiscountRes.getMarketPlan())
                .marketExpr(groupBuyDiscountRes.getMarketExpr())
                .tagId(groupBuyDiscountRes.getTagId())
                .build();

        return GroupBuyActivityDiscountVO.builder()
                .activityId(groupBuyActivityRes.getActivityId())
                .activityName(groupBuyActivityRes.getActivityName())
                .groupBuyDiscount(groupBuyDiscount)
                .groupType(groupBuyActivityRes.getGroupType())
                .takeLimitCount(groupBuyActivityRes.getTakeLimitCount())
                .target(groupBuyActivityRes.getTarget())
                .validTime(groupBuyActivityRes.getValidTime())
                .status(groupBuyActivityRes.getStatus())
                .startTime(groupBuyActivityRes.getStartTime())
                .endTime(groupBuyActivityRes.getEndTime())
                .tagId(groupBuyActivityRes.getTagId())
                .tagScope(groupBuyActivityRes.getTagScope())
                .build();
    }

    @Override
    public SkuVO querySkuByGoodsId(String goodsId) {
        Sku sku = skuDefaultService.ensureSkuByGoodsId(goodsId);
        if (null == sku) return null;
        return SkuVO.builder()
                .goodsId(sku.getGoodsId())
                .goodsName(sku.getGoodsName())
                .originalPrice(sku.getOriginalPrice())
                .build();
    }

    @Override
    public SCSkuActivityVO querySCSkuActivityBySCGoodsId(String source, String channel, String goodsId) {
        SCSkuActivity scSkuActivityReq = new SCSkuActivity();
        scSkuActivityReq.setSource(source);
        scSkuActivityReq.setChannel(channel);
        scSkuActivityReq.setGoodsId(goodsId);

        SCSkuActivity scSkuActivity = skuActivityDao.querySCSkuActivityBySCGoodsId(scSkuActivityReq);
        if (null == scSkuActivity) return null;

        return SCSkuActivityVO.builder()
                .source(scSkuActivity.getSource())
                .chanel(scSkuActivity.getChannel())
                .activityId(scSkuActivity.getActivityId())
                .goodsId(scSkuActivity.getGoodsId())
                .build();
    }

    @Override
    public List<MarketProductItemEntity> queryMarketProductList(String source, String channel) {
        List<Sku> skuList = skuDao.querySkuList();
        if (null == skuList || skuList.isEmpty()) {
            return Collections.emptyList();
        }

        Date now = new Date();
        List<MarketProductItemEntity> productList = new ArrayList<>();
        for (Sku sku : skuList) {
            if (null == sku || null == sku.getGoodsId() || sku.getGoodsId().trim().isEmpty()) {
                continue;
            }

            Long activityId = null;
            Integer activityTarget = null;
            SCSkuActivity scSkuActivityReq = new SCSkuActivity();
            scSkuActivityReq.setSource(source);
            scSkuActivityReq.setChannel(channel);
            scSkuActivityReq.setGoodsId(sku.getGoodsId());
            SCSkuActivity scSkuActivity = skuActivityDao.querySCSkuActivityBySCGoodsId(scSkuActivityReq);
            if (null != scSkuActivity) {
                GroupBuyActivity activity = groupBuyActivityDao.queryGroupBuyActivityByActivityId(scSkuActivity.getActivityId());
                if (isActivityAvailable(activity, now)) {
                    activityId = activity.getActivityId();
                    activityTarget = activity.getTarget();
                }
            }

            productList.add(MarketProductItemEntity.builder()
                    .goodsId(sku.getGoodsId())
                    .goodsName(sku.getGoodsName())
                    .originalPrice(sku.getOriginalPrice())
                    .activityId(activityId)
                    .activityTarget(activityTarget)
                    .build());
        }

        return productList;
    }

    @Override
    public AdminProductListEntity queryAdminProductList(String source, String channel) {
        List<Sku> skuList = Optional.ofNullable(skuDao.querySkuList()).orElse(Collections.emptyList());
        List<GroupBuyDiscount> discountList = Optional.ofNullable(groupBuyDiscountDao.queryGroupBuyDiscountList()).orElse(Collections.emptyList());
        Map<String, GroupBuyDiscount> discountMap = discountList.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        discount -> String.valueOf(discount.getDiscountId()),
                        discount -> discount,
                        (oldValue, newValue) -> oldValue
                ));
        Map<String, Long> paidOrderCountMap = queryPaidOrderCountMap();

        Date now = new Date();
        List<AdminProductEntity> productList = new ArrayList<>();
        for (Sku sku : skuList) {
            if (null == sku || null == sku.getGoodsId() || sku.getGoodsId().trim().isEmpty()) {
                continue;
            }

            SCSkuActivity queryReq = new SCSkuActivity();
            queryReq.setSource(source);
            queryReq.setChannel(channel);
            queryReq.setGoodsId(sku.getGoodsId());
            SCSkuActivity relation = skuActivityDao.querySCSkuActivityBySCGoodsId(queryReq);
            GroupBuyActivity relationActivity = null == relation ? null : groupBuyActivityDao.queryGroupBuyActivityByActivityId(relation.getActivityId());
            boolean hasUsableActivity = null != relationActivity && isActivityAvailable(relationActivity, now);
            String activityTypeDisplay = "无活动";
            if (hasUsableActivity) {
                GroupBuyDiscount relationDiscount = discountMap.get(String.valueOf(relationActivity.getDiscountId()));
                activityTypeDisplay = convertActivityTypeText(relationActivity) + " / " + convertDiscountPlanText(relationDiscount);
            }

            productList.add(AdminProductEntity.builder()
                    .goodsId(sku.getGoodsId())
                    .goodsName(sku.getGoodsName())
                    .stock(calculateAvailableStock(sku.getStock(), sku.getGoodsId(), paidOrderCountMap))
                    .status("上架")
                    .price(null == sku.getOriginalPrice() ? BigDecimal.ZERO : sku.getOriginalPrice())
                    .activityId(hasUsableActivity ? relation.getActivityId() : null)
                    .activityName(hasUsableActivity ? relationActivity.getActivityName() : "无活动")
                    .activityType(activityTypeDisplay)
                    .activityTypeDisplay(activityTypeDisplay)
                    .build());
        }

        List<String> activityTypes = discountList.stream()
                .filter(this::isAvailableDiscountTier)
                .map(this::convertDiscountPlanText)
                .distinct()
                .collect(Collectors.toList());

        return AdminProductListEntity.builder()
                .productList(productList)
                .activityTypes(activityTypes)
                .build();
    }

    @Override
    public boolean createProduct(String source, String channel, String goodsId, String goodsName, BigDecimal price, Integer stock, Long activityId) {
        Sku sku = buildSku(source, channel, goodsId, goodsName, price, null == stock ? DEFAULT_PRODUCT_STOCK : stock);
        int count = skuDao.insertSku(sku);
        if (count <= 0) {
            return false;
        }
        return syncProductActivityMapping(source, channel, goodsId, activityId);
    }

    @Override
    public boolean updateProduct(String source, String channel, String goodsId, String goodsName, BigDecimal price, Integer stock, Long activityId) {
        Sku sku = buildSku(source, channel, goodsId, goodsName, price, null == stock ? null : calculateConfiguredStock(goodsId, stock));
        int count = skuDao.updateSkuByGoodsId(sku);
        if (count <= 0) {
            return false;
        }
        return syncProductActivityMapping(source, channel, goodsId, activityId);
    }

    @Override
    public boolean deleteProduct(String source, String channel, String goodsId) {
        skuActivityDao.deleteSCSkuActivityByGoodsId(source, channel, goodsId);
        return skuDao.deleteSkuByGoodsId(goodsId) > 0;
    }

    @Override
    public DefaultProductInitializeEntity initializeDefaultProducts(String source, String channel) {
        List<Sku> demoSkus = Optional.ofNullable(skuDefaultService.getDemoSkus()).orElse(Collections.emptyList());
        if (demoSkus.isEmpty()) {
            return DefaultProductInitializeEntity.builder()
                    .total(0)
                    .inserted(0)
                    .build();
        }

        skuActivityDao.deleteSCSkuActivityBySourceChannel(source, channel);
        skuDao.deleteSkuBySourceChannel(source, channel);

        int insertedCount = 0;
        Long defaultActivityId = resolveAvailableActivityId();
        for (Sku demoSku : demoSkus) {
            Sku sku = buildSku(source, channel, demoSku.getGoodsId(), demoSku.getGoodsName(), demoSku.getOriginalPrice(),
                    null == demoSku.getStock() ? DEFAULT_PRODUCT_STOCK : demoSku.getStock());
            insertedCount += skuDao.insertSku(sku);
            if (null != defaultActivityId) {
                syncProductActivityMapping(source, channel, sku.getGoodsId(), defaultActivityId);
            }
        }

        return DefaultProductInitializeEntity.builder()
                .total(demoSkus.size())
                .inserted(insertedCount)
                .defaultActivityId(defaultActivityId)
                .build();
    }

    @Override
    public boolean isTagCrowdRange(String tagId, String userId) {
        RBitSet bitSet = redisService.getBitSet(tagId);
        if (!bitSet.isExists()) return true;
        // 判断用户是否存在人群中
        return bitSet.get(redisService.getIndexFromUserId(userId));
    }

    @Override
    public boolean downgradeSwitch() {
        return dccService.isDowngradeSwitch();
    }

    @Override
    public boolean cutRange(String userId) {
        return dccService.isCutRange(userId);
    }

    @Override
    public List<UserGroupBuyOrderDetailEntity> queryInProgressUserGroupBuyOrderDetailListByOwner(Long activityId, String goodsId, String userId, Integer ownerCount) {
        // 1. 根据用户ID、活动ID，查询用户参与的拼团队伍
        GroupBuyOrderList groupBuyOrderListReq = new GroupBuyOrderList();
        groupBuyOrderListReq.setActivityId(activityId);
        groupBuyOrderListReq.setGoodsId(goodsId);
        groupBuyOrderListReq.setUserId(userId);
        groupBuyOrderListReq.setCount(ownerCount);
        List<GroupBuyOrderList> groupBuyOrderLists = groupBuyOrderListDao.queryInProgressUserGroupBuyOrderDetailListByUserId(groupBuyOrderListReq);
        if (null == groupBuyOrderLists || groupBuyOrderLists.isEmpty()) return null;

        // 2. 过滤队伍获取 TeamId
        Set<String> teamIds = groupBuyOrderLists.stream()
                .map(GroupBuyOrderList::getTeamId)
                .filter(teamId -> teamId != null && !teamId.isEmpty()) // 过滤非空和非空字符串
                .collect(Collectors.toSet());

        // 3. 查询队伍明细，组装Map结构
        List<GroupBuyOrder> groupBuyOrders = groupBuyOrderDao.queryGroupBuyProgressByTeamIds(teamIds);
        if (null == groupBuyOrders || groupBuyOrders.isEmpty()) return null;

        Map<String, GroupBuyOrder> groupBuyOrderMap = groupBuyOrders.stream()
                .collect(Collectors.toMap(GroupBuyOrder::getTeamId, order -> order));

        // 4. 转换数据
        List<UserGroupBuyOrderDetailEntity> userGroupBuyOrderDetailEntities = new ArrayList<>();
        for (GroupBuyOrderList groupBuyOrderList : groupBuyOrderLists) {
            String teamId = groupBuyOrderList.getTeamId();
            GroupBuyOrder groupBuyOrder = groupBuyOrderMap.get(teamId);
            if (null == groupBuyOrder) continue;

            UserGroupBuyOrderDetailEntity userGroupBuyOrderDetailEntity = UserGroupBuyOrderDetailEntity.builder()
                    .userId(groupBuyOrderList.getUserId())
                    .teamId(groupBuyOrder.getTeamId())
                    .activityId(groupBuyOrder.getActivityId())
                    .targetCount(groupBuyOrder.getTargetCount())
                    .completeCount(groupBuyOrder.getCompleteCount())
                    .lockCount(groupBuyOrder.getLockCount())
                    .validStartTime(groupBuyOrder.getValidStartTime())
                    .validEndTime(groupBuyOrder.getValidEndTime())
                    .outTradeNo(groupBuyOrderList.getOutTradeNo())
                    .build();

            userGroupBuyOrderDetailEntities.add(userGroupBuyOrderDetailEntity);
        }

        return userGroupBuyOrderDetailEntities;
    }

    @Override
    public List<UserGroupBuyOrderDetailEntity> queryInProgressUserGroupBuyOrderDetailListByRandom(Long activityId, String goodsId, String userId, Integer randomCount) {
        // 1. 根据用户ID、活动ID，查询用户参与的拼团队伍
        GroupBuyOrderList groupBuyOrderListReq = new GroupBuyOrderList();
        groupBuyOrderListReq.setActivityId(activityId);
        groupBuyOrderListReq.setGoodsId(goodsId);
        groupBuyOrderListReq.setUserId(userId);
        groupBuyOrderListReq.setCount(randomCount * 2); // 查询2倍的量，之后其中 randomCount 数量
        List<GroupBuyOrderList> groupBuyOrderLists = groupBuyOrderListDao.queryInProgressUserGroupBuyOrderDetailListByRandom(groupBuyOrderListReq);
        if (null == groupBuyOrderLists || groupBuyOrderLists.isEmpty()) return null;

        // 判断总量是否大于 randomCount
        if (groupBuyOrderLists.size() > randomCount) {
            // 随机打乱列表
            Collections.shuffle(groupBuyOrderLists);
            // 获取前 randomCount 个元素
            groupBuyOrderLists = groupBuyOrderLists.subList(0, randomCount);
        }

        // 2. 过滤队伍获取 TeamId
        Set<String> teamIds = groupBuyOrderLists.stream()
                .map(GroupBuyOrderList::getTeamId)
                .filter(teamId -> teamId != null && !teamId.isEmpty()) // 过滤非空和非空字符串
                .collect(Collectors.toSet());

        // 3. 查询队伍明细，组装Map结构
        List<GroupBuyOrder> groupBuyOrders = groupBuyOrderDao.queryGroupBuyProgressByTeamIds(teamIds);
        if (null == groupBuyOrders || groupBuyOrders.isEmpty()) return null;

        Map<String, GroupBuyOrder> groupBuyOrderMap = groupBuyOrders.stream()
                .collect(Collectors.toMap(GroupBuyOrder::getTeamId, order -> order));

        // 4. 转换数据
        List<UserGroupBuyOrderDetailEntity> userGroupBuyOrderDetailEntities = new ArrayList<>();
        for (GroupBuyOrderList groupBuyOrderList : groupBuyOrderLists) {
            String teamId = groupBuyOrderList.getTeamId();
            GroupBuyOrder groupBuyOrder = groupBuyOrderMap.get(teamId);
            if (null == groupBuyOrder) continue;

            UserGroupBuyOrderDetailEntity userGroupBuyOrderDetailEntity = UserGroupBuyOrderDetailEntity.builder()
                    .userId(groupBuyOrderList.getUserId())
                    .teamId(groupBuyOrder.getTeamId())
                    .activityId(groupBuyOrder.getActivityId())
                    .targetCount(groupBuyOrder.getTargetCount())
                    .completeCount(groupBuyOrder.getCompleteCount())
                    .lockCount(groupBuyOrder.getLockCount())
                    .validStartTime(groupBuyOrder.getValidStartTime())
                    .validEndTime(groupBuyOrder.getValidEndTime())
                    .build();

            userGroupBuyOrderDetailEntities.add(userGroupBuyOrderDetailEntity);
        }

        return userGroupBuyOrderDetailEntities;
    }

    @Override
    public String queryTeamLeaderUserIdByTeamId(String teamId) {
        return groupBuyOrderListDao.queryTeamLeaderUserIdByTeamId(teamId);
    }

    @Override
    public List<String> queryTeamMemberUserIdsByTeamId(String teamId) {
        return groupBuyOrderListDao.queryTeamMemberUserIdsByTeamId(teamId);
    }

    @Override
    public TeamStatisticVO queryTeamStatisticByActivityId(Long activityId) {
        // 1. 根据活动ID查询拼团队伍
        List<GroupBuyOrderList> groupBuyOrderLists = groupBuyOrderListDao.queryInProgressUserGroupBuyOrderDetailListByActivityId(activityId);

        if (null == groupBuyOrderLists || groupBuyOrderLists.isEmpty()) {
            return new TeamStatisticVO(0, 0, 0);
        }

        // 2. 过滤队伍获取 TeamId
        Set<String> teamIds = groupBuyOrderLists.stream()
                .map(GroupBuyOrderList::getTeamId)
                .filter(teamId -> teamId != null && !teamId.isEmpty()) // 过滤非空和非空字符串
                .collect(Collectors.toSet());

        // 3. 统计数据
        Integer allTeamCount = groupBuyOrderDao.queryAllTeamCount(teamIds);
        Integer allTeamCompleteCount = groupBuyOrderDao.queryAllTeamCompleteCount(teamIds);
        Integer allTeamUserCount = groupBuyOrderDao.queryAllUserCount(teamIds);

        // 4. 构建对象
        return TeamStatisticVO.builder()
                .allTeamCount(allTeamCount)
                .allTeamCompleteCount(allTeamCompleteCount)
                .allTeamUserCount(allTeamUserCount)
                .build();
    }

    private boolean isActivityAvailable(GroupBuyActivity activity, Date now) {
        if (null == activity) return false;
        if (!Objects.equals(activity.getStatus(), 1)) return false;
        if (null == activity.getStartTime() || null == activity.getEndTime()) return false;
        return !now.before(activity.getStartTime()) && !now.after(activity.getEndTime());
    }

    private Long resolveAvailableActivityId() {
        List<GroupBuyActivity> activityList = groupBuyActivityDao.queryGroupBuyActivityList();
        if (null == activityList || activityList.isEmpty()) return null;

        Date now = new Date();
        return activityList.stream()
                .filter(activity -> isActivityAvailable(activity, now))
                .map(GroupBuyActivity::getActivityId)
                .findFirst()
                .orElse(null);
    }

    private boolean isAvailableDiscountTier(GroupBuyDiscount discount) {
        if (null == discount) return false;
        if (!"N".equalsIgnoreCase(discount.getMarketPlan())) return true;
        String expr = null == discount.getMarketExpr() ? "" : discount.getMarketExpr().trim();
        return !"1.99".equals(expr);
    }

    private Map<String, Long> queryPaidOrderCountMap() {
        List<Map<String, Object>> paidCountRows = Optional.ofNullable(groupBuyOrderListDao.queryAdminOrderCountByStatusGroupByGoodsId(COMPLETE_ORDER_STATUS))
                .orElse(Collections.emptyList());
        Map<String, Long> paidCountMap = new HashMap<>();
        for (Map<String, Object> row : paidCountRows) {
            if (null == row) continue;
            String goodsId = parseText(firstPresent(row, "goodsId", "goods_id", "GOODSID", "GOODS_ID"));
            if (goodsId.isEmpty()) continue;
            paidCountMap.merge(goodsId, parseLong(firstPresent(row, "orderCount", "order_count", "ORDERCOUNT", "ORDER_COUNT")), Long::sum);
        }
        if (!paidCountMap.isEmpty()) {
            return paidCountMap;
        }

        List<GroupBuyOrderList> paidOrderList = Optional.ofNullable(groupBuyOrderListDao.queryAdminOrderListByStatus(COMPLETE_ORDER_STATUS))
                .orElse(Collections.emptyList());
        return paidOrderList.stream()
                .filter(Objects::nonNull)
                .map(GroupBuyOrderList::getGoodsId)
                .filter(goodsId -> null != goodsId && !goodsId.trim().isEmpty())
                .collect(Collectors.groupingBy(goodsId -> goodsId, Collectors.counting()));
    }

    private int calculateAvailableStock(Integer configuredStock, String goodsId, Map<String, Long> paidOrderCountMap) {
        int baseStock = null == configuredStock ? DEFAULT_PRODUCT_STOCK : configuredStock;
        long paidCount = Optional.ofNullable(paidOrderCountMap.get(goodsId)).orElse(0L);
        return Math.max(baseStock - (int) paidCount, 0);
    }

    private int calculateConfiguredStock(String goodsId, Integer availableStock) {
        long paidCount = Optional.ofNullable(queryPaidOrderCountMap().get(goodsId)).orElse(0L);
        return availableStock + (int) paidCount;
    }

    private Object firstPresent(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private String parseText(Object value) {
        return null == value ? "" : String.valueOf(value).trim();
    }

    private long parseLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (null == value) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignore) {
            return 0L;
        }
    }

    private String convertActivityTypeText(GroupBuyActivity activity) {
        Integer target = null == activity ? null : activity.getTarget();
        if (null == target || target <= 0) return "拼团";
        return target + "人拼团";
    }

    private String convertDiscountPlanText(GroupBuyDiscount discount) {
        if (null == discount) return "未知优惠";

        String marketPlan = discount.getMarketPlan();
        if ("ZJ".equalsIgnoreCase(marketPlan)) return "直减";
        if ("MJ".equalsIgnoreCase(marketPlan)) return "满减";
        if ("ZK".equalsIgnoreCase(marketPlan)) return "折扣";
        if ("N".equalsIgnoreCase(marketPlan)) {
            String expr = null == discount.getMarketExpr() ? "" : discount.getMarketExpr().trim();
            if (!expr.isEmpty()) {
                return "N" + expr;
            }
            return "N元购";
        }
        return null == marketPlan || marketPlan.trim().isEmpty() ? "未知优惠" : marketPlan;
    }

    private Sku buildSku(String source, String channel, String goodsId, String goodsName, BigDecimal price, Integer stock) {
        Sku sku = new Sku();
        sku.setSource(source);
        sku.setChannel(channel);
        sku.setGoodsId(goodsId);
        sku.setGoodsName(goodsName);
        sku.setOriginalPrice(price);
        sku.setStock(stock);
        return sku;
    }

    private boolean syncProductActivityMapping(String source, String channel, String goodsId, Long activityId) {
        if (null == activityId) {
            skuActivityDao.deleteSCSkuActivityByGoodsId(source, channel, goodsId);
            return true;
        }

        SCSkuActivity queryReq = new SCSkuActivity();
        queryReq.setSource(source);
        queryReq.setChannel(channel);
        queryReq.setGoodsId(goodsId);
        SCSkuActivity existed = skuActivityDao.querySCSkuActivityBySCGoodsId(queryReq);

        GroupBuyActivity activity = groupBuyActivityDao.queryGroupBuyActivityByActivityId(activityId);
        if (null == activity || !Objects.equals(activity.getStatus(), 1)) {
            return false;
        }

        if (null != existed && Objects.equals(existed.getActivityId(), activityId)) {
            return true;
        }

        if (null != existed && !Objects.equals(existed.getActivityId(), activityId)) {
            closeInProgressTeamsForOldActivity(goodsId, existed.getActivityId());
        }

        skuActivityDao.deleteSCSkuActivityByGoodsId(source, channel, goodsId);
        SCSkuActivity scSkuActivity = new SCSkuActivity();
        scSkuActivity.setSource(source);
        scSkuActivity.setChannel(channel);
        scSkuActivity.setActivityId(activityId);
        scSkuActivity.setGoodsId(goodsId);
        skuActivityDao.insertSCSkuActivity(scSkuActivity);

        return null != skuActivityDao.querySCSkuActivityBySCGoodsId(queryReq);
    }

    private void closeInProgressTeamsForOldActivity(String goodsId, Long oldActivityId) {
        if (null == oldActivityId || null == goodsId || goodsId.trim().isEmpty()) {
            return;
        }

        int closedOrderListCount = groupBuyOrderListDao.closeInProgressOrderListByActivityIdGoodsId(oldActivityId, goodsId);
        int closedTeamCount = groupBuyOrderDao.closeInProgressTeamByActivityIdGoodsId(oldActivityId, goodsId);

        if (closedOrderListCount > 0 || closedTeamCount > 0) {
            log.info("切换商品活动自动收口旧团 goodsId:{} oldActivityId:{} closedOrderListCount:{} closedTeamCount:{}",
                    goodsId, oldActivityId, closedOrderListCount, closedTeamCount);
        }
    }

}
