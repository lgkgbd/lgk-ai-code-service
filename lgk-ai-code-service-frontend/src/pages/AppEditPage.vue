<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  getAppVoById,
  updateApp,
  updateAppByAdmin
} from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'
import { getCodeGenTypeOptions } from '@/constants/codeGenType'
import type { API } from '@/api/typings'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

// 应用信息
const appInfo = ref<Record<string, any> | null>(null)
const appId = ref<string>('')
const loading = ref(false)
const saving = ref(false)

// 检查是否为管理员
const isAdmin = computed(() => loginUserStore.loginUser?.userRole === 'admin')

// 表单数据
const formData = reactive({
  appName: '',
  cover: '',
  priority: 0,
  codeGenType: '',
})

// 表单验证规则
const rules = {
  appName: [
    { required: true, message: '请输入应用名称', trigger: 'blur' },
    { min: 1, max: 50, message: '应用名称长度在 1 到 50 个字符', trigger: 'blur' },
  ],
  cover: [
    { type: 'url', message: '请输入有效的图片URL', trigger: 'blur' },
  ],
  priority: [
    { type: 'number', min: 0, max: 100, message: '优先级必须在 0-100 之间', trigger: 'blur' },
  ],
  codeGenType: [
    { required: true, message: '请选择代码生成类型', trigger: 'change' },
  ],
}

// 获取应用信息
const loadAppInfo = async () => {
  loading.value = true
  try {
    const res = await getAppVoById({ id: appId.value })
    if (res.data.code === 0 && res.data.data) {
      appInfo.value = res.data.data

      // 检查权限：普通用户只能编辑自己的应用
      const isOwner = appInfo.value.userId === loginUserStore.loginUser?.id

      if (!isAdmin.value && !isOwner) {
        message.error({
          content: '您没有权限编辑此应用',
          duration: 3,
          closable: true,
          onClick: () => {
            message.destroy()
          },
        })
        router.push('/')
        return
      }

      // 填充表单数据
      formData.appName = res.data.data.appName || ''
      formData.cover = res.data.data.cover || ''
      formData.priority = res.data.data.priority || 0
      formData.codeGenType = res.data.data.codeGenType || ''
    } else {
      message.error({
        content: '获取应用信息失败',
        duration: 3,
        closable: true,
        onClick: () => {
          message.destroy()
        },
      })
      router.push('/')
    }
  } catch (error) {
    console.error('获取应用信息失败:', error)
    message.error({
      content: '获取应用信息失败',
      duration: 3,
      closable: true,
      onClick: () => {
        message.destroy()
      },
    })
    router.push('/')
  } finally {
    loading.value = false
  }
}

// 保存应用信息
const handleSave = async () => {
  if (!appInfo.value?.id) return

  saving.value = true
  try {
    let res
    if (isAdmin.value) {
      // 管理员可以更新所有字段
      res = await updateAppByAdmin({
        id: appInfo.value.id,
        appName: formData.appName,
        cover: formData.cover,
        priority: formData.priority,
        codeGenType: formData.codeGenType,
      })
    } else {
      // 普通用户只能更新应用名称
      res = await updateApp({
        id: appInfo.value.id,
        appName: formData.appName,
      })
    }

    if (res.data.code === 0) {
      message.success({
        content: '保存成功',
        duration: 3,
        closable: true,
        onClick: () => {
          message.destroy()
        },
      })
      // 更新本地数据
      if (appInfo.value) {
        appInfo.value.appName = formData.appName
        if (isAdmin.value) {
          appInfo.value.cover = formData.cover
          appInfo.value.priority = formData.priority
          appInfo.value.codeGenType = formData.codeGenType
        }
      }
    } else {
      message.error({
        content: res.data.message || '保存失败',
        duration: 3,
        closable: true,
        onClick: () => {
          message.destroy()
        },
      })
    }
  } catch (error) {
    console.error('保存应用信息失败:', error)
    message.error({
      content: '保存失败',
      duration: 3,
      closable: true,
      onClick: () => {
        message.destroy()
      },
    })
  } finally {
    saving.value = false
  }
}

// 返回
const handleBack = () => {
  router.back()
}

// 格式化时间
const formatTime = (timeStr: string) => {
  if (!timeStr) return '-'
  return new Date(timeStr).toLocaleString('zh-CN')
}

onMounted(() => {
  appId.value = route.params.id as string
  if (!appId.value) {
    message.error({
      content: '应用ID无效',
      duration: 3,
      closable: true,
      onClick: () => {
        message.destroy()
      },
    })
    router.push('/')
    return
  }

  loadAppInfo()
})
</script>

