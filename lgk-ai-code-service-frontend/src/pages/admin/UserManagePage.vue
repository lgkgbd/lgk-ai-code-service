<template>
  <div id="userManagePage">
    <!-- 搜索表单 -->
    <a-form layout="inline" :model="searchParams" @finish="doSearch" style="margin-bottom: 16px;">
      <a-form-item label="账号">
        <a-input v-model:value="searchParams.userAccount" placeholder="输入账号" />
      </a-form-item>
      <a-form-item label="用户名">
        <a-input v-model:value="searchParams.userName" placeholder="输入用户名" />
      </a-form-item>
      <a-form-item label="用户角色">
        <a-select v-model:value="searchParams.userRole" placeholder="选择角色" style="width: 120px;" allowClear>
          <a-select-option value="admin">管理员</a-select-option>
          <a-select-option value="user">普通用户</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">搜索</a-button>
        <a-button style="margin-left: 8px;" @click="resetSearch">重置</a-button>
      </a-form-item>
    </a-form>

    <!-- 操作按钮 -->
    <div style="margin-bottom: 16px;">
      <a-button type="primary" @click="showAddModal">添加用户</a-button>
    </div>

    <!-- 表格 -->
    <a-table
      :columns="columns"
      :data-source="data"
      :pagination="pagination"
      @change="doTableChange"
      :loading="loading"
      row-key="id"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'userAvatar'">
          <a-avatar :src="record.userAvatar" :size="40">
            {{ record.userName?.charAt(0)?.toUpperCase() }}
          </a-avatar>
        </template>
        <template v-else-if="column.dataIndex === 'userRole'">
          <a-tag :color="record.userRole === 'admin' ? 'green' : 'blue'">
            {{ record.userRole === 'admin' ? '管理员' : '普通用户' }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="showEditModal(record)">编辑</a-button>
            <a-popconfirm
              title="确定要删除这个用户吗？"
              @confirm="doDelete(record.id)"
              ok-text="确定"
              cancel-text="取消"
            >
              <a-button type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 添加/编辑用户模态框 -->
    <a-modal
      v-model:open="modalVisible"
      :title="isEdit ? '编辑用户' : '添加用户'"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
      :confirm-loading="modalLoading"
    >
      <a-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        layout="vertical"
      >
        <a-form-item label="账号" name="userAccount">
          <a-input v-model:value="formData.userAccount" placeholder="请输入账号" :disabled="isEdit" />
        </a-form-item>
        <a-form-item label="用户名" name="userName">
          <a-input v-model:value="formData.userName" placeholder="请输入用户名" />
        </a-form-item>
        <a-form-item label="密码" name="userPassword" v-if="!isEdit">
          <a-input-password v-model:value="formData.userPassword" placeholder="请输入密码" />
        </a-form-item>
        <a-form-item label="用户角色" name="userRole">
          <a-select v-model:value="formData.userRole" placeholder="请选择角色">
            <a-select-option value="user">普通用户</a-select-option>
            <a-select-option value="admin">管理员</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="用户简介" name="userProfile">
          <a-textarea v-model:value="formData.userProfile" placeholder="请输入用户简介" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { deleteUser, listUserVoByPage, addUser, updateUser, getUserVoById } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import type { FormInstance } from 'ant-design-vue'

const columns = [
  {
    title: 'ID',
    dataIndex: 'id',
    width: 80,
  },
  {
    title: '账号',
    dataIndex: 'userAccount',
    width: 120,
  },
  {
    title: '用户名',
    dataIndex: 'userName',
    width: 120,
  },
  {
    title: '头像',
    dataIndex: 'userAvatar',
    width: 80,
  },
  {
    title: '简介',
    dataIndex: 'userProfile',
    width: 200,
    ellipsis: true,
  },
  {
    title: '用户角色',
    dataIndex: 'userRole',
    width: 100,
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    width: 160,
  },
  {
    title: '操作',
    key: 'action',
    width: 120,
    fixed: 'right',
  },
]

// 数据
const data = ref<API.UserVO[]>([])
const total = ref(0)
const loading = ref(false)

// 搜索条件
const searchParams = reactive<API.UserQueryRequest>({
  pageNum: 1,
  pageSize: 10,
  userAccount: '',
  userName: '',
  userRole: undefined,
})

// 模态框相关
const modalVisible = ref(false)
const modalLoading = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const formData = reactive<API.UserAddRequest & { userPassword?: string; id?: number }>({
  id: undefined,
  userAccount: '',
  userName: '',
  userPassword: '',
  userRole: 'user',
  userProfile: '',
})

// 表单验证规则
const formRules = {
  userAccount: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 4, max: 20, message: '账号长度在 4 到 20 个字符', trigger: 'blur' },
  ],
  userName: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度在 2 到 20 个字符', trigger: 'blur' },
  ],
  userPassword: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在 6 到 20 个字符', trigger: 'blur' },
  ],
  userRole: [
    { required: true, message: '请选择用户角色', trigger: 'change' },
  ],
}

