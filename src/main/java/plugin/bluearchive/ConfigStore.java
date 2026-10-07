package plugin.bluearchive;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置持久化
 *
 * PluginConfig 是内存 Bean，Halo 重启即丢失。本类把配置以 JSON 形式
 * 保存到 Halo 工作目录下：
 *   {halo.work-dir}/plugins/configs/plugin-blue-archive-stats/config.json
 * 该目录是插件目录的兄弟目录，插件卸载/重装不会清理它，
 * 因此「重启不丢、重装也不丢」。
 *
 * 读取时机: BlueArchivePlugin.start()（重启后自动恢复）
 * 写入时机: StatsEndpoint.updateConfig()（后台保存配置时）
 */
@Component
public class ConfigStore {

    private static final Logger log = LoggerFactory.getLogger(ConfigStore.class);
    private static final String PLUGIN_NAME = "plugin-blue-archive-stats";
    private final ObjectMapper mapper = new ObjectMapper();

    /** Halo 工作目录（halo.work-dir，默认 ${user.home}/.halo2） */
    @Value("${halo.work-dir:}")
    private String workDir;

    /** 解析配置文件路径；workDir 未配置时回退到 user.home/.halo2 */
    public Path resolveConfigFile() {
        String dir = (workDir == null || workDir.isBlank())
            ? System.getProperty("user.home") + File.separator + ".halo2"
            : workDir;
        return Path.of(dir, "plugins", "configs", PLUGIN_NAME, "config.json");
    }

    /** 启动时从文件恢复配置到内存 Bean（文件不存在则保持默认值） */
    public synchronized void load(PluginConfig config) {
        try {
            Path file = resolveConfigFile();
            if (!Files.exists(file)) {
                log.info("未发现已保存的配置文件: {}（首次运行或从未保存过）", file);
                return;
            }
            mapper.readerForUpdating(config).readValue(file.toFile());
            log.info("已从文件恢复配置: {}（UID={}）", file, config.getBiliUid());
        } catch (Exception e) {
            log.error("加载配置文件失败，使用默认配置", e);
        }
    }

    /** 保存配置到文件（调用失败只记日志，不影响内存中的配置生效） */
    public synchronized void save(PluginConfig config) {
        try {
            Path file = resolveConfigFile();
            Files.createDirectories(file.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), config);
            log.info("配置已持久化: {}", file);
        } catch (Exception e) {
            log.error("保存配置文件失败（配置仍保存在内存中，重启后会丢失）", e);
        }
    }
}