<template>
  <div class="app-edit-page">
    <div class="page-header">
      <div class="header-left">
        <a-button type="text" @click="handleBack">
          <template #icon><arrow-left-outlined /></template>
          返回
        </a-button>
        <h1>编辑应用信息</h1>
      </div>
    </div>

    <a-spin :spinning="loading">
      <div v-if="appInfo" class="edit-content">
        <!-- 应用基本信息展示 -->
        <div class="info-section">
          <h2>应用基本信息</h2>
          <div class="info-grid">
            <div class="info-item">
              <label>应用ID：</label>
              <span>{{ appInfo.id }}</span>
            </div>
            <div class="info-item">
              <label>创建者：</label>
              <span>{{ appInfo.user?.userName || '-' }}</span>
            </div>
            <div class="info-item">
              <label>创建时间：</label>
              <span>{{ formatTime(appInfo.createTime!) }}</span>
            </div>
            <div class="info-item">
              <label>更新时间：</label>
              <span>{{ formatTime(appInfo.updateTime!) }}</span>
            </div>
            <div class="info-item">
              <label>代码类型：</label>
              <span>{{ appInfo.codeGenType || '-' }}</span>
            </div>
            <div class="info-item">
              <label>部署时间：</label>
              <span>{{ formatTime(appInfo.deployedTime!) }}</span>
            </div>
          </div>
        </div>

        <!-- 编辑表单 -->
        <div class="form-section">
          <h2>编辑信息</h2>
          <a-form
            :model="formData"
            :rules="rules"
            layout="vertical"
            @finish="handleSave"
          >
            <a-form-item label="应用名称" name="appName">
              <a-input
                v-model:value="formData.appName"
                placeholder="请输入应用名称"
                :disabled="saving"
              />
            </a-form-item>

            <a-form-item label="封面图片" name="cover">
              <a-input
                v-model:value="formData.cover"
                placeholder="请输入封面图片URL"
                :disabled="saving || !isAdmin"
              />
              <div class="form-tip">仅管理员可编辑</div>
            </a-form-item>

            <a-form-item label="优先级" name="priority">
              <a-input-number
                v-model:value="formData.priority"
                :min="0"
                :max="100"
                :disabled="saving || !isAdmin"
                style="width: 100%"
              />
              <div class="form-tip">仅管理员可编辑，数值越高优先级越高</div>
            </a-form-item>

            <a-form-item label="代码生成类型" name="codeGenType">
              <a-select
                v-model:value="formData.codeGenType"
                placeholder="请选择代码生成类型"
                style="width: 100%"
                :disabled="saving || !isAdmin"
              >
                <a-select-option
                  v-for="option in getCodeGenTypeOptions()"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </a-select-option>
              </a-select>
              <div class="form-tip">仅管理员可编辑</div>
            </a-form-item>

            <a-form-item label="初始提示词">
              <a-textarea
                :value="appInfo?.initPrompt || ''"
                placeholder="无初始提示词"
                :rows="3"
                readonly
                disabled
              />
              <div class="form-tip">初始提示词不可编辑</div>
            </a-form-item>

            <a-form-item>
              <a-button
                type="primary"
                html-type="submit"
                :loading="saving"
                size="large"
              >
                <template #icon><save-outlined /></template>
                保存修改
              </a-button>
              <a-button
                style="margin-left: 8px"
                @click="handleBack"
              >
                取消
              </a-button>
            </a-form-item>
          </a-form>
        </div>

        <!-- 预览区域 -->
        <div class="preview-section">
          <h2>应用预览</h2>
          <div class="app-preview">
            <div class="app-cover">
              <img
                v-if="formData.cover"
                :src="formData.cover"
                :alt="formData.appName"
                class="cover-image"
              />
              <div v-else class="cover-placeholder">
                <picture-outlined />
                <p>暂无封面</p>
              </div>
            </div>
            <div class="app-info">
              <h3>{{ formData.appName || '未命名应用' }}</h3>
              <p class="app-desc">{{ appInfo.initPrompt }}</p>
              <div class="app-meta">
                <span class="priority">优先级: {{ formData.priority }}</span>
                <span class="creator">创建者: {{ appInfo.user?.userName }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.app-edit-page {
  padding: 24px;
}

.page-header {
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-left h1 {
  font-size: 24px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0;
}

.edit-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

.info-section,
.form-section,
.preview-section {
  background: white;
  padding: 24px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.preview-section {
  grid-column: 1 / -1;
}

.info-section h2,
.form-section h2,
.preview-section h2 {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 20px 0;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.info-item {
  display: flex;
  align-items: center;
}

.info-item label {
  font-weight: 500;
  color: #666;
  margin-right: 8px;
  min-width: 80px;
}

.info-item span {
  color: #1a1a1a;
}

.form-tip {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.app-preview {
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  overflow: hidden;
}

.app-cover {
  height: 200px;
  background: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.cover-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-placeholder {
  text-align: center;
  color: #ccc;
}

.cover-placeholder .anticon {
  font-size: 48px;
  margin-bottom: 8px;
}

.app-info {
  padding: 20px;
}

.app-info h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 12px 0;
}

.app-desc {
  color: #666;
  line-height: 1.5;
  margin: 0 0 16px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.app-meta {
  display: flex;
  gap: 16px;
  font-size: 14px;
  color: #999;
}

.priority,
.creator {
  display: flex;
  align-items: center;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .app-edit-page {
    padding: 16px;
  }

  .edit-content {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }

  .info-section,
  .form-section,
  .preview-section {
    padding: 16px;
  }
}
</style>
