# 可视化编辑功能测试指南

## 测试前准备

1. **确保后端服务运行**：
   ```bash
   # 确保后端服务在 http://localhost:8123 运行
   ```

2. **启动前端开发服务器**：
   ```bash
   npm run dev
   ```

3. **准备测试应用**：
   - 确保有一个已生成代码的应用
   - 应用应该有可编辑的网页内容

## 测试步骤

### 第一步：基础功能测试

1. **访问应用对话页面**
   - 打开浏览器，访问应用对话页面
   - 确保页面正常加载

2. **等待网页预览加载**
   - 确认右侧预览区域显示网页内容
   - 等待iframe完全加载

3. **检查编辑模式按钮**
   - 确认输入框左侧显示编辑模式按钮（铅笔图标）
   - 确认按钮只在有编辑权限时显示

### 第二步：同源问题验证

1. **打开浏览器开发者工具**
   - 按F12打开开发者工具
   - 切换到Console标签页

2. **查看调试信息**
   - 页面加载后，应该看到类似以下日志：
   ```
   AppChatPage: preview updated: /api/static/xxx_xxx/
   AppChatPage: iframe loaded, initializing editor
   === DebugHelper: iframe debug start ===
   DebugHelper: iframe info: {src: "/api/static/xxx_xxx/", sameOrigin: true}
   DebugHelper: iframe is same-origin, testing script injection...
   DebugHelper: script injection test passed
   === DebugHelper: iframe debug end ===
   AppChatPage: initializing visual editor
   AppChatPage: visual editor initialized
   ```

3. **确认同源检查通过**
   - 如果看到 `sameOrigin: true`，说明同源问题已解决
   - 如果看到 `sameOrigin: false`，说明配置还有问题

### 第三步：编辑功能测试

1. **进入编辑模式**
   - 点击编辑模式按钮
   - 确认按钮状态变为激活状态（蓝色）

2. **测试元素悬浮**
   - 鼠标悬浮在网页元素上
   - 确认元素显示蓝色虚线边框
   - 确认鼠标指针变为十字形

3. **测试元素选择**
   - 点击网页元素
   - 确认元素边框变为绿色实线
   - 确认输入框上方显示选中元素信息

4. **测试元素信息显示**
   - 确认Alert组件显示元素标签和文本内容
   - 点击Alert组件的关闭按钮
   - 确认选中状态被清除

### 第四步：消息发送测试

1. **无选中元素发送**
   - 不选中任何元素
   - 输入提示词并发送
   - 确认消息正常发送，不包含元素信息

2. **有选中元素发送**
   - 选中一个网页元素
   - 输入提示词并发送
   - 确认消息包含元素信息
   - 确认发送后自动退出编辑模式

## 预期结果

### 成功标志

✅ **控制台日志正常**：
- 没有同源策略错误
- 脚本注入成功
- 编辑器初始化成功

✅ **编辑功能正常**：
- 元素悬浮效果正常
- 元素选择功能正常
- 元素信息显示正确

✅ **消息发送正常**：
- 元素信息正确添加到提示词
- 发送后状态正确重置

### 失败标志

❌ **同源策略错误**：
```
VisualEditor: Same-origin policy violation
```

❌ **脚本注入失败**：
```
VisualEditor: failed to inject script
```

❌ **元素选择无效果**：
- 鼠标悬浮无边框显示
- 点击元素无反应

## 故障排除

### 问题1：同源检查失败

**症状**：控制台显示 `sameOrigin: false`

**解决方案**：
1. 检查 `vite.config.ts` 中的代理配置
2. 确认 `src/constants/urls.ts` 中使用相对路径
3. 重启开发服务器

### 问题2：脚本注入失败

**症状**：控制台显示脚本注入错误

**解决方案**：
1. 确认iframe完全加载后再初始化编辑器
2. 检查iframe src是否正确
3. 验证代理配置是否生效

### 问题3：元素选择无效果

**症状**：点击编辑模式后无法选择元素

**解决方案**：
1. 检查控制台是否有错误信息
2. 确认iframe内容是否正常加载
3. 验证编辑脚本是否正确注入

### 问题4：网络请求错误

**症状**：静态资源加载失败

**解决方案**：
1. 检查后端服务是否正常运行
2. 验证代理配置是否正确
3. 查看网络面板中的请求状态

## 调试技巧

### 1. 使用浏览器开发者工具

- **Console面板**：查看详细的调试日志
- **Network面板**：检查网络请求是否通过代理
- **Elements面板**：检查iframe是否正确加载

### 2. 检查URL配置

在控制台中运行：
```javascript
// 检查当前URL配置
console.log('API_BASE_URL:', '/api')
console.log('iframe src:', document.querySelector('iframe').src)
```

### 3. 验证代理配置

在浏览器中直接访问：
```
http://localhost:5173/api/static/xxx_xxx/
```

如果能看到内容，说明代理配置正确。

## 常见问题

### Q: 为什么需要同源？
A: 浏览器的同源策略阻止了跨域iframe的DOM操作，必须同源才能注入脚本和监听事件。

### Q: 生产环境如何处理？
A: 使用Nginx等反向代理，将静态资源代理到同一域名下。

### Q: 调试信息太多怎么办？
A: 可以在生产环境中移除DebugHelper的调用，或者添加环境变量控制。

### Q: 如果后端服务端口不同怎么办？
A: 修改 `vite.config.ts` 中的 `target` 配置为正确的后端地址。
