package cn.bugstack.domain.activity.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * @description 首页商品列表条目
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MarketProductItemEntity {

    /** 商品ID */
    private String goodsId;
    /** 商品名称 */
    private String goodsName;
    /** 原始价格 */
    private BigDecimal originalPrice;
    /** 当前可用活动ID */
    private Long activityId;
    /** 当前可用活动目标人数 */
    private Integer activityTarget;

}
