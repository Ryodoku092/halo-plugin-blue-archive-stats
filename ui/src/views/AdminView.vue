<script setup lang="ts">
import { ref, onMounted } from "vue";
import {
  VPageHeader,
  VCard,
  VButton,
  VSpace,
  VAlert,
  VDescription,
  VDescriptionItem,
  Toast,
  IconRefreshLine,
  IconEye,
} from "@halo-dev/components";

interface PluginConfig {
  biliUid: string;
  cookieSet: boolean;
  cookie?: string;
  refreshHours: number;
  autoRefresh: boolean;
  pageTitle: string;
  showStudents: boolean;
  showRaids: boolean;
  maxStudents: number;
  themeColor: string;
}

interface PlayerStats {
  biliName?: string;
  roleName?: string;
  roleLevel?: number;
  loginDays?: number;
  charCount?: number;
  fiveStar?: number;
  fourStar?: number;
  totalRaidRank?: number;
  totalRaidBoss?: string;
  eliminateRank?: number;
  eliminateBoss?: string;
  updatedAt?: string;
  channelName?: string;
}

const formState = ref<PluginConfig>({
  biliUid: "",
  cookieSet: false,
  cookie: "",
  refreshHours: 6,
  autoRefresh: true,
  pageTitle: "蔚蓝档案战绩",
  showStudents: true,
  showRaids: true,
  maxStudents: 0,
  themeColor: "#6d5dfc",
});

const API = "/apis/console.api.blue-archive.halo.run/v1alpha1";
const stats = ref<PlayerStats | null>(null);
const refreshing = ref(false);
const saving = ref(false);
const embedCode = `<iframe src="${API}/stats/html" width="100%" height="1200" style="border:none;" loading="lazy"></iframe>`;

onMounted(() => {
  loadConfig();
  loadStats();
});

/** 读取 Cookie 值（用于携带 Halo 的 CSRF token） */
function getCookie(name: string): string {
  const m = document.cookie.match(
    new RegExp("(?:^|; )" + name + "=([^;]*)")
  );
  return m ? decodeURIComponent(m[1]) : "";
}

async function loadConfig() {
  try {
    const resp = await fetch(`${API}/config`);
    if (resp.ok) {
      const data: PluginConfig = await resp.json();
      formState.value = { ...formState.value, ...data, cookie: "" };
    }
  } catch (e) {
    console.error("加载配置失败:", e);
  }
}

async function loadStats() {
  try {
    const resp = await fetch(`${API}/stats`);
    stats.value = resp.ok ? await resp.json() : null;
  } catch {
    stats.value = null;
  }
}

async function handleSave() {
  if (saving.value) return;
  if (!formState.value.biliUid.trim()) {
    Toast.error("请先填写 B站 UID");
    return;
  }
  saving.value = true;
  try {
    const payload: Record<string, unknown> = {
      biliUid: formState.value.biliUid.trim(),
      refreshHours: Number(formState.value.refreshHours) || 6,
      maxStudents: Number(formState.value.maxStudents) || 0,
      pageTitle: formState.value.pageTitle || "蔚蓝档案战绩",
      themeColor: formState.value.themeColor || "#6d5dfc",
      autoRefresh: !!formState.value.autoRefresh,
      showStudents: !!formState.value.showStudents,
      showRaids: !!formState.value.showRaids,
    };
    // Cookie 留空表示不修改已保存的值
    const cookie = formState.value.cookie.trim();
    if (cookie) {
      payload.cookie = cookie;
    }
    const resp = await fetch(`${API}/config`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": getCookie("XSRF-TOKEN"),
      },
      body: JSON.stringify(payload),
    });
    if (!resp.ok) {
      throw new Error("HTTP " + resp.status);
    }
    Toast.success("配置已保存");
    formState.value.cookie = "";
    await loadConfig();
    await loadStats();
  } catch (e: any) {
    Toast.error("保存失败：" + (e?.message || "未知错误"));
  } finally {
    saving.value = false;
  }
}

async function handleRefresh() {
  if (refreshing.value) return;
  refreshing.value = true;
  Toast.info("正在从 B 站获取战绩数据...", { duration: 3000 });
  try {
    const resp = await fetch(`${API}/refresh`, {
      method: "POST",
      headers: { "X-XSRF-TOKEN": getCookie("XSRF-TOKEN") },
    });
    if (!resp.ok) {
      throw new Error("HTTP " + resp.status);
    }
    const data = await resp.json();
    if (data.status === "ok") {
      Toast.success(`数据获取成功！共 ${data.charCount ?? "-"} 个学员`);
      await loadStats();
    } else {
      Toast.error("数据获取失败，请检查 Cookie 是否有效");
    }
  } catch (e: any) {
    Toast.error("数据获取失败：" + (e?.message || "请检查 Cookie 和 UID"));
  } finally {
    refreshing.value = false;
  }
}

