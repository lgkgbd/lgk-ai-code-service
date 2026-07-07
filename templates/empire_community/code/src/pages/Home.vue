<template>
  <div class="home-page">
    <section class="hero">
      <div class="hero-bg"></div>
      <h1 class="hero-title">帝国隐忍</h1>
      <p class="hero-sub">沉潜蓄势 · 静待风雷</p>
      <div class="hero-desc">一个以史为鉴、议事论道的深度话题社区</div>
    </section>

    <section class="categories">
      <button
        v-for="cat in categories"
        :key="cat.id"
        :class="['cat-btn', { active: currentCat === cat.id }]"
        @click="currentCat = cat.id"
      >
        <span class="cat-name">{{ cat.name }}</span>
        <span class="cat-desc">{{ cat.desc }}</span>
      </button>
      <button :class="['cat-btn', { active: currentCat === '' }]" @click="currentCat = ''">
        <span class="cat-name">全部话题</span>
        <span class="cat-desc">纵览全局</span>
      </button>
    </section>

    <section class="topic-list">
      <article
        v-for="topic in filteredTopics"
        :key="topic.id"
        class="topic-card"
        @click="$router.push(`/topic/${topic.id}`)"
      >
        <div class="card-left">
          <img :src="topic.avatar" :alt="topic.author" class="avatar" />
        </div>
        <div class="card-body">
          <h3 class="card-title">{{ topic.title }}</h3>
          <p class="card-meta">
            <span class="meta-author">{{ topic.author }}</span>
            <span class="meta-cat">{{ categories.find(c => c.id === topic.category)?.name }}</span>
            <span class="meta-time">{{ topic.time }}</span>
          </p>
        </div>
        <div class="card-stats">
          <span>{{ topic.replies }} 回复</span>
          <span>{{ topic.views }} 浏览</span>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { categories, topics } from '@/data'

const currentCat = ref('')

const filteredTopics = computed(() => {
  if (!currentCat.value) return topics
  return topics.filter(t => t.category === currentCat.value)
})
</script>

<style scoped>
.hero {
  position: relative;
  text-align: center;
  padding: 60px 20px 50px;
  border-bottom: 1px solid rgba(201, 168, 76, 0.08);
  overflow: hidden;
}

.hero-bg {
  position: absolute;
  inset: 0;
  background: radial-gradient(ellipse at center top, rgba(201,168,76,0.04) 0%, transparent 60%);
}

.hero-title {
  font-size: 42px;
  color: #c9a84c;
  letter-spacing: 12px;
  font-weight: 700;
  margin-bottom: 8px;
}

.hero-sub {
  font-size: 15px;
  color: #665;
  letter-spacing: 6px;
  margin-bottom: 16px;
}

.hero-desc {
  font-size: 13px;
  color: #555;
  letter-spacing: 2px;
}

.categories {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 24px 0;
  border-bottom: 1px solid rgba(255,255,255,0.04);
}

.cat-btn {
  flex: 1;
  min-width: 140px;
  padding: 12px 16px;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 4px;
  text-align: left;
  transition: all 0.3s;
}

.cat-btn:hover,
.cat-btn.active {
  background: rgba(201,168,76,0.06);
  border-color: rgba(201,168,76,0.2);
}

.cat-name {
  display: block;
  font-size: 14px;
  color: #c9a84c;
  letter-spacing: 2px;
  margin-bottom: 2px;
}

.cat-desc {
  font-size: 11px;
  color: #555;
}

.topic-list {
  display: flex;
  flex-direction: column;
  gap: 1px;
  background: rgba(255,255,255,0.02);
  margin-top: 20px;
}

.topic-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 16px;
  background: #0d0d0d;
  cursor: pointer;
  transition: background 0.3s;
}

.topic-card:hover {
  background: rgba(201,168,76,0.03);
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 1px solid rgba(201,168,76,0.15);
  object-fit: cover;
}

.card-body {
  flex: 1;
  min-width: 0;
}

.card-title {
  font-size: 15px;
  font-weight: 500;
  color: #d4d0c8;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-meta {
  font-size: 12px;
  color: #555;
  display: flex;
  gap: 12px;
}

.meta-author { color: #c9a84c; }

.card-stats {
  font-size: 12px;
  color: #555;
  display: flex;
  gap: 14px;
  white-space: nowrap;
}

@media (max-width: 600px) {
  .hero-title { font-size: 30px; letter-spacing: 8px; }
  .cat-btn { min-width: 100px; }
  .card-stats { display: none; }
}
</style>
