<template>
  <div class="post-card">
    <a-card hoverable class="post-card-content" @click="handleCardClick">
      <div class="post-header">
        <a-space>
          <a-avatar :src="post.user?.userAvatar">
            <template #icon><UserOutlined /></template>
          </a-avatar>
          <div>
            <div class="post-author">{{ post.user?.userName || '匿名用户' }}</div>
            <div class="post-time">{{ formatTime(post.createTime) }}</div>
          </div>
        </a-space>
      </div>
      
      <div class="post-content">
        <h4 class="post-title" @click.stop="handleTitleClick">{{ post.title }}</h4>
        <p class="post-description">{{ post.content }}</p>
        <div v-if="post.tags && post.tags.length > 0" class="post-tags">
          <a-tag v-for="(tag, index) in parseTags(post.tags)" :key="index" color="blue">
            {{ tag }}
          </a-tag>
        </div>
      </div>
      
      <div class="post-actions">
        <a-space size="large">
          <a-button type="text" @click.stop="handleLike">
            <template #icon><LikeOutlined /></template>
            点赞
          </a-button>
          <a-button type="text" @click.stop="handleComment">
            <template #icon><CommentOutlined /></template>
            评论
          </a-button>
          <a-button type="text" @click.stop="handleShare">
            <template #icon><ShareAltOutlined /></template>
            分享
          </a-button>
        </a-space>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { UserOutlined, LikeOutlined, CommentOutlined, ShareAltOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'

interface Props {
  post: API.PostVO
}

const props = defineProps<Props>()
const router = useRouter()

const emit = defineEmits<{
  like: [postId: string]
  comment: [postId: string]
  share: [postId: string]
}>()

// 点击卡片跳转到详情页
const handleCardClick = () => {
  if (props.post.id) {
    router.push(`/post/${props.post.id}`)
  }
}

// 点击标题跳转到详情页
const handleTitleClick = () => {
  if (props.post.id) {
    router.push(`/post/${props.post.id}`)
  }
}

// 点赞处理
const handleLike = () => {
  if (props.post.id) {
    emit('like', props.post.id)
  }
}

// 评论处理
const handleComment = () => {
  if (props.post.id) {
    emit('comment', props.post.id)
  }
}

// 分享处理
const handleShare = () => {
  if (props.post.id) {
    emit('share', props.post.id)
  }
}

const formatTime = (time: string) => {
  return new Date(time).toLocaleString()
}

const parseTags = (tags: string[] | string | undefined) => {
  if (!tags) return []
  if (Array.isArray(tags)) {
    return tags
  }
  if (typeof tags === 'string') {
    try {
      return JSON.parse(tags)
    } catch {
      return [tags]
    }
  }
  return []
}
</script>

<style scoped>
.post-card {
  margin-bottom: 16px;
}

.post-card-content {
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: all 0.3s ease;
  cursor: pointer;
}

.post-card-content:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  transform: translateY(-2px);
}

.post-header {
  margin-bottom: 16px;
}

.post-author {
  font-weight: 500;
  color: #262626;
}

.post-time {
  font-size: 12px;
  color: #8c8c8c;
}

.post-content {
  margin-bottom: 16px;
}

.post-title {
  margin-bottom: 8px;
  color: #1890ff;
  cursor: pointer;
  font-size: 16px;
  font-weight: 600;
}

.post-title:hover {
  color: #40a9ff;
}

.post-description {
  margin-bottom: 12px;
  color: #666;
  line-height: 1.6;
}

.post-tags {
  margin-bottom: 8px;
}

.post-actions {
  border-top: 1px solid #f0f0f0;
  padding-top: 12px;
}

.post-actions .ant-btn {
  color: #666;
}

.post-actions .ant-btn:hover {
  color: #1890ff;
}
</style>