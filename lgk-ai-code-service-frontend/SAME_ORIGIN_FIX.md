# 同源问题修复说明

## 问题描述

在实现可视化编辑功能时，遇到了"点击编辑的时候选不了元素"的问题。这是因为iframe内容与主页面不同源，浏览器阻止了DOM操作和事件监听。

## 解决方案

按照方案2 - 动态注入代码，通过前端代理解决同源问题。

### 1. 修改URL配置

**文件**: `src/constants/urls.ts`

```typescript
// 修改前
export const API_BASE_URL = 'http://localhost:8123/api'

// 修改后 - 使用相对路径
export const API_BASE_URL = '/api'
```

### 2. 更新Vite代理配置

**文件**: `vite.config.ts`

```typescript
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8123',
      changeOrigin: true,
      secure: false,
    },
    // 新增：代理静态资源
    '/api/static': {
      target: 'http://localhost:8123',
      changeOrigin: true,
      secure: false,
    },
  },
}
```

### 3. 增强调试功能

**新增文件**: `src/utils/debugHelper.ts`

提供了iframe同源检查和脚本注入测试功能。

**修改文件**: `src/pages/AppChatPage.vue`

- 添加了详细的调试日志
- 集成了DebugHelper工具
- 改进了编辑器初始化时机

## 工作原理

1. **相对路径**: 前端请求使用相对路径 `/api/static/...`
2. **代理转发**: Vite开发服务器将请求代理到 `http://localhost:8123`
3. **同源访问**: iframe内容与主页面同源，可以正常进行DOM操作
4. **脚本注入**: 可以成功在iframe内注入编辑脚本

## 测试步骤

1. 启动开发服务器: `npm run dev`
2. 进入应用对话页面
3. 等待网页加载完成
4. 打开浏览器开发者工具控制台
5. 点击编辑模式按钮
6. 查看控制台日志，确认：
   - iframe同源检查通过
   - 脚本注入成功
   - 元素选择功能正常

## 预期结果

- ✅ iframe内容与主页面同源
- ✅ 可以成功注入编辑脚本
- ✅ 元素悬浮和选择功能正常
- ✅ 控制台显示详细的调试信息

## 故障排除

### 如果仍然无法选择元素：

1. **检查控制台错误**：
   - 查看是否有同源策略错误
   - 确认脚本注入是否成功

2. **验证代理配置**：
   - 确认vite.config.ts中的代理配置正确
   - 重启开发服务器

3. **检查URL生成**：
   - 确认getStaticPreviewUrl返回相对路径
   - 验证iframe src是否正确

4. **网络请求检查**：
   - 在浏览器网络面板中查看请求
   - 确认静态资源通过代理加载

## 生产环境部署

在生产环境中，需要使用Nginx等反向代理来实现相同的功能：

```nginx
location /api/ {
    proxy_pass http://backend-server:8123/api/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}
```

这样就能确保iframe内容与主页面同源，实现动态代码注入功能。
