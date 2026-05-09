package cn.bugstack.domain.activity.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @description 默认商品初始化结果
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DefaultProductInitializeEntity {

    /** 默认商品总数 */
    private int total;
    /** 成功写入数量 */
    private int inserted;
    /** 默认绑定活动ID */
    private Long defaultActivityId;

}
