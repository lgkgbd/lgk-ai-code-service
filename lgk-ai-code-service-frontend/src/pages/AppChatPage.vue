<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Modal, Alert } from 'ant-design-vue'
import { showSuccess, showError } from '@/utils/message'
import {
  Html5Outlined,
  FileOutlined,
  CodeOutlined,
  QuestionCircleOutlined,
  EditOutlined,
  ArrowUpOutlined,
  UserOutlined,
  InfoCircleOutlined,
  DownloadOutlined,
  RocketOutlined,
  DownOutlined,
  CheckOutlined
} from '@ant-design/icons-vue'
import {
  getAppVoById,
  deployApp,
  updateApp,
  deleteApp
} from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'
import { listAppChatHistory } from '@/api/chatHistoryController'
import { healthCheck } from '@/api/healthController'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import '@/assets/collapsible-code.css'
import { getStaticPreviewUrl, API_BASE_URL } from '../constants/urls'
import { getCodeGenTypeConfig } from '@/constants/codeGenType'
import { VisualEditor, type ElementInfo } from '@/utils/visualEditor'
import { DebugHelper } from '@/utils/debugHelper'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

// Markdown 渲染器（支持代码高亮、自动链接、换行）
const md = new MarkdownIt({
  html: true, // 允许HTML，用于折叠功能
  linkify: true,
  breaks: true,
  typographer: true,
  highlight: function (str: string, lang: string) {
    let highlightedCode = ''
    if (lang && hljs.getLanguage(lang)) {
      try {
        highlightedCode = hljs.highlight(str, { language: lang }).value
      } catch {
        highlightedCode = str
          .replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;')
      }
    } else {
      highlightedCode = str
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
    }

    // 直接返回高亮后的代码，fence 渲染器会包装为可折叠块
    return highlightedCode
  }
})

/**
 * 自定义 fence 渲染器：把代码块渲染为可折叠组件
 */
md.renderer.rules.fence = (tokens, idx) => {
  const token = tokens[idx]
  const info = token.info ? md.utils.unescapeAll(token.info).trim() : ''
  const lang = (info.split(/\s+/g)[0] || 'text').toLowerCase()
  const codeRaw = token.content || ''
  // 去除所有首尾空行与 BOM，避免顶部/底部白带（兼容 CRLF）
  const code = (() => {
    const lines = codeRaw.replace(/^\uFEFF?/, '').split(/\r?\n/);
    while (lines.length && /^\s*$/.test(lines[0])) lines.shift();
    while (lines.length && /^\s*$/.test(lines[lines.length - 1])) lines.pop();
    return lines.join('\n');
  })()

  let highlightedCode = code
  if (lang && hljs.getLanguage(lang)) {
    try {
      highlightedCode = hljs.highlight(code, { language: lang }).value
    } catch {
      highlightedCode = md.utils.escapeHtml(code)
    }
  } else {
    highlightedCode = md.utils.escapeHtml(code)
  }

  // 使用原生 details/summary 折叠（默认展开，紧贴上一行文字）
  return `
    <details class="md-code-details" open>
      <summary class="md-code-summary">
        <span class="md-code-lang">${(lang || 'text').toUpperCase()}</span>
      </summary>
      <pre class="md-code-pre"><code class="language-${lang || 'text'}">${highlightedCode}</code></pre>
    </details>
  `
}

// 自定义渲染规则以减少空白
md.renderer.rules.paragraph_open = () => '<p class="md-paragraph">'
md.renderer.rules.paragraph_close = () => '</p>'
md.renderer.rules.heading_open = (tokens, idx) => {
  const level = tokens[idx].markup.length
  return `<h${level} class="md-heading md-h${level}">`
}
md.renderer.rules.heading_close = (tokens, idx) => {
  const level = tokens[idx].markup.length
  return `</h${level}>`
}

