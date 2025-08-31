<template>
  <a-layout-header class="global-header">
    <div class="header-inner">
      <div class="left">
        <img class="logo" src="@/assets/logo.png" alt="logo" />
        <span class="site-title">AI 零代码生成平台</span>
      </div>

      <div class="center">
        <div class="custom-menu">
          <a
            v-for="item in filteredMenuItems"
            :key="item.key"
            :href="'#'"
            @click.prevent="onMenuItemClick(item.path)"
            :class="['menu-link', { 'active': isMenuItemActive(item) }]"
          >
            <span class="menu-text">{{ item.label }}</span>
          </a>
        </div>
      </div>

      <div class="right">
        <div class="user-login-status">
          <div v-if="loginUserStore.loginUser.id" class="user-info">
            <a-dropdown>
              <a-space class="user-dropdown">
                <a-avatar :src="loginUserStore.loginUser.userAvatar" class="user-avatar">
                  {{ loginUserStore.loginUser.userName?.charAt(0)?.toUpperCase() }}
                </a-avatar>
                <span class="user-name">{{ loginUserStore.loginUser.userName ?? '无名' }}</span>
                <a-icon type="down" />
              </a-space>
              <template #overlay>
                <a-menu class="user-menu">
                  <a-menu-item @click="doLogout" class="logout-item">
                    <LogoutOutlined />
                    <span>退出登录</span>
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>

          <div v-else class="login-button">
            <a-button type="primary" href="/user/login" class="login-btn">登录</a-button>
          </div>
        </div>
      </div>
    </div>
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { LogoutOutlined } from '@ant-design/icons-vue'
import { userLogout } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import checkAccess from '@/access/checkAccess.ts'

type MenuItem = {
  key: string
  label: string
  path: string
  access?: string
}

const props = defineProps<{
  menuItems: MenuItem[]
}>()

const loginUserStore = useLoginUserStore()
const route = useRoute()
const router = useRouter()

// 过滤有权限的菜单项
const filteredMenuItems = computed(() => {
  return props.menuItems.filter(item => {
    return checkAccess(loginUserStore.loginUser, item.access);
  });
});

// 检查菜单项是否激活
const isMenuItemActive = (item: MenuItem) => {
  // 1) 精确匹配优先
  if (item.path === route.path) return true
  // 2) 最长前缀匹配（避免 '/' 抢占所有路径）
  return route.path.startsWith(item.path) && item.path !== '/'
}

// 用户注销
const doLogout = async () => {
  try {
    const res = await userLogout()
    if (res.data.code === 0) {
      loginUserStore.setLoginUser({
        userName: '未登录',
      })
      message.success({
        content: '退出登录成功',
        duration: 3,
        closable: true,
        onClick: () => {
          message.destroy()
        },
      })
      await router.push('/user/login')
    } else {
      message.error({
        content: '退出登录失败，' + res.data.message,
        duration: 3,
        closable: true,
        onClick: () => {
          message.destroy()
        },
      })
    }
  } catch {
    message.error({
      content: '退出登录失败',
      duration: 3,
      closable: true,
      onClick: () => {
        message.destroy()
      },
    })
  }
}

function onMenuItemClick(path: string) {
  if (path && path !== route.path) {
    router.push(path)
  }
}
</script>

<style scoped>
.global-header {
  background: #ffffff;
  padding: 0 24px;
  border-bottom: 1px solid #f0f0f0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  position: sticky;
  top: 0;
  z-index: 1000;
}

.header-inner {
  display: flex;
  align-items: center;
  gap: 24px;
  max-width: 1200px;
  margin: 0 auto;
  height: 64px;
}

.left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.logo {
  width: 32px;
  height: 32px;
  border-radius: 6px;
}

.site-title {
  font-weight: 600;
  font-size: 18px;
  color: #1890ff;
  white-space: nowrap;
}

.center {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.custom-menu {
  display: flex;
  align-items: center;
  height: 64px;
}

.menu-link {
  display: inline-block;
  height: 64px;
  line-height: 64px;
  padding: 0 24px;
  margin: 0 4px;
  border-radius: 6px;
  transition: all 0.3s ease;
  color: #333;
  text-decoration: none;
}

.menu-link:hover {
  background: #f5f5f5;
  color: #1890ff;
}

.menu-link.active {
  background: #e6f7ff;
  color: #1890ff;
  border-bottom: 2px solid #1890ff;
}

.menu-text {
  font-weight: 500;
  font-size: 14px;
}

.right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.user-info {
  display: flex;
  align-items: center;
}

.user-dropdown {
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 6px;
  transition: all 0.3s ease;
}

.user-dropdown:hover {
  background: #f5f5f5;
}

.user-avatar {
  background: #1890ff;
  color: white;
  font-weight: 600;
}

.user-name {
  font-weight: 500;
  color: #333;
  margin-left: 8px;
}

.user-menu {
  min-width: 120px;
}

.logout-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.login-button {
  display: flex;
  align-items: center;
}

.login-btn {
  height: 36px;
  padding: 0 20px;
  border-radius: 6px;
  font-weight: 500;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .global-header {
    padding: 0 16px;
  }

  .header-inner {
    gap: 16px;
  }

  .site-title {
    display: none;
  }

  .main-menu :deep(.ant-menu-item) {
    padding: 0 16px;
    margin: 0 2px;
  }

  .user-name {
    display: none;
  }
}

@media (max-width: 576px) {
  .center {
    display: none;
  }

  .header-inner {
    justify-content: space-between;
  }
}
</style>
