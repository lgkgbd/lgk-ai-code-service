<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import { capture } from '@/api/wordController'
import WordOcrCaptureModal from './WordOcrCaptureModal.vue'

const emit = defineEmits<{
  // 录入成功，把新卡片交给父组件做乐观插入
  (e: 'captured', cards: API.WordCardVO[]): void
}>()

const inputText = ref('')
const submitting = ref(false)
const rootRef = ref<HTMLElement>()
const inputRef = ref<HTMLInputElement>()
const ocrOpen = ref(false)

// 拍照录入走的是同一条 capture 接口，卡片照样交给父组件乐观插入
function onOcrCaptured(cards: API.WordCardVO[]) {
  emit('captured', cards)
}

async function submit(text?: string) {
  const raw = (text ?? inputText.value).trim()
  if (!raw) return
  if (submitting.value) return
  submitting.value = true
  try {
    const res = await capture({ text: raw, channel: 'paste' })
    if (res.data.code === 0 && res.data.data) {
      const cards = res.data.data
      inputText.value = ''
      // 乐观 UI：交给父组件立刻插到列表顶部
      emit('captured', cards)
      notify(cards)
    } else {
      message.error(res.data.message || '录入失败')
    }
  } catch (e) {
    message.error('录入失败，请稍后再试')
  } finally {
    submitting.value = false
    // 录入后把光标交回输入框，方便连续录入（无需再用鼠标点一下）
    await nextTick()
    inputRef.value?.focus()
  }
}

function notify(cards: API.WordCardVO[]) {
  const added = cards.filter((c) => c.newlyAdded)
  const seen = cards.filter((c) => !c.newlyAdded)
  // 已存在的词：逐个提示「第 N 次遇见」
  seen.forEach((c) => {
    message.info(`⚡ ${c.spelling} 第 ${c.encounterNum} 次遇见`)
  })
  if (added.length === 1 && seen.length === 0) {
    message.success(`已记录 ${added[0].spelling}`)
  } else if (added.length > 1) {
    message.success(`已记录 ${added.length} 个新词`)
  }
}

/**
 * 页面级 paste：进页面直接 Ctrl+V 即录入，无需先点输入框。
 * 焦点在其它 input/textarea 里时不抢（只在焦点为空或就在本组件内时接管）。
 */
function onPaste(e: ClipboardEvent) {
  // 拍照录入弹窗开着时不抢粘贴，免得在背后偷偷录了一条
  if (ocrOpen.value) return
  const el = document.activeElement as HTMLElement | null
  const editable =
    !!el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.isContentEditable)
  if (editable && !rootRef.value?.contains(el)) {
    return
  }
  const text = e.clipboardData?.getData('text') ?? ''
  if (!text.trim()) return
  e.preventDefault()
  inputText.value = text
  submit(text)
}

onMounted(() => {
  document.addEventListener('paste', onPaste)
})
onBeforeUnmount(() => {
  // 组件卸载务必移除，避免离开 /word 后仍在监听
  document.removeEventListener('paste', onPaste)
})
</script>

<template>
  <div ref="rootRef" class="capture">
    <input
      ref="inputRef"
      v-model="inputText"
      class="capture-input"
      placeholder="粘贴或输入单词，回车即存"
      :disabled="submitting"
      @keydown.enter.prevent="submit()"
    />
    <div class="capture-foot">
      <div class="hints">
        <span class="hint hint-kbd">进页面 <kbd>Ctrl</kbd>+<kbd>V</kbd> 直接录入，不用点输入框</span>
        <span class="hint">逗号 / 换行 / 空格分隔可一次多个</span>
        <span class="hint">粘一整句也行，自动挑生词并留住原句</span>
      </div>
      <div class="foot-btns">
        <a-button class="ocr-btn" @click="ocrOpen = true">📷 拍照录入</a-button>
        <a-button type="primary" :loading="submitting" @click="submit()">录入</a-button>
      </div>
    </div>

    <WordOcrCaptureModal v-model:open="ocrOpen" @captured="onOcrCaptured" />
  </div>
</template>

<style scoped>
.capture {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(24, 144, 255, 0.1);
  border: 2px solid #1890ff;
  padding: 22px 24px;
  margin-bottom: 16px;
}
.capture-input {
  width: 100%;
  border: none;
  outline: none;
  font-size: 20px;
  font-family: inherit;
  color: #262626;
  background: transparent;
}
.capture-input::placeholder {
  color: #bfbfbf;
  font-size: 18px;
}
.capture-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed #f0f0f0;
  flex-wrap: wrap;
  gap: 10px;
}
.hints {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.hint {
  font-size: 12px;
  color: #8c8c8c;
  background: #fafafa;
  padding: 5px 10px;
  border-radius: 4px;
  border: 1px solid #f0f0f0;
}
.hint kbd {
  background: #fff;
  border: 1px solid #d9d9d9;
  border-bottom-width: 2px;
  border-radius: 3px;
  padding: 1px 5px;
  font-size: 11px;
  font-family: inherit;
}
.foot-btns {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ocr-btn {
  color: #1890ff;
  border-color: #91caff;
  background: #e6f4ff;
}
.ocr-btn:hover {
  color: #096dd9;
  border-color: #1890ff;
}
@media (max-width: 576px) {
  .capture {
    padding: 16px;
  }
  .capture-input {
    font-size: 17px;
  }
  .capture-input::placeholder {
    font-size: 16px;
  }
  /* 手机上没有 Ctrl+V 这回事，把位置让给按钮 */
  .hint-kbd {
    display: none;
  }
  .foot-btns {
    width: 100%;
  }
  .foot-btns :deep(.ant-btn) {
    flex: 1;
    height: 40px;
  }
}
</style>