const renderMarkdown = (text: string) => {
  if (!text) return ''
  let rendered = md.render(text)

  // 移除多余的空行和空白
  rendered = rendered.replace(/\n\s*\n\s*\n/g, '\n\n')

  // 移除代码块之间的多余空白
  rendered = rendered.replace(/(<\/div>\s*)\n+(\s*<div class="collapsible-code-block">)/g, '$1$2')

  // 移除段落和代码块之间的多余空白
  rendered = rendered.replace(/(<\/p>\s*)\n+(\s*<div class="collapsible-code-block">)/g, '$1$2')
  rendered = rendered.replace(/(<\/div>\s*)\n+(\s*<p class="md-paragraph">)/g, '$1$2')

  // 移除开头和结尾的空白
  rendered = rendered.trim()

  return rendered
}



// 应用信息
const appInfo = ref<API.AppVO | null>(null)
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
const minChatWidth = 300 // 聊天区域最小宽度
const minPreviewWidth = 400 // 预览区域最小宽度

// 计算默认宽度比例 2:3
const defaultChatWidth = Math.floor(window.innerWidth * 0.4) // 40% 对应 2:3 比例
chatWidth.value = Math.max(minChatWidth, Math.min(window.innerWidth - minPreviewWidth, defaultChatWidth))

// 监听窗口大小变化，重新计算宽度限制
const handleResize = () => {
  const maxChatWidth = window.innerWidth - minPreviewWidth
  if (chatWidth.value > maxChatWidth) {
    chatWidth.value = maxChatWidth
  }
  if (chatWidth.value < minChatWidth) {
    chatWidth.value = minChatWidth
  }
}

// 开始拖拽
const startDrag = (e: MouseEvent) => {
  isDragging.value = true
  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', stopDrag)
  e.preventDefault()

  // 添加拖拽时的样式
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

// 拖拽中
const onDrag = (e: MouseEvent) => {
  if (!isDragging.value) return

  e.preventDefault()
  const newWidth = e.clientX
  const maxChatWidth = window.innerWidth - minPreviewWidth // 确保预览区域有最小宽度

  // 限制在最小和最大宽度之间
  if (newWidth >= minChatWidth && newWidth <= maxChatWidth) {
    chatWidth.value = newWidth
  } else if (newWidth < minChatWidth) {
    chatWidth.value = minChatWidth
  } else if (newWidth > maxChatWidth) {
    chatWidth.value = maxChatWidth
  }
}

// 停止拖拽
const stopDrag = () => {
  isDragging.value = false
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)

  // 恢复默认样式
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
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
      showError('获取应用信息失败')
      router.push('/')
    }
  } catch (error) {
    console.error('获取应用信息失败:', error)
    showError('获取应用信息失败')
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
          showSuccess('删除成功')
          router.push('/')
        } else {
          showError(res.data.message || '删除失败')
        }
      } catch (error) {
        console.error('删除应用失败:', error)
        showError('删除失败')
      }
    },
  })
}

/** 确保 details 代码块默认展开并可见 */
const ensureCodeDetailsOpen = () => {
  document.querySelectorAll('details.md-code-details').forEach((el) => {
    const d = el as HTMLDetailsElement
    if (!d.open) d.open = true
  })
}

