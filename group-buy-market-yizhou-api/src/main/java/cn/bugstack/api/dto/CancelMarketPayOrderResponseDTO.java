package cn.bugstack.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * @description 取消未支付拼团订单响应对象
 */
@Data
@Builder
public class CancelMarketPayOrderResponseDTO {

    /** 是否成功取消 */
    private Boolean canceled;

}

