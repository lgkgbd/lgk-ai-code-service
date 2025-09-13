<template>
  <div class="post-detail-page">
    <div v-if="isLoading" class="loading-container">
      <a-spin size="large" />
      <p>加载中...</p>
    </div>
    
    <div v-else-if="post" class="post-detail-container">
      <!-- 返回按钮 -->
      <div class="back-button">
        <a-button type="text" @click="goBack">
          <template #icon>←</template>
          返回
        </a-button>
      </div>

      <!-- 帖子头部信息 -->
      <div class="post-header">
        <div class="user-info">
          <a-avatar :src="post.user?.userAvatar" :size="48">
            {{ post.user?.userName?.charAt(0) || 'U' }}
          </a-avatar>
          <div class="user-details">
            <div class="username">{{ post.user?.userName || '匿名用户' }}</div>
            <div class="post-meta">
              <span class="post-time">{{ formatTime(post.createTime) }}</span>
              <span class="separator">·</span>
              <span class="view-count">阅读 {{ post.viewNum || 0 }}</span>
            </div>
          </div>
        </div>
        
        <div class="header-actions">
          <a-button type="text">关注</a-button>
          <a-dropdown>
            <a-button type="text">
              <template #icon>⋯</template>
            </a-button>
            <template #overlay>
              <a-menu>
                <a-menu-item key="report">举报</a-menu-item>
                <a-menu-item key="block">屏蔽</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </div>

      <!-- 帖子标题 -->
      <h1 v-if="post.title" class="post-title">{{ post.title }}</h1>

      <!-- 帖子标签 -->
      <div v-if="post.tags && post.tags.length > 0" class="post-tags">
        <a-tag v-for="tag in parseTags(post.tags)" :key="tag" color="blue" class="post-tag">
          {{ tag }}
        </a-tag>
      </div>

      <!-- 帖子内容 -->
      <div class="post-content">
        <div class="content-text" v-html="formatContent(post.content)"></div>
      </div>

      <!-- 帖子操作栏 -->
      <div class="post-actions">
        <div class="action-buttons">
          <a-button 
            type="text" 
            :class="{ active: isLiked }"
            @click="handleLike"
            class="action-btn like-btn"
          >
            <template #icon>👍</template>
            <span>{{ post.thumbNum || 0 }}</span>
          </a-button>
          
          <a-button type="text" class="action-btn">
            <template #icon>💬</template>
            <span>评论</span>
          </a-button>
          
          <a-button 
            type="text" 
            :class="{ active: isFavorited }"
            @click="handleFavorite"
            class="action-btn favorite-btn"
          >
            <template #icon>⭐</template>
            <span>{{ post.favourNum || 0 }}</span>
          </a-button>
        </div>
        
        <div class="share-actions">
          <a-button type="text" @click="handleShare" class="action-btn">
            <template #icon>📤</template>
            <span>分享</span>
          </a-button>
        </div>
      </div>

      <!-- 评论区域 -->
      <div class="comments-section">
        <div class="comments-header">
          <h3>评论 (0)</h3>
          <div class="comment-sort">
            <a-select v-model:value="commentSort" size="small" style="width: 120px">
              <a-select-option value="time">按时间</a-select-option>
              <a-select-option value="hot">按热度</a-select-option>
            </a-select>
          </div>
        </div>
        
        <!-- 评论输入框 -->
        <div class="comment-input-section">
          <a-textarea
            v-model:value="commentContent"
            placeholder="写下你的评论..."
            :rows="3"
            class="comment-textarea"
          />
          <div class="comment-actions">
            <a-button type="primary" @click="handleComment" :disabled="!commentContent.trim()">
              发表评论
            </a-button>
          </div>
        </div>
        
        <!-- 评论列表 -->
        <div class="comments-list">
          <div class="no-comments">
            <p>暂无评论，快来抢沙发吧！</p>
          </div>
        </div>
      </div>
    </div>
    
    <div v-else class="error-container">
      <a-result
        status="404"
        title="帖子不存在"
        sub-title="抱歉，您访问的帖子不存在或已被删除"
      >
        <template #extra>
          <a-button type="primary" @click="goBack">返回</a-button>
        </template>
      </a-result>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPostVoById } from '@/api/postController'
