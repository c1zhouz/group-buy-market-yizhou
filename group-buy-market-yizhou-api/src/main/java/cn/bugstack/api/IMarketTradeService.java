package cn.bugstack.api;

import cn.bugstack.api.dto.CancelMarketPayOrderRequestDTO;
import cn.bugstack.api.dto.CancelMarketPayOrderResponseDTO;
import cn.bugstack.api.dto.LockMarketPayOrderRequestDTO;
import cn.bugstack.api.dto.LockMarketPayOrderResponseDTO;
import cn.bugstack.api.dto.SettlementMarketPayOrderRequestDTO;
import cn.bugstack.api.dto.SettlementMarketPayOrderResponseDTO;
import cn.bugstack.api.response.Response;

/**
 * @description 营销交易服务接口
 */
public interface IMarketTradeService {

    /**
     * 营销锁单
     *
     * @param requestDTO 锁单商品信息
     * @return 锁单结果信息
     */
    Response<LockMarketPayOrderResponseDTO> lockMarketPayOrder(LockMarketPayOrderRequestDTO requestDTO);

    /**
     * 营销结算
     *
     * @param requestDTO 结算商品信息
     * @return 结算结果信息
     */
    Response<SettlementMarketPayOrderResponseDTO> settlementMarketPayOrder(SettlementMarketPayOrderRequestDTO requestDTO);

    /**
     * 取消未支付营销订单
     *
     * @param requestDTO 取消请求参数
     * @return 取消结果
     */
    Response<CancelMarketPayOrderResponseDTO> cancelMarketPayOrder(CancelMarketPayOrderRequestDTO requestDTO);

}
