package plugin.bluearchive.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import plugin.bluearchive.PluginConfig;
import plugin.bluearchive.model.PlayerStats;
import plugin.bluearchive.model.RaidInfo;
import plugin.bluearchive.model.StudentInfo;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 蔚蓝档案数据服务
 *
 * 核心: 调用 le3-api.game.bilibili.com/pc/api/game/role_bind/query_list
 * 响应中 detail_url 字段包含签名 URL，每次调用自动重新生成，永不过期！
 *
 * 流程:
 *   Cookie → role_bind/query_list → detail_url(签名) → query_record_detail → 玩家数据
 */
@Component
public class StatsService {

    private static final Logger log = LoggerFactory.getLogger(StatsService.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile PlayerStats cachedStats;
    private volatile long lastFetchTime = 0;
    private volatile PluginConfig config;
    /** 当前主显渠道名（来自 query_list 的 distributor_channel_name，"B站账号"/"悠星账号"） */
    private volatile String currentChannelName = "";
    /** 最近一次刷新失败的可读原因（供 /refresh 接口返回给前端） */
    private volatile String lastError = "";
    private ScheduledExecutorService scheduler;

    /** JDK 自带 HttpClient：连接池/HTTP1.1 复用，零额外打包体积 */
    private final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    /** 签名 URL 参数提取（预编译复用） */
    private static final Pattern WR_PARAMS = Pattern.compile(
        "wr-uid=(\\d+).*?wr-game=(\\d+).*?wr-channel=(\\d+).*?wr-sign=([a-f0-9]+).*?wr-expire=(\\d+).*?wr-nonce=([A-Za-z0-9]+)");

    private static final String ROLE_BIND_API = "https://le3-api.game.bilibili.com/pc/api/game/role_bind/query_list";
    private static final String RECORD_API = "https://line1-h5-mobile-api.biligame.com/game/center/h5/role_bind/query_record_detail";

    public void setConfig(PluginConfig config) { this.config = config; }
    public String getLastError() { return lastError; }

    public void initialize() {
        log.info("数据服务初始化, UID={}", config.getBiliUid());
    }

    public PlayerStats getStats() {
        PlayerStats c = cachedStats;
        long now = System.currentTimeMillis();
        if (c != null && now - lastFetchTime < config.getRefreshHours() * 3600000L) {
            return c;
        }
        if (c != null) {
            // 缓存过期但存在: 立即返回旧数据 + 后台异步刷新 —— 读者永远不阻塞在 B站 API 上
            // (旧实现: 过期后第一个请求同步等两次外网往返 2~4s)
            tryAsyncRefresh();
            return c;
        }
        return refreshStats(); // 冷启动无缓存: 首请求仍需阻塞拉一次
    }

    /** 异步刷新互斥与节流: 并发请求只触发一次；失败/尝试后 60s 内不再重复尝试 */
    private final java.util.concurrent.atomic.AtomicBoolean refreshing = new java.util.concurrent.atomic.AtomicBoolean(false);
    private volatile long lastAttemptTime = 0;
    private volatile ExecutorService asyncRefresher;

    private void tryAsyncRefresh() {
        long now = System.currentTimeMillis();
        if (now - lastAttemptTime < 60_000) return;
        if (!refreshing.compareAndSet(false, true)) return;
        lastAttemptTime = now;
        ExecutorService ex = asyncRefresher;
        if (ex == null) {
            synchronized (this) {
                if (asyncRefresher == null) {
                    asyncRefresher = Executors.newSingleThreadExecutor(r -> {
                        Thread t = new Thread(r, "ba-async-refresh"); t.setDaemon(true); return t;
                    });
                }
                ex = asyncRefresher;
            }
        }
        ex.submit(() -> {
            try { refreshStats(); } finally { refreshing.set(false); }
        });
    }

    public synchronized PlayerStats refreshStats() {
        if (config == null || !config.isConfigured()) {
            log.warn("插件未配置 Cookie/UID");
            return cachedStats;
        }
        try {
            String signedUrl = getSignedUrl();
            if (signedUrl == null) {
                // role_bind 失败＝Cookie 失效或 B站返回非预期内容，显式抛错让接口给出可读原因
                throw new IllegalStateException(
                    "无法获取签名 URL：Cookie 可能已过期/失效，或 B站未返回 JSON（请重新保存有效 Cookie）");
            }

            String json = callRecordApi(signedUrl);
            if (json == null) {
                throw new IllegalStateException("战绩接口无响应（HTTP 非 200 或网络异常）");
            }

            PlayerStats stats = parseApiResponse(json);
            if (stats == null) {
                throw new IllegalStateException("战绩接口返回内容无法解析（数据结构异常）");
            }

            stats.setChannelName(currentChannelName);
            cachedStats = stats;
            lastFetchTime = System.currentTimeMillis();
            log.info("数据刷新成功: {} 学员, {} 五星", stats.getCharCount(), stats.getFiveStar());
            return stats;
        } catch (Exception e) {
            lastError = e.getMessage();
            log.error("刷新失败: {}", e.getMessage());
            return cachedStats;
        }
    }

    private String getSignedUrl() {
        try {
            String params = "owner_mid=" + URLEncoder.encode(config.getBiliUid(), StandardCharsets.UTF_8)
                + "&sdk_type=3&query_page=roleTab&need_steam=true";

            {
                HttpRequest req = HttpRequest.newBuilder(URI.create(ROLE_BIND_API + "?" + params))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Mozilla/5.0 Chrome/120.0")
                    .header("Cookie", config.getCookie())
                    .header("Referer", "https://game.bilibili.com/")
                    .header("Accept", "application/json, text/plain, */*")
                    .GET().build();

                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                String body = resp.statusCode() == 200 ? resp.body() : null;
                if (body == null) return null;

                JsonNode root;
                try {
                    root = mapper.readTree(body);
                } catch (Exception je) {
                    // 拿到的是 HTML（如官网登录页）而非 JSON → Cookie 失效的典型表现
                    log.error("role_bind 返回非 JSON（Cookie 可能已过期），body 前120字: {}",
                        body.length() > 120 ? body.substring(0, 120) : body);
                    return null;
                }
                if (root.path("code").asInt(-1) != 0) {
                    log.error("role_bind API 错误: {}", root.path("message").asText());
                    return null;
                }

                for (JsonNode role : root.path("data").path("bind_roles")) {
                    if (role.path("game_base_id").asInt(0) == 109864
                        || role.path("game_name").asText("").contains("蔚蓝")) {
                        // 顺手记录当前主显渠道名（B服="B站账号" / 官服="悠星账号"）
                        currentChannelName = role.path("distributor_channel_name").asText("");
                        String url = role.path("detail_url").asText("");
                        log.info("签名 URL: {} (渠道: {})", url.substring(0, Math.min(100, url.length())), currentChannelName);
                        return url;
                    }
                }
                log.warn("未找到蔚蓝档案绑定角色");
                return null;
            }
        } catch (Exception e) {
            log.error("获取签名 URL 失败", e);
            return null;
        }
    }

    private String callRecordApi(String signedUrl) {
        try {
            Matcher m = WR_PARAMS.matcher(signedUrl);
            if (!m.find()) return null;

            String params = "game_base_id=" + m.group(2)
                + "&owner_mid=" + m.group(1) + "&mid=" + m.group(1)
                + "&channel_type=" + m.group(3) + "&sign=" + m.group(4)
                + "&expire_time=" + m.group(5) + "&nonce_str=" + m.group(6)
                + "&share_scene=&timestamp=" + System.currentTimeMillis()
                + "&build=769&client=h5&platform=web&cur_host=app.biligame.com&source_from=1001000041";

            {
                HttpRequest req = HttpRequest.newBuilder(URI.create(RECORD_API + "?" + params))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Mozilla/5.0 Chrome/120.0")
                    .header("Referer", "https://app.biligame.com/")
                    .header("Origin", "https://app.biligame.com")
                    .GET().build();

                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                return resp.statusCode() == 200 ? resp.body() : null;
            }
        } catch (Exception e) {
            log.error("战绩 API 失败", e);
            return null;
        }
    }

    /** 展示用日期格式（线程安全复用，勿每次 new） */
    public static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 官方难度数字 → 名称 */
    private static final String[] DIFF_NAMES = {"", "Normal", "Hard", "HardCore", "Extreme", "Insane", "Torment"};

    public PlayerStats parseApiResponse(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode data = root.path("data");
            JsonNode record = data.path("record_detail");
            JsonNode section = record.path("section_role");
            JsonNode owner = data.path("owner_info");

            PlayerStats stats = new PlayerStats();
            stats.setBiliName(owner.path("name").asText(""));
            stats.setBiliAvatar(owner.path("avatar").asText(""));
            stats.setRoleName(section.path("role_name").asText(""));
            stats.setRoleLevel(section.path("role_level").asInt(0));
            stats.setLoginDays(section.path("acc_login_days").asInt(0));
            stats.setBannerUrl(data.path("ui_config").path("banner_img_url").asText(""));

            List<StudentInfo> students = new ArrayList<>();
            for (JsonNode c : section.path("character_list")) {
                StudentInfo s = new StudentInfo();
                s.setId(c.path("character_id").asLong(0));
                s.setName(c.path("character_name").asText(""));
                s.setLv(c.path("level").asInt(1));
                s.setStar(c.path("star_grade").asInt(3));
                s.setWstar(c.path("weapon_star_grade").asInt(0));
                s.setFavor(c.path("favor_rank").asInt(1));
                s.setRole(c.path("tactic_role_name").asText(""));
                s.setAvatar(c.path("avatar_url").asText(""));
                s.setDetail(c.path("detail_image_url").asText(""));
                s.setRoleIcon(c.path("tactic_role_icon_url").asText(""));
                s.setBulletName(c.path("bullet_type_name").asText(""));
                s.setBulletColor(c.path("bullet_type_color").asText(""));
                s.setBulletIcon(c.path("bullet_type_icon_url").asText(""));
                s.setArmorName(c.path("armor_type_name").asText(""));
                s.setArmorColor(c.path("armor_type_color").asText(""));
                s.setArmorIcon(c.path("armor_type_icon_url").asText(""));
                s.setFavorIcon(c.path("favor_icon_url").asText(""));
                s.setStarIcon(c.path("star_icon_url").asText(""));
                s.setWeaponIcon(c.path("weapon_star_icon_url").asText(""));
                students.add(s);
            }
            students.sort((a, b) -> {
                int c = Integer.compare(b.getStar(), a.getStar());
                return c != 0 ? c : Integer.compare(b.getLv(), a.getLv());
            });
            stats.setStudents(students);
            stats.setCharCount(students.size());
            stats.setFiveStar((int) students.stream().filter(s -> s.getStar() >= 5).count());

            students.stream().max(Comparator.comparingInt(StudentInfo::getFavor))
                .ifPresent(s -> { stats.setMaxFavorChar(s.getName()); stats.setMaxFavorVal(s.getFavor()); });

            // 总力战 + 大决战：保留每期每场完整记录
            List<RaidInfo> raids = new ArrayList<>();
            parseRaidGroup(section.path("raid"), "总力战", raids);
            parseRaidGroup(section.path("eliminate_raid"), "大决战", raids);
            // 期数大的在前（最新期在最上）
            raids.sort((a, b) -> b.getPeriod().compareTo(a.getPeriod()));
            stats.setRaids(raids);

            for (JsonNode a : section.path("role_attribute_list")) {
                String k = a.path("key").asText("");
                long v = a.path("value").asLong(0);
                switch (k) {
                    case "last_raid_rank" -> stats.setTotalRaidRank(v);
                    case "last_raid_boos_name" -> stats.setTotalRaidBoss(a.path("value").asText(""));
                    case "last_eliminate_raid_rank" -> stats.setEliminateRank(v);
                    case "last_eliminate_raid_boss_name" -> stats.setEliminateBoss(a.path("value").asText(""));
                    case "last_multi_floor_id" -> stats.setMultiFloor(v);
                }
            }

            stats.setUpdatedAt(LocalDateTime.now().format(TS_FMT));
            return stats;
        } catch (Exception e) {
            log.error("解析失败", e);
            return null;
        }
    }

