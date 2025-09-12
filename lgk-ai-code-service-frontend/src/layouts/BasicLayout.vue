<script setup lang="ts">
import { reactive, computed } from 'vue'
import { useRoute } from 'vue-router'
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'
import ACCESS_ENUM from '@/access/accessEnum'

const route = useRoute()

// 检查是否是聊天页面
const isChatPage = computed(() => {
  return route.path.includes('/app/chat/')
})

type MenuItem = {
  key: string
  label: string
  path: string
  access?: string
}

const menuItems = reactive<MenuItem[]>([
  { key: 'home', label: '首页', path: '/', access: ACCESS_ENUM.NOT_LOGIN },
  { key: 'community', label: '交流', path: '/community', access: ACCESS_ENUM.NOT_LOGIN },
  { key: 'userManage', label: '用户管理', path: '/admin/userManage', access: ACCESS_ENUM.ADMIN },
  { key: 'appManage', label: '应用管理', path: '/admin/appManage', access: ACCESS_ENUM.ADMIN },
  { key: 'about', label: '关于', path: '/about', access: ACCESS_ENUM.NOT_LOGIN },
])
</script>

<template>
  <a-layout class="basic-layout">
    <GlobalHeader :menu-items="menuItems" />

    <a-layout-content class="site-content" :class="{ 'chat-page-content': isChatPage }">
      <div class="content-wrapper" :class="{ 'chat-page-wrapper': isChatPage }">
        <router-view />
      </div>
    </a-layout-content>

    <GlobalFooter />
  </a-layout>
</template>

<style scoped>
.basic-layout {
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
}

.site-content {
  flex: 1;
  padding: 24px;
  background: transparent;
}

.content-wrapper {
  max-width: 1200px;
  margin: 0 auto;
  background: transparent;
  border-radius: 0;
  box-shadow: none;
  overflow: visible;
}

/* 聊天页面特殊样式 */
.chat-page-content {
  padding: 0;
}

.chat-page-wrapper {
  max-width: none;
  margin: 0;
  width: 100vw;
  margin-left: calc(-50vw + 50%);
  margin-right: calc(-50vw + 50%);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .site-content {
    padding: 16px;
  }

  .content-wrapper {
    border-radius: 8px;
    min-height: calc(100vh - 180px);
  }
}

@media (max-width: 576px) {
  .site-content {
    padding: 12px;
  }

  .content-wrapper {
    border-radius: 6px;
  }
}
</style>


