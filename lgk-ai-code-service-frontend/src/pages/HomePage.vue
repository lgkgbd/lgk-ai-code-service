<script setup lang="ts">
// 移除BasicLayout导入，因为App.vue已经使用了BasicLayout
</script>

<template>
  <div class="home-page">
    <h1>欢迎来到AI零代码生成平台</h1>
    <p>这是一个强大的平台，帮助您快速构建应用程序。</p>
    
    <div class="features">
      <div class="feature-card">
        <h3>用户管理</h3>
        <p>完整的用户管理系统，支持用户注册、登录、权限控制</p>
      </div>
      <div class="feature-card">
        <h3>权限控制</h3>
        <p>基于角色的权限管理系统，确保系统安全</p>
      </div>
      <div class="feature-card">
        <h3>代码生成</h3>
        <p>AI驱动的代码生成功能，提高开发效率</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.home-page {
  padding: 24px;
  text-align: center;
}

.home-page h1 {
  color: #1890ff;
  margin-bottom: 16px;
  font-size: 2.5em;
}

.home-page p {
  font-size: 1.2em;
  color: #666;
  margin-bottom: 48px;
}

.features {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 24px;
  margin-top: 48px;
}

.feature-card {
  padding: 24px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  background: #fafafa;
  transition: all 0.3s ease;
}

.feature-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.feature-card h3 {
  color: #1890ff;
  margin-bottom: 12px;
}

.feature-card p {
  color: #666;
  line-height: 1.6;
}
</style>
