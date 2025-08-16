# 权限管理使用指南

## 概述

本项目实现了完整的权限管理系统，支持基于角色的权限控制（RBAC）。

## 权限级别

- `NOT_LOGIN`: 无需登录即可访问
- `USER`: 需要用户登录才能访问
- `ADMIN`: 需要管理员权限才能访问

## 使用方法

### 1. 路由权限控制

在路由配置中添加 `meta.access` 属性：

```typescript
{
  path: '/admin/userManage',
  name: '用户管理',
  component: UserManagePage,
  meta: {
    access: ACCESS_ENUM.ADMIN,
  },
}
```

### 2. 组件内权限检查

```vue
<template>
  <div>
    <!-- 只有管理员才能看到的按钮 -->
    <a-button v-if="isAdmin()" type="primary">管理员功能</a-button>
    
    <!-- 已登录用户才能看到的内容 -->
    <div v-if="isLoggedIn()">欢迎回来！</div>
  </div>
</template>

<script setup lang="ts">
import { isAdmin, isLoggedIn, hasAccess } from '@/access'

// 检查是否为管理员
const adminOnly = isAdmin()

// 检查是否已登录
const loggedIn = isLoggedIn()

// 检查特定权限
const canManageUsers = hasAccess(ACCESS_ENUM.ADMIN)
</script>
```

### 3. 权限指令

使用 `v-access` 指令控制元素显示：

```vue
<template>
  <div>
    <!-- 只有管理员才能看到 -->
    <a-button v-access="ACCESS_ENUM.ADMIN" type="primary">
      管理员功能
    </a-button>
    
    <!-- 已登录用户才能看到 -->
    <div v-access="ACCESS_ENUM.USER">
      用户专属内容
    </div>
  </div>
</template>
```

### 4. 菜单权限控制

在菜单配置中添加 `access` 属性：

```typescript
const menuItems = [
  { key: 'home', label: '首页', path: '/', access: ACCESS_ENUM.NOT_LOGIN },
  { key: 'userManage', label: '用户管理', path: '/admin/userManage', access: ACCESS_ENUM.ADMIN },
]
```

### 5. 组合权限检查

```typescript
import { hasAnyPermission, hasAllPermissions } from '@/access'

// 满足任一权限即可
const canAccess = hasAnyPermission([ACCESS_ENUM.ADMIN, ACCESS_ENUM.USER])

// 必须满足所有权限
const canManage = hasAllPermissions([ACCESS_ENUM.ADMIN, ACCESS_ENUM.USER])
```

## 权限检查流程

1. 用户访问页面时，路由守卫会自动检查权限
2. 如果用户未登录且需要登录权限，自动跳转到登录页
3. 如果用户权限不足，显示无权限提示
4. 组件内的权限检查会在渲染时进行

## 注意事项

1. 权限检查应该在前后端都进行，前端权限控制主要是用户体验优化
2. 敏感操作必须通过后端API进行权限验证
3. 权限配置变更后需要重新登录才能生效
4. 建议在开发环境中使用管理员账号进行测试

## 扩展权限

如需添加新的权限级别，请按以下步骤：

1. 在 `src/access/accessEnum.ts` 中添加新的权限常量
2. 在 `src/access/checkAccess.ts` 中添加对应的权限检查逻辑
3. 更新相关组件的权限配置
4. 测试权限控制是否正常工作
