<template>
  <div class="post-card">
    <a-card hoverable class="post-card-content">
      <div class="post-header">
        <a-space>
          <a-avatar :src="post.userAvatar">
            <template #icon><UserOutlined /></template>
          </a-avatar>
          <div>
            <div class="post-author">{{ post.userName || '匿名用户' }}</div>
            <div class="post-time">{{ formatTime(post.createTime) }}</div>
          </div>
        </a-space>
      </div>
      
      <div class="post-content">
        <h4 class="post-title">{{ post.title }}</h4>
        <p class="post-description">{{ post.content }}</p>
        <div v-if="post.tags && post.tags.length > 0" class="post-tags">
          <a-tag v-for="(tag, index) in parseTags(post.tags)" :key="index" color="blue">
            {{ tag }}
          </a-tag>
        </div>
      </div>
      
      <div class="post-actions">
        <a-space size="large">
          <a-button type="text" @click="$emit('like', post.id)">
            <template #icon><LikeOutlined /></template>
            点赞
          </a-button>
          <a-button type="text" @click="$emit('comment', post.id)">
            <template #icon><CommentOutlined /></template>
            评论
          </a-button>
          <a-button type="text" @click="$emit('share', post.id)">
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

interface Props {
  post: API.PostVO
}

defineProps<Props>()

defineEmits<{
  like: [postId: number]
  comment: [postId: number]
  share: [postId: number]
}>()

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
}

.post-card-content:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
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