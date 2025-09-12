<template>
  <div class="test-home">
    <h1>测试页面</h1>
    <p>如果你能看到这个，说明路由工作正常</p>
    <CommunityPanel />
  </div>
</template>

<script setup lang="ts">
import CommunityPanel from '@/components/CommunityPanel.vue'
</script>

<style scoped>
.test-home {
  padding: 20px;
}
</style>