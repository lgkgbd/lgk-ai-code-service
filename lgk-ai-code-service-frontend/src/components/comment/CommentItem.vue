<template>
  <div class="comment-item-row">
    <a-avatar :src="comment.user?.userAvatar" :size="isReply ? 32 : 40">
      {{ comment.user?.userName?.charAt(0) || 'U' }}
    </a-avatar>
    <div class="comment-body">
      <div class="comment-meta">
        <span class="username">{{ comment.user?.userName || '匿名用户' }}</span>
        <span v-if="comment.replyToUserName" class="reply-to">
          回复 <span class="reply-to-name">@{{ comment.replyToUserName }}</span>
        </span>
      </div>
      <div class="comment-content">{{ comment.content }}</div>
      <div class="comment-footer">
        <span class="comment-time">{{ formatTime(comment.createTime) }}</span>
        <a-button type="text" size="small" :class="{ liked: comment.hasThumb }" @click="$emit('like')">
          👍 {{ comment.thumbNum || 0 }}
        </a-button>
        <a-button type="text" size="small" @click="showReplyBox = !showReplyBox">回复</a-button>
        <a-button v-if="canDelete" type="text" size="small" danger @click="$emit('delete')">删除</a-button>
      </div>

      <div v-if="showReplyBox" class="reply-input-section">
        <a-textarea
          v-model:value="replyContent"
          :placeholder="`回复 @${comment.user?.userName || '匿名用户'}`"
          :rows="1"
          auto-size
        />
        <a-button type="primary" size="small" :disabled="!replyContent.trim()" @click="submitReply">发布</a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

interface Props {
  comment: API.CommentVO
  canDelete: boolean
  isReply?: boolean
}

defineProps<Props>()
const emit = defineEmits<{
  like: []
  reply: [content: string]
  delete: []
}>()

const showReplyBox = ref(false)
const replyContent = ref('')

const submitReply = () => {
  if (!replyContent.value.trim()) return
  emit('reply', replyContent.value.trim())
  replyContent.value = ''
  showReplyBox.value = false
}

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
</script>

<style scoped>
.comment-item-row {
  display: flex;
  gap: 12px;
}
.comment-body {
  flex: 1;
  min-width: 0;
}
.comment-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}
.username {
  font-weight: 500;
  color: #333;
  font-size: 14px;
}
.reply-to {
  color: #999;
  font-size: 13px;
}
.reply-to-name {
  color: #1890ff;
}
.comment-content {
  font-size: 14px;
  color: #333;
  line-height: 1.6;
  word-wrap: break-word;
  white-space: pre-wrap;
}
.comment-footer {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
}
.comment-time {
  color: #999;
  font-size: 12px;
  margin-right: 8px;
}
.comment-footer .ant-btn {
  color: #666;
  font-size: 13px;
  padding: 0 6px;
}
.comment-footer .ant-btn.liked {
  color: #1890ff;
}
.reply-input-section {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  align-items: flex-start;
}
.reply-input-section .ant-input {
  flex: 1;
}
</style>
