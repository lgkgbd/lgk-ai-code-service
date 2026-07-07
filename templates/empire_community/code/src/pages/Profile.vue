<template>
  <div class="profile-page">
    <div class="profile-card">
      <div class="profile-avatar">
        <img src="https://picsum.photos/seed/me/80/80" alt="用户头像" />
      </div>
      <h2 class="profile-name">南山隐士</h2>
      <p class="profile-title">布衣 · 好议天下事</p>
      <p class="profile-bio">居南山之麓，读圣贤之书。偶有所得，录于帝国隐忍，以俟同好。</p>
      <div class="profile-stats">
        <div class="stat"><span class="stat-num">7</span><span class="stat-label">发帖</span></div>
        <div class="stat"><span class="stat-num">43</span><span class="stat-label">评论</span></div>
        <div class="stat"><span class="stat-num">126</span><span class="stat-label">获赞</span></div>
      </div>
    </div>

    <section class="my-topics">
      <h3 class="section-title">我的议题</h3>
      <div class="my-topic-list">
        <div class="my-topic-item" v-for="t in myTopics" :key="t.id" @click="$router.push(`/topic/${t.id}`)">
          <span class="my-topic-title">{{ t.title }}</span>
          <span class="my-topic-meta">{{ t.replies }} 回复 · {{ t.views }} 浏览</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { topics } from '@/data'

const myTopics = topics.filter(t => t.author === '镇远将军' || t.author === '南山隐士')
</script>

<style scoped>
.profile-page {
  max-width: 640px;
  margin: 0 auto;
  padding: 20px 0;
}

.profile-card {
  text-align: center;
  padding: 40px 20px;
  border-bottom: 1px solid rgba(255,255,255,0.04);
}

.profile-avatar img {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  border: 2px solid rgba(201,168,76,0.2);
  object-fit: cover;
  margin-bottom: 16px;
}

.profile-name {
  font-size: 22px;
  color: #d4d0c8;
  letter-spacing: 4px;
  margin-bottom: 4px;
}

.profile-title {
  font-size: 13px;
  color: #c9a84c;
  letter-spacing: 2px;
  margin-bottom: 12px;
}

.profile-bio {
  font-size: 13px;
  color: #665;
  max-width: 360px;
  margin: 0 auto 24px;
  line-height: 1.8;
}

.profile-stats {
  display: flex;
  justify-content: center;
  gap: 40px;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-num {
  font-size: 24px;
  color: #c9a84c;
  font-weight: 600;
}

.stat-label {
  font-size: 12px;
  color: #555;
  letter-spacing: 1px;
}

.section-title {
  font-size: 15px;
  color: #c9a84c;
  letter-spacing: 2px;
  font-weight: 500;
  margin: 28px 0 16px;
}

.my-topic-list {
  display: flex;
  flex-direction: column;
}

.my-topic-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  cursor: pointer;
  border-bottom: 1px solid rgba(255,255,255,0.02);
  transition: background 0.3s;
}

.my-topic-item:hover {
  background: rgba(201,168,76,0.03);
}

.my-topic-title {
  font-size: 14px;
  color: #bbb;
}

.my-topic-meta {
  font-size: 12px;
  color: #555;
  white-space: nowrap;
}

@media (max-width: 600px) {
  .profile-stats { gap: 24px; }
  .my-topic-item { flex-direction: column; align-items: flex-start; gap: 4px; }
}
</style>
