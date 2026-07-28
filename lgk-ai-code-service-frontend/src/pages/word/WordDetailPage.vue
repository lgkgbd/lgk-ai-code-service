<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import MasteryTag from '@/components/word/MasteryTag.vue'
import WordSightingList from '@/components/word/WordSightingList.vue'
import { getWordCardVo, updateNote, deleteWord } from '@/api/wordController'
import { examLabel, splitTranslation, dueLabel } from '@/utils/word'

const route = useRoute()
const router = useRouter()

const card = ref<API.WordCardVO>({})
const loading = ref(true)
const note = ref('')
const savingNote = ref(false)

const userWordId = computed(() => route.params.id as string)
const hasPhonetic = computed(() => !!card.value.phonetic?.trim())
const exam = computed(() => examLabel(card.value.tag))
const translations = computed(() => splitTranslation(card.value.translation))
const definitions = computed(() => splitTranslation(card.value.definition))
const isManual = computed(() => card.value.source === 'manual')

async function load() {
  loading.value = true
  try {
    const res = await getWordCardVo({ userWordId: userWordId.value as unknown as number })
    if (res.data.code === 0 && res.data.data) {
      card.value = res.data.data
      note.value = res.data.data.note ?? ''
    } else {
      message.error(res.data.message || '加载失败')
    }
  } finally {
    loading.value = false
  }
}

async function saveNote() {
  savingNote.value = true
  try {
    const res = await updateNote({
      userWordId: userWordId.value as unknown as number,
      note: note.value,
    })
    if (res.data.code === 0) {
      message.success('已保存')
      card.value.note = note.value
    } else {
      message.error(res.data.message || '保存失败')
    }
  } finally {
    savingNote.value = false
  }
}

function confirmDelete() {
  Modal.confirm({
    title: '删除这张词卡？',
    content: `「${card.value.spelling}」的学习记录与遇见历史将一并移除。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      const res = await deleteWord({ id: userWordId.value as unknown as number })
      if (res.data.code === 0) {
        message.success('已删除')
        router.push('/word/list')
      } else {
        message.error(res.data.message || '删除失败')
      }
    },
  })
}

onMounted(load)
</script>

<template>
  <div class="word-detail">
    <a-spin :spinning="loading">
      <div class="card">
        <div class="detail-head">
          <div>
            <div class="detail-word">{{ card.spelling }}</div>
            <div v-if="hasPhonetic" class="detail-ph">/{{ card.phonetic }}/</div>
            <div class="detail-tags">
              <MasteryTag :mastery="card.mastery" />
              <span v-if="exam" class="tag tag-exam">{{ exam }}</span>
              <span v-if="(card.encounterNum ?? 1) > 1" class="tag tag-plain">
                遇见 {{ card.encounterNum }} 次
              </span>
            </div>
          </div>
          <div class="head-right">
            <div class="due-label">下次复习</div>
            <div class="due-value">{{ dueLabel(card.dueTime) || '—' }}</div>
            <a-space :size="8" style="margin-top: 10px">
              <a-button @click="router.push('/word/review')">立即复习</a-button>
              <a-button danger @click="confirmDelete">删除</a-button>
            </a-space>
          </div>
        </div>
      </div>

      <div class="grid-2">
        <div>
          <div class="card">
            <div class="card-title">释义</div>
            <div v-if="translations.length" class="def-block">
              <div v-for="(t, i) in translations" :key="i" class="def-row">{{ t }}</div>
            </div>
            <div v-else-if="isManual" class="def-empty">暂无释义，可稍后补充</div>
            <div v-if="definitions.length" class="def-en">
              <div v-for="(d, i) in definitions" :key="i">{{ d }}</div>
            </div>
          </div>

          <div class="card">
            <div class="card-title">
              <span>遇见记录</span>
              <span v-if="card.sightings?.length" class="tag tag-plain">
                共 {{ card.sightings.length }} 次
              </span>
            </div>
            <WordSightingList :sightings="card.sightings" :spelling="card.spelling" />
          </div>
        </div>

        <div>
          <div class="card">
            <div class="card-title">私人笔记</div>
            <a-textarea
              v-model:value="note"
              :rows="4"
              :maxlength="1024"
              placeholder="写点什么帮自己记住它…"
            />
            <div class="note-foot">
              <a-button type="primary" size="small" :loading="savingNote" @click="saveNote">
                保存
              </a-button>
            </div>
          </div>
        </div>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
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
.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.detail-word {
  font-size: 40px;
  font-weight: 700;
  line-height: 1.1;
  color: #262626;
}
.detail-ph {
  font-size: 16px;
  color: #8c8c8c;
  margin-top: 10px;
  font-family: 'Times New Roman', serif;
}
.detail-tags {
  display: flex;
  gap: 8px;
  margin-top: 14px;
  flex-wrap: wrap;
}
.head-right {
  text-align: right;
}
.due-label {
  font-size: 13px;
  color: #8c8c8c;
}
.due-value {
  font-size: 20px;
  font-weight: 600;
  margin-top: 4px;
  color: #262626;
}
.def-row {
  padding: 8px 0;
  font-size: 15px;
  line-height: 1.7;
  color: #262626;
  border-bottom: 1px solid #fafafa;
}
.def-en {
  color: #8c8c8c;
  font-size: 13px;
  margin-top: 10px;
  line-height: 1.7;
  white-space: pre-line;
}
.def-empty {
  color: #bfbfbf;
  font-style: italic;
}
.note-foot {
  margin-top: 10px;
  text-align: right;
}
.tag {
  display: inline-block;
  font-size: 12px;
  line-height: 1;
  padding: 4px 8px;
  border-radius: 4px;
  font-weight: 500;
}
.tag-exam {
  background: #fff1f0;
  color: #ff4d4f;
}
.tag-plain {
  background: #fafafa;
  color: #8c8c8c;
  border: 1px solid #f0f0f0;
}
.grid-2 {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 16px;
}
@media (max-width: 900px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
  .detail-word {
    font-size: 32px;
  }
}
</style>
