package plugin.bluearchive.controller;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import plugin.bluearchive.ConfigStore;
import plugin.bluearchive.PluginConfig;
import plugin.bluearchive.model.PlayerStats;
import plugin.bluearchive.model.RaidInfo;
import plugin.bluearchive.model.StudentInfo;
import plugin.bluearchive.service.StatsService;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 战绩数据 API（CustomEndpoint 方式）
 *
 * Halo 的插件自定义 API 通过 CustomEndpoint 注册（RouterFunction），
 * Halo 自动为路由添加 /apis/{group}/{version} 前缀。
 * 实际路径: /apis/console.api.blue-archive.halo.run/v1alpha1/*
 *
 * 战绩页「档案图录」风格:
 * - 暖白纸底 + 衬线大数字 + 01/02/03 编号分节 + 发丝线分隔
 * - 全程无卡片框无阴影：只有学员立绘用细框装裱（博物馆展品式）
 * - 页头 = 档案题名（眉标+衬线大名+更新日期印章）
 * - 战绩 = 图录记录条（细线分隔的条目列表）
 * - 交互保留: 折叠前6名/查看全部=底部弹层+拖拽全屏
 * - 数据解析/图片逻辑零改动（URL仍来自API字段+no-referrer）
 */
@Component
public class StatsEndpoint implements CustomEndpoint {

    private final StatsService statsService;
    private final PluginConfig config;
    private final ConfigStore configStore;

    public StatsEndpoint(StatsService statsService, PluginConfig config, ConfigStore configStore) {
        this.statsService = statsService;
        this.config = config;
        this.configStore = configStore;
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion("console.api.blue-archive.halo.run", "v1alpha1");
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("/stats", this::getStats)
            .GET("/stats/html", this::statsHtml)
            .GET("/config", this::getConfig)
            .POST("/config", this::updateConfig)
            .POST("/refresh", this::refresh)
            .GET("/admin", this::adminPage)
            .build();
    }

    private Mono<ServerResponse> getStats(ServerRequest request) {
        return Mono.fromCallable(statsService::getStats)
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(s -> s != null
                ? ServerResponse.ok().bodyValue(s)
                : ServerResponse.status(503).build());
    }

