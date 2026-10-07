package plugin.bluearchive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;
import plugin.bluearchive.service.StatsService;

/**
 * 蔚蓝档案战绩插件（版本见 plugin.yaml）
 *
 * - 可配置 Cookie 和 UID（配置持久化，重启不丢）
 * - 自动刷新（永不过期，每次自动重新获取签名）
 * - 原创样式战绩页（浅/深色自适应 + 可配置主题色）
 * - 当前服务器标签（B站账号/悠星账号）
 * - 博客读者公开访问（role-template 聚合到 anonymous）
 */
@Component
public class BlueArchivePlugin extends BasePlugin {

    private static final Logger log = LoggerFactory.getLogger(BlueArchivePlugin.class);
    private final StatsService statsService;
    private final PluginConfig config;
    private final ConfigStore configStore;

    public BlueArchivePlugin(PluginContext context, StatsService statsService, PluginConfig config,
                             ConfigStore configStore) {
        super(context);
        this.statsService = statsService;
        this.config = config;
        this.configStore = configStore;
    }

    @Override
    public void start() {
        log.info("蔚蓝档案战绩插件启动");
        // 先从持久化文件恢复配置（Halo 重启后 Cookie/UID 不丢失）
        configStore.load(config);
        if (!config.isConfigured()) {
            log.warn("未配置！请设置 Cookie 和 UID");
            return;
        }
        statsService.setConfig(config);
        statsService.initialize();
        if (config.isAutoRefresh()) {
            statsService.startAutoRefresh(config.getRefreshHours() * 3600000L);
            log.info("自动刷新: 每 {} 小时", config.getRefreshHours());
        }
    }

    @Override
    public void stop() {
        statsService.stop();
        log.info("插件已停止");
    }
}