// SSE流式对话
const generateCode = async (promptMessage: string) => {
  if (!promptMessage.trim()) return

  isGenerating.value = true

  // 添加AI消息到数组末尾（使用对象引用，避免索引错位）
  const aiMessageId = Date.now().toString()
  const aiMsg = reactive<ChatMessage>({
    id: aiMessageId,
    type: 'ai',
    content: '',
    timestamp: new Date(),
  })
  messages.value.push(aiMsg)

  // 记录锚点（AI消息插入时的索引），并开启保护
  streamOrderGuard.value = {
    active: true,
    anchorId: aiMessageId,
    anchorIndex: messages.value.length - 1,
  }

  console.log('开始生成代码，AI消息ID:', aiMessageId)
  console.log('当前消息列表:', messages.value)
  let fullContent = ''

  try {
    // 构建URL参数
    const params = new URLSearchParams({
      appId: appId.value || '',
      message: promptMessage,
    })

    const url = `${API_BASE_URL}/app/chat/gen/code?${params}`

    // 首选原生 EventSource（支持 withCredentials），避免 fetch 在部分环境的缓冲
    let endedByES = false
    const es = new EventSource(url, { withCredentials: true })

    // rAF 合并提交，降低频繁渲染
    let pendingDelta = ''
    let rafId: number | null = null
    const commitDelta = () => {
      if (!pendingDelta) return
      fullContent += pendingDelta
      pendingDelta = ''
      aiMsg.content = fullContent
      nextTick().then(() => { ensureCodeDetailsOpen(); scrollToBottom() })
    }
    const scheduleCommit = () => {
      if (rafId !== null) return
      rafId = requestAnimationFrame(() => {
        rafId = null
        commitDelta()
      })
    }
    const pushDelta = (delta: string) => {
      if (!delta) return
      pendingDelta += delta
      scheduleCommit()
    }

    es.addEventListener('message', (evt: MessageEvent) => {
      const text = String(evt.data || '')
      if (!text) return
      if (text === '[DONE]') return
      try {
        const maybe = JSON.parse(text)
        if (maybe && typeof maybe === 'object' && 'd' in maybe) {
          pushDelta(String(maybe.d ?? ''))
          return
        }
      } catch {}
      pushDelta(text)
    })

    // 处理business-error事件（后端限流等错误）
    es.addEventListener('business-error', (event: MessageEvent) => {
      if (endedByES) return

      try {
        const errorData = JSON.parse(event.data)
        console.error('SSE业务错误事件:', errorData)

        // 显示具体的错误信息
        const errorMessage = errorData.message || '生成过程中出现错误'
        aiMsg.content = `❌ ${errorMessage}`
        showError(errorMessage)

        endedByES = true
        isGenerating.value = false
        streamOrderGuard.value.active = false
        es.close()
      } catch (parseError) {
        console.error('解析错误事件失败:', parseError, '原始数据:', event.data)
        handleError(new Error('服务器返回错误'), aiMsg)
      }
    })

    es.addEventListener('done', () => {
      endedByES = true
      commitDelta()
      es.close()
      isGenerating.value = false
      if (fullContent.trim()) {
        setTimeout(async () => {
          await refreshAfterAIFinish()
        }, 1000)
        showSuccess('代码生成完成')
      } else {
        handleError(new Error('生成失败，无内容'), aiMsg)
      }
      streamOrderGuard.value.active = false
    })

    es.onerror = () => {
      // 若 ES 出错且尚未结束，回退到 fetch 流解析
      if (endedByES) return
      es.close()
      void (async () => {
        try {
          const response = await fetch(url, {
            method: 'GET',
            credentials: 'include',
            headers: { 'Accept': 'text/event-stream', 'Cache-Control': 'no-cache' },
          })
          if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`)
          const reader = response.body?.getReader()
          if (!reader) throw new Error('无法获取响应流')
          const decoder = new TextDecoder()
          let buffer = ''
          let currentEvent = { name: '', dataLines: [] as string[] }
          const flushEvent = () => {
            const dataStr = currentEvent.dataLines.join('\n').trim()
            const eventName = currentEvent.name.trim().toLowerCase()
            currentEvent = { name: '', dataLines: [] }
            if (!dataStr) return

            // 处理business-error事件
            if (eventName === 'business-error') {
              try {
                const errorData = JSON.parse(dataStr)
                console.error('SSE业务错误事件:', errorData)

                // 显示具体的错误信息
                const errorMessage = errorData.message || '生成过程中出现错误'
                aiMsg.content = `❌ ${errorMessage}`
                showError(errorMessage)

                isGenerating.value = false
                streamOrderGuard.value.active = false
                return
              } catch (parseError) {
                console.error('解析错误事件失败:', parseError, '原始数据:', dataStr)
                handleError(new Error('服务器返回错误'), aiMsg)
                return
              }
            }

            if (dataStr === '[DONE]' || eventName === 'done') {
              commitDelta()
              isGenerating.value = false
              if (fullContent.trim()) {
                setTimeout(async () => { await refreshAfterAIFinish() }, 1000)
                showSuccess('代码生成完成')
              } else {
                handleError(new Error('生成失败，无内容'), aiMsg)
              }
              streamOrderGuard.value.active = false
              return
            }
            try {
              const maybeJson = JSON.parse(dataStr)
              if (maybeJson && typeof maybeJson === 'object' && 'd' in maybeJson) {
                pushDelta(String(maybeJson.d ?? ''))
                return
              }
            } catch {}
            pushDelta(dataStr)
          }
          while (true) {
            const { done, value } = await reader.read()
            if (done) break
            buffer += decoder.decode(value, { stream: true })
            const lines = buffer.split(/\r?\n/)
            buffer = lines.pop() || ''
            for (const rawLine of lines) {
              const line = rawLine.trimEnd()
              if (line === '') { flushEvent(); continue }
              if (line.startsWith('data:')) {
                const payload = line.slice(5).trimStart()
                if (payload === '[DONE]') { flushEvent(); continue }
                try {
                  const maybeJson = JSON.parse(payload)
                  if (maybeJson && typeof maybeJson === 'object' && 'd' in maybeJson) {
                    pushDelta(String(maybeJson.d ?? ''))
                    continue
                  }
                } catch {}
                if (payload) pushDelta(payload)
              } else if (line.startsWith('event:')) {
                currentEvent.name = line.slice(6).trim()
              }
            }
          }
          if (buffer.length > 0 || currentEvent.dataLines.length > 0) {
            flushEvent()
          }
        } catch (err) {
          console.error('回退fetch流失败:', err)
          handleError(err, aiMsg)
        }
      })()
    }

    // 卸载时关闭连接
    onUnmounted(() => {
      try { es.close() } catch {}
    })
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

  showError('生成失败，请重试')
  isGenerating.value = false
  streamOrderGuard.value.active = false
}

const refreshAfterAIFinish = async () => {
  try { await healthCheck() } catch (e) { console.warn('healthCheck failed', e) }
  await loadAppInfo()
  updatePreview()
}

// 更新预览
const updatePreview = () => {
  if (appInfo.value?.codeGenType && appInfo.value?.id) {
    const baseUrl = getStaticPreviewUrl(String(appInfo.value.codeGenType), String(appInfo.value.id))
    const urlWithBust = `${baseUrl}${baseUrl.includes('?') ? '&' : '?'}ts=${Date.now()}`
    websiteUrl.value = urlWithBust
    showWebsite.value = true
    console.log('AppChatPage: preview updated:', websiteUrl.value)

    // 重置编辑器状态
    if (visualEditor.value) {
      visualEditor.value.disableEditMode()
      visualEditor.value = null
    }
    isEditMode.value = false
    selectedElement.value = null

    // 延迟初始化编辑器，确保iframe已加载
    setTimeout(() => {
      if (iframeRef.value && !visualEditor.value) {
        console.log('AppChatPage: attempting to initialize editor after iframe load')
        initVisualEditor()
      }
    }, 2000) // 增加延迟时间，确保iframe完全加载
  }
}

// 加载对话历史（游标分页）
const loadChatHistory = async (isInitial: boolean = false) => {
  if (!appId.value) return
  if (historyLoading.value) return
  historyLoading.value = true
  try {
    const lastCreateTimeParam: number | undefined = isInitial ? undefined : (() => {
      const v = lastCursorCreateTime.value
      if (!v) return undefined
      const n = Number(v)
      if (!Number.isNaN(n)) return n
      const t = Date.parse(v)
      return Number.isNaN(t) ? undefined : t
    })()

    const res = await listAppChatHistory({
      appId: appId.value,
      pageSize: historyPageSize,
      lastCreateTime: (lastCreateTimeParam === undefined ? undefined : String(lastCreateTimeParam)),
    })
    if (res.data.code === 0 && res.data.data) {
      const records = res.data.data.records || []
      // 将后端记录映射到前端消息结构
      type ChatRecord = {
        id?: string | number
        createTime?: string | number | Date
        messageType?: string
        message?: string
      }
      const mapped = records.map((r: ChatRecord) => ({
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
      showError('加载对话历史失败')
    }
  } catch (error) {
    console.error('加载对话历史失败:', error)
    showError('加载对话历史失败')
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

  let messageContent = userInput.value.trim()

  // 如果有选中的元素，将元素信息添加到提示词中
  if (selectedElement.value) {
    messageContent += getSelectedElementPrompt()
  }

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
  ensureCodeDetailsOpen()
  scrollToBottom()

  // 清除选中元素并退出编辑模式
  if (selectedElement.value) {
    clearSelectedElement()
    if (visualEditor.value) {
      visualEditor.value.disableEditMode()
      isEditMode.value = false
    }
  }

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
      const url = String(res.data.data)
      try {
        await navigator.clipboard.writeText(url)
        showSuccess(`部署成功：${url}（已复制）`)
      } catch {
        showSuccess(`部署成功：${url}`)
      }
    } else {
      showError(res.data.message || '部署失败')
    }
  } catch (error) {
    console.error('部署失败:', error)
    showError('部署失败')
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
      showSuccess('应用名称更新成功')
    } else {
      showError(res.data.message || '更新失败')
    }
  } catch (error) {
    console.error('更新应用名称失败:', error)
    showError('更新失败')
  } finally {
    saving.value = false
  }
}

// 下载应用代码
const handleDownloadAppCode = async () => {
  if (!appInfo.value?.id) return

  try {
    // 直接使用fetch下载ZIP文件，避免axios的响应拦截器处理
    const response = await fetch(`${API_BASE_URL}/app/download/${appInfo.value.id}`, {
      method: 'GET',
      credentials: 'include',
    })

    if (response.ok) {
      const blob = await response.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${appInfo.value.appName || '应用'}.zip`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
      showSuccess('应用代码下载成功')
    } else {
      showError('下载失败')
    }
  } catch (error) {
    console.error('下载应用代码失败:', error)
    showError('下载失败')
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
const formatTime = (time: string | Date | undefined) => {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleString('zh-CN')
}

// 可视化编辑相关
const visualEditor = ref<VisualEditor | null>(null)
const isEditMode = ref(false)
const selectedElement = ref<ElementInfo | null>(null)
const iframeRef = ref<HTMLIFrameElement | null>(null)

// 初始化可视化编辑器
const initVisualEditor = () => {
  if (!iframeRef.value) {
    console.warn('AppChatPage: iframe ref not found')
    return
  }

  console.log('AppChatPage: initializing visual editor')

  visualEditor.value = new VisualEditor({
    onElementSelected: (elementInfo: ElementInfo) => {
      console.log('AppChatPage: element selected:', elementInfo)
      selectedElement.value = elementInfo
    },
    onElementHover: () => {
      // 可以在这里处理悬浮效果
    }
  })

  visualEditor.value.init(iframeRef.value)

  // 监听iframe消息
  window.addEventListener('message', handleIframeMessage)

  console.log('AppChatPage: visual editor initialized')
}

// 处理iframe消息
const handleIframeMessage = (event: MessageEvent) => {
  if (visualEditor.value) {
    visualEditor.value.handleIframeMessage(event)
  }
}

// 切换编辑模式
const toggleEditMode = () => {
  if (!visualEditor.value) return

  isEditMode.value = visualEditor.value.toggleEditMode()

  if (!isEditMode.value) {
    // 退出编辑模式时清除选中元素
    selectedElement.value = null
  }
}

// 清除选中元素
const clearSelectedElement = () => {
  selectedElement.value = null
  if (visualEditor.value) {
    visualEditor.value.clearSelection()
  }
}

// 获取选中元素的提示词
const getSelectedElementPrompt = () => {
  if (!selectedElement.value) return ''

  const element = selectedElement.value
  return `\n\n选中的元素信息：
- 标签：${element.tagName}
- 选择器：${element.selector}
- 文本内容：${element.textContent}
- 页面路径：${element.pagePath}
- 位置：(${element.rect.left}, ${element.rect.top})
- 尺寸：${element.rect.width} x ${element.rect.height}`
}

onMounted(async () => {
  appId.value = route.params.id as string
  if (!appId.value) {
    showError('应用ID无效')
    router.push('/')
    return
  }

  showWorkMode.value = route.query.showWork === '1'
  await loadAppInfo()
  await loadChatHistory(true)
  await tryAutoSendInitPrompt()

  // 添加窗口大小变化监听
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  // 清理可能的SSE连接
  // 清理iframe消息监听
  window.removeEventListener('message', handleIframeMessage)
  // 清理窗口大小变化监听
  window.removeEventListener('resize', handleResize)
  // 清理拖拽事件监听
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)
})
</script>

<template>
  <div class="app-chat-page">
    <!-- 应用信息栏 -->
    <div class="app-info-bar">
      <div class="app-info-left">
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

        <!-- 生成类型标签 -->
        <a-tag
          v-if="appInfo?.codeGenType"
          :color="getCodeGenTypeConfig(appInfo.codeGenType).color"
          class="gen-type-tag"
        >
          <template #icon>
            <component :is="getCodeGenTypeConfig(appInfo.codeGenType).icon" />
          </template>
          {{ getCodeGenTypeConfig(appInfo.codeGenType).label }}
        </a-tag>

        <a-input
          v-if="isEditing"
          v-model:value="editAppName"
          size="small"
          style="width: 200px; margin-left: 8px;"
          @press-enter="saveAppName"
          @blur="saveAppName"
        />
      </div>

      <div class="app-info-right">
        <a-button
          @click="showAppDetail"
          style="margin-right: 8px;"
        >
          <info-circle-outlined />
          应用详情
        </a-button>
        <a-button
          @click="handleDownloadAppCode"
          style="margin-right: 8px;"
          :disabled="!canEdit"
        >
          <download-outlined />
          下载代码
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
          <!-- 选中元素信息显示 -->
          <div v-if="selectedElement" class="selected-element-info">
            <Alert
              message="已选中元素"
              :description="`${selectedElement.tagName} - ${selectedElement.textContent || '无文本内容'}`"
              type="info"
              show-icon
              closable
              @close="clearSelectedElement"
            />
          </div>

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
            <!-- 编辑模式按钮 -->
            <a-button
              v-if="showWebsite && canEdit"
              :type="isEditMode ? 'primary' : 'default'"
              shape="circle"
              size="small"
              :disabled="isGenerating"
              @click="toggleEditMode"
              style="margin-right: 8px;"
              :title="isEditMode ? '退出编辑模式' : '进入编辑模式'"
            >
              <EditOutlined />
            </a-button>

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
        :title="isDragging ? '拖拽调整大小...' : '拖拽调整左右区域大小'"
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
              :src="getStaticPreviewUrl(String(appInfo.codeGenType), String(appInfo.id))"
              class="website-preview"
              frameborder="0"
            ></iframe>
          </div>
          <iframe
            v-else-if="showWebsite"
            ref="iframeRef"
            :src="websiteUrl"
            class="website-preview"
            frameborder="0"
            @load="() => {
              console.log('AppChatPage: iframe loaded, initializing editor')

              // 调试iframe同源问题
              if (iframeRef) {
                DebugHelper.debugIframe(iframeRef)
              }

              if (visualEditor) {
                visualEditor.onIframeLoad()
              } else {
                initVisualEditor()
              }
            }"
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
          <div class="detail-item">
            <label>生成类型：</label>
            <div v-if="appInfo?.codeGenType" class="gen-type-info">
              <a-tag
                :color="getCodeGenTypeConfig(appInfo.codeGenType).color"
                size="small"
              >
                <template #icon>
                  <component :is="getCodeGenTypeConfig(appInfo.codeGenType).icon" />
                </template>
                {{ getCodeGenTypeConfig(appInfo.codeGenType).label }}
              </a-tag>
            </div>
            <span v-else>-</span>
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
  height: calc(100vh - 64px); /* 仅减去 header，高度覆盖剩余区域；页脚需滚动才可见 */
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
  width: 100vw;
  position: relative;
  overflow: hidden;
}

.app-info-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background: white;
  border-bottom: 1px solid #e8e8e8;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  flex-shrink: 0;
}

.app-info-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.app-name-btn {
  font-weight: 500;
  font-size: 16px;
}

.gen-type-tag {
  margin-left: 8px;
  font-size: 12px;
  height: 24px;
  line-height: 22px;
  padding: 0 8px;
}

.app-info-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.main-content {
  flex: 1;
  display: flex;
  overflow: hidden;
  width: 100%;
  position: relative;
  min-height: 0; /* 确保 flex 子元素能够正确收缩 */
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
  padding: 10px 14px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.load-more-bar {
  display: flex;
  justify-content: center;
  margin-bottom: 8px;
}

.message {
  margin-bottom: 6px;
  display: flex;
  align-items: flex-start;
  gap: 6px;
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
  padding: 6px 10px;
  border-radius: 10px;
  position: relative;
  word-wrap: break-word;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
  transition: all 0.2s ease;
}

.user-message .message-content {
  background: linear-gradient(135deg, #1890ff 0%, #096dd9 100%);
  color: white;
  margin-left: auto;
}

.ai-message .message-content {
  background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
  color: #333;
  border: 1px solid #e1e4e8;
}

.user-message .message-content:hover {
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.3);
  transform: translateY(-1px);
}

.ai-message .message-content:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  transform: translateY(-1px);
}

.message-text {
  line-height: 1.16; /* 更紧凑 */
  word-wrap: break-word;
  white-space: pre-wrap;
}

/* 全局重置markdown元素间距 - 完全紧贴 */
.message-text > * {
  margin: 0 !important;
}

/* 段落和代码块之间完全无间距 */
.message-text > .md-paragraph + .collapsible-code-block {
  margin-top: 0 !important;
}

.message-text > .collapsible-code-block + .md-paragraph {
  margin-top: 0 !important;
}

/* 代码块之间完全无间距 */
.message-text > .collapsible-code-block + .collapsible-code-block {
  margin-top: 0 !important;
}

/* 确保所有元素都紧贴 */
.message-text .collapsible-code-block {
  margin: 0 !important;
  display: block;
}

/* 优化markdown渲染样式 - 紧凑布局 */
.message-text {
  font-size: 14px;
}

/* 标题样式 - 紧凑间距 */
.message-text .md-heading {
  margin: 4px 0 2px 0 !important;
  color: #24292e;
  font-weight: 600;
  line-height: 1.28;
}

.message-text .md-h1 { font-size: 18px; }
.message-text .md-h2 { font-size: 16px; }
.message-text .md-h3 { font-size: 15px; }
.message-text .md-h4 { font-size: 14px; }
.message-text .md-h5 { font-size: 13px; }
.message-text .md-h6 { font-size: 12px; }

/* 段落样式 - 完全无间距 */
.message-text .md-paragraph {
  margin: 0 !important;
  line-height: 1.12; /* 段落再收紧 */
  padding: 0;
}

/* 列表样式 - 紧凑 */
.message-text ul,
.message-text ol {
  margin: 0 !important;
  padding-left: 12px; /* 列表再缩进 */
}

.message-text li {
  margin: 0;
  line-height: 1.06; /* 列表项更紧凑 */
}

.message-text li p {
  margin: 0 !important;
  display: inline;
}

/* 引用块样式 */
.message-text blockquote {
  margin: 2px 0 !important;
  padding: 6px 10px;
  background: rgba(0, 0, 0, 0.03);
  border-left: 3px solid #0366d6;
  border-radius: 0 3px 3px 0;
}

.message-text blockquote p {
  margin: 0 !important;
}

/* 表格样式 */
.message-text table {
  border-collapse: collapse;
  margin: 6px 0 !important;
  width: 100%;
  font-size: 13px;
}

.message-text th,
.message-text td {
  border: 1px solid #e1e4e8;
  padding: 4px 6px;
  text-align: left;
}

.message-text th {
  background: #f6f8fa;
  font-weight: 600;
}

/* 行内代码样式 */
.message-text code:not(.language-html):not(.language-css):not(.language-js):not(.language-javascript):not(.language-python):not(.language-java):not(.language-cpp):not(.language-c) {
  background: #f6f8fa;
  border: 1px solid #e1e4e8;
  border-radius: 3px;
  padding: 1px 3px;
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', monospace;
  font-size: 12px;
  color: #e36209;
}

/* 水平分割线 */
.message-text hr {
  margin: 4px 0 !important;
  border: none;
  border-top: 1px solid #e1e4e8;
}

/* 强调文本 */
.message-text strong {
  font-weight: 600;
  color: #24292e;
}

.message-text em {
  font-style: italic;
  color: #586069;
}

/* 用户消息中的markdown样式 */
.user-message .message-text .md-heading {
  color: rgba(255, 255, 255, 0.95);
}

.user-message .message-text code:not(.language-html):not(.language-css):not(.language-js):not(.language-javascript):not(.language-python):not(.language-java):not(.language-cpp):not(.language-c) {
  background: rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 255, 255, 0.25);
  color: rgba(255, 255, 255, 0.95);
}

