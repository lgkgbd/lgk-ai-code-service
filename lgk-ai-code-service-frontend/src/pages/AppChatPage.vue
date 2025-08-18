<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  getAppVoById,
  deployApp,
  updateApp,
  deleteApp
} from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'
import { listAppChatHistory } from '@/api/chatHistoryController'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

// 简单的Markdown渲染函数
const renderMarkdown = (text: string) => {
  if (!text) return ''

  let html = text

  // 处理代码块 ```language
  html = html.replace(/```(\w+)?\n([\s\S]*?)```/g, (match, lang, code) => {
    const language = lang || 'text'
    return `<pre class="code-block"><code class="language-${language}">${escapeHtml(code.trim())}</code></pre>`
  })

  // 处理行内代码 `code`
  html = html.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')

  // 处理换行
  html = html.replace(/\n/g, '<br>')

  return html
}

// HTML转义函数
const escapeHtml = (text: string) => {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

// 应用信息
const appInfo = ref<Record<string, any> | null>(null)
const appId = ref<string>('')
const loading = ref(false)
const saving = ref(false)

// 权限控制
const canEdit = ref(false)
const showWorkMode = ref(false)

// 应用详情弹窗
const showAppDetailModal = ref(false)

// 对话相关
type ChatMessage = {
  id: string
  type: 'user' | 'ai'
  content: string
  timestamp: Date
  createTimeStr?: string
}
const messages = ref<ChatMessage[]>([])
// 流内排序保护：记录当前流式生成时AI消息的锚点位置
type StreamOrderGuard = {
  active: boolean
  anchorId?: string
  anchorIndex?: number
}
const streamOrderGuard = ref<StreamOrderGuard>({ active: false })
const historyPageSize = 10
const historyLoading = ref(false)
const hasMoreHistory = ref(false)
const lastCursorCreateTime = ref<string | undefined>(undefined)

const userInput = ref('')
const isGenerating = ref(false)
const isDeploying = ref(false)
const isEditing = ref(false)

// 网页展示
const websiteUrl = ref('')
const showWebsite = ref(false)

// 编辑应用名称
const editAppName = ref('')

// 分割线拖拽相关
const isDragging = ref(false)
const chatWidth = ref(400) // 聊天区域宽度
const minChatWidth = 300 // 最小宽度
const maxChatWidth = 800 // 最大宽度

// 计算默认宽度比例 2:3
const defaultChatWidth = Math.floor(window.innerWidth * 0.4) // 40% 对应 2:3 比例
chatWidth.value = Math.max(minChatWidth, Math.min(maxChatWidth, defaultChatWidth))

// 开始拖拽
const startDrag = (e: MouseEvent) => {
  isDragging.value = true
  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', stopDrag)
  e.preventDefault()
}

// 拖拽中
const onDrag = (e: MouseEvent) => {
  if (!isDragging.value) return

  const newWidth = e.clientX
  if (newWidth >= minChatWidth && newWidth <= maxChatWidth) {
    chatWidth.value = newWidth
  }
}

// 停止拖拽
const stopDrag = () => {
  isDragging.value = false
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)
}

// 检查权限
const checkPermissions = () => {
  if (!appInfo.value || !loginUserStore.loginUser) return

  const isAdmin = loginUserStore.loginUser.userRole === 'admin'
  const isOwner = appInfo.value.userId === loginUserStore.loginUser.id

  canEdit.value = isAdmin || isOwner
}

// 获取应用信息
const loadAppInfo = async () => {
  loading.value = true
  try {
    const res = await getAppVoById({ id: appId.value })
    if (res.data.code === 0 && res.data.data) {
      appInfo.value = res.data.data
      editAppName.value = res.data.data.appName || ''

      // 检查权限
      checkPermissions()
    } else {
      message.error('获取应用信息失败')
      router.push('/')
    }
  } catch (error) {
    console.error('获取应用信息失败:', error)
    message.error('获取应用信息失败')
    router.push('/')
  } finally {
    loading.value = false
  }
}

// 显示应用详情
const showAppDetail = () => {
  showAppDetailModal.value = true
}

