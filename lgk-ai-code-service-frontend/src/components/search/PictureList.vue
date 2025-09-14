<template>
  <div class="picture-list">
    <a v-for="(item, index) in pictureList" :key="index" :href="item.url" target="_blank">
      <a-card
        hoverable
        class="picture-card"
      >
        <template #cover>
          <img :alt="item.title" :src="item.url" />
        </template>
        <a-card-meta :title="item.title"></a-card-meta>
      </a-card>
    </a>
  </div>
</template>

<script setup lang="ts">
import { defineProps } from 'vue'
import type { Picture } from '@/api/models'

interface Props {
  pictureList: Picture[]
}

defineProps<Props>()
</script>

<style scoped>
.picture-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}
.picture-card img {
  height: 200px;
  object-fit: cover;
}
</style>