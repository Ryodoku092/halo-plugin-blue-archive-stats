package run.halo.app.core.extension.endpoint;

import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import run.halo.app.extension.GroupVersion;

/**
 * Halo Plugin API Stub - 用于编译
 * 形状与真实 Halo API 完全一致（run.halo.app.core.extension.endpoint.CustomEndpoint），
 * 运行时由 Halo 提供真实类，此 stub 不会打包进最终 JAR。
 */
public interface CustomEndpoint {

    RouterFunction<ServerResponse> endpoint();

    default GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.console.halo.run/v1alpha1");
    }
}
