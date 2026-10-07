package plugin.bluearchive;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * 插件配置模型
 * 用户在 Halo 后台设置 Cookie 和 UID
 */
@Data
@Component
public class PluginConfig {
    
    /** B站 Cookie（SESSDATA + bili_jct） */
    private String cookie = "";
    
    /** B站 UID */
    private String biliUid = "";
    
    /** 自动刷新间隔（小时） */
    private int refreshHours = 6;
    
    /** 是否启用自动刷新 */
    private boolean autoRefresh = true;
    
    /** 页面标题 */
    private String pageTitle = "蔚蓝档案战绩";
    
    /** 是否显示学员列表 */
    private boolean showStudents = true;
    
    /** 是否显示总力战记录 */
    private boolean showRaids = true;
    
    /** 学员显示数量上限（<=0 表示不限制，页面默认折叠前6名，点"查看全部"展开） */
    private int maxStudents = 0;

    /** 战绩页主题色（强调色，CSS 颜色值，默认紫色；非法值渲染时回退默认） */
    private String themeColor = "#6d5dfc";

    /**
     * 检查配置是否完整
     */
    @JsonIgnore
    public boolean isConfigured() {
        return cookie != null && !cookie.isEmpty() 
            && biliUid != null && !biliUid.isEmpty();
    }
}
