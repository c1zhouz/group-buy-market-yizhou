package cn.bugstack.trigger.http;

import cn.bugstack.api.dto.NotifyRequestDTO;
import cn.bugstack.infrastructure.dao.IGroupBuyOrderListDao;
import cn.bugstack.infrastructure.dao.po.GroupBuyOrderList;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

/**
 * @description 回调服务接口测试
 */
@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/test/")
public class TestApiClientController {

    @Resource
    private IGroupBuyOrderListDao groupBuyOrderListDao;

    /**
     * 模拟回调案例
     *
     * @param notifyRequestDTO 通知回调参数
     * @return success 成功，error 失败
     */
    @RequestMapping(value = "group_buy_notify", method = RequestMethod.POST)
    public String groupBuyNotify(@RequestBody NotifyRequestDTO notifyRequestDTO) {
        log.info("模拟测试第三方服务接收拼团回调 {}", JSON.toJSONString(notifyRequestDTO));

        return "success";
    }

    /**
     * 模拟单独购买回调，不参与拼团组队
     */
    @RequestMapping(value = "direct_buy_notify", method = RequestMethod.POST)
    public String directBuyNotify(@RequestBody Map<String, Object> request) {
        log.info("模拟测试第三方服务接收单独购买回调 {}", JSON.toJSONString(request));

        String userId = String.valueOf(request.getOrDefault("userId", "")).trim();
        String outTradeNo = String.valueOf(request.getOrDefault("outTradeNo", "")).trim();
        if (userId.isEmpty() || outTradeNo.isEmpty()) {
            return "error";
        }

        GroupBuyOrderList existed = groupBuyOrderListDao.queryGroupBuyOrderByOutTradeNo(userId, outTradeNo);
        if (null != existed) {
            return "success";
        }

        String goodsId = String.valueOf(request.getOrDefault("goodsId", "DIRECT-BUY")).trim();
        String source = String.valueOf(request.getOrDefault("source", "s01")).trim();
        String channel = String.valueOf(request.getOrDefault("channel", "c01")).trim();
        BigDecimal payAmount;
        try {
            payAmount = new BigDecimal(String.valueOf(request.getOrDefault("payAmount", "0")));
        } catch (Exception e) {
            payAmount = BigDecimal.ZERO;
        }

        Date now = new Date();
        GroupBuyOrderList directOrder = GroupBuyOrderList.builder()
                .userId(userId)
                .teamId("DIRECT")
                .orderId(outTradeNo)
                .activityId(0L)
                .startTime(now)
                .endTime(now)
                .goodsId(goodsId.isEmpty() ? "DIRECT-BUY" : goodsId)
                .source(source.isEmpty() ? "s01" : source)
                .channel(channel.isEmpty() ? "c01" : channel)
                .originalPrice(payAmount)
                .deductionPrice(BigDecimal.ZERO)
                .status(1)
                .outTradeNo(outTradeNo)
                .outTradeTime(now)
                .bizId("DIRECT-" + outTradeNo)
                .build();
        groupBuyOrderListDao.insert(directOrder);

        return "success";
    }

    /**
     * 模拟单独购买取消回调，记录已取消订单用于后台审计
     */
    @RequestMapping(value = "direct_buy_cancel_notify", method = RequestMethod.POST)
    public String directBuyCancelNotify(@RequestBody Map<String, Object> request) {
        log.info("模拟测试第三方服务接收单独购买取消回调 {}", JSON.toJSONString(request));

        String userId = String.valueOf(request.getOrDefault("userId", "")).trim();
        String outTradeNo = String.valueOf(request.getOrDefault("outTradeNo", "")).trim();
        if (userId.isEmpty() || outTradeNo.isEmpty()) {
            return "error";
        }

        GroupBuyOrderList existed = groupBuyOrderListDao.queryGroupBuyOrderByOutTradeNo(userId, outTradeNo);
        if (null != existed) {
            return "success";
        }

        String goodsId = String.valueOf(request.getOrDefault("goodsId", "DIRECT-BUY")).trim();
        String source = String.valueOf(request.getOrDefault("source", "s01")).trim();
        String channel = String.valueOf(request.getOrDefault("channel", "c01")).trim();
        BigDecimal payAmount;
        try {
            payAmount = new BigDecimal(String.valueOf(request.getOrDefault("payAmount", "0")));
        } catch (Exception e) {
            payAmount = BigDecimal.ZERO;
        }

        Date now = new Date();
        GroupBuyOrderList directOrder = GroupBuyOrderList.builder()
                .userId(userId)
                .teamId("DIRECT")
                .orderId(outTradeNo)
                .activityId(0L)
                .startTime(now)
                .endTime(now)
                .goodsId(goodsId.isEmpty() ? "DIRECT-BUY" : goodsId)
                .source(source.isEmpty() ? "s01" : source)
                .channel(channel.isEmpty() ? "c01" : channel)
                .originalPrice(payAmount)
                .deductionPrice(BigDecimal.ZERO)
                .status(2)
                .outTradeNo(outTradeNo)
                .outTradeTime(now)
                .bizId("DIRECT-CANCEL-" + outTradeNo)
                .build();
        groupBuyOrderListDao.insert(directOrder);

        return "success";
    }

}
