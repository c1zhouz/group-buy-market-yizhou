package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.trade.adapter.port.ITradePort;
import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import cn.bugstack.infrastructure.dao.INotifyTaskDao;
import cn.bugstack.infrastructure.dcc.DCCService;
import cn.bugstack.infrastructure.gateway.GroupBuyNotifyService;
import cn.bugstack.infrastructure.redis.IRedisService;
import cn.bugstack.types.enums.NotifyTaskHTTPEnumVO;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * @description
 */
@Slf4j
@Service
public class TradePort implements ITradePort {

    private static final String GROUP_BUY_NOTIFY_MQ_QUEUE_KEY = "group_buy_notify_mq_queue";

    @Resource
    private GroupBuyNotifyService groupBuyNotifyService;
    @Resource
    private IRedisService redisService;
    @Resource
    private DCCService dccService;
    @Resource
    private INotifyTaskDao notifyTaskDao;

    @Override
    public String groupBuyNotify(NotifyTaskEntity notifyTask) throws Exception {
        RLock lock = redisService.getLock(notifyTask.lockKey());
        try {
            // group-buy-market 拼团服务端会被部署到多台应用服务器上，那么就会有很多任务一起执行。这个时候要进行抢占，避免被多次执行
            if (lock.tryLock(3, 0, TimeUnit.SECONDS)) {
                try {
                    // 无效的 notifyUrl 则直接返回成功
                    if (StringUtils.isBlank(notifyTask.getNotifyUrl()) || "暂无".equals(notifyTask.getNotifyUrl())) {
                        return NotifyTaskHTTPEnumVO.SUCCESS.getCode();
                    }

                    if (dccService.isNotifyHttpSwitch()) {
                        String httpResponse = doHttpNotify(notifyTask);
                        if (NotifyTaskHTTPEnumVO.SUCCESS.getCode().equals(httpResponse)) {
                            return NotifyTaskHTTPEnumVO.SUCCESS.getCode();
                        }
                    }

                    if (dccService.isNotifyMqSwitch()) {
                        redisService.addToList(GROUP_BUY_NOTIFY_MQ_QUEUE_KEY, JSON.toJSONString(notifyTask));
                        return NotifyTaskHTTPEnumVO.QUEUED.getCode();
                    }

                    return NotifyTaskHTTPEnumVO.ERROR.getCode();
                } finally {
                    if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }
            return NotifyTaskHTTPEnumVO.NULL.getCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NotifyTaskHTTPEnumVO.NULL.getCode();
        } catch (Exception e) {
            log.error("回调通知执行异常 teamId:{}", notifyTask.getTeamId(), e);
            return NotifyTaskHTTPEnumVO.ERROR.getCode();
        }
    }

    @Override
    public int consumeGroupBuyNotifyQueue(int batchSize) throws Exception {
        if (!dccService.isNotifyMqSwitch()) {
            return 0;
        }

        int consumeBatchSize = batchSize > 0 ? batchSize : dccService.getNotifyMqConsumeBatchSize();

        int consumeCount = 0;
        int retryBackCount = 0;
        for (int i = 0; i < consumeBatchSize; i++) {
            String notifyTaskJson = redisService.pollFromList(GROUP_BUY_NOTIFY_MQ_QUEUE_KEY);
            if (StringUtils.isBlank(notifyTaskJson)) {
                break;
            }

            NotifyTaskEntity notifyTask = JSON.parseObject(notifyTaskJson, NotifyTaskEntity.class);
            if (null == notifyTask || StringUtils.isBlank(notifyTask.getTeamId())) {
                continue;
            }

            String httpResponse = doHttpNotify(notifyTask);
            if (NotifyTaskHTTPEnumVO.SUCCESS.getCode().equals(httpResponse)) {
                int updateCount = notifyTaskDao.updateNotifyTaskStatusSuccess(notifyTask.getTeamId());
                if (1 == updateCount) {
                    consumeCount += 1;
                } else {
                    redisService.addToList(GROUP_BUY_NOTIFY_MQ_QUEUE_KEY, notifyTaskJson);
                    retryBackCount += 1;
                    log.warn("回调通知 MQ 消费成功但状态更新失败，已重新入队 teamId:{}", notifyTask.getTeamId());
                }
            } else {
                // 回放失败重新入队，防止临时故障导致消息丢失
                redisService.addToList(GROUP_BUY_NOTIFY_MQ_QUEUE_KEY, notifyTaskJson);
                retryBackCount += 1;
            }
        }

        if (consumeCount > 0 || retryBackCount > 0) {
            log.info("回调通知 MQ 队列消费完成 consumeCount:{} retryBackCount:{}", consumeCount, retryBackCount);
        }
        return consumeCount;
    }

    private String doHttpNotify(NotifyTaskEntity notifyTask) {
        try {
            return groupBuyNotifyService.groupBuyNotify(notifyTask.getNotifyUrl(), notifyTask.getParameterJson());
        } catch (Exception e) {
            log.error("回调通知 HTTP 失败 teamId:{}", notifyTask.getTeamId(), e);
            return NotifyTaskHTTPEnumVO.ERROR.getCode();
        }
    }

}
