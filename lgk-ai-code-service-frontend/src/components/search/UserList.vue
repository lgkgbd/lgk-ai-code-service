<template>
  <a-list
    item-layout="horizontal"
    :data-source="userList"
    :grid="{ gutter: 16, xs: 1, sm: 2, md: 3, lg: 3, xl: 4, xxl: 4 }"
  >
    <template #renderItem="{ item }">
      <a-list-item>
        <a-card hoverable>
          <a-card-meta :title="item.userName" :description="item.userProfile">
            <template #avatar>
              <a-avatar :src="item.userAvatar" />
            </template>
          </a-card-meta>
        </a-card>
      </a-list-item>
    </template>
  </a-list>
</template>

<script setup lang="ts">
import { defineProps } from 'vue'
import type { UserVO } from '@/api/models'

interface Props {
  userList: UserVO[]
}

defineProps<Props>()
</script>