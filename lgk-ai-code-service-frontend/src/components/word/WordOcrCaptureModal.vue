<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { capture, captureFromImage, getImageCaptureResult } from '@/api/wordController'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', open: boolean): void
  // 录入成功，把新卡片交给父组件做乐观插入
  (e: 'captured', cards: API.WordCardVO[]): void
}>()

// 与后端 WordConstant 保持一致，前端先拦一道，少一次白跑的上传
const MAX_IMAGES = 5
const MAX_BYTES = 10 * 1024 * 1024
const ALLOWED_SUFFIX = ['jpg', 'jpeg', 'png', 'webp', 'heic', 'bmp']
const MAX_WORDS = 50
const MAX_SPELLING_LEN = 128
const MAX_TRANSLATION_LEN = 256
// VL 单张 2~5 秒，1 秒一轮足够跟手；90 秒还没出结果就当它挂了
const POLL_INTERVAL = 1000
const POLL_TIMEOUT = 90 * 1000

type Step = 'pick' | 'uploading' | 'running' | 'review' | 'failed'

interface Picked {
  file: File
  url: string
  broken: boolean
}

interface EditRow {
  key: number
  word: string
  translation: string
  checked: boolean
}

const step = ref<Step>('pick')
const picked = ref<Picked[]>([])
const rows = ref<EditRow[]>([])
const task = ref<API.WordOcrTaskVO | null>(null)
const taskId = ref('')
const errorMsg = ref('')
const submitting = ref(false)
// 录入成功后要清空，但得等关窗动画结束
const pendingReset = ref(false)

const cameraInput = ref<HTMLInputElement>()
const albumInput = ref<HTMLInputElement>()

let rowKey = 0
let timer: ReturnType<typeof setTimeout> | null = null
let deadline = 0

// capture="environment" 在部分浏览器会锁死成「只能拍照」，所以桌面端只给一个「选择图片」，
// 移动端才额外给「拍照」直通后置摄像头
const isMobile = /Android|iPhone|iPad|iPod|HarmonyOS|Mobile/i.test(navigator.userAgent)

const busy = computed(() => step.value === 'uploading' || step.value === 'running')
const totalImages = computed(() => task.value?.totalImages ?? picked.value.length)
const doneImages = computed(() => task.value?.doneImages ?? 0)
const percent = computed(() =>
  totalImages.value ? Math.round((doneImages.value / totalImages.value) * 100) : 0
)
const progressText = computed(() => {
  if (step.value === 'uploading') return '上传中…'
  if (task.value?.status === 'pending') return '排队中…'
  return `识别中 ${doneImages.value}/${totalImages.value}`
})

const selectedRows = computed(() => rows.value.filter((r) => r.checked && r.word.trim()))
const selectedCount = computed(() => selectedRows.value.length)
const allChecked = computed({
  get: () => rows.value.length > 0 && rows.value.every((r) => r.checked),
  set: (v: boolean) => rows.value.forEach((r) => (r.checked = v)),
})
const indeterminate = computed(
  () => rows.value.some((r) => r.checked) && !rows.value.every((r) => r.checked)
)

/* ---------------- 选图 ---------------- */

function pickFromCamera() {
  cameraInput.value?.click()
}
function pickFromAlbum() {
  albumInput.value?.click()
}

function onFileChange(e: Event) {
  const el = e.target as HTMLInputElement
  addFiles(el.files)
  // 清空 value，否则连续选同一张不会再触发 change
  el.value = ''
}

function checkFile(file: File): boolean {
  const name = file.name || '该文件'
  const dot = (file.name || '').lastIndexOf('.')
  const suffix = dot >= 0 ? file.name.slice(dot + 1).toLowerCase() : ''
  if (!ALLOWED_SUFFIX.includes(suffix)) {
    message.warning(`${name} 格式不支持，仅支持 ${ALLOWED_SUFFIX.join('/')}`)
    return false
  }
  if (file.size > MAX_BYTES) {
    message.warning(`${name} 超过 ${MAX_BYTES / 1024 / 1024}MB，换一张小点的`)
    return false
  }
  return true
}

