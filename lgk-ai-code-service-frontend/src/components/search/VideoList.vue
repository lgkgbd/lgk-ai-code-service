<template>
  <div class="video-list">
    <a v-for="(item, index) in videoList" :key="index" :href="item.arcurl" target="_blank">
      <a-card
        hoverable
        class="video-card"
      >
        <template #cover>
        <div class="video-cover">
          <img :alt="item.title" :src="item.cover ? 'https:' + item.cover : ''" />
          <div class="video-info">
            <span><play-circle-outlined /> {{ item.playCount }}</span>
            <span><clock-circle-outlined /> {{ item.duration }}</span>
          </div>
        </div>
      </template>
      <a-card-meta :title="item.title">
        <template #description>
          <p>UP: {{ item.author }}</p>
        </template>
      </a-card-meta>
      </a-card>
    </a>
  </div>
</template>

<script setup lang="ts">
import { defineProps } from 'vue'
import { PlayCircleOutlined, ClockCircleOutlined } from '@ant-design/icons-vue'
import type { Video } from '@/api/models'

interface Props {
  videoList: Video[]
}

defineProps<Props>()
</script>

<style scoped>
.video-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
.video-cover {
  position: relative;
}
.video-cover img {
  width: 100%;
  height: 150px;
  object-fit: cover;
}
.video-info {
  position: absolute;
  bottom: 8px;
  left: 8px;
  right: 8px;
  color: white;
  display: flex;
  justify-content: space-between;
  background-color: rgba(0, 0, 0, 0.5);
  padding: 4px 8px;
  border-radius: 4px;
}
</style>