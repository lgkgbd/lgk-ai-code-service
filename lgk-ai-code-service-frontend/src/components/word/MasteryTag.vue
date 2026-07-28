<script setup lang="ts">
import { computed } from 'vue'
import { MASTERY_META } from '@/utils/word'

const props = defineProps<{
  mastery?: number
}>()

const meta = computed(() => MASTERY_META[props.mastery ?? 0] ?? MASTERY_META[0])
const graduated = computed(() => (props.mastery ?? 0) === 3)
</script>

<template>
  <span class="mastery-tag" :style="{ color: meta.color, background: meta.bg }">
    {{ meta.label }}<template v-if="graduated"> 🎓</template>
  </span>
</template>

<style scoped>
.mastery-tag {
  display: inline-block;
  font-size: 12px;
  line-height: 1;
  padding: 4px 8px;
  border-radius: 4px;
  font-weight: 500;
  white-space: nowrap;
}
</style>
