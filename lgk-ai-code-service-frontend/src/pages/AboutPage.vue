<template>
  <div class="about-page">
    <a-typography>
      <a-typography-title :level="1">关于我们</a-typography-title>
      <a-typography-paragraph>
        这是一个基于 <a-typography-text mark>AI + LangGraph 多智能体工作流</a-typography-text> 的代码生成与服务管理平台，
        旨在帮助用户快速构建应用程序、提升开发效率。
      </a-typography-paragraph>
    </a-typography>

    <a-divider />

    <a-typography>
      <a-typography-title :level="2">🛠️ 技术栈</a-typography-title>
    </a-typography>

    <a-tabs v-model:activeKey="activeTab" size="large">
      <a-tab-pane key="backend" tab="后端技术">
        <a-table :columns="backendColumns" :data-source="backendData" :pagination="false" bordered size="small">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'tags'">
              <a-tag v-for="tag in record.tags" :key="tag" :color="getTagColor(tag)">{{ tag }}</a-tag>
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="frontend" tab="前端技术">
        <a-table :columns="frontendColumns" :data-source="frontendData" :pagination="false" bordered size="small">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'tags'">
              <a-tag v-for="tag in record.tags" :key="tag" :color="getTagColor(tag)">{{ tag }}</a-tag>
            </template>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <a-divider />

    <a-typography>
      <a-typography-title :level="2">🚀 核心功能模块</a-typography-title>
    </a-typography>

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :sm="12" :md="8" v-for="feature in features" :key="feature.title">
        <a-card hoverable>
          <template #extra><span style="font-size: 24px">{{ feature.icon }}</span></template>
          <a-card-meta :title="feature.title" :description="feature.description" />
        </a-card>
      </a-col>
    </a-row>

    <a-divider />

    <a-typography>
      <a-typography-title :level="2">🏗️ 系统架构</a-typography-title>
      <a-typography-paragraph>
        <a-steps :current="3" size="small">
          <a-step title="用户交互" description="Vue3 前端界面" />
          <a-step title="API 网关" description="Spring Boot REST API" />
          <a-step title="AI 引擎" description="LangChain4j + LangGraph" />
          <a-step title="数据层" description="MySQL / Redis / ES / MinIO" />
        </a-steps>
      </a-typography-paragraph>
    </a-typography>

    <a-divider />

    <a-typography>
      <a-typography-title :level="2">🤖 AI 模型支持</a-typography-title>
      <a-list size="small" bordered :data-source="aiModels">
        <template #renderItem="{ item }">
          <a-list-item>
            <a-list-item-meta :title="item.name" :description="item.desc" />
            <template #actions>
              <a-tag :color="item.color">{{ item.type }}</a-tag>
            </template>
          </a-list-item>
        </template>
      </a-list>
    </a-typography>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const activeTab = ref('backend')

const backendColumns = [
  { title: '类别', dataIndex: 'category', key: 'category', width: 120 },
  { title: '技术', dataIndex: 'tech', key: 'tech' },
  { title: '说明', dataIndex: 'desc', key: 'desc' },
  { title: '标签', key: 'tags', width: 200 }
]

const backendData = [
  { category: '框架', tech: 'Spring Boot 3.5.4', desc: '核心 Web 框架', tags: ['Java 21', 'Web'] },
  { category: 'ORM', tech: 'MyBatis-Flex', desc: '轻量级 ORM 框架，支持多数据源', tags: ['MySQL'] },
  { category: '缓存', tech: 'Redis + Redisson', desc: '分布式缓存与锁', tags: ['分布式', '锁'] },
  { category: '缓存', tech: 'Caffeine', desc: '本地高性能缓存', tags: ['本地'] },
  { category: '消息', tech: 'RabbitMQ', desc: '异步消息队列', tags: ['AMQP'] },
  { category: '搜索', tech: 'Elasticsearch', desc: '全文搜索引擎', tags: ['全文搜索'] },
  { category: '存储', tech: 'MinIO', desc: '对象存储服务', tags: ['S3兼容'] },
  { category: 'AI', tech: 'LangChain4j', desc: 'LLM 应用开发框架', tags: ['AI'] },
  { category: 'AI', tech: 'LangGraph4j', desc: '多智能体工作流编排', tags: ['多智能体'] },
  { category: '同步', tech: 'Canal', desc: 'MySQL → ES 数据同步', tags: ['CDC'] },
  { category: '工具', tech: 'Hutool', desc: 'Java 工具库', tags: ['工具'] },
  { category: '文档', tech: 'Knife4j', desc: 'API 文档生成', tags: ['Swagger'] }
]