.user-message .message-text blockquote {
  background: rgba(255, 255, 255, 0.1);
  border-left-color: rgba(255, 255, 255, 0.6);
}

.user-message .message-text strong {
  color: white;
}

.user-message .message-text em {
  color: rgba(255, 255, 255, 0.8);
}

.message-time {
  font-size: 11px;
  color: #999;
  margin-top: 2px;
  opacity: 0.8;
}

.input-section {
  padding: 12px 16px;
  border-top: 1px solid #e8e8e8;
  background: #fafafa;
  flex-shrink: 0;
}

.selected-element-info {
  margin-bottom: 12px;
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
  min-width: 0; /* 确保能够正确收缩 */
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
  width: 8px;
  cursor: col-resize;
  background-color: #e8e8e8;
  border-radius: 4px;
  transition: all 0.2s ease;
  flex-shrink: 0;
  position: relative;
  z-index: 10;
  user-select: none;
  display: flex;
  align-items: center;
  justify-content: center;
}

.resize-handle:hover {
  background-color: #d0d0d0;
  width: 10px;
}

.resize-handle.dragging {
  background-color: #1890ff;
  width: 10px;
  box-shadow: 0 0 8px rgba(24, 144, 255, 0.3);
}

.resize-indicator {
  width: 3px;
  height: 60px;
  background-color: #999;
  border-radius: 2px;
  opacity: 0;
  transition: opacity 0.2s ease;
  position: relative;
}

