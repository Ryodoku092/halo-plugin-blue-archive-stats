package plugin.bluearchive.model;

import lombok.Data;

/**
 * 学员数据模型（含官方图标 URL，全部为完整 URL）
 */
@Data
public class StudentInfo {
    private long id;
    private String name;
    private int lv;
    private int star;
    private int wstar;
    private int favor;
    private String role;          // 战术角色名 (输出/坦克/治疗...)
    private String avatar;        // 头像完整 URL
    private String detail;        // 大立绘完整 URL
    // 官方图标（完整 URL，直接可用）
    private String roleIcon;      // 战术角色图标
    private String bulletName;    // 攻击类型名 (爆发/贯通/贯穿...)
    private String bulletColor;   // 攻击类型颜色
    private String bulletIcon;    // 攻击类型图标
    private String armorName;     // 防御类型名 (轻装甲/重装甲...)
    private String armorColor;    // 防御类型颜色
    private String armorIcon;     // 防御类型图标
    private String favorIcon;     // 好感度心形图标
    private String starIcon;      // 星级图标
    private String weaponIcon;    // 武器星级图标
}
