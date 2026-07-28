<script setup lang="ts">
import { computed } from 'vue'
import { splitTranslation } from '@/utils/word'

const props = defineProps<{
  card: API.WordReviewCardVO
  flipped: boolean
}>()

const hasPhonetic = computed(() => !!props.card.phonetic?.trim())
const translations = computed(() => splitTranslation(props.card.translation))
// definition 同样含字面量 \n，多义项拆行展示
const definitions = computed(() => splitTranslation(props.card.definition))

// 两种复习方向：
//   有原句 → 挖空题干（后端已把词替换成 ＿＿＿＿），回忆「这个词」
//   无原句 → 直接把单词摆在正面，回忆「它的意思」——正面永远有内容，绝不会出现无从下手的空题
const isCloze = computed(() => !!props.card.clozeSentence?.trim())
</script>

<template>
  <div class="flip">
    <!-- 正面题干 -->
    <template v-if="isCloze">
      <div v-if="card.sourceTitle" class="flip-src">📄 你在「{{ card.sourceTitle }}」遇到过它</div>
      <div class="flip-sent">{{ card.clozeSentence }}</div>
    </template>
    <template v-else>
      <div class="flip-word flip-word-front">{{ card.spelling }}</div>
      <div v-if="hasPhonetic" class="flip-ph">/{{ card.phonetic }}/</div>
      <div class="flip-front-tip">还记得它的意思吗？</div>
    </template>

    <!-- 背面答案 -->
    <div v-if="flipped" class="flip-answer">
      <!-- 挖空题的答案是单词本身；单词题的答案是词义 -->
      <div v-if="isCloze" class="flip-word">{{ card.spelling }}</div>
      <div v-if="isCloze && hasPhonetic" class="flip-ph">/{{ card.phonetic }}/</div>
      <div v-if="translations.length" class="flip-tr">
        <div v-for="(t, i) in translations" :key="i">{{ t }}</div>
      </div>
      <div v-else class="flip-tr flip-tr-empty">暂无释义，可在详情页补充</div>
      <div v-if="definitions.length" class="flip-en">
        <div v-for="(d, i) in definitions" :key="i">{{ d }}</div>
      </div>
    </div>

    <div class="flip-hint">
      <kbd>空格</kbd> 翻卡 · <kbd>1</kbd>–<kbd>4</kbd> 评分 · <kbd>Esc</kbd> 退出
    </div>
  </div>
</template>

<style scoped>
.flip {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
  padding: 40px 36px;
  text-align: center;
  min-height: 340px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.flip-src {
  font-size: 12px;
  color: #8c8c8c;
  margin-bottom: 20px;
}
.flip-sent {
  font-size: 20px;
  line-height: 1.9;
  color: #262626;
}
.flip-front-tip {
  font-size: 13px;
  color: #bfbfbf;
  margin-top: 18px;
}
.flip-answer {
  border-top: 1px dashed #f0f0f0;
  margin-top: 28px;
  padding-top: 24px;
}
.flip-word {
  font-size: 34px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: #262626;
}
/* 正面单独摆词时给个大一点的字号，突出主体 */
.flip-word-front {
  font-size: 40px;
}
.flip-ph {
  font-size: 15px;
  color: #8c8c8c;
  margin-top: 8px;
  font-family: 'Times New Roman', serif;
}
.flip-tr {
  font-size: 16px;
  color: #595959;
  margin-top: 16px;
  line-height: 1.8;
}
.flip-tr-empty {
  color: #bfbfbf;
  font-style: italic;
}
.flip-en {
  font-size: 13px;
  color: #8c8c8c;
  margin-top: 12px;
  line-height: 1.7;
  white-space: pre-line;
}
.flip-hint {
  margin-top: 28px;
  font-size: 13px;
  color: #8c8c8c;
}
.flip-hint kbd {
  background: #fafafa;
  border: 1px solid #d9d9d9;
  border-bottom-width: 2px;
  border-radius: 3px;
  padding: 2px 7px;
  font-size: 12px;
  font-family: inherit;
}
@media (max-width: 576px) {
  .flip {
    padding: 28px 20px;
  }
  .flip-sent {
    font-size: 17px;
  }
  .flip-word-front {
    font-size: 32px;
  }
}
</style>
