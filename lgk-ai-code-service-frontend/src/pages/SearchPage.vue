<template>
  <div class="search-page">
    <a-input-search
      v-model:value="searchText"
      placeholder="输入搜索内容"
      enter-button="搜索"
      size="large"
      @search="onSearch"
    />
    <a-tabs v-model:activeKey="activeKey" @change="onTabChange">

      <a-tab-pane key="post" tab="帖子">
        <PostList :post-list="postList" />
      </a-tab-pane>
      <a-tab-pane key="picture" tab="图片">
        <PictureList :picture-list="pictureList" />
      </a-tab-pane>
      <a-tab-pane key="video" tab="视频">
        <VideoList :video-list="videoList" />
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useSearchStore } from '@/stores/searchStore'
import { useRoute, useRouter } from 'vue-router'
import PostList from '@/components/search/PostList.vue'
import PictureList from '@/components/search/PictureList.vue'
import VideoList from '@/components/search/VideoList.vue'
import { searchAll } from '@/api/searchController'
import type { PostVO, Picture, Video, SearchVO } from '@/api/models'

const route = useRoute()
const router = useRouter()
const searchStore = useSearchStore()

const searchText = ref('')
const activeKey = ref('post')

const postList = ref<PostVO[]>([])
const pictureList = ref<Picture[]>([])
const videoList = ref<Video[]>([])

const pageNum = ref(1)
const loading = ref(false)
const noMoreData = ref(false)
const currentSearchParams = ref({ searchText: '', type: 'post' })

const loadData = async (params: any, loadMore = false) => {
  if (loading.value || (loadMore && noMoreData.value)) {
    return
  }
  loading.value = true

  if (!loadMore) {
    pageNum.value = 1
    noMoreData.value = false
    postList.value = []
    pictureList.value = []
    videoList.value = []
  }

  const requestParams = {
    ...params,
    pageNum: pageNum.value,
  }

  try {
    const res = await searchAll(requestParams)
    if (res.data.code === 0 && res.data.data) {
      const searchVO: SearchVO = res.data.data as any
      const type = params.type

      const mapVideoData = (video: any) => ({
        ...video,
        cover: video.pic,
        playCount: video.play,
        arcurl: video.arcurl,
      })

      let newDataReceived = false


        const dataList = searchVO.dataList || []
        if (dataList.length > 0) {
          newDataReceived = true
        }

        const mappedDataList = type === 'video' ? dataList.map(mapVideoData) : dataList

        if (loadMore) {
          if (type === 'post') postList.value.push(...mappedDataList)
          else if (type === 'picture') pictureList.value.push(...mappedDataList)
          else if (type === 'video') videoList.value.push(...mappedDataList.slice(0, 9))
        } else {
          if (type === 'post') postList.value = mappedDataList
          else if (type === 'picture') pictureList.value = mappedDataList
          else if (type === 'video') videoList.value = mappedDataList.slice(0, 9)
        }

      if (!newDataReceived) {
        noMoreData.value = true
      }
    } else {
      noMoreData.value = true
    }
  } catch (error) {
    console.error('Failed to load data:', error)
    noMoreData.value = true
  } finally {
    loading.value = false
  }
}

const handleScroll = () => {
  const { scrollTop, scrollHeight, clientHeight } = document.documentElement
  if (scrollTop + clientHeight >= scrollHeight - 100 && !loading.value && !noMoreData.value) {
    pageNum.value++
    loadData(currentSearchParams.value, true)
  }
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll)
  searchStore.setShowHeaderSearch(false)
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  searchStore.setShowHeaderSearch(true)
  searchStore.setHeaderSearchText('')
})

watch(
  () => route.query,
  (newQuery) => {
    const querySearchText = Array.isArray(newQuery.text) ? newQuery.text[0] : newQuery.text
    const queryType = Array.isArray(newQuery.type) ? newQuery.type[0] : newQuery.type

    const params = {
      searchText: querySearchText || '',
      type: queryType || 'post',
    }

    searchText.value = params.searchText
    activeKey.value = params.type
    currentSearchParams.value = params

    loadData(params, false)
  },
  { immediate: true, deep: true }
)

const doSearch = (text: string) => {
  router.push({
    query: {
      type: activeKey.value,
      text: text,
    },
  })
}

let debounceTimer: any = null
watch(searchText, (newValue) => {
  if (newValue === (Array.isArray(route.query.text) ? route.query.text[0] : route.query.text || '')) {
    return;
  }
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    doSearch(newValue)
  }, 2000)
})

const onSearch = (value: string) => {
  clearTimeout(debounceTimer)
  doSearch(value)
}

const onTabChange = (key: string) => {
  router.push({
    query: {
      text: searchText.value,
      type: key,
    },
  })
}
</script>

<style scoped>
.search-page {
  padding: 20px;
}
</style>