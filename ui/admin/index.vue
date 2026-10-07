/**
 * 蔚蓝档案战绩 - Halo 后台管理页面
 * 
 * 这个文件会被 Halo 加载为插件的后台管理界面
 * 出现在 Halo 侧边栏菜单中
 */

<template>
  <div class="blue-archive-admin">
    <BasicForm
      v-model="formState"
      :schema="formSchema"
      @submit="handleSubmit"
    >
      <template #actions>
        <a-space>
          <a-button type="primary" @click="handleSubmit">保存配置</a-button>
          <a-button @click="handleRefresh">立即刷新数据</a-button>
          <a-button @click="handlePreview">预览页面</a-button>
        </a-space>
      </template>
    </BasicForm>
    
    <a-divider />
    
    <a-descriptions :column="2" bordered>
      <a-descriptions-item label="数据状态">
        <a-badge :status="dataLoaded ? 'success' : 'default'" :text="dataLoaded ? '已加载' : '未加载'" />
      </a-descriptions-item>
      <a-descriptions-item label="学员数量">{{ stats.charCount || '-' }}</a-descriptions-item>
      <a-descriptions-item label="五星学员">{{ stats.fiveStar || '-' }}</a-descriptions-item>
      <a-descriptions-item label="最后更新">{{ stats.updatedAt || '-' }}</a-descriptions-item>
    </a-descriptions>
    
    <a-divider />
    
    <a-alert
      message="使用说明"
      description="1. 填写 B站 UID 和 Cookie  2. 点击保存配置  3. 点击立即刷新数据  4. 在文章中插入 iframe 展示"
      type="info"
      show-icon
    />
    
    <a-divider />
    
    <h3>嵌入代码</h3>
    <pre><code>&lt;iframe src="/api/blue-archive/stats/html" 
        width="100%" height="1200" 
        style="border:none;" loading="lazy"&gt;&lt;/iframe&gt;</code></pre>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { message } from 'ant-design-vue';

const formState = ref({
  biliUid: '',
  cookie: '',
  refreshHours: 6,
  maxStudents: 30,
  pageTitle: '蔚蓝档案战绩',
  autoRefresh: true,
  showStudents: true,
  showRaids: true
});

const stats = ref({});
const dataLoaded = ref(false);

const formSchema = [
  {
    component: 'input',
    name: 'biliUid',
    label: 'B站 UID',
    props: {
      placeholder: '例如: 123456789',
      maxLength: 20
    },
    help: '打开 bilibili.com，右上角头像旁的数字'
  },
  {
    component: 'textarea',
    name: 'cookie',
    label: 'B站 Cookie',
    props: {
      placeholder: 'SESSDATA=xxx; bili_jct=xxx',
      rows: 3
    },
    help: '浏览器打开 bilibili.com → F12 → Application → Cookies → 复制 SESSDATA 和 bili_jct'
  },
  {
    component: 'number',
    name: 'refreshHours',
    label: '刷新间隔（小时）',
    props: {
      min: 1,
      max: 24
    }
  },
  {
    component: 'number',
    name: 'maxStudents',
    label: '学员显示数量',
    props: {
      min: 6,
      max: 174
    }
  },
  {
    component: 'input',
    name: 'pageTitle',
    label: '页面标题'
  },
  {
    component: 'switch',
    name: 'autoRefresh',
    label: '自动刷新'
  },
  {
    component: 'switch',
    name: 'showStudents',
    label: '显示学员列表'
  },
  {
    component: 'switch',
    name: 'showRaids',
    label: '显示总力战'
  }
];

onMounted(async () => {
  try {
    const resp = await fetch('/api/blue-archive/config');
    const config = await resp.json();
    formState.value = { ...formState.value, ...config };
  } catch (e) {
    console.error('加载配置失败:', e);
  }
  
  try {
    const resp = await fetch('/api/blue-archive/stats');
    if (resp.ok) {
      stats.value = await resp.json();
      dataLoaded.value = true;
    }
  } catch (e) {
    console.error('加载数据失败:', e);
  }
});

async function handleSubmit() {
  try {
    const resp = await fetch('/api/blue-archive/config', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(formState.value)
    });
    if (resp.ok) {
      message.success('配置已保存');
    } else {
      message.error('保存失败');
    }
  } catch (e) {
    message.error('保存失败: ' + e.message);
  }
}

async function handleRefresh() {
  message.loading('正在获取数据...', 0);
  try {
    const resp = await fetch('/api/blue-archive/refresh', { method: 'POST' });
    const result = await resp.json();
    message.destroy();
    if (result.status === 'ok') {
      message.success(`数据获取成功！共 ${result.charCount} 个学员`);
      const statsResp = await fetch('/api/blue-archive/stats');
      stats.value = await statsResp.json();
      dataLoaded.value = true;
    } else {
      message.error('数据获取失败，请检查 Cookie 是否有效');
    }
  } catch (e) {
    message.destroy();
    message.error('请求失败: ' + e.message);
  }
}

function handlePreview() {
  window.open('/api/blue-archive/stats/html', '_blank');
}
</script>

<style scoped>
.blue-archive-admin {
  padding: 24px;
}
pre {
  background: #f5f5f5;
  padding: 16px;
  border-radius: 8px;
  overflow-x: auto;
  font-size: 13px;
}
</style>
