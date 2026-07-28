<script setup lang="ts">
import { computed } from 'vue'
import MasteryTag from './MasteryTag.vue'
import { examLabel, relativeTime, dueLabel, splitTranslation } from '@/utils/word'

const props = defineProps<{
  card: API.WordCardVO
}>()

const emit = defineEmits<{
  (e: 'click', card: API.WordCardVO): void
}>()

// ⚠️ ECDICT 仅约 28.3% 的词有音标，空值必须整块不渲染，不留空括号
const hasPhonetic = computed(() => !!props.card.phonetic?.trim())
// ECDICT 的 translation 用字面量 \n 分隔多义项，列表里合成一行展示
const translationText = computed(() => splitTranslation(props.card.translation).join('；'))
const exam = computed(() => examLabel(props.card.tag))
const encountered = computed(() => (props.card.encounterNum ?? 1) > 1)
const isManual = computed(() => props.card.source === 'manual')
</script>

<template>
  <div class="wcard" @click="emit('click', card)">
    <div class="wcard-head">
      <span class="wcard-word">{{ card.spelling }}</span>
      <span v-if="hasPhonetic" class="wcard-ph">/{{ card.phonetic }}/</span>
      <MasteryTag :mastery="card.mastery" />
      <span v-if="exam" class="tag tag-exam">{{ exam }}</span>
    </div>

    <div v-if="translationText" class="wcard-tr">{{ translationText }}</div>
    <div v-else-if="isManual" class="wcard-tr wcard-empty">暂无释义，点击补充</div>

    <div class="wcard-meta">
      <span v-if="encountered" class="encounter">⚡ 第 {{ card.encounterNum }} 次遇见</span>
      <span v-if="encountered" class="dot">·</span>
      <span v-if="card.dueTime">下次复习：{{ dueLabel(card.dueTime) }}</span>
      <span class="dot">·</span>
      <span>{{ relativeTime(card.createTime) }}录入</span>
    </div>
  </div>
</template>

<style scoped>
.wcard {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 10px;
  transition: all 0.2s;
  cursor: pointer;
  background: #fff;
}
.wcard:hover {
  border-color: #1890ff;
  box-shadow: 0 2px 12px rgba(24, 144, 255, 0.12);
}
.wcard-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}
.wcard-word {
  font-size: 18px;
  font-weight: 600;
  color: #262626;
}
.wcard-ph {
  font-size: 13px;
  color: #8c8c8c;
  font-family: 'Times New Roman', serif;
}
.wcard-tr {
  font-size: 14px;
  color: #595959;
  margin-top: 6px;
  line-height: 1.6;
  white-space: pre-line;
}
.wcard-empty {
  color: #bfbfbf;
  font-style: italic;
}
.wcard-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  font-size: 12px;
  color: #8c8c8c;
  flex-wrap: wrap;
}
.encounter {
  color: #faad14;
  font-weight: 600;
}
.dot {
  color: #d9d9d9;
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
</style>
