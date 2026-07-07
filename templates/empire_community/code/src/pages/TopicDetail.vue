<template>
  <div class="detail-page" v-if="topic">
    <article class="topic-detail">
      <div class="detail-header">
        <router-link to="/" class="back-link">‹ 返回</router-link>
        <h1 class="detail-title">{{ topic.title }}</h1>
        <div class="detail-meta">
          <img :src="topic.avatar" :alt="topic.author" class="avatar" />
          <span class="author">{{ topic.author }}</span>
          <span class="time">{{ topic.time }}</span>
          <span class="views">{{ topic.views }} 浏览</span>
        </div>
      </div>
      <div class="detail-content">{{ topic.content }}</div>
      <div class="detail-actions">
        <button :class="['action-btn', { liked: topic.liked }]" @click="toggleLike">
          {{ topic.liked ? '已·赞' : '赞·同' }}
        </button>
        <button class="action-btn share-btn" @click="shareTopic">分·享</button>
      </div>
    </article>

    <section class="comments-section">
      <h2 class="comments-title">评·论（{{ topic.replies }}）</h2>
      <div class="comments-list">
        <div v-if="!topicComments || topicComments.length === 0" class="no-comments">尚未有评论，请君先发高论。</div>
        <div v-for="comment in topicComments" :key="comment.id" class="comment-item">
          <img :src="comment.avatar" alt="" class="avatar" />
          <div class="comment-body">
            <div class="comment-header">
              <span class="comment-author">{{ comment.author }}</span>
              <span class="comment-time">{{ comment.time }}</span>
            </div>
            <p class="comment-text">{{ comment.content }}</p>
            <div class="comment-actions">
              <button class="mini-btn">{{ comment.likes }} 赞</button>
              <button class="mini-btn">回复</button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="reply-section">
      <textarea v-model="replyText" class="reply-input" placeholder="发表评论…" rows="3"></textarea>
      <button class="reply-btn" @click="submitReply">提交评议</button>
    </section>
  </div>
  <div v-else class="not-found">话题未找到</div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { topics, comments } from '@/data'

const route = useRoute()
const replyText = ref('')

const topic = computed(() => topics.find(t => t.id === Number(route.params.id)))
const topicComments = computed(() => comments[route.params.id] || [])

function toggleLike() {
  topic.value.liked = !topic.value.liked
}

function shareTopic() {
  const text = `${topic.value.title} —— 帝国隐忍话题社区`
  if (navigator.share) {
    navigator.share({ title: text, text, url: window.location.href })
  } else {
    navigator.clipboard?.writeText(window.location.href)
    alert('链接已复制')
  }
}

function submitReply() {
  if (!replyText.value.trim()) return
  alert('评论已提交，待审核后展示。')
  replyText.value = ''
}
</script>

<style scoped>
.back-link {
  display: inline-block;
  font-size: 13px;
  color: #665;
  margin-bottom: 16px;
  letter-spacing: 1px;
}

.back-link:hover { color: #c9a84c; }

.detail-header {
  padding-bottom: 20px;
  border-bottom: 1px solid rgba(255,255,255,0.04);
}

.detail-title {
  font-size: 22px;
  color: #d4d0c8;
  font-weight: 600;
  line-height: 1.5;
  margin-bottom: 12px;
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #555;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid rgba(201,168,76,0.12);
  object-fit: cover;
}

.author { color: #c9a84c; }

.detail-content {
  padding: 28px 0;
  font-size: 15px;
  line-height: 2;
  color: #bbb;
  white-space: pre-wrap;
}

.detail-actions {
  display: flex;
  gap: 16px;
  padding: 16px 0 24px;
  border-bottom: 1px solid rgba(255,255,255,0.04);
}

.action-btn {
  padding: 6px 22px;
  font-size: 13px;
  color: #888;
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.08);
  letter-spacing: 2px;
  transition: all 0.3s;
}

.action-btn:hover { border-color: rgba(201,168,76,0.3); color: #c9a84c; }
.action-btn.liked { background: rgba(201,168,76,0.1); border-color: #c9a84c; color: #c9a84c; }

.comments-section { padding: 28px 0; }

.comments-title {
  font-size: 15px;
  color: #c9a84c;
  letter-spacing: 2px;
  margin-bottom: 20px;
  font-weight: 500;
}

.no-comments {
  font-size: 13px;
  color: #555;
  text-align: center;
  padding: 30px 0;
}

.comment-item {
  display: flex;
  gap: 14px;
  padding: 16px 0;
  border-bottom: 1px solid rgba(255,255,255,0.03);
}

.comment-item .avatar { width: 36px; height: 36px; flex-shrink: 0; }

.comment-body { flex: 1; }

.comment-header {
  display: flex;
  gap: 10px;
  font-size: 13px;
  margin-bottom: 6px;
}

.comment-author { color: #c9a84c; }
.comment-time { color: #555; }

.comment-text {
  font-size: 14px;
  color: #aaa;
  line-height: 1.8;
}

.comment-actions {
  display: flex;
  gap: 14px;
  margin-top: 8px;
}

.mini-btn {
  font-size: 12px;
  color: #555;
  background: none;
}

.mini-btn:hover { color: #c9a84c; }

.reply-section {
  padding: 20px 0 40px;
}

.reply-input {
  width: 100%;
  padding: 12px 16px;
  font-size: 14px;
  color: #d4d0c8;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 4px;
  resize: vertical;
  margin-bottom: 12px;
}

.reply-input:focus {
  border-color: rgba(201,168,76,0.3);
}

.reply-btn {
  padding: 8px 28px;
  font-size: 13px;
  color: #0a0a0a;
  background: #c9a84c;
  letter-spacing: 2px;
  border-radius: 2px;
}

.reply-btn:hover { background: #b8942e; }

.not-found {
  text-align: center;
  padding: 80px 0;
  color: #555;
  font-size: 16px;
}

@media (max-width: 600px) {
  .detail-title { font-size: 18px; }
  .detail-meta { flex-wrap: wrap; gap: 8px; }
}
</style>
