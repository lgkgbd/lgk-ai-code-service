<template>
  <a-modal
    :open="visible"
    title="发布新帖子"
    :width="600"
    :footer="null"
    @cancel="$emit('cancel')"
  >
    <a-form
      :model="formData"
      :rules="rules"
      layout="vertical"
      @finish="handleSubmit"
    >
      <a-form-item
        name="title"
        label="标题"
      >
        <a-input
          v-model:value="formData.title"
          placeholder="请输入帖子标题"
        />
      </a-form-item>

      <a-form-item
        name="content"
        label="内容"
      >
        <a-textarea
          v-model:value="formData.content"
          :rows="8"
          placeholder="分享你的想法..."
          show-count
          :maxlength="5000"
        />
      </a-form-item>

      <a-form-item
        name="tags"
        label="标签"
      >
        <a-select
          v-model:value="formData.tags"
          mode="tags"
          placeholder="添加标签（可选）"
          :max-tag-count="5"
          :max-tag-text-length="20"
        >
          <a-select-option value="技术">技术</a-select-option>
          <a-select-option value="生活">生活</a-select-option>
          <a-select-option value="学习">学习</a-select-option>
          <a-select-option value="工作">工作</a-select-option>
          <a-select-option value="分享">分享</a-select-option>
        </a-select>
      </a-form-item>

      <a-form-item class="form-actions">
        <a-space>
          <a-button @click="$emit('cancel')">取消</a-button>
          <a-button type="primary" html-type="submit" :loading="loading">
            发布
          </a-button>
        </a-space>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { message } from 'ant-design-vue'
import { addPost } from '@/api/postController'

interface Props {
  visible: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  cancel: []
  success: []
}>()

const loading = ref(false)

const formData = reactive({
  title: '',
  content: '',
  tags: [] as string[]
})

const rules = {
  title: [
    { required: true, message: '请输入帖子标题' },
    { max: 100, message: '标题不能超过100个字符' }
  ],
  content: [
    { required: true, message: '请输入帖子内容' },
    { max: 5000, message: '内容不能超过5000个字符' }
  ]
}

const handleSubmit = async () => {
  try {
    loading.value = true
    const postData: API.PostAddRequest = {
      title: formData.title,
      content: formData.content,
      tags: formData.tags.length > 0 ? formData.tags : undefined,
    }
    
    const response = await addPost(postData)
    if (response.code === 0) {
      message.success('发布成功！')
      // 重置表单
      formData.title = ''
      formData.content = ''
      formData.tags = []
      emit('success')
    } else {
      message.error(response.message || '发布失败')
    }
  } catch (error) {
    message.error('发布失败，请重试')
  } finally {
    loading.value = false
  }
}

// 监听visible变化，重置表单
watch(() => props.visible, (newVal) => {
  if (!newVal) {
    formData.title = ''
    formData.content = ''
    formData.tags = []
  }
})
</script>

<style scoped>
.form-actions {
  margin-bottom: 0;
  text-align: right;
}

:deep(.ant-modal-header) {
  border-bottom: 1px solid #f0f0f0;
}

:deep(.ant-form-item-label > label) {
  font-weight: 500;
}

:deep(.ant-input),
:deep(.ant-input:focus),
:deep(.ant-input-focused) {
  border-color: #d9d9d9;
  box-shadow: none;
}

:deep(.ant-input:hover) {
  border-color: #40a9ff;
}

:deep(.ant-select:hover .ant-select-selector) {
  border-color: #40a9ff;
}
</style>