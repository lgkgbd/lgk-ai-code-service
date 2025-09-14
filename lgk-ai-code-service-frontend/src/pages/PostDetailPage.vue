<template>
  <div class="post-detail-page-layout" v-if="!isLoading && post">
    <!-- Floating Action Bar -->
    <div class="floating-actions">
      <a-button type="primary" shape="circle" class="action-btn" :class="{ active: isLiked }" @click="handleLike">
        👍
        <span class="count">{{ post.thumbNum || 0 }}</span>
      </a-button>
      <a-button shape="circle" class="action-btn" @click="scrollToComments">
        💬
      </a-button>
      <a-button shape="circle" class="action-btn" :class="{ active: isFavorited }" @click="handleFavorite">
        ⭐
      </a-button>
      <a-divider />
      <a-button shape="circle" class="action-btn" @click="handleShare">
        📤
      </a-button>
    </div>

    <div class="main-wrapper">
      <div class="post-content-main">
        <div class="post-card">
          <!-- Post Title -->
          <h1 v-if="post.title" class="post-title">{{ post.title }}</h1>

          <!-- Post Meta -->
          <div class="post-meta-info">
            <span class="username">{{ post.user?.userName || '匿名用户' }}</span>
            <span class="post-time">{{ formatTime(post.createTime) }}</span>
            <span class="view-count">阅读 {{ post.viewNum || 0 }}</span>
            <a-button v-if="isAuthor" type="link" @click="handleEdit">编辑</a-button>
          </div>

          <!-- Post Content -->
          <div class="post-content" v-html="formattedContent"></div>
        </div>

        <!-- Comments Section -->
        <div id="comments-section" class="comments-section-wrapper">
          <div class="comments-header">
            <h3>评论</h3>
            <div class="comment-sort">
              <a-button type="text" class="active">最热</a-button>
              <a-button type="text">最新</a-button>
            </div>
          </div>
          <div class="comment-input-section">
            <a-avatar :src="post.user?.userAvatar" :size="40" />
            <a-textarea
              v-model:value="commentContent"
              placeholder="写下你的评论..."
              :rows="1"
              auto-size
              class="comment-textarea"
            />
            <a-button type="primary" @click="handleComment" :disabled="!commentContent.trim()">
              发布
            </a-button>
          </div>
          <div class="comments-list">
            <div class="no-comments">
              <p>暂无评论，快来抢沙发吧！</p>
            </div>
          </div>
        </div>
      </div>

      <div class="post-sidebar">
        <!-- Author Card -->
        <div class="sidebar-card author-card">
          <div class="author-info">
            <a-avatar :src="post.user?.userAvatar" :size="48">
              {{ post.user?.userName?.charAt(0) || 'U' }}
            </a-avatar>
            <div class="author-details">
              <div class="author-name">{{ post.user?.userName || '匿名用户' }}</div>
              <div class="author-bio">{{ post.user?.userProfile || '这个人很懒，什么都没留下...' }}</div>
            </div>
          </div>
          <a-button type="primary" block>+ 关注</a-button>
        </div>


      </div>
    </div>
  </div>

  <div v-else-if="isLoading" class="loading-container">
    <a-spin size="large" />
    <p>加载中...</p>
  </div>
  
  <div v-else class="error-container">
    <a-result
      status="404"
      title="帖子不存在"
      sub-title="抱歉，您访问的帖子不存在或已被删除"
    >
      <template #extra>
        <a-button type="primary" @click="router.push('/community')">返回社区</a-button>
      </template>
    </a-result>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPostVoById } from '@/api/postController'
import { showError, showSuccess, showWarning } from '@/utils/message'
import { marked } from 'marked'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { storeToRefs } from 'pinia'

const loginUserStore = useLoginUserStore()
const route = useRoute()
const router = useRouter()

// 数据状态
const post = ref<API.PostVO | null>(null)
const isLoading = ref(true)
const isLiked = ref(false)
const isFavorited = ref(false)
const commentContent = ref('')

// 获取帖子详情
const fetchPostDetail = async () => {
  const postId = route.params.id as string
  if (!postId) {
    showError('帖子ID不存在')
    isLoading.value = false
    return
  }

  try {
    isLoading.value = true
    const { data: res } = await getPostVoById({ id: postId })
    
    if (res?.code === 0 && res.data) {
      post.value = res.data
    } else {
      post.value = null
      showError(res?.message || '获取帖子详情失败')
    }
  } catch (error) {
    post.value = null
    console.error('获取帖子详情失败:', error)
    showError('网络错误，请稍后重试')
  } finally {
    isLoading.value = false
  }
}

// 格式化时间
const formatTime = (time: string | undefined) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  if (diff < 2592000000) return `${Math.floor(diff / 86400000)}天前`
  
  return date.toLocaleDateString()
}

// 解析标签
const parseTags = (tags: string[] | string | undefined) => {
  if (!tags) return []
  if (Array.isArray(tags)) return tags
  if (typeof tags === 'string') {
    try {
      return JSON.parse(tags)
    } catch {
      return [tags]
    }
  }
  return []
}

// 将Markdown内容转换为HTML
const formattedContent = computed(() => {
  if (!post.value?.content) {
    return ''
  }
  return marked(post.value.content)
})

// 判断当前用户是否为作者
const isAuthor = computed(() => {
  if (!post.value || !loginUserStore.loginUser) {
    return false
  }
  return post.value.userId === loginUserStore.loginUser.id
})

// 编辑处理
const handleEdit = () => {
  if (post.value) {
    router.push({
      path: '/write-post',
      query: { id: post.value.id }
    })
  }
}


