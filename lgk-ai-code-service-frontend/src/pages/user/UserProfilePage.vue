<template>
  <div class="profile-page">
    <div class="card">
      <div class="header">
        <a-avatar :size="80" :src="loginUser.userAvatar">
          {{ loginUser.userName?.charAt(0)?.toUpperCase() }}
        </a-avatar>
        <div class="info">
          <h2>{{ loginUser.userName || '未命名用户' }}</h2>
          <p class="sub">账号：{{ loginUser.userAccount || '-' }}</p>
        </div>
        <a-button @click="showEditModal" class="edit-btn">编辑</a-button>
      </div>

      <div class="body">
        <div class="item">
          <span class="label">个性签名</span>
          <span class="value">{{ loginUser.userProfile || '这个人很懒，什么都没写~' }}</span>
        </div>
        <div class="item">
          <span class="label">角色</span>
          <span class="value">
            <a-tag :color="loginUser.userRole === 'admin' ? 'green' : 'blue'">
              {{ loginUser.userRole === 'admin' ? '管理员' : '普通用户' }}
            </a-tag>
          </span>
        </div>
        <div class="item">
          <span class="label">创建时间</span>
          <span class="value">{{ loginUser.createTime || '-' }}</span>
        </div>

        <!-- 年度签到格子图 -->
        <div class="calendar-card">
          <div class="calendar-title">{{ currentYear }} 年签到记录</div>
          <div class="calendar-body">
            <div class="weekday-labels">
              <span v-for="(w, i) in ['日','一','二','三','四','五','六']" :key="i" class="weekday">{{ w }}</span>
            </div>
            <div class="calendar-scroll">
              <div class="calendar-months">
                <div class="month-col" v-for="(label, i) in monthLabels" :key="i">{{ label }}</div>
              </div>
              <div class="calendar-heatmap">
                <div class="week" v-for="(col, ci) in weeks" :key="ci">
                  <div
                    v-for="(d, di) in col"
                    :key="di"
                    class="day-cell"
                    :class="{ active: d && d.signed, empty: !d }"
                    :title="d ? d.dateStr : ''"
                  ></div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <a-modal v-model:visible="isModalVisible" title="编辑个人信息" @ok="handleOk" @cancel="handleCancel" :maskClosable="false">
      <a-form :model="editableUser" layout="vertical">
        <a-form-item label="头像">
          <a-upload
            name="file"
            list-type="picture-card"
            class="avatar-uploader"
            :show-upload-list="false"
            :customRequest="handleAvatarUpload"
            :before-upload="() => !uploading"
          >
            <img v-if="editableUser.userAvatar" :src="editableUser.userAvatar" alt="avatar" style="width: 100%; border-radius: 50%;" />
            <div v-else>
              <loading-outlined v-if="uploading"></loading-outlined>
              <plus-outlined v-else></plus-outlined>
              <div class="ant-upload-text">上传</div>
            </div>
          </a-upload>
        </a-form-item>
        <a-form-item label="用户昵称" required>
          <a-input v-model:value="editableUser.userName" placeholder="请输入新的昵称" />
        </a-form-item>
        <a-form-item label="个性签名">
          <a-textarea v-model:value="editableUser.userProfile" :rows="3" placeholder="介绍一下自己吧~" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { onMounted, ref } from 'vue'
import { getLoginUser, getUserSignInRecord, updateUser } from '@/api/userController.ts'
import { uploadFile } from '@/api/fileController.ts'
import { message, type UploadProps } from 'ant-design-vue'
import { PlusOutlined, LoadingOutlined } from '@ant-design/icons-vue'
import { showError, showSuccess } from '@/utils/message'

const loginUserStore = useLoginUserStore()
const { loginUser } = storeToRefs(loginUserStore)

const isModalVisible = ref(false)
const editableUser = ref<API.UserUpdateMyRequest>({})
const uploading = ref(false)

const showEditModal = () => {
  editableUser.value = { ...loginUser.value }
  isModalVisible.value = true
}

const handleCancel = () => {
  isModalVisible.value = false
}

const handleOk = async () => {
  try {
    const res = await updateUser(editableUser.value)
    if (res.data.code === 0) {
      showSuccess('更新成功')
      // 重新获取用户信息并更新 store
      const userRes = await getLoginUser()
      if (userRes.data.code === 0 && userRes.data.data) {
        loginUserStore.setLoginUser(userRes.data.data)
      }
      isModalVisible.value = false
    } else {
      showError('更新失败: ' + res.data.message)
    }
  } catch (e) {
    showError('更新失败')
  }
}

const handleAvatarUpload: UploadProps['customRequest'] = async ({ file }) => {
  uploading.value = true
  const hide = message.loading('正在上传...', 0)
  try {
    const res = await uploadFile({ biz: 'user_avatar' }, file as File)
    if (res.data.code === 0 && res.data.data) {
      editableUser.value.userAvatar = res.data.data
      showSuccess('上传成功')
    } else {
      showError('上传失败: ' + res.data.message)
    }
  } catch (e) {
    showError('上传失败')
  } finally {
    uploading.value = false
    hide()
  }
}