function addFiles(list: FileList | null) {
  if (!list || !list.length) return
  const incoming = Array.from(list)
  const room = MAX_IMAGES - picked.value.length
  if (room <= 0) {
    message.warning(`一次最多 ${MAX_IMAGES} 张图片`)
    return
  }
  if (incoming.length > room) {
    message.warning(`一次最多 ${MAX_IMAGES} 张，多出来的已忽略`)
  }
  const accepted = incoming
    .slice(0, room)
    .filter(checkFile)
    .map((file) => ({ file, url: URL.createObjectURL(file), broken: false }))
  if (accepted.length) {
    picked.value = [...picked.value, ...accepted]
  }
}

function removeAt(idx: number) {
  const [gone] = picked.value.splice(idx, 1)
  if (gone) URL.revokeObjectURL(gone.url)
}

/* ---------------- 拖拽选图（桌面端） ---------------- */

const dragging = ref(false)
// dragenter/dragleave 在划过子元素时也会触发，用计数抵消，否则高亮会疯狂闪烁
let dragDepth = 0

/**
 * 拖进来的是文件才接管。拖一段文字、一个链接进来时不该抢事件
 */
function isFileDrag(e: DragEvent): boolean {
  const types = e.dataTransfer?.types
  if (!types) return false
  return Array.prototype.indexOf.call(types, 'Files') !== -1
}

function onDragEnter(e: DragEvent) {
  if (step.value !== 'pick' || !isFileDrag(e)) return
  e.preventDefault()
  dragDepth++
  dragging.value = true
}

function onDragOver(e: DragEvent) {
  if (step.value !== 'pick' || !isFileDrag(e)) return
  // 不 preventDefault 的话浏览器会走默认行为（直接打开图片），drop 根本不触发
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'copy'
}

function onDragLeave(e: DragEvent) {
  if (step.value !== 'pick') return
  e.preventDefault()
  dragDepth = Math.max(0, dragDepth - 1)
  if (dragDepth === 0) dragging.value = false
}

function onDrop(e: DragEvent) {
  if (step.value !== 'pick') return
  e.preventDefault()
  dragDepth = 0
  dragging.value = false
  addFiles(e.dataTransfer?.files ?? null)
}

/**
 * 弹窗开着时，吞掉落在拖放区之外的文件拖放。
 * <p>
 * 不拦的话，用户手一抖没对准，浏览器会按默认行为直接打开那张图片——
 * 当前页面被顶掉，已选的图和识别结果全没了。这个代价比"没拖准"大得多。
 */
function swallowStrayDrop(e: DragEvent) {
  if (!isFileDrag(e)) return
  e.preventDefault()
  if (e.type === 'drop') {
    dragDepth = 0
    dragging.value = false
  }
}

function bindStrayDropGuard() {
  window.addEventListener('dragover', swallowStrayDrop)
  window.addEventListener('drop', swallowStrayDrop)
}

function unbindStrayDropGuard() {
  window.removeEventListener('dragover', swallowStrayDrop)
  window.removeEventListener('drop', swallowStrayDrop)
}

/* ---------------- 识别 ---------------- */

function stopPolling() {
  if (timer) {
    clearTimeout(timer)
    timer = null
  }
}

function fail(msg: string) {
  stopPolling()
  errorMsg.value = msg
  step.value = 'failed'
}

async function startRecognize() {
  if (!picked.value.length) {
    message.warning('先拍一张或选一张图片')
    return
  }
  stopPolling()
  step.value = 'uploading'
  errorMsg.value = ''
  task.value = null
  taskId.value = ''
  try {
    const res = await captureFromImage(
      picked.value.map((p) => p.file),
      // 手机流量上传大图慢，给宽一点的超时
      { timeout: 120000 }
    )
    if (res.data.code === 0 && res.data.data) {
      taskId.value = res.data.data
      step.value = 'running'
      deadline = Date.now() + POLL_TIMEOUT
      schedulePoll(taskId.value)
    } else {
      fail(res.data.message || '上传失败')
    }
  } catch {
    fail('上传失败，检查一下网络再试')
  }
}

