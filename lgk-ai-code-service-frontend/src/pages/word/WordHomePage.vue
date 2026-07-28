<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import WordCaptureBox from '@/components/word/WordCaptureBox.vue'
import WordCard from '@/components/word/WordCard.vue'
import { statistics, listMyWordsByPage } from '@/api/wordController'
import { count as countDue } from '@/api/wordReviewController'

const router = useRouter()

const stats = ref<API.WordStatisticsVO>({})
const dueCount = ref<number>(0)
const recent = ref<API.WordCardVO[]>([])
const loadingRecent = ref(false)

async function loadStats() {
  const [s, c] = await Promise.all([statistics(), countDue()])
  if (s.data.code === 0 && s.data.data) stats.value = s.data.data
  if (c.data.code === 0) dueCount.value = Number(c.data.data ?? 0)
}

async function loadRecent() {
  loadingRecent.value = true
  try {
    const res = await listMyWordsByPage({ pageNum: 1, pageSize: 10 })
    if (res.data.code === 0 && res.data.data?.records) {
      recent.value = res.data.data.records
    }
  } finally {
    loadingRecent.value = false
  }
}

// 乐观 UI：录入后立刻插到列表顶部，不整页 loading
function onCaptured(cards: API.WordCardVO[]) {
  const ids = new Set(recent.value.map((r) => String(r.userWordId)))
  const fresh = cards.filter((c) => !ids.has(String(c.userWordId)))
  // 已存在但这次又遇见的，更新其遇见次数并置顶
  cards
    .filter((c) => ids.has(String(c.userWordId)))
    .forEach((c) => {
      const idx = recent.value.findIndex((r) => String(r.userWordId) === String(c.userWordId))
      if (idx >= 0) recent.value.splice(idx, 1)
    })
  recent.value = [...cards, ...recent.value.filter((r) => !cards.some((c) => String(c.userWordId) === String(r.userWordId)))]
  void fresh
  // 统计与角标后台刷新
  loadStats()
}

function goReview() {
  router.push('/word/review')
}
function goList() {
  router.push('/word/list')
}
function goDetail(card: API.WordCardVO) {
  router.push(`/word/detail/${card.userWordId}`)
}

onMounted(() => {
  loadStats()
  loadRecent()
})
</script>

<template>
  <div class="word-home">
    <WordCaptureBox @captured="onCaptured" />

    <div class="grid-2">
      <div class="review-cta" @click="goReview">
        <div>
          <div class="num">{{ dueCount }}</div>
          <div class="lbl">个词等你复习</div>
          <div class="sub">统一队列，不分词书</div>
        </div>
        <a-button class="cta-btn" size="large" @click.stop="goReview">开始复习</a-button>
      </div>

      <div class="stats">
        <div class="stat">
          <div class="n">{{ stats.totalCount ?? 0 }}</div>
          <div class="t">总词数</div>
        </div>
        <div class="stat">
          <div class="n">+{{ stats.todayNewCount ?? 0 }}</div>
          <div class="t">今日新增</div>
        </div>
        <div class="stat">
          <div class="n">{{ stats.masteredCount ?? 0 }}</div>
          <div class="t">已掌握</div>
        </div>
        <div class="stat">
          <div class="n">{{ stats.streakDays ?? 0 }}</div>
          <div class="t">连续天数</div>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-title">
        <span>最近录入</span>
        <span class="more" @click="goList">查看全部词库 →</span>
      </div>
      <a-spin :spinning="loadingRecent">
        <template v-if="recent.length">
          <WordCard v-for="w in recent" :key="String(w.userWordId)" :card="w" @click="goDetail" />
        </template>
        <a-empty v-else description="还没有录入单词，粘一个试试" />
      </a-spin>
    </div>
  </div>
</template>

<style scoped>
.grid-2 {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}
.review-cta {
  background: linear-gradient(135deg, #1890ff 0%, #096dd9 100%);
  color: #fff;
  border-radius: 8px;
  padding: 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 4px 16px rgba(24, 144, 255, 0.28);
  cursor: pointer;
}
.review-cta .num {
  font-size: 42px;
  font-weight: 700;
  line-height: 1;
}
.review-cta .lbl {
  font-size: 14px;
  opacity: 0.9;
  margin-top: 8px;
}
.review-cta .sub {
  font-size: 12px;
  opacity: 0.7;
  margin-top: 4px;
}
.cta-btn {
  color: #1890ff;
  font-weight: 500;
}
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.stat {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  padding: 16px;
  text-align: center;
}
.stat .n {
  font-size: 24px;
  font-weight: 700;
  color: #262626;
}
.stat .t {
  font-size: 12px;
  color: #8c8c8c;
  margin-top: 6px;
}
.card {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  padding: 20px;
  margin-bottom: 16px;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #262626;
}
.card-title .more {
  font-size: 13px;
  color: #1890ff;
  font-weight: 400;
  cursor: pointer;
}
@media (max-width: 900px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
  .stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