const currentYear = new Date().getFullYear()

type DayCell = {
  date: Date
  dateStr: string
  dayOfYear: number
  signed: boolean
}
const weeks = ref<Array<Array<DayCell | null>>>([])
const monthLabels = ref<string[]>([])

function dayOfYear(date: Date): number {
  const start = new Date(date.getFullYear(), 0, 0)
  const diff = date.getTime() - start.getTime()
  const oneDay = 1000 * 60 * 60 * 24
  return Math.floor(diff / oneDay)
}





async function fetchSignInRecords() {
  try {
    const res = await getUserSignInRecord({ year: currentYear })
    const arr = Array.isArray(res.data?.data) ? (res.data.data as number[]) : []
    buildWeeksFromSignedDays(arr)
  } catch {
    buildWeeksFromSignedDays([])
  }
}

function buildWeeksFromSignedDays(signedArr: number[]) {
  weeks.value = []
  const signedSet = new Set(signedArr)
  const start = new Date(currentYear, 0, 1)
  let week: Array<DayCell | null> = []
  // 前置占位，使第一列与周对齐（周日=0）
  for (let i = 0; i < start.getDay(); i++) {
    week.push(null)
  }
  const d = new Date(currentYear, 0, 1)
  while (d.getFullYear() === currentYear) {
    const doy = dayOfYear(d)
    const dateStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
      d.getDate()
    ).padStart(2, '0')}`
    week.push({
      date: new Date(d),
      dateStr,
      dayOfYear: doy,
      signed: signedSet.has(doy),
    })
    if (week.length === 7) {
      weeks.value.push(week)
      week = []
    }
    d.setDate(d.getDate() + 1)
  }
  if (week.length > 0) {
    while (week.length < 7) week.push(null)
    weeks.value.push(week)
  }
  // 生成月份标签：以每列（周）内的最大月份为准，检测与上一列是否变化
  monthLabels.value = weeks.value.map((col, idx) => {
    const months = col.filter(c => !!c).map(c => (c as DayCell).date.getMonth())
    const prevMonths = idx > 0 ? weeks.value[idx - 1].filter(c => !!c).map(c => (c as DayCell).date.getMonth()) : []
    const curM = months.length ? Math.max(...months) : null
    const prevM = prevMonths.length ? Math.max(...prevMonths) : null
    return curM !== null && curM !== prevM ? `${curM + 1}月` : ''
  })
}

onMounted(async () => {
  // 尝试刷新个人信息（后端若已在 cookie 会返回当前登录用户）
  const res = await getLoginUser()
  if (res.data.code === 0 && res.data.data) {
    loginUserStore.setLoginUser(res.data.data)
  }
  // 拉取今年签到记录并渲染热力图
  await fetchSignInRecords()
})
</script>

<style scoped>
.profile-page {
  max-width: 800px;
  margin: 24px auto;
  padding: 0 16px;
}

.card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  padding: 24px;
}

.header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f5f5f5;
  position: relative;
}

.edit-btn {
  position: absolute;
  top: 24px;
  right: 24px;
}

.info h2 {
  margin: 0;
}
.sub {
  color: #999;
  margin-top: 4px;
}

.body {
  padding-top: 16px;
}

.item {
  display: flex;
  padding: 10px 0;
}

.label {
  width: 100px;
  color: #666;
}

.value {
  flex: 1;
  color: #333;
  word-break: break-all;
}
.calendar-card {
  margin-top: 16px;
  padding: 12px 12px 12px 16px; /* 增加左侧与整体上下内边距 */
}

.calendar-title {
  font-weight: 600;
  margin-bottom: 12px; /* 拉开与月份行的距离 */
  color: #333;
}

.calendar-heatmap {
  display: flex;
  gap: 3px;
  padding: 6px 0;
}

.week {
  display: flex;
  flex-direction: column;
  gap: 3px;
  flex: 0 0 auto; /* 固定每周列宽度，防止收缩导致与月份列错位 */
}

.day-cell {
  width: 12px;
  height: 12px;
  border-radius: 2px;
  background: #ebedf0;
}

.day-cell.active {
  background: #52c41a;
}

.day-cell.empty {
  background: transparent;
}
.calendar-months {
  display: flex;
  gap: 3px;
  height: 18px;
  margin-bottom: 8px; /* 拉开与格子图的距离 */
  font-size: 12px;
  color: #666;
}

.month-col {
  width: 12px; /* 与 day-cell 宽度一致 */
  flex: 0 0 12px; /* 固定月份列宽度，确保与周列逐列对齐 */
  text-align: center;
}

.calendar-body {
  display: flex;
  gap: 12px; /* 增大星期标签与格子图之间的间距 */
}

.weekday-labels {
  display: flex;
  flex-direction: column;
  gap: 3px;
  width: 24px;
  color: #999;
  font-size: 12px;
  margin-top: 32px; /* 与月份行(18) + 下边距(8) + 方格上内边距(6) 对齐 */
}

.weekday {
  height: 12px;
  line-height: 12px;
}

.calendar-scroll {
  overflow-x: auto;
}
</style>

