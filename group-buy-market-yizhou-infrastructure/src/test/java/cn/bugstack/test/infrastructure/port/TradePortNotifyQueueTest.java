package cn.bugstack.test.infrastructure.port;

import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import cn.bugstack.infrastructure.adapter.port.TradePort;
import cn.bugstack.infrastructure.dao.INotifyTaskDao;
import cn.bugstack.infrastructure.dcc.DCCService;
import cn.bugstack.infrastructure.gateway.GroupBuyNotifyService;
import cn.bugstack.infrastructure.redis.IRedisService;
import cn.bugstack.types.enums.NotifyTaskHTTPEnumVO;
import com.alibaba.fastjson2.JSON;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;

public class TradePortNotifyQueueTest {

    @Test
    public void consumeGroupBuyNotifyQueue_shouldMarkNotifyTaskSuccessAfterHttpSuccess() throws Exception {
        TradePort tradePort = new TradePort();
        GroupBuyNotifyService groupBuyNotifyService = Mockito.mock(GroupBuyNotifyService.class);
        IRedisService redisService = Mockito.mock(IRedisService.class);
        DCCService dccService = Mockito.mock(DCCService.class);
        INotifyTaskDao notifyTaskDao = Mockito.mock(INotifyTaskDao.class);
        setField(tradePort, "groupBuyNotifyService", groupBuyNotifyService);
        setField(tradePort, "redisService", redisService);
        setField(tradePort, "dccService", dccService);
        setField(tradePort, "notifyTaskDao", notifyTaskDao);

        NotifyTaskEntity notifyTask = NotifyTaskEntity.builder()
                .teamId("T001")
                .notifyUrl("http://127.0.0.1:8091/api/v1/test/group_buy_notify")
                .parameterJson("{}")
                .build();
        Mockito.when(dccService.isNotifyMqSwitch()).thenReturn(true);
        Mockito.when(redisService.pollFromList("group_buy_notify_mq_queue"))
                .thenReturn(JSON.toJSONString(notifyTask))
                .thenReturn(null);
        Mockito.when(groupBuyNotifyService.groupBuyNotify(notifyTask.getNotifyUrl(), notifyTask.getParameterJson()))
                .thenReturn(NotifyTaskHTTPEnumVO.SUCCESS.getCode());
        Mockito.when(notifyTaskDao.updateNotifyTaskStatusSuccess("T001")).thenReturn(1);

        int consumeCount = tradePort.consumeGroupBuyNotifyQueue(1);

        Assert.assertEquals(1, consumeCount);
        Mockito.verify(notifyTaskDao).updateNotifyTaskStatusSuccess("T001");
        Mockito.verify(redisService, Mockito.never()).addToList(Mockito.anyString(), Mockito.anyString());
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
