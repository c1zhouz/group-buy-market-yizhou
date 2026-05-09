package cn.bugstack.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * @description 首页商品列表应答对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MarketProductListResponseDTO {

    private List<Product> list;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Product {
        private String goodsId;
        private String sku;
        private String name;
        private BigDecimal price;
        private Long activityId;
        private String activityType;
    }

}
