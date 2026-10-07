package run.halo.app.plugin;

import org.pf4j.RuntimeMode;

/**
 * Halo Plugin API Stub - 用于编译
 * 形状与真实 Halo API 保持一致（@RequiredArgsConstructor + @Getter 的等效手写版本），
 * 运行时由 Halo 提供真实类，此 stub 不会打包进最终 JAR。
 */
public class PluginContext {

    private final String name;
    private final String configMapName;
    private final String version;
    private final RuntimeMode runtimeMode;

    public PluginContext(String name, String configMapName, String version, RuntimeMode runtimeMode) {
        this.name = name;
        this.configMapName = configMapName;
        this.version = version;
        this.runtimeMode = runtimeMode;
    }

    public String getName() {
        return name;
    }

    public String getConfigMapName() {
        return configMapName;
    }

    public String getVersion() {
        return version;
    }

    public RuntimeMode getRuntimeMode() {
        return runtimeMode;
    }
}
