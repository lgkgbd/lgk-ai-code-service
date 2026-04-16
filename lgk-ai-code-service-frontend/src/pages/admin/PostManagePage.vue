<template>
  <div id="postManagePage">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <a-input-search
        v-model:value="searchKeyword"
        placeholder="搜索标题 / 内容关键词"
        enter-button="搜索"
        allow-clear
        style="width: 400px;"
        @search="doSearch"
      />
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
        <template v-if="column.dataIndex === 'coverImage'">
          <a-image
            v-if="record.coverImage"
            :src="record.coverImage"
            :width="60"
            :height="60"
            style="object-fit: cover; border-radius: 4px;"
          />
          <span v-else style="color: #999;">无封面</span>
        </template>
        <template v-else-if="column.dataIndex === 'title'">
          <a :href="`/post/${record.id}`" target="_blank" style="color: #1890ff;">
            {{ record.title }}
          </a>
        </template>
        <template v-else-if="column.dataIndex === 'tags'">
          <a-tag v-for="tag in record.tags" :key="tag" color="blue" style="margin: 2px;">
            {{ tag }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'user'">
          <a-space>
            <a-avatar :src="record.user?.userAvatar" :size="24">
              {{ record.user?.userName?.charAt(0)?.toUpperCase() }}
            </a-avatar>
            <span>{{ record.user?.userName }}</span>
          </a-space>
        </template>
        <template v-else-if="column.dataIndex === 'thumbNum'">
          <span style="color: #ff4d4f;">
            <LikeOutlined /> {{ record.thumbNum ?? 0 }}
          </span>
        </template>
        <template v-else-if="column.dataIndex === 'favourNum'">
          <span style="color: #faad14;">
            <StarOutlined /> {{ record.favourNum ?? 0 }}
          </span>
        </template>
        <template v-else-if="column.dataIndex === 'viewNum'">
          <span style="color: #52c41a;">
            <EyeOutlined /> {{ record.viewNum ?? 0 }}
          </span>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="showEditModal(record)">编辑</a-button>
            <a-popconfirm
              title="确定要删除这个帖子吗？"
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

    <!-- 编辑帖子模态框 -->
    <a-modal
      v-model:open="modalVisible"
      title="编辑帖子"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
      :confirm-loading="modalLoading"
      width="700px"
    >
      <a-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        layout="vertical"
      >
        <a-form-item label="标题" name="title">
          <a-input v-model:value="formData.title" placeholder="请输入标题" />
        </a-form-item>
        <a-form-item label="封面图片" name="coverImage">
          <a-input v-model:value="formData.coverImage" placeholder="请输入封面图片URL" />
        </a-form-item>
        <a-form-item label="标签" name="tags">
          <a-select
            v-model:value="formData.tags"
            placeholder="选择标签"
            mode="multiple"
            allowClear
          >
            <a-select-option value="java">java</a-select-option>
            <a-select-option value="python">python</a-select-option>
            <a-select-option value="前端">前端</a-select-option>
            <a-select-option value="后端">后端</a-select-option>
            <a-select-option value="算法">算法</a-select-option>
            <a-select-option value="架构">架构</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="内容" name="content">
          <a-textarea v-model:value="formData.content" placeholder="请输入内容" :rows="8" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { listPostByPage, deletePost, updatePost, getPostVoById } from '@/api/postController.ts'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import type { FormInstance } from 'ant-design-vue'
import { LikeOutlined, StarOutlined, EyeOutlined } from '@ant-design/icons-vue'

const columns = [
  {
    title: 'ID',
    dataIndex: 'id',
    width: 80,
  },
  {
    title: '封面',
    dataIndex: 'coverImage',
    width: 80,
  },
  {
    title: '标题',
    dataIndex: 'title',
    width: 200,
    ellipsis: true,
  },
  {
    title: '作者',
    dataIndex: 'user',
    width: 120,
  },
  {
    title: '标签',
    dataIndex: 'tags',
    width: 150,
  },
  {
    title: '点赞',
    dataIndex: 'thumbNum',
    width: 80,
  },
  {
    title: '收藏',
    dataIndex: 'favourNum',
    width: 80,
  },
  {
    title: '浏览',
    dataIndex: 'viewNum',
    width: 80,
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
const data = ref<API.PostVO[]>([])
const total = ref(0)
const loading = ref(false)

// 全局搜索关键词
const searchKeyword = ref('')

// 搜索条件
const searchParams = reactive<API.PostQueryRequest>({
  pageNum: 1,
  pageSize: 10,
  searchText: '',
})

// 模态框相关
const modalVisible = ref(false)
const modalLoading = ref(false)
const formRef = ref<FormInstance>()
const formData = reactive<API.PostUpdateRequest>({
  id: undefined,
  title: '',
  content: '',
  tags: [],
  coverImage: '',
})

// 表单验证规则
const formRules = {
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { min: 2, max: 100, message: '标题长度在 2 到 100 个字符', trigger: 'blur' },
  ],
  content: [
    { required: true, message: '请输入内容', trigger: 'blur' },
  ],
}

// 获取数据
const fetchData = async () => {
  loading.value = true
  try {
    const res = await listPostByPage({
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
  searchParams.searchText = searchKeyword.value || ''
  searchParams.pageNum = 1
  fetchData()
}

// 重置搜索
const resetSearch = () => {
  searchKeyword.value = ''
  searchParams.searchText = ''
  searchParams.pageNum = 1
  fetchData()
}

// 显示编辑模态框
const showEditModal = async (record: API.PostVO) => {
  modalVisible.value = true
  resetForm()

  try {
    const res = await getPostVoById({ id: record.id! })
    if (res.data.code === 0 && res.data.data) {
      Object.assign(formData, {
        id: res.data.data.id,
        title: res.data.data.title,
        content: res.data.data.content,
        tags: res.data.data.tags ?? [],
        coverImage: res.data.data.coverImage,
      })
    }
  } catch {
    message.error({
      content: '获取帖子信息失败',
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
    title: '',
    content: '',
    tags: [],
    coverImage: '',
  })
  formRef.value?.clearValidate()
}

// 模态框确认
const handleModalOk = async () => {
  try {
    await formRef.value?.validate()
    modalLoading.value = true

    const res = await updatePost(formData)
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
    const res = await deletePost({ id })
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
#postManagePage {
  padding: 16px;
}

.search-bar {
  margin-bottom: 16px;
}
</style>
