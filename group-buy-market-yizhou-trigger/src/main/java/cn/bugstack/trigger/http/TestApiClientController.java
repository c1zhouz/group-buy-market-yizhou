package cn.bugstack.trigger.http;

import cn.bugstack.api.dto.NotifyRequestDTO;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * @description 回调服务接口测试
 */
@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/test/")
public class TestApiClientController {

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
        return "success";
    }

}
