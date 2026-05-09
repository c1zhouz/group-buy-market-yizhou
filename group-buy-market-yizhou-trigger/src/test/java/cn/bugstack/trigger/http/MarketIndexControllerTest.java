package cn.bugstack.trigger.http;

import cn.bugstack.api.dto.MarketProductListResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.activity.model.entity.MarketProductItemEntity;
import cn.bugstack.domain.activity.service.IIndexGroupBuyMarketService;
import cn.bugstack.types.enums.ResponseCode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;

public class MarketIndexControllerTest {

    @Test
    public void queryMarketProductList_shouldReturnPublicProductCatalogFromDomainService() {
        MarketIndexController controller = new MarketIndexController();
        IIndexGroupBuyMarketService indexGroupBuyMarketService = Mockito.mock(IIndexGroupBuyMarketService.class);
        ReflectionTestUtils.setField(controller, "indexGroupBuyMarketService", indexGroupBuyMarketService);

        MarketProductItemEntity product = MarketProductItemEntity.builder()
                .goodsId("10001")
                .goodsName("测试商品")
                .originalPrice(new BigDecimal("99.00"))
                .activityId(1001L)
                .activityTarget(3)
                .build();
        Mockito.when(indexGroupBuyMarketService.queryMarketProductList("s01", "c01"))
                .thenReturn(Collections.singletonList(product));

        Response<MarketProductListResponseDTO> response = controller.queryMarketProductList();

        Assertions.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        Assertions.assertEquals(1, response.getData().getList().size());
        MarketProductListResponseDTO.Product item = response.getData().getList().get(0);
        Assertions.assertEquals("10001", item.getGoodsId());
        Assertions.assertEquals("测试商品", item.getName());
        Assertions.assertEquals(new BigDecimal("99.00"), item.getPrice());
        Assertions.assertEquals(Long.valueOf(1001L), item.getActivityId());
        Assertions.assertEquals("3人拼团", item.getActivityType());
    }
}