function schedulePoll(id: string) {
  stopPolling()
  timer = setTimeout(() => pollOnce(id), POLL_INTERVAL)
}

async function pollOnce(id: string) {
  // 任务已被取消 / 被新任务顶替，直接收手
  if (id !== taskId.value) return
  try {
    const res = await getImageCaptureResult({ taskId: id })
    if (id !== taskId.value) return
    if (res.data.code !== 0 || !res.data.data) {
      fail(res.data.message || '识别失败')
      return
    }
    const t = res.data.data
    task.value = t
    if (t.status === 'succeed') {
      onSucceed(t)
      return
    }
    if (t.status === 'failed') {
      fail(t.errorMsg || '识别失败')
      return
    }
  } catch {
    // 单次网络抖动不判死刑，继续轮询到超时为止
  }
  if (id !== taskId.value) return
  if (Date.now() > deadline) {
    fail('识别超时了，请重试')
    return
  }
  schedulePoll(id)
}

function onSucceed(t: API.WordOcrTaskVO) {
  stopPolling()
  rows.value = (t.items ?? []).map((it) => ({
    key: ++rowKey,
    word: (it.word ?? '').trim(),
    translation: (it.translation ?? '').trim(),
    checked: true,
  }))
  step.value = 'review'
  if (!rows.value.length) {
    message.warning('没识别到单词，换个角度或光线再拍一张试试')
  }
}

function retry() {
  if (!picked.value.length) {
    step.value = 'pick'
    return
  }
  startRecognize()
}

/* ---------------- 确认候选 ---------------- */

function addRow() {
  rows.value.push({ key: ++rowKey, word: '', translation: '', checked: true })
}

function removeRow(idx: number) {
  rows.value.splice(idx, 1)
}

function backToPick() {
  stopPolling()
  taskId.value = ''
  task.value = null
  rows.value = []
  errorMsg.value = ''
  step.value = 'pick'
}

async function confirmCapture() {
  const chosen = selectedRows.value
  if (!chosen.length) {
    message.warning('至少勾一个单词')
    return
  }
  if (chosen.length > MAX_WORDS) {
    message.warning(`一次最多录入 ${MAX_WORDS} 个词，先取消一些`)
    return
  }
  if (submitting.value) return
  submitting.value = true
  try {
    // 手写释义只会进「用户私有的笔记」，词典已有释义时后端会以词典为准
    const translations: Record<string, string> = {}
    chosen.forEach((r) => {
      const t = r.translation.trim()
      if (t) translations[r.word.trim()] = t
    })
    const res = await capture({
      text: chosen.map((r) => r.word.trim()).join(' '),
      channel: 'ocr',
      translations,
      imageUrl: task.value?.imageUrls?.[0],
    })
    if (res.data.code === 0 && res.data.data) {
      const cards = res.data.data
      notify(cards)
      emit('captured', cards)
      // 关窗动画走完再清状态，否则会闪一下「选图」那一屏
      pendingReset.value = true
      close()
    } else {
      message.error(res.data.message || '录入失败')
    }
  } catch {
    message.error('录入失败，请稍后再试')
  } finally {
    submitting.value = false
  }
}

function notify(cards: API.WordCardVO[]) {
  if (!cards.length) {
    message.warning('这次没有可录入的词')
    return
  }
  const added = cards.filter((c) => c.newlyAdded).length
  const seen = cards.length - added
  if (added && seen) {
    message.success(`已记录 ${added} 个新词，${seen} 个是又遇见的`)
  } else if (added) {
    message.success(`已记录 ${added} 个新词`)
  } else {
    message.info(`${seen} 个词都是又遇见，已记一次`)
  }
}

/* ---------------- 生命周期 ---------------- */

function resetAll() {
  stopPolling()
  picked.value.forEach((p) => URL.revokeObjectURL(p.url))
  picked.value = []
  rows.value = []
  task.value = null
  taskId.value = ''
  errorMsg.value = ''
  submitting.value = false
  step.value = 'pick'
}