// 删除应用
const handleDeleteApp = async () => {
  if (!appInfo.value || !appInfo.value.id) return

  Modal.confirm({
    title: '确认删除',
    content: `确定要删除应用"${appInfo.value?.appName || ''}"吗？此操作不可恢复。`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await deleteApp({ id: appInfo.value!.id })
        if (res.data.code === 0) {
          message.success('删除成功')
          router.push('/')
        } else {
          message.error(res.data.message || '删除失败')
        }
      } catch (error) {
        console.error('删除应用失败:', error)
        message.error('删除失败')
      }
    },
  })
}

// SSE流式对话
const generateCode = async (promptMessage: string) => {
  if (!promptMessage.trim()) return

  isGenerating.value = true

  // 添加AI消息到数组末尾（使用对象引用，避免索引错位）
  const aiMessageId = Date.now().toString()
  const aiMsg: ChatMessage = {
    id: aiMessageId,
    type: 'ai',
    content: '',
    timestamp: new Date(),
  }
  messages.value.push(aiMsg)

  // 记录锚点（AI消息插入时的索引），并开启保护
  streamOrderGuard.value = {
    active: true,
    anchorId: aiMessageId,
    anchorIndex: messages.value.length - 1,
  }

  console.log('开始生成代码，AI消息ID:', aiMessageId)
  console.log('当前消息列表:', messages.value)

  let streamCompleted = false
  let fullContent = ''

  try {
    // 构建URL参数
    const params = new URLSearchParams({
      appId: appId.value || '',
      message: promptMessage,
    })

    const url = `http://localhost:8123/api/app/chat/gen/code?${params}`

    // 使用fetch API替代EventSource，以支持携带认证信息
    const response = await fetch(url, {
      method: 'GET',
      credentials: 'include', // 携带cookies
      headers: {
        'Accept': 'text/event-stream',
        'Cache-Control': 'no-cache',
      },
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('无法获取响应流')
    }

    const decoder = new TextDecoder()

    while (true) {
      const { done, value } = await reader.read()

      if (done) {
        break
      }

      const chunk = decoder.decode(value, { stream: true })
      console.log('收到SSE数据块:', chunk) // 调试信息

      const lines = chunk.split('\n')

      for (const line of lines) {
        const trimmedLine = line.trim()
        if (!trimmedLine) continue

        console.log('处理SSE行:', trimmedLine) // 调试信息

        if (trimmedLine.startsWith('data:')) {
          const dataStr = trimmedLine.slice(5) // 移除 'data:' 前缀

          if (dataStr.trim() === '') continue

          try {
            const data = JSON.parse(dataStr)
            console.log('解析的JSON数据:', data) // 调试信息

            if (data.d) {
              fullContent += data.d
              aiMsg.content = fullContent
              console.log('AI回复更新:', fullContent)
              scrollToBottom()
            }
          } catch (error) {
            console.error('解析SSE数据失败:', error, '原始数据:', dataStr)
          }
        } else if (trimmedLine.startsWith('event:done')) {
          console.log('收到完成事件') // 调试信息
          streamCompleted = true
          isGenerating.value = false

          // 检查是否有内容生成
          if (fullContent.trim()) {
            console.log('代码生成成功，内容长度:', fullContent.length)
            // 延迟更新预览，确保后端已完成处理
            setTimeout(async () => {
              await loadAppInfo()
              updatePreview()
            }, 1000)

            message.success('代码生成完成')
          } else {
            console.log('代码生成失败，无内容')
            handleError(new Error('生成失败，无内容'), aiMsg)
          }
          // 关闭排序保护
          streamOrderGuard.value.active = false
          return
        }
      }
    }

    // 如果没有收到done事件，手动结束
    streamCompleted = true
    isGenerating.value = false

    // 延迟更新预览，确保后端已完成处理
    setTimeout(async () => {
      await loadAppInfo()
      updatePreview()
    }, 1000)

    message.success('代码生成完成')

    // 关闭排序保护
    streamOrderGuard.value.active = false

  } catch (error) {
    console.error('生成代码失败:', error)
    handleError(error, aiMsg)
  }
}

// 错误处理函数
const handleError = (error: unknown, aiMsg?: ChatMessage) => {
  console.error('生成代码失败：', error)

  // 只有在真正出错时才显示错误消息
  if (aiMsg && !aiMsg.content) {
    aiMsg.content = '抱歉，生成过程中出现了错误，请重试。'
  }

  message.error('生成失败，请重试')
  isGenerating.value = false
  streamOrderGuard.value.active = false
}

// 更新预览，增加时间戳避免缓存
const previewVersion = ref(0)
const updatePreview = () => {
  if (appInfo.value?.codeGenType && appInfo.value?.id) {
    previewVersion.value = Date.now()
    websiteUrl.value = `http://localhost:8123/api/static/${appInfo.value.codeGenType}_${appInfo.value.id}/?v=${previewVersion.value}`
    showWebsite.value = true
    console.log('预览已更新:', websiteUrl.value) // 调试信息
  }
}

// 加载对话历史（游标分页）
const loadChatHistory = async (isInitial: boolean = false) => {
  if (!appId.value) return
  if (historyLoading.value) return
  historyLoading.value = true
  try {
    const res = await listAppChatHistory({
      appId: appId.value,
      pageSize: historyPageSize,
      lastCreateTime: isInitial ? undefined : lastCursorCreateTime.value,
    })
    if (res.data.code === 0 && res.data.data) {
      const records = res.data.data.records || []
      // 将后端记录映射到前端消息结构
      const mapped = records.map((r: any) => ({
        id: String(r.id ?? `${r.createTime}`),
        type: (String(r.messageType || '').toLowerCase() === 'user' ? 'user' : 'ai') as 'user' | 'ai',
        content: String(r.message || ''),
        timestamp: r.createTime ? new Date(r.createTime) : new Date(),
        createTimeStr: r.createTime ? String(r.createTime) : undefined,
      })) as Array<{ id: string; type: 'user' | 'ai'; content: string; timestamp: Date; createTimeStr?: string }>
      if (isInitial) {
        // 首次加载：整体按时间升序
        messages.value = mapped.sort((a, b) => a.timestamp.getTime() - b.timestamp.getTime())
      } else {
        // 加载更多：仅将更早的记录按升序插到队列最前，避免替换数组，保持流式消息对象引用不变
        const ascending = [...mapped].sort((a, b) => a.timestamp.getTime() - b.timestamp.getTime())
        messages.value.unshift(...ascending)
        // 若未处于流内保护，则再做一次整体升序兜底
        if (!streamOrderGuard.value.active) {
          messages.value.sort((a, b) => a.timestamp.getTime() - b.timestamp.getTime())
        }
      }

      // 维护游标（取当前已加载中最早一条的时间）
      if (messages.value.length > 0) {
        const earliest = messages.value[0]
        lastCursorCreateTime.value = earliest.createTimeStr || earliest.timestamp.toISOString()
      }
      hasMoreHistory.value = (records.length === historyPageSize)

      // 进入页面：如果已有至少 2 条对话记录，展示网站
      if (isInitial && messages.value.length >= 2) {
        updatePreview()
      }
    } else {
      message.error('加载对话历史失败')
    }
  } catch (error) {
    console.error('加载对话历史失败:', error)
    message.error('加载对话历史失败')
  } finally {
    historyLoading.value = false
  }
}

// 自动首条消息：仅当自己的应用且无历史时，发送 initPrompt
const tryAutoSendInitPrompt = async () => {
  if (!appInfo.value) return
  const isOwner = appInfo.value.userId === loginUserStore.loginUser?.id
  if (isOwner && (messages.value.length === 0) && appInfo.value.initPrompt) {
    const initMsg = appInfo.value.initPrompt
    messages.value.push({
      id: Date.now().toString(),
      type: 'user',
      content: initMsg,
      timestamp: new Date(),
    })
    await nextTick()
    scrollToBottom()
    generateCode(initMsg)
  }
}

// 发送用户消息
const sendMessage = async () => {
  if (!userInput.value.trim() || isGenerating.value) return

  const messageContent = userInput.value.trim()
  userInput.value = ''

  // 添加用户消息
  messages.value.push({
    id: Date.now().toString(),
    type: 'user',
    content: messageContent,
    timestamp: new Date()
  })

  // 滚动到底部
  await nextTick()
  scrollToBottom()

  // 生成代码
  generateCode(messageContent)
}

// 部署应用
const handleDeploy = async () => {
  if (!appInfo.value?.id) return

  isDeploying.value = true
  try {
    const res = await deployApp({ appId: appInfo.value.id })
    if (res.data.code === 0 && res.data.data) {
      Modal.success({
        title: '部署成功',
        content: `您的应用已成功部署，访问地址：${res.data.data}`,
        onOk: () => {
          // 可以复制链接到剪贴板
          navigator.clipboard.writeText(String(res.data.data))
          message.success('链接已复制到剪贴板')
        }
      })
    } else {
      message.error(res.data.message || '部署失败')
    }
  } catch (error) {
    console.error('部署失败:', error)
    message.error('部署失败')
  } finally {
    isDeploying.value = false
  }
}

// 保存应用名称
const saveAppName = async () => {
  if (!appInfo.value?.id || !editAppName.value.trim()) return

  saving.value = true
  try {
    const res = await updateApp({
      id: appInfo.value.id,
      appName: editAppName.value.trim()
    })
    if (res.data.code === 0) {
      appInfo.value.appName = editAppName.value.trim()
      isEditing.value = false
      message.success('应用名称更新成功')
    } else {
      message.error(res.data.message || '更新失败')
    }
  } catch (error) {
    console.error('更新应用名称失败:', error)
    message.error('更新失败')
  } finally {
    saving.value = false
  }
}

// 滚动到底部
const scrollToBottom = () => {
  const messagesContainer = document.querySelector('.messages-container')
  if (messagesContainer) {
    messagesContainer.scrollTop = messagesContainer.scrollHeight
  }
}

// 格式化时间
const formatTime = (time: string | Date) => {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleString('zh-CN')
}

onMounted(async () => {
  appId.value = route.params.id as string
  if (!appId.value) {
    message.error('应用ID无效')
    router.push('/')
    return
  }

  showWorkMode.value = route.query.showWork === '1'
  await loadAppInfo()
  await loadChatHistory(true)
  await tryAutoSendInitPrompt()
})

onUnmounted(() => {
  // 清理可能的SSE连接
})
</script>

<template>
  <div class="app-chat-page">
    <!-- 顶部导航栏 -->
    <div class="top-nav">
      <div class="nav-left">
        <a-dropdown v-if="appInfo">
          <a-button type="text" class="app-name-btn">
            {{ appInfo.appName || '未命名应用' }}
            <down-outlined />
          </a-button>
          <template #overlay>
            <a-menu>
              <a-menu-item v-if="!isEditing" @click="isEditing = true">
                <edit-outlined />
                编辑应用名称
              </a-menu-item>
              <a-menu-item v-else @click="saveAppName">
                <check-outlined />
                保存
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>

        <a-input
          v-if="isEditing"
          v-model:value="editAppName"
          size="small"
          style="width: 200px; margin-left: 8px;"
          @press-enter="saveAppName"
          @blur="saveAppName"
        />
      </div>

      <div class="nav-right">
        <a-button
          @click="showAppDetail"
          style="margin-right: 8px;"
        >
          <info-circle-outlined />
          应用详情
        </a-button>
        <a-button
          type="primary"
          :loading="isDeploying"
          @click="handleDeploy"
        >
          <rocket-outlined />
          部署
        </a-button>
      </div>
    </div>

    <!-- 核心内容区域 -->
    <div class="main-content" :class="{ 'dragging': isDragging }">
      <!-- 左侧对话区域 -->
      <div class="chat-section" :style="{ width: chatWidth + 'px' }">
        <div class="chat-header">
          <span>生成{{ appInfo?.appName || '应用' }}</span>
          <span>用户消息</span>
        </div>

        <div class="messages-container">
          <div v-if="hasMoreHistory" class="load-more-bar">
            <a-button size="small" :loading="historyLoading" @click="loadChatHistory(false)">加载更多</a-button>
          </div>
          <div
            v-for="msg in messages"
            :key="msg.id"
            :class="['message', msg.type === 'user' ? 'user-message' : 'ai-message']"
          >
            <!-- AI消息显示头像 -->
            <div v-if="msg.type === 'ai'" class="message-avatar">
              <img src="/src/assets/aiAvatar.png" alt="AI" class="avatar-image" />
            </div>

            <div class="message-content">
              <div class="message-text" v-if="msg.content" v-html="renderMarkdown(msg.content)"></div>
              <div class="message-text" v-else>
                <a-spin size="small" />
                正在生成代码...
              </div>
              <div class="message-time">{{ formatTime(msg.timestamp) }}</div>
            </div>

            <!-- 用户消息显示头像 -->
            <div v-if="msg.type === 'user'" class="message-avatar">
              <div class="user-avatar">
                <user-outlined />
              </div>
            </div>
          </div>


        </div>

        <div class="input-section">
          <a-input
            v-model:value="userInput"
            placeholder="请描述你想生成的网站，越详细效果越好哦"
            :disabled="isGenerating || !canEdit"
            @press-enter="sendMessage"
          />
          <div v-if="!canEdit" class="permission-tip">
            无法在别人的作品下对话哦~
          </div>
          <div class="input-actions">
            <a-button
              type="primary"
              shape="circle"
              size="small"
              :disabled="!userInput.trim() || isGenerating || !canEdit"
              @click="sendMessage"
            >
              <arrow-up-outlined />
            </a-button>
          </div>
        </div>
      </div>

      <!-- 可拖拽分割线 -->
      <div
        class="resize-handle"
        :class="{ 'dragging': isDragging }"
        @mousedown="startDrag"
      >
        <div class="resize-indicator"></div>
      </div>

      <!-- 右侧网页展示区域 -->
      <div class="preview-section">
        <div class="preview-header">
          <span v-if="showWorkMode">作品展示</span>
          <span v-else>生成后的网页展示</span>
        </div>

        <div class="preview-content">
          <div v-if="!showWebsite && !showWorkMode" class="preview-placeholder">
            <code-outlined />
            <p>等待代码生成完成...</p>
          </div>
          <div v-else-if="showWorkMode && appInfo?.codeGenType && appInfo?.id" class="work-preview">
            <iframe
              :src="`http://localhost:8123/api/static/${appInfo.codeGenType}_${appInfo.id}/`"
              class="website-preview"
              frameborder="0"
            ></iframe>
          </div>
          <iframe
            v-else-if="showWebsite"
            :src="websiteUrl"
            class="website-preview"
            frameborder="0"
          ></iframe>
        </div>
      </div>
    </div>

    <!-- 应用详情弹窗 -->
    <a-modal
      v-model:open="showAppDetailModal"
      title="应用详情"
      :footer="null"
      width="400px"
    >
      <div class="app-detail-content">
        <div class="detail-section">
          <h4>基础信息</h4>
          <div class="detail-item">
            <label>创建者：</label>
            <div class="creator-info">
              <img
                v-if="appInfo?.user?.userAvatar"
                :src="appInfo.user.userAvatar"
                :alt="appInfo.user.userName"
                class="creator-avatar"
              />
              <div v-else class="creator-avatar-placeholder">
                <user-outlined />
              </div>
              <span>{{ appInfo?.user?.userName || '未知用户' }}</span>
            </div>
          </div>
          <div class="detail-item">
            <label>创建时间：</label>
            <span>{{ formatTime(appInfo?.createTime) }}</span>
          </div>
        </div>

        <div v-if="canEdit" class="detail-section">
          <h4>操作</h4>
          <div class="action-buttons">
            <a-button
              type="primary"
              @click="() => { showAppDetailModal = false; router.push(`/app/edit/${appInfo?.id}`) }"
            >
              修改
            </a-button>
            <a-button
              danger
              @click="handleDeleteApp"
            >
              删除
            </a-button>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.app-chat-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

.top-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background: white;
  border-bottom: 1px solid #e8e8e8;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  flex-shrink: 0;
}

