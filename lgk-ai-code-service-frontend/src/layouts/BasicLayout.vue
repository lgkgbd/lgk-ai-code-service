<script setup lang="ts">
import { reactive } from 'vue'
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'

type MenuItem = {
  key: string
  label: string
  path: string
}

const menuItems = reactive<MenuItem[]>([
  { key: 'home', label: '首页', path: '/' },
  { key: 'about', label: '关于', path: '/about' },
])
</script>

<template>
  <a-layout class="basic-layout">
    <GlobalHeader :menu-items="menuItems" />

    <a-layout-content class="site-content">
      <router-view />
    </a-layout-content>

    <GlobalFooter />
  </a-layout>
  
</template>

<style scoped>
.basic-layout {
  min-height: 100vh;
  background: #f5f5f5;
}

.site-content {
  margin: 16px;
  padding: 16px;
  background: #ffffff;
  border-radius: 8px;
  /* 给固定底部留出空间，避免内容被遮挡 */
  padding-bottom: 72px;
}

@media (max-width: 768px) {
  .site-content {
    margin: 8px;
    padding: 12px;
  }
}
</style>


