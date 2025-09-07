<template>
  <div class="profile-page">
    <div class="card">
      <div class="header">
        <a-avatar :size="80" :src="loginUser.userAvatar">
          {{ loginUser.userName?.charAt(0)?.toUpperCase() }}
        </a-avatar>
        <div class="info">
          <h2>{{ loginUser.userName || '未命名用户' }}</h2>
          <p class="sub">账号：{{ loginUser.userAccount || '-' }}</p>
        </div>
      </div>

      <div class="body">
        <div class="item">
          <span class="label">个性签名</span>
          <span class="value">{{ loginUser.userProfile || '这个人很懒，什么都没写~' }}</span>
        </div>
        <div class="item">
          <span class="label">角色</span>
          <span class="value">{{ loginUser.userRole || '-' }}</span>
        </div>
        <div class="item">
          <span class="label">创建时间</span>
          <span class="value">{{ loginUser.createTime || '-' }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { onMounted } from 'vue'
import { getMyProfile } from '@/api/userController.ts'

const loginUserStore = useLoginUserStore()
const { loginUser } = storeToRefs(loginUserStore)

onMounted(async () => {
  // 尝试刷新个人信息（后端若已在 cookie 会返回当前登录用户）
  const res = await getMyProfile()
  if (res.data.code === 0 && res.data.data) {
    loginUserStore.setLoginUser(res.data.data)
  }
})
</script>

<style scoped>
.profile-page {
  max-width: 800px;
  margin: 24px auto;
  padding: 0 16px;
}

.card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  padding: 24px;
}

.header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f5f5f5;
}

.info h2 {
  margin: 0;
}
.sub {
  color: #999;
  margin-top: 4px;
}

.body {
  padding-top: 16px;
}

.item {
  display: flex;
  padding: 10px 0;
}

.label {
  width: 100px;
  color: #666;
}

.value {
  flex: 1;
  color: #333;
  word-break: break-all;
}
</style>