.nav-left {
  display: flex;
  align-items: center;
}

.app-name-btn {
  font-weight: 500;
  font-size: 16px;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.main-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.chat-section {
  display: flex;
  flex-direction: column;
  background: white;
  border-right: 1px solid #e8e8e8;
  flex-shrink: 0;
}

.chat-header {
  padding: 12px 16px;
  border-bottom: 1px solid #e8e8e8;
  background: #fafafa;
  font-weight: 500;
  color: #666;
  flex-shrink: 0;
}

.messages-container {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.load-more-bar {
  display: flex;
  justify-content: center;
  margin-bottom: 8px;
}

.message {
  margin-bottom: 12px;
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.user-message { justify-content: flex-end; }
.ai-message { justify-content: flex-start; }

.message-avatar {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  overflow: hidden;
  background: #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-image { width: 100%; height: 100%; object-fit: cover; }

.user-avatar {
  width: 100%; height: 100%; background: #1890ff; color: white;
  display: flex; align-items: center; justify-content: center; font-size: 16px;
}

.message-content {
  max-width: calc(80% - 40px);
  padding: 10px 14px;
  border-radius: 12px;
  position: relative;
  word-wrap: break-word;
}

.user-message .message-content { background: #1890ff; color: white; margin-left: auto; }
.ai-message .message-content { background: #f0f0f0; color: #333; }

.message-text {
  line-height: 1.5;
  word-wrap: break-word;
  white-space: pre-wrap;
}

.message-time {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
  opacity: 0.8;
}

.input-section {
  padding: 12px 16px;
  border-top: 1px solid #e8e8e8;
  background: #fafafa;
  flex-shrink: 0;
}

.input-section .ant-input {
  border-radius: 8px;
  border: 1px solid #d9d9d9;
}

.input-section .ant-input:focus {
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.2);
}

.permission-tip {
  font-size: 12px;
  color: #ff4d4f;
  margin-top: 4px;
  text-align: center;
}

.input-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.preview-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: white;
  overflow: hidden;
}

.preview-header {
  padding: 12px 16px;
  border-bottom: 1px solid #e8e8e8;
  background: #fafafa;
  font-weight: 500;
  color: #666;
  flex-shrink: 0;
}

.preview-content {
  flex: 1;
  position: relative;
  overflow: hidden;
}

.preview-placeholder {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  color: #999;
}

.preview-placeholder .anticon {
  font-size: 3rem;
  margin-bottom: 16px;
  display: block;
}

.website-preview {
  width: 100%;
  height: 100%;
  border: none;
}

.work-preview {
  width: 100%;
  height: 100%;
  position: relative;
}

.work-preview .website-preview {
  border: 2px solid #e8e8e8;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

/* 可拖拽分割线样式 */
.resize-handle {
  width: 6px;
  cursor: col-resize;
  background-color: #e8e8e8;
  border-radius: 3px;
  transition: background-color 0.2s ease;
  flex-shrink: 0;
  position: relative;
  z-index: 10;
}

.resize-handle:hover {
  background-color: #d0d0d0;
}

.resize-handle.dragging {
  background-color: #1890ff;
}

.resize-indicator {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 2px;
  height: 40px;
  background-color: #1890ff;
  border-radius: 1px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.resize-handle:hover .resize-indicator {
  opacity: 1;
}

.resize-handle.dragging .resize-indicator {
  opacity: 1;
}

/* 拖拽时禁用文本选择 */
.main-content.dragging {
  user-select: none;
  cursor: col-resize;
}

/* 代码块样式 */
.code-block {
  background: #f6f8fa;
  border: 1px solid #e1e4e8;
  border-radius: 6px;
  padding: 12px;
  margin: 8px 0;
  overflow-x: auto;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  line-height: 1.4;
}

.code-block code {
  background: transparent;
  padding: 0;
  border: none;
  color: #24292e;
}

.inline-code {
  background: #f6f8fa;
  border: 1px solid #e1e4e8;
  border-radius: 3px;
  padding: 2px 4px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.9em;
  color: #e36209;
}

/* 用户消息中的代码样式 */
.user-message .code-block {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.2);
}

.user-message .code-block code {
  color: white;
}

.user-message .inline-code {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.2);
  color: white;
}

/* 权限提示 */
.permission-tip {
  font-size: 12px;
  color: #ff4d4f;
  margin-top: 4px;
  text-align: center;
}

/* 应用详情弹窗样式 */
.app-detail-content {
  padding: 16px 0;
}

.detail-section {
  margin-bottom: 24px;
}

.detail-section h4 {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 12px 0;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.detail-item {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

.detail-item label {
  font-weight: 500;
  color: #666;
  min-width: 80px;
  margin-right: 12px;
}

.creator-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.creator-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
}

.creator-avatar-placeholder {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666;
  font-size: 12px;
}

.action-buttons {
  display: flex;
  gap: 12px;
}

.action-buttons .ant-btn {
  flex: 1;
}
</style>
