<template>
  <div class="about-page">
    <h1>关于我们</h1>
    <p>这是一个AI零代码生成平台，旨在帮助用户快速构建应用程序。</p>
    
    <h2>功能特性</h2>
    <ul>
      <li>用户管理</li>
      <li>权限控制</li>
      <li>代码生成</li>
      <li>项目管理</li>
    </ul>
    
    <h2>技术栈</h2>
    <ul>
      <li>前端：Vue 3 + TypeScript + Ant Design Vue</li>
      <li>状态管理：Pinia</li>
      <li>路由：Vue Router</li>
      <li>构建工具：Vite</li>
    </ul>
  </div>
</template>

<script setup lang="ts">
// 移除BasicLayout导入，因为App.vue已经使用了BasicLayout
</script>

<style scoped>
.about-page {
  padding: 24px;
  max-width: 800px;
  margin: 0 auto;
}

.about-page h1 {
  color: #1890ff;
  margin-bottom: 24px;
}

.about-page h2 {
  color: #333;
  margin: 24px 0 16px 0;
}

.about-page ul {
  margin-left: 20px;
}

.about-page li {
  margin-bottom: 8px;
  line-height: 1.6;
}
</style>
