<script setup lang="ts">
import { highlightWord, relativeTime } from '@/utils/word'

defineProps<{
  sightings?: API.WordSightingVO[]
  /** 当前词，用于在原句里高亮 */
  spelling?: string
}>()

function channelIcon(channel?: string): string {
  switch (channel) {
    case 'bookmarklet':
    case 'extension':
      return '🔖'
    case 'paste':
      return '📄'
    default:
      return '✍️'
  }
}
</script>

<template>
  <div v-if="sightings && sightings.length" class="timeline">
    <div v-for="s in sightings" :key="s.id" class="tl-item">
      <div class="tl-time">{{ relativeTime(s.createTime) }}</div>
      <div v-if="s.sentence" class="tl-sent">
        <template v-for="(part, i) in highlightWord(s.sentence, spelling)" :key="i">
          <em v-if="part.hit">{{ part.text }}</em>
          <template v-else>{{ part.text }}</template>
        </template>
      </div>
      <div class="tl-src">
        {{ channelIcon(s.channel) }}
        <template v-if="s.sourceTitle">{{ s.sourceTitle }}</template>
        <template v-else-if="s.channel === 'bookmarklet'">网页摘录</template>
        <template v-else>手动录入</template>
        <a v-if="s.sourceUrl" :href="s.sourceUrl" target="_blank" rel="noopener">　链接 ↗</a>
      </div>
    </div>
  </div>
  <a-empty v-else description="还没有遇见记录" :image="undefined" />
</template>

<style scoped>
.timeline {
  position: relative;
  padding-left: 22px;
}
.timeline::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 6px;
  bottom: 6px;
  width: 2px;
  background: #f0f0f0;
}
.tl-item {
  position: relative;
  padding-bottom: 20px;
}
.tl-item:last-child {
  padding-bottom: 0;
}
.tl-item::before {
  content: '';
  position: absolute;
  left: -21px;
  top: 6px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #1890ff;
  border: 2px solid #fff;
  box-shadow: 0 0 0 2px #e6f7ff;
}
.tl-time {
  font-size: 12px;
  color: #8c8c8c;
}
.tl-sent {
  margin-top: 6px;
  font-size: 14px;
  line-height: 1.8;
  color: #595959;
  background: #fafafa;
  padding: 10px 14px;
  border-radius: 6px;
}
.tl-sent em {
  color: #1890ff;
  font-style: normal;
  font-weight: 600;
}
.tl-src {
  margin-top: 6px;
  font-size: 12px;
  color: #8c8c8c;
}
.tl-src a {
  color: #1890ff;
  text-decoration: none;
}
</style>
