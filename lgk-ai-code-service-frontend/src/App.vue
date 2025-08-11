<script setup lang="ts">
import BasicLayout from '@/layouts/BasicLayout.vue'

import { healthCheck } from '@/api/healthController.ts'

healthCheck().then((res) => {
  console.log(res)
})

</script>

<template>
  <BasicLayout />

</template>

<style scoped>
/* 全局布局已包含头部、内容与固定底部 */
</style>
