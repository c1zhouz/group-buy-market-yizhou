package cn.bugstack.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @description 将 docs/ui/html 作为静态资源目录，支持仅启动后端访问前端页面
 */
@Configuration
public class UiStaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(
                        "file:docs/ui/html/",
                        "file:../docs/ui/html/",
                        "classpath:/static/");
    }

}

