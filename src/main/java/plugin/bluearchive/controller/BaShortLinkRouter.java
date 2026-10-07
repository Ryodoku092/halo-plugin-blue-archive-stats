package plugin.bluearchive.controller;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 前台短链接：/ba 与 /stats 直达战绩页。
 *
 * 机制：插件 Spring 上下文中所有 RouterFunction bean 会被 Halo 原样挂载到站点路由
 * （无 /apis 前缀、不经 CustomEndpoint RBAC，与主题页面同为公开访问）。
 * 若主题恰好占用了同名路径，插件路由优先级更高。
 * 支持 ?demo=1 等原有查询参数。
 */
@Component
public class BaShortLinkRouter implements RouterFunction<ServerResponse> {

    private final RouterFunction<ServerResponse> delegate;

    public BaShortLinkRouter(StatsEndpoint statsEndpoint) {
        this.delegate = RouterFunctions.route()
            .GET("/ba", statsEndpoint::statsHtml)
            .GET("/stats", statsEndpoint::statsHtml)
            .build();
    }

    @Override
    public Mono<HandlerFunction<ServerResponse>> route(ServerRequest request) {
        return delegate.route(request);
    }

    @Override
    public void accept(RouterFunctions.Visitor visitor) {
        delegate.accept(visitor);
    }
}