    /**
     * 解析 raid / eliminate_raid 分组。
     * 结构: { "期数": [ {boss_name, difficulty, score, rank, boss_image_url, rank_icon_url,
     *                    raid_type?, team:{"1":[charId...]}, char_props:{"1":[{...完整学员}]} } ] }
     */
    private void parseRaidGroup(JsonNode group, String type, List<RaidInfo> out) {
        if (group == null || !group.isObject()) return;
        Iterator<Map.Entry<String, JsonNode>> it = group.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> en = it.next();
            String period = en.getKey();
            for (JsonNode e : en.getValue()) {
                RaidInfo r = new RaidInfo();
                r.setType(type);
                r.setPeriod(period);
                r.setRaidType(e.path("raid_type").asText(""));
                r.setBoss(e.path("boss_name").asText(""));
                r.setDifficulty(e.path("difficulty").asInt(0));
                int d = r.getDifficulty();
                r.setDifficultyName(d >= 0 && d < DIFF_NAMES.length ? DIFF_NAMES[d] : ("Lv" + d));
                r.setScore(e.path("score").asLong(0));
                r.setRank(e.path("rank").asLong(0));
                r.setBossImage(e.path("boss_image_url").asText(""));
                r.setRankIcon(e.path("rank_icon_url").asText(""));

                JsonNode team = e.path("team");
                JsonNode props = e.path("char_props");
                Iterator<Map.Entry<String, JsonNode>> ti = team.fields();
                while (ti.hasNext()) {
                    String teamNo = ti.next().getKey();
                    RaidInfo.Team t = new RaidInfo.Team();
                    t.setLabel("部队" + teamNo);
                    for (JsonNode m : props.path(teamNo)) {
                        RaidInfo.Member mem = new RaidInfo.Member();
                        mem.setId(m.path("characterId").asLong(0));
                        mem.setName(m.path("character_name").asText(""));
                        mem.setLv(m.path("level").asInt(0));
                        mem.setStar(m.path("grade").asInt(0));
                        mem.setWeaponStar(m.path("weaponStarGrade").asInt(0));
                        mem.setFavor(m.path("favorRank").asInt(0));
                        mem.setAvatar(m.path("avatar_url").asText(""));
                        mem.setBulletColor(m.path("bullet_type_color").asText(""));
                        mem.setTacticIcon(m.path("tactic_role_icon_url").asText(""));
                        mem.setWeaponIcon(m.path("weapon_star_icon_url").asText(""));
                        t.getMembers().add(mem);
                    }
                    r.getTeams().add(t);
                }
                out.add(r);
            }
        }
    }

    public void startAutoRefresh(long ms) {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ba-refresh"); t.setDaemon(true); return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try { refreshStats(); } catch (Exception e) { log.error("定时刷新失败", e); }
        }, ms, ms, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (scheduler != null) scheduler.shutdown();
        ExecutorService ex = asyncRefresher;
        if (ex != null) ex.shutdownNow();
    }
}
