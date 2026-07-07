<template>
  <header class="navbar">
    <div class="nav-inner">
      <router-link to="/" class="logo">
        <span class="logo-icon">▣</span>
        <span class="logo-text">帝国隐忍</span>
      </router-link>
      <nav class="nav-links">
        <router-link to="/" class="nav-item">首页</router-link>
        <router-link to="/profile" class="nav-item">我的</router-link>
        <router-link to="/create" class="nav-btn">发帖</router-link>
      </nav>
    </div>
  </header>
</template>

<style scoped>
.navbar {
  background: linear-gradient(180deg, #111 0%, #0d0d0d 100%);
  border-bottom: 1px solid rgba(201, 168, 76, 0.15);
  position: sticky;
  top: 0;
  z-index: 100;
  backdrop-filter: blur(8px);
}

.nav-inner {
  max-width: 960px;
  margin: 0 auto;
  padding: 0 20px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #c9a84c;
}

.logo-icon {
  font-size: 22px;
}

.logo-text {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 4px;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 24px;
}

.nav-item {
  font-size: 14px;
  color: #888;
  letter-spacing: 1px;
  transition: color 0.3s;
}

.nav-item:hover,
.nav-item.router-link-active {
  color: #c9a84c;
}

.nav-btn {
  padding: 6px 18px;
  font-size: 13px;
  color: #0a0a0a;
  background: #c9a84c;
  border-radius: 2px;
  letter-spacing: 1px;
  transition: background 0.3s;
}

.nav-btn:hover {
  background: #b8942e;
}

@media (max-width: 600px) {
  .logo-text {
    font-size: 15px;
    letter-spacing: 2px;
  }
  .nav-links {
    gap: 14px;
  }
  .nav-item {
    font-size: 12px;
  }
}
</style>
