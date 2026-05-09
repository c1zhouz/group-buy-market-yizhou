package cn.bugstack.api.dto;

import lombok.Data;

/**
 * @description 取消未支付拼团订单请求对象
 */
@Data
public class CancelMarketPayOrderRequestDTO {

    /** 用户ID */
    private String userId;
    /** 外部交易单号 */
    private String outTradeNo;

}

