package cn.bugstack.types.annotations;

import java.lang.annotation.*;

/**
 * @description 注解，动态配置中心标记
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
@Documented
public @interface DCCValue {

    String value() default "";

}
