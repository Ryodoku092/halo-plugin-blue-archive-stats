package plugin.bluearchive.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * 总力战/大决战单场记录（官方完整结构）
 */
@Data
public class RaidInfo {
    private String type;          // 总力战 / 大决战
    private String raidType;      // 大决战的装甲类型 (特殊装甲/轻装甲/重装甲/弹力装甲)
    private String boss;
    private String period;        // 期数 (如 "64")
    private int difficulty;       // 难度数字 1-6
    private String difficultyName;// 难度名 (Normal/Hard/HardCore/Extreme/Insane/Torment)
    private long score;
    private long rank;
    private String bossImage;     // Boss 头像 URL
    private String rankIcon;      // 排名图标 URL
    private List<Team> teams = new ArrayList<>();

    /** 通关队伍 */
    @Data
    public static class Team {
        private String label;                 // 部队1 / 部队2
        private List<Member> members = new ArrayList<>();
    }

    /** 队伍成员（官方小头像卡片） */
    @Data
    public static class Member {
        private long id;
        private String name;
        private int lv;
        private int star;
        private int weaponStar;
        private int favor;
        private String avatar;       // 小头像完整 URL
        private String bulletColor;  // 攻击类型颜色(边框/角标底色)
        private String tacticIcon;   // 战术角色小图标(右下角标)
        private String weaponIcon;   // 武器星级图标(左下角标)
    }
}