// 获取数据
const fetchData = async () => {
  loading.value = true
  try {
    const res = await listUserVoByPage({
      ...searchParams,
    })
    if (res.data.code === 0 && res.data.data) {
      data.value = res.data.data.records ?? []
      total.value = res.data.data.totalRow ?? 0
    } else {
      message.error({
        content: '获取数据失败，' + res.data.message,
        duration: 3,
        closable: true,
        onClick: () => {
          message.destroy()
        },
      })
    }
  } catch {
    message.error({
      content: '获取数据失败',
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

// 分页参数
const pagination = computed(() => {
  return {
    current: searchParams.pageNum ?? 1,
    pageSize: searchParams.pageSize ?? 10,
    total: total.value,
    showSizeChanger: true,
    showQuickJumper: true,
    showTotal: (total: number) => `共 ${total} 条`,
  }
})

// 表格变化处理
const doTableChange = (page: { current: number; pageSize: number }) => {
  searchParams.pageNum = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

// 搜索
const doSearch = () => {
  searchParams.pageNum = 1
  fetchData()
}

// 重置搜索
const resetSearch = () => {
  searchParams.userAccount = ''
  searchParams.userName = ''
  searchParams.userRole = undefined
  searchParams.pageNum = 1
  fetchData()
}

// 显示添加模态框
const showAddModal = () => {
  isEdit.value = false
  modalVisible.value = true
  resetForm()
}

// 显示编辑模态框
const showEditModal = async (record: API.UserVO) => {
  isEdit.value = true
  modalVisible.value = true
  resetForm()

  try {
    const res = await getUserVoById({ id: record.id! })
    if (res.data.code === 0 && res.data.data) {
      Object.assign(formData, {
        id: res.data.data.id,
        userAccount: res.data.data.userAccount,
        userName: res.data.data.userName,
        userRole: res.data.data.userRole,
        userProfile: res.data.data.userProfile,
      })
    }
  } catch {
    message.error({
      content: '获取用户信息失败',
      duration: 3,
      closable: true,
      onClick: () => {
        message.destroy()
      },
    })
  }
}

// 重置表单
const resetForm = () => {
  Object.assign(formData, {
    id: undefined,
    userAccount: '',
    userName: '',
    userPassword: '',
    userRole: 'user',
    userProfile: '',
  })
  formRef.value?.clearValidate()
}

// 模态框确认
const handleModalOk = async () => {
  try {
    await formRef.value?.validate()
    modalLoading.value = true

    if (isEdit.value) {
      const res = await updateUser(formData)
      if (res.data.code === 0) {
        message.success({
          content: '更新成功',
          duration: 3,
          closable: true,
          onClick: () => {
            message.destroy()
          },
        })
        modalVisible.value = false
        fetchData()
      } else {
        message.error({
          content: '更新失败：' + res.data.message,
          duration: 3,
          closable: true,
          onClick: () => {
            message.destroy()
          },
        })
      }
    } else {
      const res = await addUser(formData)
      if (res.data.code === 0) {
        message.success({
          content: '添加成功',
          duration: 3,
          closable: true,
          onClick: () => {
            message.destroy()
          },
        })
        modalVisible.value = false
        fetchData()
      } else {
        message.error({
          content: '添加失败：' + res.data.message,
          duration: 3,
          closable: true,
          onClick: () => {
            message.destroy()
          },
        })
      }
    }
  } catch (err) {
    console.error('表单验证失败', err)
  } finally {
    modalLoading.value = false
  }
}

// 模态框取消
const handleModalCancel = () => {
  modalVisible.value = false
  resetForm()
}

// 删除数据
const doDelete = async (id: number) => {
  if (!id) {
    return
  }
  try {
    const res = await deleteUser({ id })
    if (res.data.code === 0) {
      message.success({
        content: '删除成功',
        duration: 3,
        closable: true,
        onClick: () => {
          message.destroy()
        },
      })
      fetchData()
    } else {
      message.error({
        content: '删除失败：' + res.data.message,
        duration: 3,
        closable: true,
        onClick: () => {
          message.destroy()
        },
      })
    }
  } catch {
    message.error({
      content: '删除失败',
      duration: 3,
      closable: true,
      onClick: () => {
        message.destroy()
      },
    })
  }
}

// 页面加载时请求一次
onMounted(() => {
  fetchData()
})
</script>

<style scoped>
#userManagePage {
  padding: 16px;
}
</style>