// 点赞处理
const handleLike = () => {
  isLiked.value = !isLiked.value
  if (post.value) {
    post.value.thumbNum = (post.value.thumbNum || 0) + (isLiked.value ? 1 : -1)
  }
  showSuccess(isLiked.value ? '点赞成功' : '取消点赞')
}

// 收藏处理
const handleFavorite = () => {
  isFavorited.value = !isFavorited.value
  if (post.value) {
    post.value.favourNum = (post.value.favourNum || 0) + (isFavorited.value ? 1 : -1)
  }
  showSuccess(isFavorited.value ? '收藏成功' : '取消收藏')
}

// 分享处理
const handleShare = () => {
  if (navigator.share) {
    navigator.share({
      title: post.value?.title || '分享帖子',
      text: post.value?.content || '',
      url: window.location.href
    })
  } else {
    // 复制链接到剪贴板
    navigator.clipboard.writeText(window.location.href).then(() => {
      showSuccess('链接已复制到剪贴板')
    }).catch(() => {
      showError('分享失败')
    })
  }
}

// 滚动到评论区
const scrollToComments = () => {
  const commentsElement = document.getElementById('comments-section');
  if (commentsElement) {
    commentsElement.scrollIntoView({ behavior: 'smooth' });
  }
}

// 评论处理
const handleComment = () => {
  if (!commentContent.value.trim()) {
    showWarning('请输入评论内容')
    return
  }
  
  // TODO: 实现评论功能
  showSuccess('评论功能开发中...')
  commentContent.value = ''
}

onMounted(() => {
  fetchPostDetail()
})
</script>

<style scoped>
.post-detail-page-layout {
  display: flex;
  justify-content: center;
  gap: 16px;
  padding: 20px;
  background-color: transparent;
}

.floating-actions {
  position: fixed;
  left: calc((100vw - 1200px) / 2 - 80px); /* Position left of the main content */
  top: 150px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: transparent;
  padding: 12px;
  border-radius: 8px;
  box-shadow: none;
}

.floating-actions .action-btn {
  position: relative;
  width: 48px;
  height: 48px;
  font-size: 20px;
}
.floating-actions .action-btn .count {
  position: absolute;
  top: -4px;
  right: -4px;
  background: #ff4d4f;
  color: white;
  font-size: 12px;
  padding: 0 5px;
  border-radius: 10px;
  line-height: 18px;
}
.floating-actions .action-btn.active {
  color: #1890ff;
}

.main-wrapper {
  display: flex;
  gap: 16px;
  width: 100%;
  max-width: 1100px;
}

.post-content-main {
  flex: 1;
  min-width: 0;
}

.post-card {
  background: white;
  padding: 24px;
  border-radius: 8px;
  margin-bottom: 16px;
}

.post-title {
  font-size: 28px;
  font-weight: 700;
  color: #333;
  margin-bottom: 16px;
}

.post-meta-info {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #999;
  font-size: 14px;
  margin-bottom: 24px;
  padding-bottom: 24px;
  border-bottom: 1px solid #f0f0f0;
}
.post-meta-info .username {
  color: #666;
  font-weight: 500;
}

.post-content {
  font-size: 16px;
  line-height: 1.8;
  color: #333;
  word-wrap: break-word;
}
/* Basic markdown styles */
.post-content :deep(h1),
.post-content :deep(h2),
.post-content :deep(h3) {
  margin-top: 24px;
  margin-bottom: 16px;
  font-weight: 600;
}
.post-content :deep(p) {
  margin-bottom: 16px;
}
.post-content :deep(ul),
.post-content :deep(ol) {
  padding-left: 24px;
}
.post-content :deep(blockquote) {
  margin: 16px 0;
  padding: 10px 20px;
  background-color: #fafafa;
  border-left: 4px solid #e8e8e8;
  color: #666;
}
.post-content :deep(pre) {
  background-color: #f5f5f5;
  padding: 16px;
  border-radius: 4px;
  overflow-x: auto;
}
.post-content :deep(code) {
  font-family: 'Courier New', Courier, monospace;
  background-color: #f5f5f5;
  padding: 2px 4px;
  border-radius: 4px;
}
.post-content :deep(pre) > code {
  background: none;
  padding: 0;
}


.comments-section-wrapper {
  background: white;
  padding: 24px;
  border-radius: 8px;
}
.comments-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.comments-header h3 {
  font-size: 18px;
  font-weight: 600;
}
.comment-sort .ant-btn {
  color: #666;
}
.comment-sort .ant-btn.active {
  color: #1890ff;
}

.comment-input-section {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 24px;
}
.comment-textarea {
  flex: 1;
}

.no-comments {
  text-align: center;
  padding: 40px 20px;
  color: #999;
}

.post-sidebar {
  width: 300px;
  flex-shrink: 0;
}

.sidebar-card {
  background: white;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 16px;
}
.sidebar-card h3 {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.author-card .author-info {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.author-card .author-details {
  min-width: 0;
}
.author-card .author-name {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}
.author-card .author-bio {
  font-size: 13px;
  color: #999;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.loading-container,
.error-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  background: white;
  border-radius: 8px;
  width: 100%;
  max-width: 800px;
  margin: 20px auto;
}

@media (max-width: 1200px) {
  .floating-actions {
    display: none; /* Hide floating actions on smaller screens */
  }
}

@media (max-width: 992px) {
  .post-sidebar {
    display: none; /* Hide sidebar on smaller screens */
  }
  .main-wrapper {
    max-width: 800px;
    margin: 0 auto;
  }
}

@media (max-width: 768px) {
  .post-detail-page-layout {
    padding: 0;
  }
  .main-wrapper {
    flex-direction: column;
    gap: 0;
  }
  .post-card, .comments-section-wrapper {
    margin-bottom: 12px;
    border-radius: 0;
  }
}
</style>