import { showError, showSuccess, showWarning } from '@/utils/message'

const route = useRoute()
const router = useRouter()

// 数据状态
const post = ref<API.PostVO | null>(null)
const isLoading = ref(true)
const isLiked = ref(false)
const isFavorited = ref(false)
const commentContent = ref('')
const commentSort = ref('time')

// 获取帖子详情
const fetchPostDetail = async () => {
  const postId = route.params.id as string
  if (!postId) {
    showError('帖子ID不存在')
    return
  }

  try {
    isLoading.value = true
    const { data: res } = await getPostVoById({ id: postId })
    
    if (res?.code === 0 && res.data) {
      post.value = res.data
    } else {
      showError(res?.message || '获取帖子详情失败')
    }
  } catch (error) {
    console.error('获取帖子详情失败:', error)
    showError('网络错误，请稍后重试')
  } finally {
    isLoading.value = false
  }
}

// 返回上一页
const goBack = () => {
  router.back()
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

// 格式化内容（简单的换行处理）
const formatContent = (content: string | undefined) => {
  if (!content) return ''
  return content.replace(/\n/g, '<br>')
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
.post-detail-page {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
  background: #f5f5f5;
  min-height: calc(100vh - 120px);
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
}

.loading-container p {
  margin-top: 16px;
  color: #666;
}

.post-detail-container {
  background: white;
  border-radius: 8px;
  overflow: hidden;
}

.back-button {
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;
}

.back-button .ant-btn {
  color: #666;
  font-size: 14px;
}

.back-button .ant-btn:hover {
  color: #1890ff;
}

.post-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 20px;
  border-bottom: 1px solid #f0f0f0;
}

.user-info {
  display: flex;
  align-items: center;
}

.user-details {
  margin-left: 12px;
}

.username {
  font-weight: 600;
  color: #333;
  font-size: 16px;
  margin-bottom: 4px;
}

.post-meta {
  font-size: 13px;
  color: #999;
}

.separator {
  margin: 0 8px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.post-title {
  font-size: 28px;
  font-weight: 700;
  color: #333;
  line-height: 1.4;
  margin: 0;
  padding: 20px 20px 16px;
}

.post-tags {
  padding: 0 20px 16px;
}

.post-tag {
  margin-right: 8px;
  margin-bottom: 4px;
}

.post-content {
  padding: 0 20px 24px;
}

.content-text {
  font-size: 16px;
  line-height: 1.8;
  color: #333;
  word-wrap: break-word;
}

.post-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-top: 1px solid #f0f0f0;
  border-bottom: 1px solid #f0f0f0;
}

.action-buttons {
  display: flex;
  gap: 24px;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #666;
  border: none;
  padding: 8px 12px;
  border-radius: 6px;
  transition: all 0.2s;
}

.action-btn:hover {
  color: #1890ff;
  background: #f0f8ff;
}

.action-btn.active {
  color: #1890ff;
  background: #e6f7ff;
}

.like-btn.active {
  color: #ff4d4f;
}

.favorite-btn.active {
  color: #faad14;
}

.comments-section {
  padding: 24px 20px;
}

.comments-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.comments-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.comment-input-section {
  margin-bottom: 24px;
}

.comment-textarea {
  border-radius: 8px;
  margin-bottom: 12px;
}

.comment-actions {
  display: flex;
  justify-content: flex-end;
}

.no-comments {
  text-align: center;
  padding: 40px 20px;
  color: #999;
}

/* 响应式 */
@media (max-width: 768px) {
  .post-detail-page {
    padding: 12px;
  }
  
  .post-title {
    font-size: 24px;
    padding: 16px 16px 12px;
  }
  
  .post-header,
  .post-content,
  .post-actions,
  .comments-section {
    padding-left: 16px;
    padding-right: 16px;
  }
  
  .action-buttons {
    gap: 16px;
  }
  
  .action-btn {
    padding: 6px 8px;
    font-size: 13px;
  }
}
</style>