<template>
  <div class="community-panel">
    <a-card class="header-card">
      <div class="header">
        <div class="header-title">
          <h3>交流社区</h3>
          <p>分享想法，交流经验</p>
        </div>
        <a-button
          type="primary"
          @click="showPostForm = true"
          class="publish-btn"
        >
          <template #icon><PlusOutlined /></template>
          发布帖子
        </a-button>
      </div>
      
      <a-divider />
      
      <div class="controls">
        <a-space direction="vertical" style="width: 100%">
          <a-input-search
            v-model:value="searchText"
            placeholder="搜索帖子..."
            allow-clear
            @search="handleSearch"
            class="search-input"
          >
            <template #enterButton>
              <SearchOutlined />
            </template>
          </a-input-search>
          <div class="sort-controls">
            <span>排序方式：</span>
            <a-select
              v-model:value="sortBy"
              style="width: 120px"
              @change="handleSortChange"
            >
              <a-select-option value="createTime">
                <ClockCircleOutlined /> 最新
              </a-select-option>
              <a-select-option value="updateTime">
                <FireOutlined /> 最热
              </a-select-option>
            </a-select>
          </div>
        </a-space>
      </div>
    </a-card>

    <div class="posts-container">
      <a-spin :spinning="loading">
        <div v-if="posts.length > 0">
          <PostCard
            v-for="post in posts"
            :key="post.id"
            :post="post"
            @like="handleLike"
            @comment="handleComment"
            @share="handleShare"
          />
          <div v-if="hasMore" class="load-more">
            <a-button @click="handleLoadMore" :loading="loading">
              加载更多
            </a-button>
          </div>
        </div>
        <a-empty v-else description="暂无帖子" :image="simpleImage" />
      </a-spin>
    </div>

    <PostForm
      :visible="showPostForm"
      @cancel="showPostForm = false"
      @success="handlePostSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { Empty } from 'ant-design-vue'
import { 
  PlusOutlined, 
  SearchOutlined, 
  FireOutlined, 
  ClockCircleOutlined,
  CodeOutlined,
  UserOutlined 
} from '@ant-design/icons-vue'
import PostCard from './PostCard.vue'
import PostForm from './PostForm.vue'
import { listPostVoByPage } from '@/api/postController'

const posts = ref<API.PostVO[]>([])
const loading = ref(false)
const showPostForm = ref(false)
const searchText = ref('')
const sortBy = ref<'createTime' | 'updateTime'>('createTime')
const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
})

const hasMore = computed(() => {
  return pagination.current * pagination.pageSize < pagination.total
})

const fetchPosts = async (params?: {
  current?: number
  pageSize?: number
  searchText?: string
  sortField?: string
  append?: boolean
}) => {
  try {
    loading.value = true
    const queryParams: API.PostQueryRequest = {
      current: params?.current || pagination.current,
      pageSize: params?.pageSize || pagination.pageSize,
      searchText: params?.searchText || searchText.value,
      sortField: params?.sortField || sortBy.value,
      sortOrder: 'desc',
    }

    const response = await listPostVoByPage(queryParams)
    if (response.code === 0 && response.data) {
      const newPosts = response.data.records || []
      
      if (params?.append) {
        posts.value = [...posts.value, ...newPosts]
      } else {
        posts.value = newPosts
      }
      
      pagination.current = response.data.current || 1
      pagination.pageSize = response.data.size || 10
      pagination.total = response.data.total || 0
    }
  } catch (error) {
    console.error('获取帖子列表失败:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = (value: string) => {
  searchText.value = value
  pagination.current = 1
  fetchPosts({ current: 1, searchText: value })
}

const handleSortChange = (value: 'createTime' | 'updateTime') => {
  sortBy.value = value
  pagination.current = 1
  fetchPosts({ current: 1, sortField: value })
}

const handlePostSuccess = () => {
  showPostForm.value = false
  pagination.current = 1
  fetchPosts({ current: 1 })
}

const handleLoadMore = () => {
  if (hasMore.value) {
    fetchPosts({ 
      current: pagination.current + 1,
      append: true
    })
  }
}

const handleLike = (postId: number) => {
  console.log('点赞:', postId)
}

const handleComment = (postId: number) => {
  console.log('评论:', postId)
}

const handleShare = (postId: number) => {
  console.log('分享:', postId)
}

onMounted(() => {
  fetchPosts()
})
</script>

<style scoped>
.community-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.header-card {
  margin-bottom: 16px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.header-title h3 {
  margin: 0;
  color: #1890ff;
  font-size: 18px;
  font-weight: 600;
}

.header-title p {
  margin: 4px 0 0 0;
  color: #666;
  font-size: 14px;
}

.publish-btn {
  border-radius: 6px;
  box-shadow: 0 2px 4px rgba(24, 144, 255, 0.2);
}

.controls .search-input :deep(.ant-input-group-addon) {
  background: #1890ff;
  border-color: #1890ff;
}

.controls .search-input :deep(.ant-btn) {
  color: white;
  border: none;
}

.sort-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #666;
  font-size: 14px;
}

.posts-container {
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
}

.posts-container::-webkit-scrollbar {
  width: 6px;
}

.posts-container::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.posts-container::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.posts-container::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

.load-more {
  text-align: center;
  margin: 16px 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .header {
    flex-direction: column;
    gap: 16px;
  }

  .publish-btn {
    width: 100%;
  }

  .controls .search-input {
    margin-bottom: 8px;
  }
}
</style>