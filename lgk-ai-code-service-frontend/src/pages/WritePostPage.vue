<template>
  <div class="write-post-page">
    <h1>{{ pageTitle }}</h1>
    <a-input v-model:value="title" placeholder="请输入标题" style="margin-bottom: 20px;" />
    <div id="markdown-editor"></div>
    <a-button type="primary" @click="publishPost" style="margin-top: 20px;">{{ submitButtonText }}</a-button>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { addPost, getPostVoById, updatePost } from '@/api/postController';
import { showSuccess, showError } from '@/utils/message';
import Vditor from 'vditor';
import 'vditor/dist/index.css';

const router = useRouter();
const route = useRoute();
const postId = ref<string | null>(null);

const title = ref('');
const contentEditor = ref<Vditor | null>(null);

const isEditMode = computed(() => !!postId.value);
const pageTitle = computed(() => isEditMode.value ? '编辑文章' : '写文章');
const submitButtonText = computed(() => isEditMode.value ? '更新' : '发布');

onMounted(async () => {
  const id = route.query.id as string;

  contentEditor.value = new Vditor('markdown-editor', {
    height: 500,
    placeholder: '请输入正文内容...',
    after: async () => {
      if (id) {
        postId.value = id;
        await fetchPostData(id);
      }
    },
  });
});

const fetchPostData = async (id: string) => {
  try {
    const res = await getPostVoById({ id: id });
    if (res.data.code === 0 && res.data.data) {
      const post = res.data.data;
      title.value = post.title || '';
      contentEditor.value?.setValue(post.content || '');
    } else {
      showError('加载文章失败：' + (res.data.message || '未知错误'));
      router.push('/community');
    }
  } catch (error) {
    showError('加载文章失败');
  }
};

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
    if (isEditMode.value && postId.value) {
      const res = await updatePost({
        id: postId.value,
        title: title.value,
        content: content,
      });
      if (res.data.code === 0) {
        showSuccess('更新成功');
        router.push(`/post/${postId.value}`);
      } else {
        showError('更新失败：' + res.data.message);
      }
    } else {
      const res = await addPost({
        title: title.value,
        content: content,
        tags: [],
      });
      if (res.data.code === 0) {
        showSuccess('发布成功');
        router.push('/community');
      } else {
        showError('发布失败：' + res.data.message);
      }
    }
  } catch (error) {
    showError(isEditMode.value ? '更新失败' : '发布失败');
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