    /** 战绩页 HTML（供 /apis 端点与 /ba、/stats 短链共用） */
    public Mono<ServerResponse> statsHtml(ServerRequest request) {
        return Mono.fromCallable(() -> {
            PlayerStats s;
            if ("1".equals(request.queryParam("demo").orElse(""))) {
                s = demoStats(); // 演示模式：无需有效 Cookie，便于预览新版设计
            } else {
                s = statsService.getStats();
            }
            if (s == null) {
                // 无有效数据：返回 200 优雅「暂无数据」档案页，不破坏桌面预览
                return ServerResponse.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .bodyValue(renderEmptyPage());
            }
            String html = renderStatsPageCached(s);
            String etag = "W/\"" + Integer.toHexString(html.hashCode()) + "-" + html.length() + "\"";
            String inm = request.headers().firstHeader("If-None-Match");
            if (etag.equals(inm)) {
                // 读者复访: 304 空响应，165KB 全文不出网关
                return ServerResponse.status(304).header("ETag", etag).build();
            }
            return ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML)
                .header("ETag", etag)
                .header("Cache-Control", "public, max-age=300, must-revalidate")
                .bodyValue(html);
        }).subscribeOn(Schedulers.boundedElastic())
            .flatMap(resp -> resp);
    }

    /** 无数据时的优雅占位档案页（200） */
    private String renderEmptyPage() {
        String ac = sanitizeColor(config.getThemeColor(), "#6d5dfc");
        return """
            <!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8"/>
            <meta name="viewport" content="width=device-width,initial-scale=1"/>
            <title>%s</title>
            <style>
            :root{--paper:#f7f4ee;--ink:#26221b;--sub:#7a7263;--line:#ddd6c8;--ac:%s;
              --serif:Georgia,"Times New Roman","Songti SC","STSong",serif;
              --sans:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif}
            @media(prefers-color-scheme:dark){:root{--paper:#191712;--ink:#e9e3d6;--sub:#9a917f;--line:#3a352b}}
            *{margin:0;padding:0;box-sizing:border-box}
            body{background:var(--paper);color:var(--ink);font:15px/1.75 var(--sans);-webkit-font-smoothing:antialiased}
            .ac-page{max-width:840px;margin:0 auto;padding:64px 24px}
            .ac-eyebrow{display:flex;align-items:flex-end;gap:14px;font-size:12px;letter-spacing:.28em;text-transform:uppercase;color:var(--sub);padding-bottom:10px}
            .ac-eyebrow::after{content:"";flex:1;border-bottom:1px solid var(--line);transform:translateY(-4px)}
            .ac-empty{text-align:center;padding:80px 0;border-bottom:1px solid var(--line)}
            .ac-empty h2{font:600 26px/1.3 var(--serif);letter-spacing:.06em}
            .ac-empty p{color:var(--sub);margin-top:14px;font-size:14px;letter-spacing:.04em}
            .ac-empty .hint{font-size:12px;color:var(--ac);margin-top:22px;letter-spacing:.1em}
            </style></head><body>
            <div class="ac-page">
              <div class="ac-eyebrow">Player Archive &middot; 玩家图录</div>
              <div class="ac-empty">
                <h2>档案暂无数据</h2>
                <p>当前 Bilibili 凭据尚未就绪或已失效，无法拉取战绩。</p>
                <p class="hint">请在插件后台重新配置 Cookie / UID，或访问 ?demo=1 预览样式</p>
              </div>
            </div></body></html>
            """.formatted(esc(config.getPageTitle()), ac);
    }

    /**
     * 演示模式（?demo=1）：直接复用插件打包的真实存档（demo-record.json），
     * 走 StatsService.parseApiResponse() 复用与线上完全一致的数据解析与渲染链路，
     * 学员头像/立绘/战绩 Boss 图均为真实 B站 CDN 图片（referrerpolicy=no-referrer）。
     * 无需有效 Cookie，便于预览「档案图录」设计与验证真实渲染。
     */
    private PlayerStats demoStats() {
        PlayerStats s = demoParsed;
        if (s == null) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("demo-record.json")) {
                if (in == null) {
                    return null;
                }
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                s = statsService.parseApiResponse(json);
                if (s != null) {
                    if (s.getChannelName() == null || s.getChannelName().isEmpty()) {
                        s.setChannelName("B站账号");
                    }
                    demoParsed = s; // 解析链路重(450KB JSON→174学员+战绩)，只解析一次
                } else {
                    return null;
                }
            } catch (Exception e) {
                return null;
            }
        }
        s.setUpdatedAt(LocalDateTime.now().format(StatsService.TS_FMT)); // 印章保持"现在"
        return s;
    }
    /** demo 存档解析结果缓存（数据静态，仅 updatedAt 每次刷新） */
    private volatile PlayerStats demoParsed;

    private Mono<ServerResponse> getConfig(ServerRequest request) {
        return Mono.fromCallable(() -> Map.<String, Object>of(
            "biliUid", config.getBiliUid(),
            "cookieSet", !config.getCookie().isEmpty(),
            "refreshHours", config.getRefreshHours(),
            "autoRefresh", config.isAutoRefresh(),
            "pageTitle", config.getPageTitle(),
            "showStudents", config.isShowStudents(),
            "showRaids", config.isShowRaids(),
            "maxStudents", config.getMaxStudents(),
            "themeColor", config.getThemeColor(),
            "lastError", statsService.getLastError()
        )).subscribeOn(Schedulers.boundedElastic())
            .flatMap(ServerResponse.ok()::bodyValue);
    }

    private Mono<ServerResponse> updateConfig(ServerRequest request) {
        return request
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .flatMap(body -> Mono.fromCallable(() -> {
                if (body.containsKey("cookie")) config.setCookie((String) body.get("cookie"));
                if (body.containsKey("biliUid")) config.setBiliUid((String) body.get("biliUid"));
                if (body.containsKey("refreshHours")) config.setRefreshHours(((Number) body.get("refreshHours")).intValue());
                if (body.containsKey("autoRefresh")) config.setAutoRefresh((Boolean) body.get("autoRefresh"));
                if (body.containsKey("pageTitle")) config.setPageTitle((String) body.get("pageTitle"));
                if (body.containsKey("showStudents")) config.setShowStudents((Boolean) body.get("showStudents"));
                if (body.containsKey("showRaids")) config.setShowRaids((Boolean) body.get("showRaids"));
                if (body.containsKey("maxStudents")) config.setMaxStudents(((Number) body.get("maxStudents")).intValue());
                if (body.containsKey("themeColor")) config.setThemeColor((String) body.get("themeColor"));

                statsService.setConfig(config);
                // 持久化到 Halo 工作目录（重启/重装不丢）
                configStore.save(config);
                return Map.of("status", "ok");
            }).subscribeOn(Schedulers.boundedElastic()))
            .flatMap(ServerResponse.ok()::bodyValue);
    }

    private Mono<ServerResponse> refresh(ServerRequest request) {
        return Mono.fromCallable(() -> {
            PlayerStats stats = statsService.refreshStats();
            if (stats != null) {
                return ServerResponse.ok()
                    .bodyValue(Map.of("status", "ok", "charCount", stats.getCharCount()));
            }
            // 刷新失败（多为 Cookie 过期）→ 不抛 500，返回 200 + 可读原因，前端据以提示
            String reason = statsService.getLastError();
            if (reason == null || reason.isEmpty()) reason = "数据刷新失败（未配置或网络异常）";
            return ServerResponse.ok()
                .bodyValue(Map.of("status", "error", "message", reason));
        }).subscribeOn(Schedulers.boundedElastic())
            .flatMap(resp -> resp);
    }

    /**
     * 独立配置页面（纯 HTML，不依赖插件 UI）
     */
    private Mono<ServerResponse> adminPage(ServerRequest request) {
        return Mono.fromCallable(() -> {
            String cached = adminHtmlCache;
            if (cached != null) {
                return ServerResponse.ok().contentType(MediaType.TEXT_HTML).bodyValue(cached);
            }
            try (InputStream is = getClass().getResourceAsStream("/config-page.html")) {
                if (is == null) {
                    return ServerResponse.status(404).bodyValue("配置页面未找到");
                }
                String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                adminHtmlCache = html;
                return ServerResponse.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .bodyValue(html);
            } catch (Exception e) {
                return ServerResponse.status(500).bodyValue("加载配置页面失败: " + e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic())
            .flatMap(resp -> resp);
    }
    /** 配置页 HTML 缓存（静态资源，读一次） */
    private volatile String adminHtmlCache;

    // ================================================================
    // 战绩页渲染 —— 「档案图录」风
    //
    // 设计原则:
    //   - 暖白纸底 + 发丝线 + 衬线展示字，像一本印刷图录
    //   - 编号分节: 01 概览 / 02 学员 / 03 总力战 / 04 大决战
    //   - 无卡片框、无阴影、无圆角容器；只有学员立绘细框装裱
    //   - 战绩为图录记录条（条目间发丝线分隔）
    //   - 主题色仅用于编号/悬停/印章描边（#hex 白名单校验）
    //   - 交互: 折叠前6名，「查看全部」=底部弹层+拖拽全屏
    // 保留不变:
    //   - 全部数据解析逻辑（StatsService 零改动）
    //   - 图片逻辑: URL 仍来自B站API字段，referrerpolicy="no-referrer"
    // ================================================================

    /** 渲染结果缓存: 174 展品 HTML ~160KB，读者高频访问时同数据只渲染一次；
     *  key = 数据更新时间 + 影响渲染的配置项，数据刷新/改配置自动失效 */
    private volatile String htmlCache;
    private volatile String htmlCacheKey;

    private String renderStatsPageCached(PlayerStats s) {
        String key = s.getUpdatedAt() + "|" + config.getThemeColor() + "|" + config.getPageTitle()
            + "|" + config.isShowStudents() + "|" + config.isShowRaids() + "|" + config.getMaxStudents();
        String c = htmlCache;
        if (c != null && key.equals(htmlCacheKey)) {
            return c;
        }
        c = renderStatsPage(s);
        htmlCache = c;
        htmlCacheKey = key;
        return c;
    }

    /** 插件作者（与 plugin.yaml spec.author.name 一致，公开页版记落款） */
    public static final String AUTHOR = "独凉生";

    private String renderStatsPage(PlayerStats s) {
        String ac = sanitizeColor(config.getThemeColor(), "#6d5dfc");
        String channel = s.getChannelName() == null ? "" : s.getChannelName().trim();
        String server = channel.isEmpty() ? "" : "<span class=\"ac-server\">" + esc(channel) + "</span>";
        String channelFoot = channel.isEmpty() ? "" : "（" + esc(channel) + "）";

        // ---- 卷首横幅（官方 banner 图 + 纸底渐隐 + 细金线收边） ----
        String hero = "";
        if (!empty(s.getBannerUrl())) {
            hero = "<div class=\"ac-hero\"><img src=\"" + esc(s.getBannerUrl()) +
                "\" alt=\"\" referrerpolicy=\"no-referrer\" onerror=\"this.parentNode.style.display='none'\"/></div>";
        }

        int secNo = 1; // 01 概览固定

        // ---- 学员展品（细框装裱） ----
        StringBuilder pl = new StringBuilder();
        int total = 0;
        if (config.isShowStudents() && s.getStudents() != null) {
            int cap = config.getMaxStudents(); // <=0 表示不限制
            int idx = 0;
            for (StudentInfo st : s.getStudents()) {
                if (cap > 0 && idx >= cap) break;
                String bulletColor = empty(st.getBulletColor()) ? "#8a93a6" : st.getBulletColor();
                String armorColor = empty(st.getArmorColor()) ? bulletColor : st.getArmorColor();
                String detail = empty(st.getDetail()) ? st.getAvatar() : st.getDetail();
                String name = empty(st.getName()) ? "-" : st.getName();
                // 官方星级图标（有图标用图标，无图标回退文字星）
                String stars = iconRow(st.getStarIcon(), st.getStar())
                        + (st.getWstar() > 0 ? " " + iconRow(st.getWeaponIcon(), st.getWstar()) : "");
                // 好感度官方图标
                String favHtml = empty(st.getFavorIcon())
                    ? "<span class=\"ac-fav\">&#9829; " + st.getFavor() + "</span>"
                    : "<span class=\"ac-fav\"><img src=\"" + esc(st.getFavorIcon()) + "\" alt=\"\" referrerpolicy=\"no-referrer\"/>"
                      + st.getFavor() + "</span>";
                // 武器/装甲属性图标 chip（带官方色描边）
                String attrs = attrChip(st.getBulletIcon(), st.getBulletName(), st.getBulletColor())
                             + attrChip(st.getArmorIcon(), st.getArmorName(), st.getArmorColor())
                             + attrChip(st.getRoleIcon(), st.getRole(), "");
                pl.append(String.format("""
                    <figure class="ac-plate%s" id="pl-%d">
                      <div class="ac-frame">
                        <img src="%s" loading="lazy" referrerpolicy="no-referrer" onerror="this.style.display='none'"/>
                        %s
                      </div>
                      <figcaption>
                        <div class="ac-cap"><span class="ac-capn">%s</span><span class="ac-caplv">Lv.%d</span></div>
                        <div class="ac-caps">%s</div>
                        <div class="ac-capa">%s</div>
                      </figcaption>
                    </figure>""",
                    idx < 6 ? "" : " ac-fold",
                    st.getId(),
                    esc(detail),
                    favHtml,
                    esc(name), st.getLv(),
                    stars, attrs));
                idx++;
            }
            total = idx;
        }

        String studentsSection = "";
        List<String> navItems = new ArrayList<>();
        navItems.add("1\t01\t概览"); // 概览固定第一
        if (pl.length() > 0) {
            secNo++;
            navItems.add(secNo + "\t" + pad(secNo) + "\t学员");
            String moreBtn = total > 6
                ? "<button class=\"ac-more\" id=\"vaBtn\" onclick=\"toggleStudents()\">查看全部 "
                    + total + " 名学员 &rarr;</button>"
                : "";
            studentsSection = "<section class=\"ac-sec\" id=\"sec-" + secNo + "\">"
                + "<div class=\"ac-sechd\"><span class=\"ac-no\">" + pad(secNo)
                + "</span><h2>学员</h2><span class=\"ac-meta\">共 " + s.getCharCount() + " 名</span></div>"
                + "<div class=\"ac-plates\" id=\"sg\">"
                + "<div class=\"ac-shed\" id=\"sgHd\">"
                + "<div class=\"ac-grip\" id=\"sgGrip\"><i></i></div>"
                + "<div class=\"ac-shrow\"><span>学员图录 · 共 " + s.getCharCount()
                + " 名</span><button class=\"ac-x\" onclick=\"toggleStudents()\">&times;</button></div>"
                + "</div>"
                + pl + "</div>" + moreBtn + "</section>";
        }

        // ---- 战绩图录记录 ----
        StringBuilder rb = new StringBuilder();
        if (config.isShowRaids() && s.getRaids() != null && !s.getRaids().isEmpty()) {
            RaidInfo latestTotal = null, latestEl = null;
            for (RaidInfo r : s.getRaids()) {
                if ("总力战".equals(r.getType())) {
                    if (latestTotal == null || periodNum(r) > periodNum(latestTotal)) latestTotal = r;
                } else {
                    if (latestEl == null || periodNum(r) > periodNum(latestEl)) latestEl = r;
                }
            }
            if (latestTotal != null) {
                secNo++;
                navItems.add(secNo + "\t" + pad(secNo) + "\t总力战");
                rb.append(renderRaidSection(secNo, "总力战", s.getRaids(), "总力战", periodNum(latestTotal)));
            }
            if (latestEl != null) {
                secNo++;
                navItems.add(secNo + "\t" + pad(secNo) + "\t大决战");
                rb.append(renderRaidSection(secNo, "大决战", s.getRaids(), "大决战", periodNum(latestEl)));
            }
        }

        // ---- 双侧栏: 左=卷首索引 / 右=附录批注 ----
        String railLeft = buildRailLeft(s, navItems);
        String railRight = buildRailRight(s);

        String html = """
            <!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8"/>
            <meta name="viewport" content="width=device-width,initial-scale=1"/>
            <title>{{TITLE}}</title>
            <style>
            :root{
              --paper:#f7f4ee;--ink:#26221b;--sub:#7a7263;--line:#ddd6c8;--mat:#ffffff;
              --ac:{{AC}};--serif:Georgia,"Times New Roman","Songti SC","STSong",serif;
              --sans:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif
            }
            @media(prefers-color-scheme:dark){
              :root{--paper:#191712;--ink:#e9e3d6;--sub:#9a917f;--line:#3a352b;--mat:#211e17}
            }
            *{margin:0;padding:0;box-sizing:border-box}
            html{-webkit-text-size-adjust:100%}
            body{background:var(--paper);color:var(--ink);font:15px/1.75 var(--sans);-webkit-font-smoothing:antialiased}
            img{display:block;max-width:100%}
            body::before{content:"";position:fixed;inset:0;pointer-events:none;z-index:-1;opacity:.35;
              background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='140' height='140'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2' stitchTiles='stitch'/%3E%3CfeColorMatrix values='0 0 0 0 0.3 0 0 0 0 0.27 0 0 0 0 0.2 0 0 0 0.05 0'/%3E%3C/filter%3E%3Crect width='140' height='140' filter='url(%23n)'/%3E%3C/svg%3E")}

            .ac-page{max-width:840px;margin:0 auto;padding:44px 24px 64px}
            /* ---- 档案题名 ---- */
            .ac-eyebrow{display:flex;align-items:flex-end;gap:14px;font-size:12px;letter-spacing:.28em;text-transform:uppercase;color:var(--sub);padding-bottom:10px}
            .ac-eyebrow::after{content:"";flex:1;border-bottom:1px solid var(--line);transform:translateY(-4px)}
            .ac-id{display:flex;gap:20px;align-items:flex-start;padding:26px 0 22px;border-bottom:1px solid var(--line)}
            .ac-avatar{width:60px;height:60px;border-radius:50%;object-fit:cover;border:1px solid var(--line);background:var(--mat);flex-shrink:0}
            .ac-idmain{flex:1;min-width:0}
            .ac-name{font:600 32px/1.2 var(--serif);letter-spacing:.01em;display:flex;align-items:center;gap:12px;flex-wrap:wrap}
            .ac-server{font:400 12px/1 var(--sans);letter-spacing:.1em;color:var(--sub);border:1px solid var(--line);border-radius:999px;padding:5px 10px}
            .ac-sub{font-size:13px;color:var(--sub);letter-spacing:.06em;margin-top:8px}
            .ac-stamp{flex-shrink:0;font:12px/1.5 var(--sans);color:var(--ac);border:1.5px solid var(--ac);border-radius:4px;padding:6px 10px;text-align:right;transform:rotate(-2deg);opacity:.85}
            .ac-stamp b{display:block;font-weight:600;letter-spacing:.05em}
            /* ---- 分节标题 ---- */
            .ac-sec{margin-top:44px}
            .ac-sechd{display:flex;align-items:baseline;gap:14px;border-bottom:1px solid var(--ink);padding-bottom:8px;margin-bottom:22px}
            .ac-no{font:600 15px/1 var(--serif);color:var(--ac);letter-spacing:.08em;border:1px solid var(--ac);border-radius:2px;padding:4px 7px}
            .ac-sechd h2{font:600 19px/1 var(--serif);letter-spacing:.14em}
            .ac-meta{margin-left:auto;font-size:12px;color:var(--sub);letter-spacing:.08em}
            /* ---- 01 概览: 衬线大数字 + 记录行 ---- */
            .ac-figures{display:flex;flex-wrap:wrap;gap:8px 0}
            .ac-fig{flex:1 1 140px;padding:6px 18px 14px;border-left:1px solid var(--line);min-width:0}
            .ac-fig:first-child{border-left:none;padding-left:0}
            .ac-num{font:600 44px/1.1 var(--serif);letter-spacing:-.01em;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
            .ac-num.is-text{font-size:30px;padding-top:9px}
            .ac-numlab{font-size:11px;letter-spacing:.18em;color:var(--sub);margin-top:6px}
            .ac-rows{margin-top:18px;border-top:1px solid var(--line)}
            .ac-row{display:flex;justify-content:space-between;align-items:baseline;gap:16px;padding:11px 0;border-bottom:1px solid var(--line);font-size:14px}
            .ac-row .k{color:var(--sub);letter-spacing:.08em;flex-shrink:0}
            .ac-row .v{font-family:var(--serif);font-size:16px;font-weight:600;text-align:right}
            .ac-row .v i{font:400 12px var(--sans);color:var(--sub);margin-left:8px;font-style:normal}
            /* ---- 卷首横幅 + 图录增强 ---- */
            .ac-hero{position:relative;overflow:hidden;border:1px solid var(--line);border-bottom:2px solid var(--ink);margin-bottom:24px;background:var(--mat)}
            .ac-hero img{width:100%;height:190px;object-fit:cover;display:block}
            .ac-hero::after{content:"";position:absolute;left:0;right:0;bottom:0;height:56px;background:linear-gradient(transparent,var(--paper))}
            .ac-icorow{display:inline-flex;gap:1px;vertical-align:-2px}
            .ac-icorow img{width:13px;height:13px;display:block}
            .ac-chip{display:inline-flex;align-items:center;gap:4px;font-size:11px;color:var(--sub);letter-spacing:.06em;border:1px solid var(--line);border-radius:2px;padding:1px 6px 1px 3px}
            .ac-chip img{width:13px;height:13px;display:block}
            .ac-fav{position:absolute;top:14px;right:14px;display:inline-flex;align-items:center;gap:4px;font:600 12px/1.6 var(--sans);color:#c0392b;background:var(--paper);border:1px solid var(--line);padding:1px 7px;letter-spacing:.04em}
            .ac-fav img{width:14px;height:14px;display:block}
            .ac-rankico{width:38px;height:38px;border-radius:50%;object-fit:cover;border:1px solid var(--line);background:var(--mat);flex-shrink:0;margin-top:6px}
            /* ---- 02 学员: 装裱展品 ---- */
            .ac-plates{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:30px 24px}
            .ac-plate{min-width:0}
            .ac-plate.ac-fold{display:none}
            .ac-frame{position:relative;border:1px solid var(--line);background:var(--mat);padding:8px}
            .ac-frame img{width:100%;aspect-ratio:404/456;object-fit:cover;background:var(--paper)}
            .ac-frame{transition:border-color .2s}
            .ac-plate:hover .ac-frame{border-color:var(--ac)}
            .ac-plate:hover .ac-frame img{transform:scale(1.015)}
            .ac-frame img{transition:transform .3s ease}
            .ac-cap{display:flex;justify-content:space-between;align-items:baseline;gap:8px;margin-top:10px}
            .ac-capn{font:600 15px/1.3 var(--serif);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
            .ac-caplv{font-size:11px;color:var(--sub);letter-spacing:.06em;flex-shrink:0}
            .ac-caps{font-size:12px;letter-spacing:2px;margin-top:3px;line-height:1.4}
            .ac-capa{font-size:11px;color:var(--sub);letter-spacing:.1em;margin-top:4px;display:flex;flex-wrap:wrap;gap:3px 10px}
            .ac-capa .a{display:inline-flex;align-items:center;gap:5px}
            .ac-capa .d{width:6px;height:6px;border-radius:50%;flex-shrink:0}
            .ac-more{display:block;width:100%;margin-top:26px;background:none;font:400 13px/1 var(--sans);letter-spacing:.14em;color:var(--sub);border:none;border-top:1px solid var(--line);border-bottom:1px solid var(--line);padding:14px 0;cursor:pointer;transition:color .15s}
            .ac-more:hover{color:var(--ac)}
            /* ---- 03/04 战绩: 图录记录条 ---- */
            .ac-records{list-style:none}
            .ac-record{display:flex;gap:16px;padding:16px 0;border-bottom:1px solid var(--line)}
            .ac-record:first-child{border-top:1px solid var(--line)}
            .ac-boss{width:46px;height:46px;border-radius:50%;object-fit:cover;border:1px solid var(--line);background:var(--mat);flex-shrink:0}
            .ac-rec{flex:1;min-width:0}
            .ac-rect{display:flex;align-items:baseline;gap:10px;flex-wrap:wrap}
            .ac-recn{font:600 17px/1.3 var(--serif)}
            .ac-recsub{font-size:12px;color:var(--sub);letter-spacing:.06em}
            .ac-recdiff{font-size:11px;letter-spacing:.12em;color:var(--ac);border:1px solid var(--line);padding:2px 8px;text-transform:uppercase}
            .ac-recstats{font-size:13px;color:var(--sub);margin-top:5px;letter-spacing:.04em}
            .ac-recstats b{font:600 15px var(--serif);color:var(--ink);margin-left:4px}
            .ac-recstats .sep{margin:0 12px;color:var(--line)}
            .ac-team{display:flex;align-items:center;gap:10px;margin-top:10px}
            .ac-tlabel{font-size:11px;color:var(--sub);letter-spacing:.12em;flex-shrink:0}
            .ac-tmems{display:flex;gap:8px;flex-wrap:wrap}
            .ac-mem{width:44px;text-align:center}
            .ac-mem img{width:34px;height:34px;border-radius:50%;object-fit:cover;border:1px solid var(--line);background:var(--mat);margin:0 auto}
            .ac-mem i{display:block;font:10px/1.5 var(--sans);color:var(--sub);font-style:normal;letter-spacing:.04em}
            @keyframes acRise{from{opacity:0;transform:translateY(14px)}to{opacity:1;transform:none}}
            .ac-sechd{animation:acRise .45s ease backwards}
            .ac-figures,.ac-rows,.ac-records{animation:acRise .55s ease .08s backwards}
            @media(prefers-reduced-motion:reduce){.ac-sechd,.ac-figures,.ac-rows,.ac-records{animation:none}}
            /* ---- 版权页 ---- */
            .ac-colophon{margin-top:56px;border-top:2px solid var(--ink);padding-top:14px;font-size:12px;color:var(--sub);letter-spacing:.06em;text-align:center}
            /* ---- 双侧栏（≥1440px 显形） ---- */
            html{scroll-behavior:smooth}
            .ac-layout{max-width:840px;margin:0 auto}
            .ac-rail{display:none}
            @media(min-width:1440px){
              .ac-layout{display:grid;grid-template-columns:190px minmax(0,680px) 210px;max-width:none;justify-content:center;column-gap:64px}
              .ac-page{max-width:none;margin:0;padding-left:0;padding-right:0}
              .ac-rail{display:block;position:sticky;top:0;align-self:start;max-height:100vh;overflow-y:auto;padding:52px 0 40px;font-size:13px;line-height:1.7;scrollbar-width:none}
              .ac-rail::-webkit-scrollbar{display:none}
            }
            .ac-rail-hd{font:600 10px/1 var(--sans);letter-spacing:.24em;color:var(--sub);text-transform:uppercase;border-bottom:2px solid var(--ink);padding-bottom:7px;margin:30px 0 14px}
            .ac-rail>.ac-rail-hd:first-child{margin-top:0}
            .ac-rail-sub{font-size:11px;color:var(--sub);letter-spacing:.14em;margin:14px 0 2px}
            .ac-mini{display:flex;gap:12px;align-items:center}
            .ac-mini img{width:38px;height:38px;border-radius:50%;object-fit:cover;border:1px solid var(--line);background:var(--mat);flex-shrink:0}
            .ac-mini b{display:block;font:600 14px/1.4 var(--serif);letter-spacing:.02em;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:130px}
            .ac-mini i{font:400 11px/1.6 var(--sans);color:var(--sub);font-style:normal;letter-spacing:.06em}
            .ac-nav{list-style:none}
            .ac-nav a{display:flex;align-items:baseline;gap:10px;padding:9px 0;border-bottom:1px solid var(--line);color:var(--ink);text-decoration:none;letter-spacing:.08em;font-size:13px;transition:color .15s}
            .ac-nav a .n{font:600 12px/1 var(--serif);color:var(--sub);width:18px;flex-shrink:0}
            .ac-nav a:hover{color:var(--ac)}
            .ac-nav a.on{color:var(--ac)}
            .ac-nav a.on .n{color:var(--ac)}
            .ac-kv{display:flex;justify-content:space-between;align-items:baseline;gap:12px;padding:8px 0;border-bottom:1px solid var(--line)}
            .ac-kv .k{color:var(--sub);font-size:12px;letter-spacing:.08em;flex-shrink:0}
            .ac-kv .v{font:600 14px/1.5 var(--serif);text-align:right;overflow:hidden;white-space:nowrap}
            .ac-kv .v .t{display:inline-block;max-width:110px;overflow:hidden;text-overflow:ellipsis;vertical-align:bottom}
            .ac-kv .v i{font:400 11px var(--sans);color:var(--sub);margin-left:6px;font-style:normal}
            .ac-dist{list-style:none}
            .ac-dist li{padding:8px 0 10px;border-bottom:1px solid var(--line)}
            .ac-dist .l1{display:flex;align-items:baseline;justify-content:space-between;gap:10px;font-size:12px;color:var(--sub);letter-spacing:.06em}
            .ac-dist .l1 b{font:600 13px/1 var(--serif);color:var(--ink)}
            .ac-dist .nm{display:inline-flex;align-items:center;gap:7px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
            .ac-dist .d{width:6px;height:6px;border-radius:50%;flex-shrink:0}
            .ac-bar{height:3px;background:var(--line);margin-top:7px}
            .ac-bar i{display:block;height:100%;opacity:.8}
            .ac-colophon-s{margin-top:26px;font-size:11px;color:var(--sub);letter-spacing:.06em;border-top:1px solid var(--line);padding-top:10px}
            .ac-search input{width:100%;background:none;border:none;border-bottom:1px solid var(--line);color:var(--ink);font:13px/1.6 var(--sans);padding:6px 2px;letter-spacing:.06em;outline:none}
            .ac-search input::placeholder{color:var(--sub);opacity:.7}
            .ac-search input:focus{border-bottom-color:var(--ac)}
            .ac-sres{list-style:none;margin-top:2px}
            .ac-sres a{display:block;font-size:12px;color:var(--sub);padding:5px 2px;border-bottom:1px solid var(--line);text-decoration:none;letter-spacing:.06em;cursor:pointer;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
            .ac-sres a:hover{color:var(--ac)}
            .ac-sres .sn{font-size:12px;color:var(--sub);display:block;padding:5px 2px;border-bottom:none}
            .ac-t5{list-style:none}
            .ac-t5 li{display:flex;align-items:center;gap:8px;padding:7px 0;border-bottom:1px solid var(--line);cursor:pointer}
            .ac-t5 li:hover .nm{color:var(--ac)}
            .ac-t5 .rk{font:600 11px/1 var(--serif);color:var(--sub);width:12px;flex-shrink:0}
            .ac-t5 img{width:22px;height:22px;border-radius:50%;object-fit:cover;border:1px solid var(--line);background:var(--mat);flex-shrink:0}
            .ac-t5 .nm{font-size:12px;letter-spacing:.05em;flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;transition:color .15s}
            .ac-t5 .fv{font:600 12px var(--serif);color:var(--ac);flex-shrink:0}
            @keyframes acFlash{0%,100%{outline-color:transparent}15%,55%{outline:2px solid var(--ac);outline-offset:3px}}
            .ac-flash{animation:acFlash 1.4s ease}
            /* ---- 底部弹层 ---- */
            .ac-mask{display:none;position:fixed;inset:0;background:rgba(38,34,27,.45);z-index:98}
            .ac-shed{display:none}
            .ac-plates.ac-open{position:fixed;left:0;right:0;bottom:0;height:70vh;z-index:99;background:var(--paper);border-top:1px solid var(--line);box-shadow:0 -10px 34px rgba(0,0,0,.18);overflow-y:auto;overscroll-behavior:contain;padding:0 24px 36px;margin:0;transition:height .22s cubic-bezier(.2,.7,.3,1);grid-template-columns:repeat(auto-fill,minmax(160px,1fr))}
            .ac-plates.ac-open.ac-dragging{transition:none;user-select:none}
            .ac-plates.ac-open .ac-shed{display:flex;flex-direction:column;grid-column:1/-1;position:sticky;top:0;z-index:2;background:var(--paper);padding:2px 0 12px;margin-bottom:16px;border-bottom:1px solid var(--ink);touch-action:none}
            .ac-grip{display:flex;justify-content:center;padding:8px 0 4px;cursor:grab}
            .ac-plates.ac-open.ac-dragging .ac-grip{cursor:grabbing}
            .ac-grip i{width:44px;height:3px;background:var(--line);display:block}
            .ac-shrow{display:flex;align-items:center;justify-content:space-between;gap:14px}
            .ac-shrow span{font:600 15px/1 var(--serif);letter-spacing:.1em}
            .ac-x{width:30px;height:30px;border:1px solid var(--line);background:none;color:var(--sub);border-radius:50%;font-size:17px;line-height:1;cursor:pointer;font-family:inherit;flex-shrink:0}
            .ac-plates.ac-open .ac-plate.ac-fold{display:block}
            .ac-plates.ac-open::-webkit-scrollbar{width:6px}
            .ac-plates.ac-open::-webkit-scrollbar-thumb{background:var(--line)}
            /* ---- 移动端侧栏 (<1440px): 悬浮按钮 + 左抽屉(索引/速查/Top5) + 底部弹层(附录) ---- */
            .ac-fabs{display:none;position:fixed;right:14px;bottom:20px;flex-direction:column;gap:10px;z-index:97}
            .ac-fab{font:600 12px/1 var(--serif);letter-spacing:.18em;color:var(--ink);background:var(--paper);border:1px solid var(--line);padding:11px 13px;cursor:pointer;box-shadow:0 2px 12px rgba(38,34,27,.10);transition:color .15s,border-color .15s}
            .ac-fab:hover,.ac-fab:active{color:var(--ac);border-color:var(--ac)}
            .ac-rbar{display:none}
            @media(max-width:1439px){
              .ac-fabs{display:flex}
              body.rail-drawer .ac-rail-l{display:block;position:fixed;top:0;left:0;bottom:0;width:min(320px,86vw);max-height:none;overflow-y:auto;-webkit-overflow-scrolling:touch;z-index:101;background:var(--paper);border-right:1px solid var(--line);padding:6px 20px 36px;box-shadow:14px 0 34px rgba(38,34,27,.18);transform:translateX(-103%);transition:transform .24s cubic-bezier(.2,.7,.3,1)}
              body.rail-drawer-open .ac-rail-l{transform:none}
              body.rail-sheet .ac-rail-r{display:flex;flex-direction:column;position:fixed;left:0;right:0;bottom:0;height:70vh;max-height:none;overflow-y:auto;-webkit-overflow-scrolling:touch;overscroll-behavior:contain;z-index:101;background:var(--paper);border-top:1px solid var(--line);box-shadow:0 -10px 34px rgba(38,34,27,.18);padding:0 20px 34px;margin:0;transition:height .22s cubic-bezier(.2,.7,.3,1)}
              body.rail-sheet .ac-rail-r.ac-dragging{transition:none;user-select:none}
              .ac-rbar{display:flex;align-items:center;justify-content:space-between;gap:12px;position:sticky;top:0;z-index:2;background:var(--paper);padding:12px 0 10px;margin:0 0 6px;border-bottom:1px solid var(--ink);touch-action:none;cursor:grab}
              .ac-rbar span{font:600 14px/1 var(--serif);letter-spacing:.12em}
            }
            </style></head><body>
            <div class="ac-mask" id="mask" onclick="onMaskTap()"></div>
            <div class="ac-fabs" id="fabs">
                <button class="ac-fab" onclick="openRailDrawer()">索引</button>
                <button class="ac-fab" onclick="openRailSheet()">附录</button>
            </div>
            <div class="ac-layout">
            {{RAIL_L}}
            <div class="ac-page">
              <header>
                {{HERO}}
                <div class="ac-eyebrow">Player Archive &middot; 玩家图录</div>
                <div class="ac-id">
                  <img class="ac-avatar" src="{{BILI_AV}}" alt="" referrerpolicy="no-referrer" onerror="this.style.display='none'"/>
                  <div class="ac-idmain">
                    <h1 class="ac-name">{{BILI_NAME}}{{SERVER}}</h1>
                    <div class="ac-sub">{{ROLE_NAME}} &middot; Lv.{{ROLE_LV}} &middot; 累计登录 {{DAYS}} 天</div>
                  </div>
                  <div class="ac-stamp">编纂于<b>{{UPDATED}}</b></div>
                </div>
              </header>

              <section class="ac-sec" id="sec-1">
                <div class="ac-sechd"><span class="ac-no">01</span><h2>概览</h2></div>
                <div class="ac-figures">
                  <div class="ac-fig"><div class="ac-num">{{DAYS}}</div><div class="ac-numlab">游戏天数</div></div>
                  <div class="ac-fig"><div class="ac-num">{{CHARS}}</div><div class="ac-numlab">学员收录</div></div>
                  <div class="ac-fig"><div class="ac-num">{{FIVE}}</div><div class="ac-numlab">五星藏品</div></div>
                  <div class="ac-fig"><div class="ac-num is-text">{{FAV_CHAR}}</div><div class="ac-numlab">最高好感 {{FAV_VAL}}</div></div>
                </div>
                <div class="ac-rows">
                  <div class="ac-row"><span class="k">总力战排名</span><span class="v">{{RAID_RANK}}<i>{{RAID_BOSS}}</i></span></div>
                  <div class="ac-row"><span class="k">大决战排名</span><span class="v">{{EL_RANK}}<i>{{EL_BOSS}}</i></span></div>
                  <div class="ac-row"><span class="k">无限制决斗</span><span class="v">{{FLOOR}} 层</span></div>
                </div>
              </section>

              {{STUDENTS_SECTION}}
              {{RAIDS}}

              <footer class="ac-colophon">数据来源 Bilibili 游戏中心{{CHANNEL_FOOT}} &middot; 更新于 {{UPDATED}} &middot; 编纂 {{AUTHOR}}</footer>
            </div>
            {{RAIL_R}}
            </div>
            <script>
            function toggleStudents(){
              var g=document.getElementById('sg'),m=document.getElementById('mask'),b=document.getElementById('vaBtn');
              var open=!g.classList.contains('ac-open');
              if(b&&open){b.style.display='none';}
              g.classList.toggle('ac-open',open);
              m.style.display=open?'block':'none';
              document.body.style.overflow=open?'hidden':'';
              if(b){b.style.display=open?'none':'block';}
              g.style.height='';
              g.dataset.snap='half';
              if(open){g.scrollTop=0;}
            }
            (function(){
              window.bindSheet=function(g,h,closeFn){
                if(!g||!h)return;
                var st={on:false,y:0,hh:0,full:0,half:0};
                function isOpen(){return g.classList.contains('ac-open')||(g.classList.contains('ac-rail-r')&&document.body.classList.contains('rail-sheet'));}
                function calc(){st.full=window.innerHeight;st.half=Math.round(window.innerHeight*0.7);}
                calc();
                window.addEventListener('resize',function(){
                  calc();
                  if(isOpen()&&!st.on){g.style.height=st[g.dataset.snap||'half']+'px';}
                });
                h.addEventListener('pointerdown',function(e){
                  if(e.target.closest('.ac-x'))return;
                  if(!isOpen())return;
                  st.on=true;st.y=e.clientY;st.hh=g.getBoundingClientRect().height;
                  g.classList.add('ac-dragging');
                  try{h.setPointerCapture(e.pointerId);}catch(err){}
                  e.preventDefault();
                });
                h.addEventListener('pointermove',function(e){
                  if(!st.on)return;
                  var hh=st.hh+(st.y-e.clientY);
                  hh=Math.max(100,Math.min(st.full,hh));
                  g.style.height=hh+'px';
                });
                function endDrag(){
                  if(!st.on)return;
                  st.on=false;g.classList.remove('ac-dragging');
                  var hh=g.getBoundingClientRect().height;
                  if(hh<st.half*0.6){closeFn();return;}
                  var to=(hh>=(st.half+st.full)/2)?'full':'half';
                  g.dataset.snap=to;g.style.height=st[to]+'px';
                }
                h.addEventListener('pointerup',endDrag);
                h.addEventListener('pointercancel',endDrag);
              };
              bindSheet(document.getElementById('sg'),document.getElementById('sgHd'),toggleStudents);
              var rr=document.querySelector('.ac-rail-r');
              bindSheet(rr,rr?rr.querySelector('.ac-rbar'):null,closeRails);
            })();
            /* 移动端侧栏控制器: <1440px 左栏=左滑抽屉, 右栏=底部弹层（复用同一 DOM, CSS 切换形态） */
            function railLayerOpen(){
              var g=document.getElementById('sg');
              return document.body.classList.contains('rail-drawer')
                  || document.body.classList.contains('rail-sheet')
                  || (g&&g.classList.contains('ac-open'));
            }
            function openRailDrawer(){
              var b=document.body,m=document.getElementById('mask');
              if(b.classList.contains('rail-drawer'))return;
              m.style.display='block';
              b.classList.add('rail-drawer');b.style.overflow='hidden';
              requestAnimationFrame(function(){b.classList.add('rail-drawer-open');});
              var r=document.querySelector('.ac-rail-l');if(r)r.scrollTop=0;
            }
            function openRailSheet(){
              var b=document.body,m=document.getElementById('mask'),r=document.querySelector('.ac-rail-r');
              if(b.classList.contains('rail-sheet')||!r)return;
              m.style.display='block';
              b.classList.add('rail-sheet');b.style.overflow='hidden';
              r.style.height='';r.dataset.snap='half';r.scrollTop=0;
            }
            function closeRails(){
              var b=document.body;
              if(!b.classList.contains('rail-drawer')&&!b.classList.contains('rail-sheet'))return;
              if(b.classList.contains('rail-drawer')){
                b.classList.remove('rail-drawer-open');
                setTimeout(function(){b.classList.remove('rail-drawer');},240);
              }
              b.classList.remove('rail-sheet');
              var r=document.querySelector('.ac-rail-r');if(r)r.style.height='';
              var g=document.getElementById('sg');
              if(!(g&&g.classList.contains('ac-open'))){
                document.getElementById('mask').style.display='none';
                b.style.overflow='';
              }
            }
            function onMaskTap(){
              var b=document.body;
              if(b.classList.contains('rail-drawer')||b.classList.contains('rail-sheet')){closeRails();return;}
              toggleStudents();
            }
            /* 移动端抽屉/弹层里点导航 → 收起侧栏再跳章节 */
            (function(){
              Array.prototype.forEach.call(document.querySelectorAll('.ac-nav a'),function(a){
                a.addEventListener('click',function(){
                  var b=document.body;
                  if(b.classList.contains('rail-drawer')||b.classList.contains('rail-sheet'))closeRails();
                });
              });
            })();
            /* 概览大数字滚动（尊重 reduced-motion） */
            (function(){
              if(window.matchMedia && window.matchMedia('(prefers-reduced-motion:reduce)').matches)return;
              var figs=document.querySelectorAll('.ac-figures .ac-num');
              figs.forEach(function(el){
                var t=el.textContent.trim();
                var m=t.match(/^(\\d+)$/);
                if(!m)return;
                var target=parseInt(m[1],10), dur=800, t0=null;
                el.textContent='0';
                function step(ts){
                  if(!t0)t0=ts;
                  var p=Math.min(1,(ts-t0)/dur);
                  el.textContent=String(Math.round(target*(1-Math.pow(1-p,3))));
                  if(p<1)requestAnimationFrame(step);
                }
                requestAnimationFrame(step);
              });
            })();
            /* 展品直达 + 学员速查 */
            window.acFocus=function(id){
              closeRails();
              var p=document.getElementById(id); if(!p)return;
              var g=document.getElementById('sg');
              var foldNeeded=g&&p.classList.contains('ac-fold')&&!g.classList.contains('ac-open');
              if(foldNeeded&&typeof toggleStudents==='function')toggleStudents();
              setTimeout(function(){
                p.scrollIntoView({block:'center',behavior:'smooth'});
                p.classList.remove('ac-flash'); void p.offsetWidth; p.classList.add('ac-flash');
              },foldNeeded?420:60);
            };
            (function(){
              var inp=document.getElementById('stSearch'), box=document.getElementById('stResults');
              if(!inp||!box)return;
              var idx=Array.prototype.slice.call(document.querySelectorAll('.ac-plate')).map(function(p){
                var c=p.querySelector('.ac-capn');
                return {id:p.id, n:c?c.textContent:''};
              });
              inp.addEventListener('input',function(){
                var q=inp.value.trim(); box.innerHTML='';
                if(!q)return;
                var hits=idx.filter(function(o){return o.n.indexOf(q)>=0;}).slice(0,8);
                if(!hits.length){box.innerHTML='<a class="sn">未收录</a>';return;}
                hits.forEach(function(o){
                  var a=document.createElement('a'); a.href='javascript:;'; a.textContent=o.n;
                  a.onclick=function(){acFocus(o.id);}; box.appendChild(a);
                });
              });
            })();
            /* 左栏导航 scrollspy: 当前章节高亮（主题色） */
            (function(){
              var links=Array.prototype.slice.call(document.querySelectorAll('.ac-nav a'));
              if(!links.length)return;
              var secs=links.map(function(a){return document.getElementById(a.getAttribute('href').slice(1));}).filter(Boolean);
              function mark(){
                if(window.innerWidth<1440)return;
                var best=null;
                for(var i=0;i<secs.length;i++){
                  if(secs[i].getBoundingClientRect().top<=140)best=i;
                }
                if(best===null)best=0;
                links.forEach(function(a,i){a.classList.toggle('on',i===best);});
              }
              mark();
              window.addEventListener('scroll',mark,{passive:true});
              window.addEventListener('resize',mark);
            })();
            </script>
            </body></html>""";

        return html
            .replace("{{TITLE}}", esc(config.getPageTitle()))
            .replace("{{HERO}}", hero)
            .replace("{{RAIL_L}}", railLeft)
            .replace("{{RAIL_R}}", railRight)
            .replace("{{AC}}", ac)
            .replace("{{BILI_AV}}", esc(s.getBiliAvatar()))
            .replace("{{BILI_NAME}}", esc(s.getBiliName()))
            .replace("{{SERVER}}", server)
            .replace("{{ROLE_NAME}}", esc(s.getRoleName()))
            .replace("{{ROLE_LV}}", String.valueOf(s.getRoleLevel()))
            .replace("{{DAYS}}", String.valueOf(s.getLoginDays()))
            .replace("{{CHARS}}", String.valueOf(s.getCharCount()))
            .replace("{{FIVE}}", String.valueOf(s.getFiveStar()))
            .replace("{{FAV_CHAR}}", esc(s.getMaxFavorChar() == null ? "-" : s.getMaxFavorChar()))
            .replace("{{FAV_VAL}}", String.valueOf(s.getMaxFavorVal()))
            .replace("{{RAID_RANK}}", String.valueOf(s.getTotalRaidRank()))
            .replace("{{RAID_BOSS}}", esc(s.getTotalRaidBoss()))
            .replace("{{EL_RANK}}", String.valueOf(s.getEliminateRank()))
            .replace("{{EL_BOSS}}", esc(s.getEliminateBoss()))
            .replace("{{FLOOR}}", String.valueOf(s.getMultiFloor()))
            .replace("{{STUDENTS_SECTION}}", studentsSection)
            .replace("{{RAIDS}}", rb.toString())
            .replace("{{CHANNEL_FOOT}}", channelFoot)
            .replace("{{UPDATED}}", esc(s.getUpdatedAt()))
            .replace("{{AUTHOR}}", AUTHOR);
    }

    /**
     * 左栏「索引」: 章节导航 + 学员速查(直达展品) + 好感Top5精选榜 —— 功能型内容，不与正文重复
     */
    private String buildRailLeft(PlayerStats s, List<String> navItems) {
        StringBuilder b = new StringBuilder("<aside class=\"ac-rail ac-rail-l\"><div class=\"ac-rbar\"><span>索引 Index</span><button class=\"ac-x\" onclick=\"closeRails()\">&times;</button></div>");

        // 章节索引（锚点导航，正文没有目录，故不重复）
        b.append("<div class=\"ac-rail-hd\">索引 Index</div><nav><ul class=\"ac-nav\">");
        for (String item : navItems) {
            String[] p = item.split("\t", 3);
            b.append("<li><a href=\"#sec-").append(p[0])
                .append("\"><span class=\"n\">").append(esc(p[1]))
                .append("</span><span class=\"t\">").append(esc(p[2])).append("</span></a></li>");
        }
        b.append("</ul></nav>");

        // 学员速查（输入即筛、点击直达并闪光定位）
        if (s.getStudents() != null && !s.getStudents().isEmpty()) {
            b.append("<div class=\"ac-rail-hd\">速查 Find</div>")
                .append("<div class=\"ac-search\"><input id=\"stSearch\" type=\"text\" placeholder=\"输入学员名…\" autocomplete=\"off\"/>")
                .append("<ul class=\"ac-sres\" id=\"stResults\"></ul></div>");

            // 好感 Top5 精选榜
            List<StudentInfo> top = new ArrayList<>(s.getStudents());
            top.sort((x, y) -> {
                int c = Integer.compare(y.getFavor(), x.getFavor());
                return c != 0 ? c : Integer.compare(y.getLv(), x.getLv());
            });
            b.append("<div class=\"ac-rail-hd\">好感 Top5</div><ul class=\"ac-t5\">");
            for (int i = 1; i <= 5 && i <= top.size(); i++) {
                StudentInfo st = top.get(i - 1);
                b.append("<li onclick=\"acFocus('pl-").append(st.getId()).append("')\">")
                    .append("<span class=\"rk\">").append(i).append("</span>")
                    .append(imgTag(st.getAvatar()))
                    .append("<span class=\"nm\">").append(esc(st.getName())).append("</span>")
                    .append("<span class=\"fv\">&#9829; ").append(st.getFavor()).append("</span></li>");
            }
            b.append("</ul>");
        }

        b.append("<div class=\"ac-colophon-s\">Player Archive</div>");
        b.append("</aside>");
        return b.toString();
    }

    /**
     * 右栏「附录」: 武器/装甲/定位 三组分布版画 + 上期战果 + 版记
     */
    private String buildRailRight(PlayerStats s) {
        StringBuilder b = new StringBuilder("<aside class=\"ac-rail ac-rail-r\"><div class=\"ac-rbar\"><span>附录 Appendix</span><button class=\"ac-x\" onclick=\"closeRails()\">&times;</button></div>");

        // 统计分布（从学员列表现算，零新增 API）
        int total = s.getStudents() == null ? 0 : s.getStudents().size();
        b.append("<div class=\"ac-rail-hd\">附录 Appendix</div>");
        b.append(distBlock("武器类型", tallyBy(s, 0), total));
        b.append(distBlock("装甲类型", tallyBy(s, 1), total));
        b.append(distBlock("战斗定位", tallyBy(s, 2), total));

        // 上期战果
        b.append("<div class=\"ac-rail-hd\">上期战果 Recaps</div>");
        String rb = s.getTotalRaidRank() > 0 ? "第 " + s.getTotalRaidRank() + " 名" : "未参与";
        String eb = s.getEliminateRank() > 0 ? "第 " + s.getEliminateRank() + " 名" : "未参与";
        b.append(kv("总力战", rb + (empty(s.getTotalRaidBoss()) ? "" : "<i class=\"t\">" + esc(s.getTotalRaidBoss()) + "</i>")));
        b.append(kv("大决战", eb + (empty(s.getEliminateBoss()) ? "" : "<i class=\"t\">" + esc(s.getEliminateBoss()) + "</i>")));
        b.append(kv("无限制", s.getMultiFloor() + " 层"));
        b.append("<div class=\"ac-colophon-s\">数据来源 · Bilibili</div>");
        b.append("</aside>");
        return b.toString();
    }

    /** 0=武器 1=装甲 2=定位 */
    private List<String[]> tallyBy(PlayerStats s, int kind) {
        Map<String, Integer> m = new LinkedHashMap<>();
        Map<String, String> colors = new LinkedHashMap<>();
        if (s.getStudents() != null) {
            for (StudentInfo st : s.getStudents()) {
                String name, color;
                if (kind == 0) { name = st.getBulletName(); color = st.getBulletColor(); }
                else if (kind == 1) { name = st.getArmorName(); color = st.getArmorColor(); }
                else { name = st.getRole(); color = ""; }
                if (empty(name)) continue;
                if (!m.containsKey(name)) colors.put(name, empty(color) ? "#8a93a6" : color);
                m.merge(name, 1, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> es = new ArrayList<>(m.entrySet());
        es.sort((a, c) -> Integer.compare(c.getValue(), a.getValue()));
        List<String[]> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : es) out.add(new String[]{e.getKey(), String.valueOf(e.getValue()), colors.get(e.getKey())});
        return out;
    }

    /** 分布版画: 名称+计数 + 3px 细条（官方属性色） */
    private String distBlock(String title, List<String[]> items, int total) {
        if (items.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        b.append("<div class=\"ac-rail-sub\">").append(esc(title)).append("</div>");
        b.append("<ul class=\"ac-dist\">");
        for (String[] it : items) {
            int n;
            try { n = Integer.parseInt(it[1]); } catch (Exception e) { n = 0; }
            double pct = total > 0 ? Math.max(4, n * 100.0 / total) : 0;
            String c = sanitizeColor(it[2], "#8a93a6");
            b.append("<li><div class=\"l1\"><span class=\"nm\"><span class=\"d\" style=\"background:").append(c)
                .append("\"></span>").append(esc(it[0])).append("</span><b>").append(n).append("</b></div>")
                .append("<div class=\"ac-bar\"><i style=\"width:").append(String.format("%.0f", pct)).append("%;background:").append(c).append("\"></i></div></li>");
        }
        b.append("</ul>");
        return b.toString();
    }

    /** key-value 一行（v 可含已转义 HTML） */
    private String kv(String k, String vHtml) {
        return "<div class=\"ac-kv\"><span class=\"k\">" + esc(k) + "</span><span class=\"v\">" + vHtml + "</span></div>";
    }

    /** 渲染某一类战绩的最新一期（图录记录条） */
    private String renderRaidSection(int secNo, String title, List<RaidInfo> raids, String type, int period) {
        StringBuilder b = new StringBuilder();
        b.append("<section class=\"ac-sec\" id=\"sec-").append(secNo).append("\">")
            .append("<div class=\"ac-sechd\"><span class=\"ac-no\">").append(pad(secNo))
            .append("</span><h2>").append(title)
            .append("</h2><span class=\"ac-meta\">第").append(period).append("期</span></div>")
            .append("<ol class=\"ac-records\">");
        for (RaidInfo r : raids) {
            if (!type.equals(r.getType()) || periodNum(r) != period) continue;
            String raidType = (r.getRaidType() == null || r.getRaidType().isEmpty())
                ? "" : "<span class=\"ac-recsub\">" + esc(r.getRaidType()) + "</span>";
            b.append("<li class=\"ac-record\">")
                .append(imgTag(r.getBossImage(), "ac-boss"))
                .append("<div class=\"ac-rec\">")
                .append("<div class=\"ac-rect\"><span class=\"ac-recn\">").append(esc(r.getBoss()))
                .append("</span>").append(raidType)
                .append("<span class=\"ac-recdiff\">").append(esc(r.getDifficultyName())).append("</span></div>")
                .append("<div class=\"ac-recstats\">排名<b>").append(r.getRank()).append("</b>")
                .append("<span class=\"sep\">/</span>分数<b>").append(String.format("%,d", r.getScore())).append("</b></div>");
            for (RaidInfo.Team t : r.getTeams()) {
                b.append("<div class=\"ac-team\"><span class=\"ac-tlabel\">").append(esc(t.getLabel()))
                    .append("</span><div class=\"ac-tmems\">");
                for (RaidInfo.Member m : t.getMembers()) {
                    b.append("<span class=\"ac-mem\">").append(imgTag(m.getAvatar()))
                        .append("<i>").append(m.getLv()).append("</i></span>");
                }
                b.append("</div></div>");
            }
            b.append("</div>");
            if (!empty(r.getRankIcon())) {
                b.append(imgTag(r.getRankIcon(), "ac-rankico")); // 官方排名徽章
            }
            b.append("</li>");
        }
        b.append("</ol></section>");
        return b.toString();
    }

    private static int periodNum(RaidInfo r) {
        try { return Integer.parseInt(r.getPeriod()); } catch (Exception e) { return 0; }
    }

    /** 分节编号补零: 1 -> "01" */
    private static String pad(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    /** 属性纯文字 + 彩色小圆点 */
    private static String attrText(String label, String color) {
        if (label == null || label.isEmpty()) return "";
        return "<span class=\"a\"><span class=\"d\" style=\"background:"
            + sanitizeColor(color, "#8a93a6") + "\"></span>" + esc(label) + "</span>";
    }

    /** 官方图标重复 n 次的一行（星级/武器星），无图标 URL 时回退文字星 */
    private static String iconRow(String iconUrl, int n) {
        if (n <= 0) return "";
        if (empty(iconUrl)) return starText(n, "#b8860b");
        StringBuilder b = new StringBuilder("<span class=\"ac-icorow\">");
        for (int i = 0; i < n; i++) {
            b.append("<img src=\"").append(esc(iconUrl)).append("\" alt=\"\" referrerpolicy=\"no-referrer\"/>");
        }
        b.append("</span>");
        return b.toString();
    }

    /** 属性图标 chip: 官方图标 + 名称，武器/装甲带官方色描边 */
    private static String attrChip(String iconUrl, String name, String color) {
        if (name == null || name.isEmpty()) return "";
        if (empty(iconUrl)) return attrText(name, color);
        String c = empty(color) ? "" : " style=\"border-color:" + sanitizeColor(color, "#8a93a6") + "\"";
        return "<span class=\"ac-chip\"" + c + "><img src=\"" + esc(iconUrl)
            + "\" alt=\"\" referrerpolicy=\"no-referrer\"/>" + esc(name) + "</span>";
    }

    /** 星级文字（衬线图录里用文字星） */
    private static String starText(int n, String color) {
        if (n <= 0) return "";
        StringBuilder b = new StringBuilder();
        b.append("<span style=\"color:").append(color).append("\">");
        for (int i = 0; i < n; i++) b.append("&#9733;");
        b.append("</span>");
        return b.toString();
    }

    private static String imgTag(String src) {
        return imgTag(src, "");
    }

    private static String imgTag(String src, String cls) {
        if (src == null || src.isEmpty()) return "";
        String c = (cls == null || cls.isEmpty()) ? "" : " class=\"" + cls + "\"";
        return "<img src=\"" + esc(src) + "\"" + c + " loading=\"lazy\" referrerpolicy=\"no-referrer\" onerror=\"this.style.display='none'\"/>";
    }

    private static boolean empty(String s) {
        return s == null || s.isEmpty();
    }

    /** 校验 CSS 颜色值（只允许 #rgb/#rrggbb/#rrggbbaa，防止注入），非法时用默认值 */
    private static String sanitizeColor(String c, String fallback) {
        if (c != null && c.matches("#[0-9a-fA-F]{3,8}")) return c;
        return fallback;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
