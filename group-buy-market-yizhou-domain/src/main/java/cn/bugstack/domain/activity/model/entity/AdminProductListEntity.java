package cn.bugstack.domain.activity.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @description 后台商品管理列表
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminProductListEntity {

    /** 商品列表 */
    private List<AdminProductEntity> productList;
    /** 可选活动优惠类型 */
    private List<String> activityTypes;

}
