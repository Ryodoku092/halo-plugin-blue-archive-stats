package plugin.bluearchive.model;

import lombok.Data;
import java.util.List;

/**
 * 玩家数据模型
 */
@Data
public class PlayerStats {
    private String biliName;
    private String biliAvatar;
    private String roleName;
    private int roleLevel;
    private int loginDays;
    private int charCount;
    private int fiveStar;
    private String maxFavorChar;
    private int maxFavorVal;
    private long totalRaidRank;
    private String totalRaidBoss;
    private long eliminateRank;
    private String eliminateBoss;
    private long multiFloor;       // 无限制决斗最高层数
    private String bannerUrl;      // 官方横幅图 URL (来自 ui_config)
    private List<StudentInfo> students;
    private List<RaidInfo> raids;  // 总力战 + 大决战 按期数排序
    private String updatedAt;
    /** 当前主显渠道名（"B站账号"=B服 / "悠星账号"=官服，来自 distributor_channel_name） */
    private String channelName;
}
