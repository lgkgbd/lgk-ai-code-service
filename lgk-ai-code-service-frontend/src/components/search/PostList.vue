<template>
  <a-list item-layout="vertical" :data-source="postList">
    <template #renderItem="{ item }">
      <a-list-item>
        <a-list-item-meta :title="item.title">
          <template #description>
            <p>{{ item.content }}</p>
          </template>
        </a-list-item-meta>
      </a-list-item>
    </template>
  </a-list>
</template>

<script setup lang="ts">
import { defineProps } from 'vue'
import type { PostVO } from '@/api/models'

interface Props {
  postList: PostVO[]
}

defineProps<Props>()
</script>