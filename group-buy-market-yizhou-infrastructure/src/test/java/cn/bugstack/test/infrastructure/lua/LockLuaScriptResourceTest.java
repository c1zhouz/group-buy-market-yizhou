package cn.bugstack.test.infrastructure.lua;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

public class LockLuaScriptResourceTest {

    @Test
    public void should_load_lock_lua_script() throws Exception {
        ClassPathResource resource = new ClassPathResource("lua/lock_market_pay_order.lua");
        String script = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        Assert.assertTrue(script.contains("return 0"));
        Assert.assertTrue(script.contains("SISMEMBER"));
    }

    @Test
    public void should_load_release_lua_script() throws Exception {
        ClassPathResource resource = new ClassPathResource("lua/release_market_pay_order.lua");
        String script = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        Assert.assertTrue(script.contains("return 1"));
        Assert.assertTrue(script.contains("SREM"));
    }
}
