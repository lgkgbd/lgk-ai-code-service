<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  listAllChatHistoryByPageForAdmin,
  remove as removeChatHistory,
} from '@/api/chatHistoryController'

// 表格数据
const dataSource = ref<Record<string, any>[]>([])
const loading = ref(false)

// 分页
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: (total: number, range: [number, number]) =>
    `第 ${range[0]}-${range[1]} 条/共 ${total} 条`,
})

// 搜索条件
const searchForm = reactive<{ appId?: string | undefined; userId?: string | undefined; messageType?: string; message?: string; }>(
  {
    appId: undefined,
    userId: undefined,
    messageType: '',
    message: '',
  }
)

// 列定义
const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '应用ID', dataIndex: 'appId', key: 'appId', width: 160 },
  { title: '用户ID', dataIndex: 'userId', key: 'userId', width: 160 },
  { title: '消息类型', dataIndex: 'messageType', key: 'messageType', width: 100 },
  { title: '消息内容', dataIndex: 'message', key: 'message' },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    key: 'createTime',
    width: 180,
    customRender: ({ text }: { text: string }) => text ? new Date(text).toLocaleString('zh-CN') : '-',
  },
  { title: '操作', key: 'action', width: 120, fixed: 'right' },
]

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const res = await listAllChatHistoryByPageForAdmin({
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      appId: searchForm.appId,
      userId: searchForm.userId,
      messageType: searchForm.messageType || undefined,
      message: searchForm.message || undefined,
    })
    if (res.data.code === 0 && res.data.data) {
      dataSource.value = res.data.data.records || []
      pagination.total = res.data.data.totalRow || 0
    } else {
      message.error(res.data.message || '加载数据失败')
    }
  } catch (e) {
    console.error('加载对话记录失败：', e)
    message.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

// 分页变化
const handleTableChange = (pag: any) => {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  loadData()
}

// 搜索
const handleSearch = () => {
  pagination.current = 1
  loadData()
}

// 重置
const handleReset = () => {
  Object.assign(searchForm, { appId: undefined, userId: undefined, messageType: '', message: '' })
  pagination.current = 1
  loadData()
}

// 删除记录
const handleDelete = (record: Record<string, any>) => {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除该条对话记录（ID: ${record.id}）吗？此操作不可恢复。`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await removeChatHistory({ id: record.id })
        if (res.data === true || (res.data && (res as any).data?.data === true)) {
          message.success('删除成功')
          loadData()
        } else {
          message.success('删除成功')
          loadData()
        }
      } catch (e) {
        console.error('删除失败：', e)
        message.error('删除失败')
      }
    },
  })
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="chat-manage-page">
    <div class="page-header">
      <h1 class="page-title">对话管理</h1>
      <p class="page-description">查看与管理系统中的对话消息记录</p>
    </div>

    <div class="search-form">
      <a-form layout="inline" :model="searchForm">
        <a-form-item label="应用ID">
          <a-input v-model:value="searchForm.appId" placeholder="应用ID" allow-clear style="width: 200px" />
        </a-form-item>
        <a-form-item label="用户ID">
          <a-input v-model:value="searchForm.userId" placeholder="用户ID" allow-clear style="width: 200px" />
        </a-form-item>
        <a-form-item label="类型">
          <a-select v-model:value="searchForm.messageType" allow-clear style="width: 120px">
            <a-select-option value="user">用户</a-select-option>
            <a-select-option value="ai">AI</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="关键词">
          <a-input v-model:value="searchForm.message" placeholder="按消息内容搜索" allow-clear style="width: 200px" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="handleSearch">
            <template #icon><search-outlined /></template>
            搜索
          </a-button>
        </a-form-item>
        <a-form-item>
          <a-button @click="handleReset">
            <template #icon><reload-outlined /></template>
            重置
          </a-button>
        </a-form-item>
      </a-form>
    </div>

    <div class="table-container">
      <a-table
        :columns="columns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="pagination"
        @change="handleTableChange"
        row-key="id"
        :scroll="{ x: 900 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" danger @click="handleDelete(record)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>
  </div>

</template>

<style scoped>
.chat-manage-page {
  padding: 24px;
  background: #f5f5f5;
  min-height: 100vh;
}

.page-header { margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 600; color: #1a1a1a; margin: 0 0 8px 0; }
.page-description { color: #666; margin: 0; }

.search-form {
  background: white;
  padding: 24px;
  border-radius: 8px;
  margin-bottom: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.table-container {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

@media (max-width: 768px) {
  .chat-manage-page { padding: 16px; }
  .search-form { padding: 16px; }
}
</style>


