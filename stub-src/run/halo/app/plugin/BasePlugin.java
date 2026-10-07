package run.halo.app.plugin;

import org.pf4j.Plugin;

/**
 * Halo Plugin API Stub - 用于编译
 * 形状与真实 Halo API (run.halo:api) 保持一致，运行时由 Halo 提供真实类，
 * 此 stub 不会打包进最终 JAR（build.gradle.kts 中 jar exclude run/**）。
 */
public class BasePlugin extends Plugin {

    protected PluginContext context;

    public BasePlugin(PluginContext pluginContext) {
        this.context = pluginContext;
    }

    public BasePlugin() {
    }

    public PluginContext getContext() {
        return context;
    }
}