function handlePreview() {
  window.open(`${API}/stats/html`, "_blank");
}

async function copyEmbed() {
  try {
    await navigator.clipboard.writeText(embedCode);
    Toast.success("嵌入代码已复制到剪贴板");
  } catch {
    Toast.warning("复制失败，请手动选择复制");
  }
}
</script>

<template>
  <div class="ba-page">
    <VPageHeader title="蔚蓝档案战绩">
      <template #actions>
        <VSpace>
          <VButton :loading="refreshing" @click="handleRefresh">
            <template #icon>
              <IconRefreshLine />
            </template>
            立即刷新
          </VButton>
          <VButton @click="handlePreview">
            <template #icon>
              <IconEye />
            </template>
            预览页面
          </VButton>
        </VSpace>
      </template>
    </VPageHeader>

    <div class="ba-body">
      <VAlert
        v-if="!stats"
        class="ba-card"
        type="warning"
        title="数据尚未加载"
        description="请先在下方填写 B 站 UID 和 Cookie，保存后点击右上角「立即刷新」获取数据。"
      />

      <!-- 玩家数据状态 -->
      <VCard class="ba-card" title="玩家数据">
        <template v-if="stats" #default>
          <VDescription>
            <VDescriptionItem
              label="当前服务器"
              :content="stats.channelName || '（未识别）'"
            />
            <VDescriptionItem label="角色名" :content="stats.roleName || '-'" />
            <VDescriptionItem
              label="等级"
              :content="'Lv.' + (stats.roleLevel ?? '-')"
            />
            <VDescriptionItem
              label="游戏天数"
              :content="String(stats.loginDays ?? '-')"
            />
            <VDescriptionItem
              label="学员收集"
              :content="String(stats.charCount ?? '-')"
            />
            <VDescriptionItem
              label="五星学员"
              :content="String(stats.fiveStar ?? '-')"
            />
            <VDescriptionItem
              label="四星学员"
              :content="String(stats.fourStar ?? '-')"
            />
            <VDescriptionItem
              label="总力战排名"
              :content="
                (stats.totalRaidBoss ? stats.totalRaidBoss + ' · ' : '') +
                (stats.totalRaidRank ?? '-')
              "
            />
            <VDescriptionItem
              label="大决战排名"
              :content="
                (stats.eliminateBoss ? stats.eliminateBoss + ' · ' : '') +
                (stats.eliminateRank ?? '-')
              "
            />
            <VDescriptionItem
              label="数据更新"
              :content="stats.updatedAt || '-'"
            />
          </VDescription>
        </template>
        <template v-else #default>
          <p class="ba-muted">暂无数据</p>
        </template>
      </VCard>

      <!-- 插件配置（原生表单，参照 Halo 官方 TodoList 示例写法） -->
      <VCard class="ba-card" title="插件配置">
        <template #default>
          <div class="ba-form">
            <div class="ba-field">
              <label class="ba-label" for="ba-uid">
                B站 UID <span class="ba-req">*</span>
              </label>
              <input
                id="ba-uid"
                v-model="formState.biliUid"
                class="ba-input"
                placeholder="例如: 123456789"
              />
              <p class="ba-help">
                打开 bilibili.com，右上角头像旁边的数字即为 UID
              </p>
            </div>

            <div class="ba-field">
              <label class="ba-label" for="ba-cookie">B站 Cookie</label>
              <textarea
                id="ba-cookie"
                v-model="formState.cookie"
                class="ba-input"
                rows="3"
                :placeholder="
                  formState.cookieSet
                    ? '已保存（留空则保持不变）'
                    : 'SESSDATA=xxx; bili_jct=xxx'
                "
              ></textarea>
              <p class="ba-help">
                浏览器登录 bilibili.com → F12 → Application → Cookies →
                复制 SESSDATA 和 bili_jct 两个值
              </p>
            </div>

            <div class="ba-grid">
              <div class="ba-field">
                <label class="ba-label" for="ba-hours">
                  自动刷新间隔（小时）
                </label>
                <input
                  id="ba-hours"
                  v-model.number="formState.refreshHours"
                  class="ba-input"
                  type="number"
                  min="1"
                  max="24"
                />
              </div>
              <div class="ba-field">
                <label class="ba-label" for="ba-max">学员显示数量上限（0=不限，页面默认折叠前6名）</label>
                <input
                  id="ba-max"
                  v-model.number="formState.maxStudents"
                  class="ba-input"
                  type="number"
                  min="0"
                />
              </div>
            </div>

            <div class="ba-field">
              <label class="ba-label" for="ba-title">页面标题</label>
              <input
                id="ba-title"
                v-model="formState.pageTitle"
                class="ba-input"
              />
            </div>

            <div class="ba-field">
              <label class="ba-label" for="ba-theme">战绩页主题色（强调色）</label>
              <div class="ba-color-row">
                <input
                  id="ba-theme"
                  v-model="formState.themeColor"
                  type="color"
                  class="ba-color"
                />
                <input
                  v-model="formState.themeColor"
                  class="ba-input ba-color-text"
                  placeholder="#6d5dfc"
                />
              </div>
              <p class="ba-help">
                战绩页面的强调色（页头渐变、排名数字、章节左边线等），默认
                #6d5dfc；页面浅色/深色随访客系统自动切换
              </p>
            </div>

            <div class="ba-switches">
              <label class="ba-switch">
                <input v-model="formState.autoRefresh" type="checkbox" />
                <span>启用自动刷新</span>
              </label>
              <label class="ba-switch">
                <input v-model="formState.showStudents" type="checkbox" />
                <span>显示学员名册</span>
              </label>
              <label class="ba-switch">
                <input v-model="formState.showRaids" type="checkbox" />
                <span>显示总力战记录</span>
              </label>
            </div>

            <div class="ba-actions">
              <button
                class="ba-btn ba-btn-primary"
                type="button"
                :disabled="saving"
                @click="handleSave"
              >
                {{ saving ? "保存中..." : "保存配置" }}
              </button>
            </div>
          </div>
        </template>
      </VCard>

      <!-- 使用说明 -->
      <VCard class="ba-card" title="使用说明">
        <template #default>
          <ol class="ba-steps">
            <li>填写 B站 UID 和 Cookie，点击「保存配置」</li>
            <li>点击右上角「立即刷新」获取战绩数据</li>
            <li>在文章中插入下方代码即可展示战绩页面</li>
            <li>开启自动刷新后，数据过期会按间隔自动补充</li>
          </ol>
        </template>
        <template #footer>
          <div class="ba-embed">
            <code>{{ embedCode }}</code>
            <VButton size="sm" @click="copyEmbed">复制代码</VButton>
          </div>
        </template>
      </VCard>
    </div>
  </div>
