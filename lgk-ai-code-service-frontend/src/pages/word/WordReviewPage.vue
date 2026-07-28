<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import ReviewFlipCard from '@/components/word/ReviewFlipCard.vue'
import { today, submit } from '@/api/wordReviewController'
import { REVIEW_GRADES } from '@/utils/word'

const router = useRouter()

const queue = ref<API.WordReviewCardVO[]>([])
const index = ref(0)
const flipped = ref(false)
const loading = ref(true)
const submitting = ref(false)
const startAt = ref(Date.now())

const current = computed(() => queue.value[index.value])
const finished = computed(() => !loading.value && index.value >= queue.value.length)
const progress = computed(() =>
  queue.value.length ? Math.round((index.value / queue.value.length) * 100) : 0,
)
const estMinutes = computed(() => Math.max(1, Math.ceil((queue.value.length - index.value) * 0.2)))

async function loadQueue() {
  loading.value = true
  try {
    const res = await today()
    if (res.data.code === 0 && res.data.data) {
      queue.value = res.data.data
    }
  } finally {
    loading.value = false
    startAt.value = Date.now()
  }
}

function flip() {
  flipped.value = !flipped.value
}

async function grade(quality: number) {
  if (!current.value || submitting.value) return
  // 未翻卡先翻卡，避免盲评
  if (!flipped.value) {
    flipped.value = true
    return
  }
  submitting.value = true
  const costMs = Date.now() - startAt.value
  try {
    const res = await submit({ userWordId: current.value.userWordId, quality, costMs })
    if (res.data.code !== 0) {
      message.error(res.data.message || '提交失败')
      return
    }
    next()
  } catch {
    message.error('提交失败，请稍后再试')
  } finally {
    submitting.value = false
  }
}

function next() {
  index.value += 1
  flipped.value = false
  startAt.value = Date.now()
}

function exit() {
  router.push('/word')
}

function onKey(e: KeyboardEvent) {
  if (finished.value) return
  if (e.key === ' ' || e.code === 'Space') {
    e.preventDefault()
    flip()
  } else if (e.key === 'Escape') {
    exit()
  } else if (['1', '2', '3', '4'].includes(e.key)) {
    e.preventDefault()
    grade(Number(e.key) - 1)
  }
}

onMounted(() => {
  loadQueue()
  window.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
})
</script>

<template>
  <div class="review-wrap">
    <a-spin :spinning="loading">
      <!-- 完成态 -->
      <div v-if="finished" class="done-card">
        <div class="done-emoji">🎉</div>
        <div class="done-title">
          {{ queue.length ? '今天的复习都完成啦' : '今天没有待复习的词' }}
        </div>
        <div class="done-sub">
          {{ queue.length ? `本轮复习了 ${queue.length} 个词` : '录入新词或明天再来' }}
        </div>
        <a-button type="primary" size="large" @click="exit">回到工作台</a-button>
      </div>

      <template v-else-if="current">
        <div class="progress-row">
          <span>{{ index + 1 }} / {{ queue.length }}</span>
          <div class="progress"><i :style="{ width: progress + '%' }"></i></div>
          <span>约 {{ estMinutes }} 分钟</span>
        </div>

        <ReviewFlipCard :card="current" :flipped="flipped" />

        <!-- 未翻卡：整块点击翻卡；已翻卡：显示四档评分 -->
        <div v-if="!flipped" class="flip-actions">
          <a-button type="primary" size="large" block @click="flip">显示答案（空格）</a-button>
        </div>
        <div v-else class="grades">
          <div
            v-for="g in REVIEW_GRADES"
            :key="g.quality"
            class="grade"
            :class="g.cls"
            @click="grade(g.quality)"
          >
            <div class="g-t">{{ g.title }}</div>
            <div class="g-s">{{ g.sub }}</div>
            <div class="g-k">{{ g.key }}</div>
          </div>
        </div>
      </template>
    </a-spin>
  </div>
</template>

<style scoped>
.review-wrap {
  max-width: 720px;
  margin: 0 auto;
}
.progress-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  font-size: 13px;
  color: #595959;
}
.progress {
  flex: 1;
  height: 6px;
  background: #f0f0f0;
  border-radius: 3px;
  overflow: hidden;
}
.progress i {
  display: block;
  height: 100%;
  background: #1890ff;
  border-radius: 3px;
  transition: width 0.3s;
}
.flip-actions {
  margin-top: 20px;
}
.grades {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-top: 20px;
}
.grade {
  border: 1px solid #d9d9d9;
  background: #fff;
  border-radius: 8px;
  padding: 12px 8px;
  cursor: pointer;
  text-align: center;
  transition: all 0.2s;
}
.grade:hover {
  border-color: #1890ff;
  background: #e6f7ff;
}
.grade .g-t {
  font-size: 14px;
  font-weight: 600;
}
.grade .g-s {
  font-size: 11px;
  color: #8c8c8c;
  margin-top: 4px;
}
.grade .g-k {
  display: inline-block;
  margin-top: 8px;
  font-size: 11px;
  color: #8c8c8c;
  border: 1px solid #d9d9d9;
  border-radius: 3px;
  padding: 1px 6px;
}
.g0 .g-t {
  color: #ff4d4f;
}
.g1 .g-t {
  color: #faad14;
}
.g2 .g-t {
  color: #1890ff;
}
.g3 .g-t {
  color: #52c41a;
}
.done-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
  padding: 56px 36px;
  text-align: center;
}
.done-emoji {
  font-size: 48px;
}
.done-title {
  font-size: 20px;
  font-weight: 600;
  margin-top: 16px;
  color: #262626;
}
.done-sub {
  font-size: 14px;
  color: #8c8c8c;
  margin: 8px 0 24px;
}
@media (max-width: 576px) {
  .grades {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
