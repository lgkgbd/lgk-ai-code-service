<template>
  <a-layout-footer class="global-footer">
    <div class="footer-content">
      <div class="footer-text">
        <a
          href="https://ghkzkbhjcxjy.eu-central-1.clawcloudrun.com"
          target="_blank"
          rel="noopener noreferrer"
          class="footer-link"
        >
          AI 零代码生成平台
        </a>
        <span class="separator">by</span>
        <span class="author">程序员 lgk</span>
      </div>
      <div class="footer-version">
        <span class="version-text">v1.0.0</span>
      </div>
    </div>
  </a-layout-footer>
</template>

<script setup lang="ts">
</script>

<style scoped>
.global-footer {
  background: #ffffff;
  border-top: 1px solid #f0f0f0;
  padding: 16px 24px;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.06);
}

.footer-content {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 8px;
  text-align: center;
}

.footer-text {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #666;
  justify-content: center;
}

.footer-link {
  color: #1890ff;
  text-decoration: none;
  font-weight: 500;
  transition: color 0.3s ease;
}

.footer-link:hover {
  color: #40a9ff;
  text-decoration: underline;
}

.separator {
  color: #999;
}

.author {
  color: #333;
  font-weight: 500;
}

.footer-version {
  display: flex;
  align-items: center;
  justify-content: center;
}

.version-text {
  font-size: 12px;
  color: #999;
  background: #f5f5f5;
  padding: 4px 8px;
  border-radius: 4px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .global-footer {
    padding: 12px 16px;
  }

  .footer-text {
    font-size: 13px;
  }
}

@media (max-width: 576px) {
  .footer-text {
    font-size: 12px;
    flex-direction: column;
    gap: 4px;
  }

  .version-text {
    font-size: 11px;
  }
}
</style>