function close() {
  emit('update:open', false)
}

function onCancel() {
  close()
}

// 关窗动画结束才真正清空；只是点「取消」的话保留现场，重开还能接着看
function onAfterClose() {
  if (pendingReset.value) {
    pendingReset.value = false
    resetAll()
  }
}

watch(
  () => props.open,
  (v) => {
    if (!v) {
      // 关弹窗必须停轮询，否则后台一直打接口
      stopPolling()
      unbindStrayDropGuard()
      dragDepth = 0
      dragging.value = false
      return
    }
    bindStrayDropGuard()
    // 上次没看完结果就关掉了，重开时接着轮
    if (taskId.value && step.value === 'running') {
      deadline = Date.now() + POLL_TIMEOUT
      schedulePoll(taskId.value)
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  stopPolling()
  unbindStrayDropGuard()
  picked.value.forEach((p) => URL.revokeObjectURL(p.url))
})
</script>

<template>
  <a-modal
    :open="props.open"
    title="📷 拍照录入"
    :width="640"
    :footer="null"
    :mask-closable="false"
    :keyboard="!busy"
    wrap-class-name="word-ocr-modal"
    :after-close="onAfterClose"
    @update:open="(v: boolean) => emit('update:open', v)"
    @cancel="onCancel"
  >
    <!-- 隐藏的两个 file input：一个直通后置摄像头，一个走系统相册/文件 -->
    <input
      ref="cameraInput"
      type="file"
      accept="image/*"
      capture="environment"
      class="hidden-file"
      @change="onFileChange"
    />
    <input
      ref="albumInput"
      type="file"
      accept="image/*"
      multiple
      class="hidden-file"
      @change="onFileChange"
    />

    <div class="ocr">
      <!-- ① 选图 -->
      <template v-if="step === 'pick'">
        <div class="tip">
          把便利贴、笔记本、书页拍下来，最多 {{ MAX_IMAGES }} 张。字写清楚点识别更准。
        </div>

        <!-- 整块都是拖放区：目标越大越好拖，不用精准对准某个按钮 -->
        <div
          class="dropzone"
          :class="{ 'is-over': dragging }"
          @dragenter="onDragEnter"
          @dragover="onDragOver"
          @dragleave="onDragLeave"
          @drop="onDrop"
        >
          <div class="pick-btns">
            <button v-if="isMobile" type="button" class="pick-btn primary" @click="pickFromCamera">
              <span class="ico">📷</span><span>拍照</span>
            </button>
            <button type="button" class="pick-btn" @click="pickFromAlbum">
              <span class="ico">🖼️</span><span>{{ isMobile ? '从相册选' : '选择图片' }}</span>
            </button>
          </div>

          <div v-if="picked.length" class="thumbs">
            <div v-for="(p, i) in picked" :key="p.url" class="thumb">
              <img v-if="!p.broken" :src="p.url" alt="预览" @error="p.broken = true" />
              <div v-else class="thumb-fallback">{{ p.file.name }}</div>
              <button type="button" class="thumb-del" title="移除" @click="removeAt(i)">✕</button>
            </div>
          </div>
          <a-empty
            v-else
            class="pick-empty"
            :description="isMobile ? '还没有选图片' : '还没有选图片，也可以直接把图拖进来'"
          />

          <!-- 拖拽悬停时盖上来的提示层 -->
          <div v-show="dragging" class="drop-hint">
            <div class="drop-hint-inner">
              <div class="drop-ico">📥</div>
              <div class="drop-text">松手就添加</div>
            </div>
          </div>
        </div>

        <div class="ocr-foot">
          <a-button @click="onCancel">取消</a-button>
          <a-button type="primary" :disabled="!picked.length" @click="startRecognize">
            开始识别{{ picked.length ? ` ${picked.length} 张` : '' }}
          </a-button>
        </div>
      </template>

      <!-- ② 上传 / 识别中 -->
      <template v-else-if="busy">
        <div class="running">
          <a-spin size="large" />
          <div class="running-text">{{ progressText }}</div>
          <a-progress
            class="running-bar"
            :percent="step === 'uploading' ? 0 : percent"
            :show-info="false"
            stroke-color="#1890ff"
          />
          <div class="running-sub">手写识别要几秒，先别关这个窗口</div>
        </div>

        <div class="ocr-foot">
          <a-button @click="backToPick">重新选图</a-button>
          <a-button type="primary" disabled>识别中…</a-button>
        </div>
      </template>

      <!-- ③ 失败 -->
      <template v-else-if="step === 'failed'">
        <div class="failed">
          <div class="failed-ico">😵</div>
          <div class="failed-msg">{{ errorMsg || '识别失败' }}</div>
          <div class="failed-sub">可以换张清楚点的图，或者稍后再试</div>
        </div>

        <div class="ocr-foot">
          <a-button @click="backToPick">重新选图</a-button>
          <a-button type="primary" @click="retry">重试</a-button>
        </div>
      </template>

      <!-- ④ 确认候选词 -->
      <template v-else>
        <div class="rv-bar">
          <a-checkbox v-model:checked="allChecked" :indeterminate="indeterminate">全选</a-checkbox>
          <span class="rv-count">已选 {{ selectedCount }}/{{ rows.length }}</span>
          <span class="rv-actions">
            <a @click="addRow">+ 加一行</a>
            <a @click="backToPick">重新拍</a>
          </span>
        </div>
        <div class="rv-tip">识别不可能全对，错的直接改，不要的取消勾选或删掉</div>

        <div v-if="rows.length" class="rows">
          <div v-for="(row, idx) in rows" :key="row.key" class="row" :class="{ off: !row.checked }">
            <a-checkbox v-model:checked="row.checked" class="row-ck" />
            <div class="row-fields">
              <a-input
                v-model:value="row.word"
                class="f-word"
                placeholder="单词"
                :maxlength="MAX_SPELLING_LEN"
              />
              <a-input
                v-model:value="row.translation"
                class="f-tr"
                placeholder="释义（可留空）"
                :maxlength="MAX_TRANSLATION_LEN"
              />
            </div>
            <button type="button" class="row-del" title="删掉这一行" @click="removeRow(idx)">✕</button>
          </div>
        </div>
        <a-empty v-else description="没识别到单词，重新拍一张试试" />

        <div class="ocr-foot">
          <a-button @click="onCancel">取消</a-button>
          <a-button
            type="primary"
            :loading="submitting"
            :disabled="!selectedCount"
            @click="confirmCapture"
          >
            确认录入 {{ selectedCount }} 个词
          </a-button>
        </div>
      </template>
    </div>
  </a-modal>
</template>

<style scoped>
.hidden-file {
  display: none;
}
.ocr {
  display: flex;
  flex-direction: column;
}
.tip {
  font-size: 13px;
  color: #8c8c8c;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 10px 12px;
  line-height: 1.6;
}

/* 选图 */
.dropzone {
  position: relative;
  /* 负外边距抵消掉边框与内边距，保证与上方提示条左右对齐（-8 + 2 + 6 = 0） */
  margin: 0 -8px;
  padding: 0 6px 4px;
  border: 2px dashed transparent;
  border-radius: 12px;
  transition:
    border-color 0.15s,
    background 0.15s;
}
.dropzone.is-over {
  border-color: #1890ff;
  background: #f0f8ff;
}
/* 悬停提示层盖住整个拖放区。pointer-events:none 很关键：
   否则它会顶掉底下元素的 dragleave，导致松手后高亮不消失 */
.drop-hint {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(240, 248, 255, 0.92);
  border-radius: 10px;
  pointer-events: none;
}
.drop-hint-inner {
  text-align: center;
  color: #1890ff;
}
.drop-ico {
  font-size: 34px;
  line-height: 1;
}
.drop-text {
  margin-top: 8px;
  font-size: 15px;
  font-weight: 600;
}
.pick-btns {
  display: flex;
  gap: 12px;
  margin-top: 14px;
}
.pick-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 56px;
  border: 1px dashed #d9d9d9;
  border-radius: 12px;
  background: #fff;
  color: #595959;
  font-size: 15px;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s;
}
.pick-btn:hover {
  border-color: #1890ff;
  color: #1890ff;
}
.pick-btn.primary {
  border-style: solid;
  border-color: #1890ff;
  color: #1890ff;
  background: #e6f4ff;
}
.pick-btn .ico {
  font-size: 20px;
}
.thumbs {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
  gap: 10px;
  margin-top: 14px;
}
.thumb {
  position: relative;
  aspect-ratio: 1;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #f0f0f0;
  background: #fafafa;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.thumb-fallback {
  padding: 8px;
  font-size: 11px;
  color: #8c8c8c;
  word-break: break-all;
  line-height: 1.4;
}
.thumb-del {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 22px;
  height: 22px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
}
.pick-empty {
  margin: 18px 0 6px;
}