const frontendColumns = [
  { title: '类别', dataIndex: 'category', key: 'category', width: 100 },
  { title: '技术', dataIndex: 'tech', key: 'tech' },
  { title: '说明', dataIndex: 'desc', key: 'desc' },
  { title: '标签', key: 'tags', width: 150 }
]

const frontendData = [
  { category: '框架', tech: 'Vue 3.5', desc: '渐进式前端框架', tags: ['TypeScript'] },
  { category: 'UI', tech: 'Ant Design Vue 4.2', desc: '企业级 UI 组件库', tags: ['组件库'] },
  { category: '状态', tech: 'Pinia', desc: 'Vue 状态管理', tags: ['Store'] },
  { category: '路由', tech: 'Vue Router 4', desc: '前端路由管理', tags: ['SPA'] },
  { category: '构建', tech: 'Vite 7.0', desc: '下一代前端构建工具', tags: ['DevServer'] },
  { category: 'Markdown', tech: 'Vditor + marked', desc: 'Markdown 编辑器与解析', tags: ['富文本'] },
  { category: '代码', tech: 'highlight.js', desc: '代码语法高亮', tags: ['语法高亮'] },
  { category: 'HTTP', tech: 'Axios', desc: 'HTTP 请求库', tags: ['Ajax'] }
]

const features = [
  { icon: '🤖', title: 'AI 代码生成', description: '基于 LangGraph 多智能体工作流，智能编排代码生成流程' },
  { icon: '💬', title: 'AI 对话', description: 'SSE 流式响应，支持 GuardRail 安全防护机制' },
  { icon: '📝', title: '帖子社区', description: '点赞系统、Redis 分布式锁保障高并发' },
  { icon: '🔗', title: '短链接服务', description: 'MinIO 对象存储，支持文件上传与分享' },
  { icon: '🖼️', title: '图片/视频搜索', description: 'MCP 工具扩展，支持多源内容搜索' },
  { icon: '🔍', title: '全文搜索', description: 'Elasticsearch 集成，支持帖子内容检索' },
  { icon: '📦', title: '文件管理', description: 'MinIO 对象存储，统一的文件管理服务' },
  { icon: '🔄', title: '数据同步', description: 'Canal CDC 实时同步 MySQL 到 Elasticsearch' }
]

const aiModels = [
  { name: 'DeepSeek Chat', desc: '通用对话模型，支持流式输出', type: '对话', color: 'blue' },
  { name: 'DeepSeek Reasoner', desc: '深度推理模型，用于复杂逻辑分析', type: '推理', color: 'purple' },
  { name: 'Qwen-turbo', desc: '阿里通义轻量级模型，用于智能路由', type: '路由', color: 'green' },
  { name: 'Wan2.2-t2i-flash', desc: '阿里图片生成模型，用于文生图', type: '图片生成', color: 'orange' }
]

const getTagColor = (tag: string) => {
  const colors: Record<string, string> = {
    'Java 21': 'blue',
    'AI': 'purple',
    '多智能体': 'magenta',
    '分布式': 'orange',
    'TypeScript': 'blue',
    '组件库': 'cyan'
  }
  return colors[tag] || 'default'
}
</script>

<style scoped>
.about-page {
  padding: 24px;
  max-width: 1000px;
  margin: 0 auto;
  background: #fff;
  min-height: 100%;
}

:deep(.ant-typography-title) {
  color: #1890ff;
}

:deep(.ant-card) {
  transition: all 0.3s;
}

:deep(.ant-card:hover) {
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.3);
}
</style>
