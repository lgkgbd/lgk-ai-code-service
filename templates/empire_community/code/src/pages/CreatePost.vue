<template>
  <div class="create-page">
    <h1 class="page-title">发·布·新·帖</h1>
    <form class="create-form" @submit.prevent="submitPost">
      <div class="form-group">
        <label class="form-label">选择门类</label>
        <select v-model="form.category" class="form-select" required>
          <option value="" disabled>请选择分类</option>
          <option v-for="cat in categories" :key="cat.id" :value="cat.id">{{ cat.name }}</option>
        </select>
      </div>
      <div class="form-group">
        <label class="form-label">议题</label>
        <input v-model="form.title" class="form-input" placeholder="议题之要，一言以蔽之" required />
      </div>
      <div class="form-group">
        <label class="form-label">正文</label>
        <textarea v-model="form.content" class="form-textarea" rows="8" placeholder="陈说己见，以飨同侪…" required></textarea>
      </div>
      <div class="form-group">
        <label class="form-label">署名</label>
        <input v-model="form.author" class="form-input" placeholder="字号、笔名或官职称谓" required />
      </div>
      <button type="submit" class="submit-btn">呈·递</button>
    </form>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { categories } from '@/data'

const router = useRouter()
const form = reactive({ category: '', title: '', content: '', author: '' })

function submitPost() {
  if (!form.title || !form.content || !form.author || !form.category) return
  alert('帖子已提交，待审核后展示于众。')
  router.push('/')
}
</script>

<style scoped>
.create-page {
  max-width: 640px;
  margin: 0 auto;
  padding: 20px 0;
}

.page-title {
  font-size: 20px;
  color: #c9a84c;
  letter-spacing: 6px;
  font-weight: 500;
  text-align: center;
  margin-bottom: 32px;
}

.create-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-label {
  font-size: 13px;
  color: #888;
  letter-spacing: 2px;
}

.form-input,
.form-select,
.form-textarea {
  padding: 10px 14px;
  font-size: 14px;
  color: #d4d0c8;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 4px;
}

.form-input:focus,
.form-select:focus,
.form-textarea:focus {
  border-color: rgba(201,168,76,0.3);
}

.form-select option {
  background: #111;
  color: #d4d0c8;
}

.form-textarea {
  resize: vertical;
  min-height: 120px;
}

.submit-btn {
  align-self: flex-end;
  padding: 10px 36px;
  font-size: 14px;
  color: #0a0a0a;
  background: #c9a84c;
  letter-spacing: 4px;
  border-radius: 2px;
  transition: background 0.3s;
}

.submit-btn:hover {
  background: #b8942e;
}

@media (max-width: 600px) {
  .page-title { font-size: 17px; letter-spacing: 4px; }
}
</style>
