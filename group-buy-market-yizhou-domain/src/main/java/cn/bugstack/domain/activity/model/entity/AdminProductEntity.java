package cn.bugstack.domain.activity.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * @description 后台商品管理条目
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminProductEntity {

    /** 商品ID */
    private String goodsId;
    /** 商品名称 */
    private String goodsName;
    /** 库存 */
    private Integer stock;
    /** 商品状态 */
    private String status;
    /** 商品价格 */
    private BigDecimal price;
    /** 绑定活动ID */
    private Long activityId;
    /** 绑定活动名称 */
    private String activityName;
    /** 活动类型 */
    private String activityType;
    /** 活动类型展示 */
    private String activityTypeDisplay;

}
