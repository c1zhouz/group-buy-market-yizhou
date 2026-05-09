package cn.bugstack.infrastructure.dcc;

import cn.bugstack.types.annotations.DCCValue;
import cn.bugstack.types.common.Constants;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * @description 动态配置服务
 */
@Service
public class DCCService {

    /**
     * 降级开关 0关闭、1开启
     */
    @DCCValue("downgradeSwitch:0")
    private String downgradeSwitch;

    @DCCValue("cutRange:100")
    private String cutRange;

    @DCCValue("scBlacklist:s02c02")
    private String scBlacklist;

    @DCCValue("tradeLockLuaSwitch:1")
    private String tradeLockLuaSwitch;

    @DCCValue("tradeLockLuaTtlSeconds:1800")
    private String tradeLockLuaTtlSeconds;

    @DCCValue("tradeLockLuaKeyPrefix:group_buy_market_lock")
    private String tradeLockLuaKeyPrefix;

    @DCCValue("notifyHttpSwitch:1")
    private String notifyHttpSwitch;

    @DCCValue("notifyMqSwitch:1")
    private String notifyMqSwitch;

    @DCCValue("notifyMqConsumeBatchSize:20")
    private String notifyMqConsumeBatchSize;

    public boolean isDowngradeSwitch() {
        return "1".equals(downgradeSwitch);
    }

    public boolean isCutRange(String userId) {
        // 计算哈希码的绝对值
        int hashCode = Math.abs(userId.hashCode());

        // 获取最后两位
        int lastTwoDigits = hashCode % 100;

        // 判断是否在切量范围内
        if (lastTwoDigits <= Integer.parseInt(cutRange)) {
            return true;
        }

        return false;
    }

    /**
     * 判断黑名单拦截渠道，true 拦截、false 放行
     */
    public boolean isSCBlackIntercept(String source, String channel) {
        List<String> list = Arrays.asList(scBlacklist.split(Constants.SPLIT));
        return list.contains(source + channel);
    }

    public boolean isTradeLockLuaSwitch() {
        return "1".equals(tradeLockLuaSwitch);
    }

    public long getTradeLockLuaTtlSeconds() {
        return Long.parseLong(tradeLockLuaTtlSeconds);
    }

    public String getTradeLockLuaKeyPrefix() {
        return tradeLockLuaKeyPrefix;
    }

    public boolean isNotifyHttpSwitch() {
        return "1".equals(notifyHttpSwitch);
    }

    public boolean isNotifyMqSwitch() {
        return "1".equals(notifyMqSwitch);
    }

    public int getNotifyMqConsumeBatchSize() {
        return Integer.parseInt(notifyMqConsumeBatchSize);
    }

}