.resize-indicator::before,
.resize-indicator::after {
  content: '';
  position: absolute;
  width: 3px;
  height: 3px;
  background-color: #999;
  border-radius: 50%;
  left: 0;
}

.resize-indicator::before {
  top: -6px;
}

.resize-indicator::after {
  bottom: -6px;
}

.resize-handle:hover .resize-indicator {
  opacity: 0.6;
  background-color: #666;
}

.resize-handle:hover .resize-indicator::before,
.resize-handle:hover .resize-indicator::after {
  background-color: #666;
}

.resize-handle.dragging .resize-indicator {
  opacity: 1;
  background-color: white;
}

.resize-handle.dragging .resize-indicator::before,
.resize-handle.dragging .resize-indicator::after {
  background-color: white;
}

/* 拖拽时禁用文本选择 */
.main-content.dragging {
  user-select: none;
  cursor: col-resize;
}

.main-content.dragging * {
  user-select: none;
  pointer-events: none;
}

.main-content.dragging .resize-handle {
  pointer-events: auto;
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

.gen-type-info {
  display: flex;
  align-items: center;
}

.action-buttons {
  display: flex;
  gap: 12px;
}

.action-buttons .ant-btn {
  flex: 1;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .chat-section {
    min-width: 280px;
  }

  /* 调整最小宽度以适应较小屏幕 */
  .resize-handle {
    width: 6px;
  }

  .resize-handle:hover,
  .resize-handle.dragging {
    width: 8px;
  }
}

@media (max-width: 768px) {
  .app-chat-page {
    height: calc(100vh - 64px);
    width: 100vw;
  }

  .app-info-bar {
    padding: 8px 16px;
  }

  .app-name-btn {
    font-size: 14px;
  }

  .gen-type-tag {
    font-size: 11px;
    height: 22px;
    line-height: 20px;
    padding: 0 6px;
  }

  .chat-section {
    min-width: 250px;
  }
}

@media (max-width: 576px) {
  .app-chat-page {
    height: calc(100vh - 64px);
    width: 100vw;
  }

  .app-info-bar {
    padding: 8px 12px;
    flex-direction: column;
    gap: 8px;
    align-items: flex-start;
  }

  .app-info-right {
    width: 100%;
    justify-content: flex-end;
  }

  .main-content {
    flex-direction: column;
  }

  .chat-section {
    width: 100% !important;
    height: 50%;
    border-right: none;
    border-bottom: 1px solid #e8e8e8;
  }

  .resize-handle {
    display: none;
  }

  .preview-section {
    height: 50%;
  }
}
</style>
