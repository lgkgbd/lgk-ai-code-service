<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

type MenuItem = {
  key: string
  label: string
  path: string
}

const props = defineProps<{
  menuItems: MenuItem[]
}>()

const route = useRoute()
const router = useRouter()

const selectedKeys = computed<string[]>(() => {
  // 1) 精确匹配优先
  const exact = props.menuItems.find((m) => m.path === route.path)
  if (exact) return [exact.key]
  // 2) 最长前缀匹配（避免 '/' 抢占所有路径）
  const matched = [...props.menuItems]
    .filter((m) => route.path.startsWith(m.path))
    .sort((a, b) => b.path.length - a.path.length)[0]
  return [matched?.key ?? '']
})

function onMenuItemClick(path: string) {
  if (path && path !== route.path) {
    router.push(path)
  }
}
</script>

<template>
  <a-layout-header class="global-header">
    <div class="header-inner">
      <div class="left">
        <img class="logo" src="@/assets/logo.png" alt="logo" />
        <span class="site-title">AI 零代码生成平台</span>
      </div>

      <div class="center">
        <a-menu mode="horizontal" :selectedKeys="selectedKeys">
          <a-menu-item
            v-for="item in menuItems"
            :key="item.key"
            @click="onMenuItemClick(item.path)"
          >
            {{ item.label }}
          </a-menu-item>
        </a-menu>
      </div>

      <div class="right">
        <a-button type="primary">登录</a-button>
      </div>
    </div>
  </a-layout-header>
</template>

<style scoped>
.global-header {
  background: #ffffff;
  padding: 0 16px;
  border-bottom: 1px solid #f0f0f0;
}

.header-inner {
  display: flex;
  align-items: center;
  gap: 12px;
}

.left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.logo {
  width: 28px;
  height: 28px;
}

.site-title {
  font-weight: 600;
  font-size: 16px;
}

.center {
  flex: 1;
  display: flex;
  align-items: center;
}

.right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 响应式：小屏隐藏文字，仅保留 logo 和菜单折行 */
@media (max-width: 576px) {
  .site-title {
    display: none;
  }
}
</style>


