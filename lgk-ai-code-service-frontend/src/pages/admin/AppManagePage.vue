<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal, Tag } from 'ant-design-vue'
import {
  listAppVoByPageByAdmin,
  deleteAppByAdmin,
  updateAppByAdmin
} from '@/api/appController'
import { getCodeGenTypeConfig } from '@/constants/codeGenType'
import type { API } from '@/api/typings'

const router = useRouter()

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
const searchForm = reactive({
  appName: '',
  userName: '',
  userAccount: '',
})

// 表格列定义
const columns = [
  {
    title: 'ID',
    dataIndex: 'id',
    key: 'id',
    width: 80,
  },
  {
    title: '封面',
    dataIndex: 'cover',
    key: 'cover',
    width: 100,
  },
  {
    title: '应用名称',
    dataIndex: 'appName',
    key: 'appName',
    width: 200,
  },
  {
    title: '创建者',
    dataIndex: 'user',
    key: 'user',
    width: 120,
    customRender: ({ record }: { record: Record<string, any> }) => record.user?.userName || '-',
  },
  {
    title: '创建者账号',
    dataIndex: 'user',
    key: 'userAccount',
    width: 150,
    customRender: ({ record }: { record: Record<string, any> }) => record.user?.userAccount || '-',
  },
  {
    title: '类型',
    dataIndex: 'codeGenType',
    key: 'codeGenType',
    width: 120,
  },
  {
    title: '优先级',
    dataIndex: 'priority',
    key: 'priority',
    width: 100,
  },
  {
    title: '部署时间',
    dataIndex: 'deployedTime',
    key: 'deployedTime',
    width: 180,
    customRender: ({ text }: { text: string }) =>
      text ? new Date(text).toLocaleString('zh-CN') : '-',
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    key: 'createTime',
    width: 180,
    customRender: ({ text }: { text: string }) =>
      text ? new Date(text).toLocaleString('zh-CN') : '-',
  },
  {
    title: '操作',
    key: 'action',
    width: 200,
    fixed: 'right',
  },
]

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const res = await listAppVoByPageByAdmin({
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      appName: searchForm.appName,
      userName: searchForm.userName,
      userAccount: searchForm.userAccount,
    })

    if (res.data.code === 0 && res.data.data) {
      dataSource.value = res.data.data.records || []
      pagination.total = res.data.data.totalRow || 0
    } else {
      message.error({
        content: res.data.message || '加载数据失败',
        duration: 3,
        closable: true,
        onClick: () => {
          message.destroy()
        },
      })
    }
  } catch (error) {
    console.error('加载应用列表失败:', error)
    message.error({
      content: '加载数据失败',
      duration: 3,
      closable: true,
      onClick: () => {
        message.destroy()
      },
    })
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

// 重置搜索
const handleReset = () => {
  Object.assign(searchForm, {
    appName: '',
    userName: '',
    userAccount: '',
  })
  pagination.current = 1
  loadData()
}

// 编辑应用
const handleEdit = (record: Record<string, any>) => {
  // 跳转到应用信息修改页
  router.push(`/app/edit/${record.id}`)
}

// 删除应用
const handleDelete = (record: Record<string, any>) => {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除应用"${record.appName}"吗？此操作不可恢复。`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await deleteAppByAdmin({ id: record.id })
        if (res.data.code === 0) {
          message.success({
            content: '删除成功',
            duration: 3,
            closable: true,
            onClick: () => {
              message.destroy()
            },
          })
          loadData()
        } else {
          message.error({
            content: res.data.message || '删除失败',
            duration: 3,
            closable: true,
            onClick: () => {
              message.destroy()
            },
          })
        }
      } catch (error) {
        console.error('删除应用失败:', error)
        message.error({
          content: '删除失败',
          duration: 3,
          closable: true,
          onClick: () => {
            message.destroy()
          },
        })
      }
    },
  })
}

// 设置/取消精选
const handleFeature = (record: Record<string, any>) => {
  const isFeatured = record.priority === 99
  const title = isFeatured ? '取消精选' : '设置精选'
  const content = isFeatured
    ? `确定要取消应用"${record.appName}"的精选状态吗？`
    : `确定要将应用"${record.appName}"设置为精选应用吗？`

  Modal.confirm({
    title,
    content,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await updateAppByAdmin({
          id: record.id,
          appName: record.appName,
          cover: record.cover,
          priority: isFeatured ? 0 : 99, // 如果是精选则取消，否则设置为精选
        })
        if (res.data.code === 0) {
          message.success({
            content: isFeatured ? '取消精选成功' : '设置精选成功',
            duration: 3, // 3秒后自动消失
            closable: true, // 可点击关闭按钮
            onClick: () => {
              // 点击消息体时立即关闭
              message.destroy()
            },
          })
          loadData()
        } else {
          message.error({
            content: res.data.message || '操作失败',
            duration: 3, // 3秒后自动消失
            closable: true, // 可点击关闭按钮
            onClick: () => {
              // 点击消息体时立即关闭
              message.destroy()
            },
          })
        }
      } catch (error) {
        console.error('操作失败:', error)
        message.error({
          content: '操作失败',
          duration: 3,
          closable: true,
          onClick: () => {
            message.destroy()
          },
        })
      }
    },
  })
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="app-manage-page">
    <div class="page-header">
      <h1 class="page-title">应用管理</h1>
      <p class="page-description">管理系统中的所有应用，支持编辑、删除、设置精选等操作</p>
    </div>

    <!-- 搜索表单 -->
    <div class="search-form">
      <a-form layout="inline" :model="searchForm">
        <a-form-item label="应用名称">
          <a-input
            v-model:value="searchForm.appName"
            placeholder="请输入应用名称"
            allow-clear
            style="width: 200px"
          />
        </a-form-item>
        <a-form-item label="创建者">
          <a-input
            v-model:value="searchForm.userName"
            placeholder="请输入创建者姓名"
            allow-clear
            style="width: 150px"
          />
        </a-form-item>
        <a-form-item label="创建者账号">
          <a-input
            v-model:value="searchForm.userAccount"
            placeholder="请输入创建者账号"
            allow-clear
            style="width: 150px"
          />
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

    <!-- 数据表格 -->
    <div class="table-container">
      <a-table
        :columns="columns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="pagination"
        @change="handleTableChange"
        row-key="id"
      >
        <!-- 封面列自定义渲染 -->
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'cover'">
            <div class="cover-cell">
              <img
                v-if="record.cover"
                :src="record.cover"
                :alt="record.appName"
                class="cover-image"
              />
              <div v-else class="cover-placeholder">
                <code-outlined />
              </div>
            </div>
          </template>

          <!-- 代码类型列自定义渲染 -->
          <template v-else-if="column.key === 'codeGenType'">
            <a-tag :color="getCodeGenTypeConfig(record.codeGenType)?.color">
              {{ getCodeGenTypeConfig(record.codeGenType)?.label }}
            </a-tag>
          </template>

          <!-- 操作列 -->
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleEdit(record)">
                编辑
              </a-button>
              <a-button type="link" size="small" danger @click="handleDelete(record)">
                删除
              </a-button>
              <a-button type="link" size="small" @click="handleFeature(record)">
                {{ record.priority === 99 ? '取消精选' : '设置精选' }}
              </a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>
  </div>
</template>

<style scoped>
.app-manage-page {
  padding: 24px;
  background: #f5f5f5;
  min-height: 100vh;
}

.page-header {
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 8px 0;
}

.page-description {
  color: #666;
  margin: 0;
}

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

/* 封面单元格样式 */
.cover-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 40px;
}

.cover-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid #e8e8e8;
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  border-radius: 4px;
  color: #999;
  font-size: 16px;
}

.action-buttons {
  display: flex;
  gap: 8px;
}

.action-buttons .ant-btn {
  padding: 0;
  height: auto;
  line-height: 1;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .app-manage-page {
    padding: 16px;
  }

  .search-form {
    padding: 16px;
  }

  .search-form .ant-form-item {
    margin-bottom: 12px;
  }
}
</style>