</template>

<style scoped>
.ba-page {
  min-height: 100%;
}
.ba-body {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.ba-card {
  max-width: 860px;
}
.ba-muted {
  color: rgba(148, 163, 184, 0.9);
  font-size: 13px;
}
.ba-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.ba-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ba-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.ba-label {
  font-size: 13px;
  font-weight: 600;
  color: inherit;
}
.ba-req {
  color: #e5484d;
}
.ba-input {
  width: 100%;
  box-sizing: border-box;
  padding: 8px 12px;
  font-size: 13px;
  font-family: inherit;
  color: inherit;
  background: rgba(148, 163, 184, 0.1);
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 6px;
  outline: none;
}
.ba-input:focus {
  border-color: #4f46e5;
}
.ba-help {
  margin: 0;
  font-size: 12px;
  color: rgba(148, 163, 184, 0.9);
}
.ba-color-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ba-color {
  width: 44px;
  height: 36px;
  padding: 2px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 6px;
  background: rgba(148, 163, 184, 0.1);
  cursor: pointer;
}
.ba-color-text {
  max-width: 140px;
}
.ba-switches {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
.ba-switch {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  cursor: pointer;
}
.ba-switch input {
  width: 16px;
  height: 16px;
  accent-color: #4f46e5;
  cursor: pointer;
}
.ba-actions {
  display: flex;
  gap: 12px;
}
.ba-btn {
  padding: 9px 22px;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.2s;
}
.ba-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.ba-btn-primary {
  background: #4f46e5;
  color: #fff;
}
.ba-btn-primary:hover:not(:disabled) {
  background: #4338ca;
}
.ba-steps {
  margin: 0;
  padding-left: 20px;
  line-height: 2;
  font-size: 13px;
  color: rgba(148, 163, 184, 0.95);
}
.ba-embed {
  display: flex;
  align-items: center;
  gap: 12px;
}
.ba-embed code {
  flex: 1;
  display: block;
  background: rgba(148, 163, 184, 0.12);
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 12px;
  overflow-x: auto;
  white-space: nowrap;
}
@media (max-width: 640px) {
  .ba-grid {
    grid-template-columns: 1fr;
  }
  .ba-body {
    padding: 16px;
  }
}
</style>
