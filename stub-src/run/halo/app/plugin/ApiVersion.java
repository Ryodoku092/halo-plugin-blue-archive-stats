package run.halo.app.plugin;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Halo Plugin API Stub - 用于编译
 * 形状与真实 Halo API 完全一致（run.halo.app.plugin.ApiVersion），
 * 运行时由 Halo 提供真实类，此 stub 不会打包进最终 JAR。
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiVersion {

    String value();
}