/* 识别中 */
.running {
  padding: 32px 0 24px;
  text-align: center;
}
.running-text {
  margin-top: 18px;
  font-size: 16px;
  font-weight: 600;
  color: #262626;
}
.running-bar {
  max-width: 320px;
  margin: 12px auto 0;
}
.running-sub {
  margin-top: 8px;
  font-size: 12px;
  color: #8c8c8c;
}

/* 失败 */
.failed {
  padding: 32px 0 24px;
  text-align: center;
}
.failed-ico {
  font-size: 40px;
}
.failed-msg {
  margin-top: 12px;
  font-size: 15px;
  font-weight: 600;
  color: #ff4d4f;
  word-break: break-all;
}
.failed-sub {
  margin-top: 6px;
  font-size: 12px;
  color: #8c8c8c;
}

/* 候选词 */
.rv-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.rv-count {
  font-size: 13px;
  color: #8c8c8c;
}
.rv-actions {
  margin-left: auto;
  display: flex;
  gap: 12px;
}
.rv-actions a {
  font-size: 13px;
  color: #1890ff;
  cursor: pointer;
}
.rv-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #8c8c8c;
}
.rows {
  margin-top: 10px;
  max-height: 46vh;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}
.row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 4px;
  border-bottom: 1px dashed #f0f0f0;
  transition: opacity 0.2s;
}
.row.off {
  opacity: 0.45;
}
.row-ck {
  flex: none;
}
.row-fields {
  flex: 1;
  display: flex;
  gap: 8px;
  min-width: 0;
}
.f-word {
  flex: 0 0 40%;
  font-weight: 600;
}
.f-tr {
  flex: 1;
  min-width: 0;
}
.row-del {
  flex: none;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #bfbfbf;
  font-size: 13px;
  cursor: pointer;
}
.row-del:hover {
  background: #fff1f0;
  color: #ff4d4f;
}

/* 底部按钮 */
.ocr-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px dashed #f0f0f0;
}

@media (max-width: 576px) {
  .pick-btn {
    height: 64px;
    font-size: 16px;
  }
  .thumbs {
    grid-template-columns: repeat(3, 1fr);
  }
  .row-fields {
    flex-direction: column;
    gap: 6px;
  }
  .f-word {
    flex: none;
    width: 100%;
  }
  .rows {
    max-height: 42vh;
  }
  .ocr-foot {
    /* 手机上按钮铺满更好点 */
    gap: 8px;
  }
  .ocr-foot :deep(.ant-btn) {
    flex: 1;
    height: 42px;
  }
}
</style>

<!-- a-modal 的外层节点被 teleport 到 body，拿不到 scoped 属性，只能靠 wrapClassName 限定 -->
<style>
@media (max-width: 576px) {
  .word-ocr-modal .ant-modal {
    max-width: 100vw;
    margin: 8px auto;
    padding-bottom: 0;
  }
  .word-ocr-modal .ant-modal-content {
    border-radius: 12px;
  }
  .word-ocr-modal .ant-modal-body {
    padding: 16px;
  }
}
</style>
