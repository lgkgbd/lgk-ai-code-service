<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import WordCard from '@/components/word/WordCard.vue'
import { listMyWordsByPage } from '@/api/wordController'
import { MASTERY_SEGMENTS } from '@/utils/word'

const router = useRouter()

const list = ref<API.WordCardVO[]>([])
const total = ref(0)
const loading = ref(false)
const activeMastery = ref<number | undefined>(undefined)

const query = reactive({
  pageNum: 1,
  pageSize: 20,
  keyword: '',
})

async function load() {
  loading.value = true
  try {
    const res = await listMyWordsByPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      mastery: activeMastery.value,
    })
    if (res.data.code === 0 && res.data.data) {
      list.value = res.data.data.records ?? []
      total.value = Number(res.data.data.totalRow ?? 0)
    }
  } finally {
    loading.value = false
  }
}

function selectMastery(m: number | undefined) {
  activeMastery.value = m
  query.pageNum = 1
  load()
}
function onSearch() {
  query.pageNum = 1
  load()
}
function onPageChange(p: number, size: number) {
  query.pageNum = p
  query.pageSize = size
  load()
}
function goDetail(card: API.WordCardVO) {
  router.push(`/word/detail/${card.userWordId}`)
}

onMounted(load)
</script>

<template>
  <div class="word-list">
    <div class="filter-bar">
      <div class="seg">
        <span
          v-for="seg in MASTERY_SEGMENTS"
          :key="seg.label"
          :class="{ on: activeMastery === seg.value }"
          @click="selectMastery(seg.value)"
        >
          {{ seg.label }}
        </span>
      </div>
      <div class="spacer"></div>
      <a-input-search
        v-model:value="query.keyword"
        placeholder="搜索单词"
        style="width: 200px"
        allow-clear
        @search="onSearch"
        @press-enter="onSearch"
      />
    </div>

    <div class="card">
      <a-spin :spinning="loading">
        <template v-if="list.length">
          <WordCard v-for="w in list" :key="String(w.userWordId)" :card="w" @click="goDetail" />
        </template>
        <a-empty v-else description="没有符合条件的单词" />
      </a-spin>

      <div v-if="total > query.pageSize" class="pager">
        <a-pagination
          :current="query.pageNum"
          :page-size="query.pageSize"
          :total="total"
          :show-size-changer="false"
          @change="onPageChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.filter-bar {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  padding: 14px 20px;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.seg {
  display: flex;
  background: #fafafa;
  border-radius: 6px;
  padding: 3px;
  flex-wrap: wrap;
}
.seg span {
  padding: 6px 14px;
  font-size: 13px;
  border-radius: 4px;
  cursor: pointer;
  color: #595959;
}
.seg span.on {
  background: #fff;
  color: #1890ff;
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}
.spacer {
  flex: 1;
}
.card {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  padding: 20px;
}
.pager {
  margin-top: 16px;
  text-align: right;
}
@media (max-width: 576px) {
  .filter-bar {
    padding: 12px;
  }
}
</style>
