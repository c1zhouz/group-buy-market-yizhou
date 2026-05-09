package cn.bugstack.domain.trade.service.settlement;

import cn.bugstack.domain.trade.adapter.port.ITradePort;
import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import cn.bugstack.types.enums.NotifyTaskHTTPEnumVO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Map;

public class TradeSettlementOrderServiceNotifyTest {

    @Test
    public void execSettlementNotifyJob_shouldNotMarkSuccessWhenNotifyOnlyQueued() throws Exception {
        TradeSettlementOrderService service = new TradeSettlementOrderService();
        ITradeRepository repository = Mockito.mock(ITradeRepository.class);
        ITradePort tradePort = Mockito.mock(ITradePort.class);
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "port", tradePort);

        NotifyTaskEntity notifyTask = NotifyTaskEntity.builder()
                .teamId("T001")
                .notifyUrl("http://127.0.0.1:8091/api/v1/test/group_buy_notify")
                .notifyCount(0)
                .parameterJson("{}")
                .build();
        Mockito.when(repository.queryUnExecutedNotifyTaskList("T001")).thenReturn(Collections.singletonList(notifyTask));
        Mockito.when(tradePort.groupBuyNotify(notifyTask)).thenReturn(NotifyTaskHTTPEnumVO.QUEUED.getCode());
        Mockito.when(repository.updateNotifyTaskStatusRetry("T001")).thenReturn(1);

        Map<String, Integer> result = service.execSettlementNotifyJob("T001");

        Assertions.assertEquals(Integer.valueOf(1), result.get("retryCount"));
        Assertions.assertEquals(Integer.valueOf(0), result.get("successCount"));
        Assertions.assertEquals(Integer.valueOf(1), result.get("queuedCount"));
        Mockito.verify(repository, Mockito.never()).updateNotifyTaskStatusSuccess("T001");
    }
}
