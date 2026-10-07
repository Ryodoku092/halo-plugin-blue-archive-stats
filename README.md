# Halo Plugin: 蔚蓝档案战绩 (Blue Archive Player Stats)

> 作者: **独凉生** · 许可: MIT · **非官方社区工具**（详见文末完整免责声明）

![Halo](https://img.shields.io/badge/Halo-%3E%3D2.20.0-6d5dfc)
![Java](https://img.shields.io/badge/Java-17%2B-orange)
![License](https://img.shields.io/badge/license-MIT-green)
![AI-made](https://img.shields.io/badge/made%20with-AI%20agents-blueviolet)

自动获取 Bilibili 游戏中心「蔚蓝档案」玩家战绩，在 Halo 博客中渲染为「档案图录」风格的公开战绩页。

---

## 🤖 AI 辅助开发声明（Disclosure）

**本插件主要由 AI 编程代理（AI coding agents）辅助设计与实现**，人类开发者负责需求定义、方案决策、代码审阅与全部功能实测验证。仓库中的每一行代码即为插件的全部行为——**源码完全开源，欢迎安全审计**。

This plugin was developed with substantial assistance from AI coding tools, under human direction, review, and testing.

## ✨ 功能

- 🔗 **前台短链接**：`/ba`、`/stats` 直达战绩页，免登录公开访问
- 📖 **档案图录风战绩页**：暖纸底 + 衬线大数字 + 编号分节，学员立绘装裱展品，官方星级/属性图标（图片直链 B站 CDN，`referrerpolicy=no-referrer`）
- 🗂 **双侧栏**：桌面（≥1440px）左右两栏；移动端（<1440px）转为悬浮按钮 + 抽屉/底部弹层——章节索引（scrollspy）、学员速查（直达展品）、好感 Top5、属性分布版画、战果批注
- 🔄 **自动刷新**：Cookie → 签名 URL → 战绩 API；stale-while-revalidate 读路径不阻塞；ETag/304 复访零字节
- 🎛 **后台可配**：Cookie/UID、刷新周期、主题色、标题、显示开关；配置持久化，重启不丢
- 🕶 **优雅降级**：Cookie 失效时返回 200 空态页 + 可读原因，不白屏不 500
- 🧪 **演示模式**：`/stats/html?demo=1` 用**脱敏存档**渲染完整样式（无需 Cookie）
- 🔓 **公开访问**：RBAC role-template 聚合到 anonymous，读者免登录

## 🎮 适用范围与服务器说明

**本插件仅支持国服（中国大陆服务器）**，由 Bilibili 代理运营。国际服（日服/韩服/台服/全球服）数据不在 B站 接口内，**无法使用**。

国服内支持两个渠道，前提是游戏账号已**绑定至 Bilibili**：

| 渠道 | 说明 |
|---|---|
| **B服**（Bilibili 渠道服） | 直接用 B站账号登录的游戏服 |
| **官服**（悠星渠道） | 用手机号/悠星账号登录的官方服务器，但已在 B站游戏中心完成绑定 |

未绑定的账号请先去 **B站 App → 游戏中心 → 蔚蓝档案 → 绑定角色**，绑定后插件才能查询到数据。

### B站的「主显」机制（重要）

B服和官服**可以同时绑定在同一个 B站账号下，但只能设置一个「主显示」**。本插件通过 B站游戏中心的绑定接口取数，拿到的**永远是当前主显渠道的数据**：

- 想展示 B服战绩 → 在 B站 App 游戏中心把主显切到 B服 → 插件后台点「立即刷新」
- 想展示官服战绩 → 主显切到官服 → 再点「立即刷新」
- 战绩页右上角的服务器标签会自动跟随显示「B站账号」（B服）或「悠星账号」（官服），无需任何配置

> 插件不提供渠道切换按钮——这是 B站接口的设计（`query_list` 只返回主显记录），切换入口在 B站 App 内。

---

## 📦 安装

1. [Releases](../../releases) 下载 `plugin-blue-archive-stats-x.y.z.jar`
2. Halo 控制台 → 插件 → 安装插件 → 上传 JAR → 启用
3. 插件设置：填 B站 UID 与 Cookie（至少含 `SESSDATA`、`bili_jct`、`DedeUserID`）→ 保存 → 立即刷新
4. 前台访问战绩页（三种等价方式）：
   - **短链接（推荐）**：`https://你的域名/ba` 或 `/stats`，可直接加入博客导航菜单
   - 完整路径：`/apis/console.api.blue-archive.halo.run/v1alpha1/stats/html`
   - 文章内嵌 iframe：

```html
<iframe src="/ba" width="100%" height="1200" style="border:none" loading="lazy"></iframe>
```

预览（无需 Cookie）：`/ba?demo=1`

## 🛠 构建

```bash
./gradlew clean jar    # JDK 17+
# 产物: build/libs/plugin-blue-archive-stats-<version>.jar
```

`stub-src/` 为编译期 Halo API 桩（打包时排除）；Fat JAR 仅内联 jackson/slf4j，HTTP 用 JDK 自带 `java.net.http`。

## 🔐 数据与隐私

- 你的 Cookie/UID **只保存在你自己的 Halo 服务器本地文件**（`{halo工作目录}/plugins/configs/`），仅用于向 Bilibili 官方接口请求你自己的战绩数据；本插件不含任何遥测、统计、第三方上报代码（可自行全文检索验证）。
- 战绩页为公开页面：访问者看到的是**你账号的战绩展示**（角色图鉴信息、战绩摘要）。请确认你愿意公开这些内容后再嵌入博客。
- 仓库内演示数据 `demo-record.json` 已脱敏：玩家身份、ID、签名链接、排名与分数均已打码，仅保留游戏公开的图鉴资料。

---

## ⚠️ 完整免责声明 / DISCLAIMER

1. **非官方性质**：本插件是社区第三方开源工具，与 **bilibili（上海宽娱数码科技）、Yostar（悠星网络）、NEXON** 及《蔚蓝档案 / Blue Archive》官方**无任何关联、授权、赞助或背书关系**。
2. **商标与素材归属**："蔚蓝档案 / Blue Archive" 及相关角色名称、立绘、图标等素材的著作权与商标权归其各自权利人（NEXON / Yostar / bilibili）所有。本插件**不分发任何素材文件**——所有图片均以 URL 形式直链 bilibili 官方 CDN，由最终用户浏览器自行加载。
3. **接口不稳定风险**：数据获取依赖 Bilibili 的**非公开网页接口**，可能随时变更、限流或失效，恕不保证持续可用。
4. **账号风险自担**：使用 Cookie 调用接口属于自动化访问，**可能违反 Bilibili 用户协议**并导致账号被风控、封禁等后果。请仅用于个人低频展示自己账号的数据；由此产生的一切后果由使用者自行承担，作者不承担任何责任。
5. **无担保**：软件按 **“AS IS”（现状提供）** 分发，在适用法律允许的最大范围内，作者不提供任何明示或暗示的担保（包括但不限于适销性、特定用途适用性、不侵权）。
6. **责任限制**：在任何情况下，作者不对因使用或无法使用本软件导致的任何损害（数据丢失、账号受限、业务中断等）负责，即使已被告知发生损害的可能性。
7. **合规使用**：请遵守你所在司法辖区及 Bilibili 服务条款的相关规定；不得用于批量爬取、查询他人账号或其他可能损害平台/第三方权益的用途。
8. **AI 辅助开发**：如上文披露，本仓库代码大量由 AI 编程工具生成、经人工审阅与实测；这既是一种透明，也意味着欢迎任何形式的审查与 fork。

> 使用本插件即视为你已阅读并同意上述全部条款。
> By using this plugin you acknowledge and agree to the above.

## 📄 License / 📧 反馈

[MIT](LICENSE)（含上述免责声明）。问题反馈请开 Issue，附 Halo 版本、插件版本与复现步骤。
