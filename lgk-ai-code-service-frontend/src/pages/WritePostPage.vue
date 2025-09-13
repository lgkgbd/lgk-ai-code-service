<template>
  <div class="write-post-page">
    <h1>写文章</h1>
    <a-input v-model:value="title" placeholder="请输入标题" style="margin-bottom: 20px;" />
    <div id="markdown-editor"></div>
    <a-button type="primary" @click="publishPost" style="margin-top: 20px;">发布</a-button>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { addPost } from '@/api/postController';
import { showSuccess, showError } from '@/utils/message';
import Vditor from 'vditor';
import 'vditor/dist/index.css';

const router = useRouter();
const title = ref('');
const contentEditor = ref<Vditor | null>(null);

onMounted(() => {
  contentEditor.value = new Vditor('markdown-editor', {
    height: 500,
    placeholder: '请输入正文内容...',
    after: () => {
      // Vditor is ready
    },
  });
});

const publishPost = async () => {
  if (!title.value) {
    showError('请输入标题');
    return;
  }
  const content = contentEditor.value?.getValue();
  if (!content) {
    showError('请输入内容');
    return;
  }

  try {
    const res = await addPost({
      title: title.value,
      content: content,
      tags: [], // 可以在这里添加标签功能
    });
    if (res.data.code === 0) {
      showSuccess('发布成功');
      router.push('/community');
    } else {
      showError('发布失败：' + res.data.message);
    }
  } catch (error) {
    showError('发布失败');
  }
};
</script>

<style scoped>
.write-post-page {
  max-width: 1000px;
  margin: 20px auto;
  padding: 20px;
  background: #fff;
  border-radius: 8px;
}
</style>