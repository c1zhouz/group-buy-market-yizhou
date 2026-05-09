package cn.bugstack.trigger.job;

import cn.bugstack.domain.trade.adapter.port.ITradePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @description 拼团回调 MQ 兜底消费任务（当前基于 Redis 列表模拟 MQ）
 */
@Slf4j
@Service
public class GroupBuyNotifyMQJob {

    @Resource
    private ITradePort tradePort;

    @Scheduled(cron = "0/10 * * * * ?")
    public void exec() {
        try {
            int consumeCount = tradePort.consumeGroupBuyNotifyQueue(0);
            if (consumeCount > 0) {
                log.info("定时任务，回调通知 MQ 兜底消费任务 consumeCount:{}", consumeCount);
            }
        } catch (Exception e) {
            log.error("定时任务，回调通知 MQ 兜底消费任务失败", e);
        }
    }
}

