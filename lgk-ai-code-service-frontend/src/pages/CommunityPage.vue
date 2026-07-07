<template>
  <div class="community-page">
    <!-- 顶部发布区域 -->
    <div v-if="activeTab !== 'myposts' && activeTab !== 'favourites'" class="publish-section">
      <div class="publish-tabs">
        <a-button type="primary" class="tab-btn active">随便聊</a-button>
        <a-button type="default" class="tab-btn" @click="goToWriteArticle">写文章</a-button>
        <div class="right-actions">
        </div>
      </div>

      <div class="publish-input">
        <a-textarea
          v-model:value="postContent"
          placeholder="分享你和大家讨论吧～"
          :rows="3"
          class="post-textarea"
        />
        <div class="publish-actions">
          <div class="action-buttons">
            <div class="emoji-container">
              <a-button type="text" class="action-btn" @click="handleEmojiClick">
                <template #icon>😊</template>
                表情
              </a-button>
              <div v-if="showEmojiPicker" class="emoji-picker">
                <!-- 表情分类标签 -->
                <div class="emoji-tabs">
                  <div
                    v-for="(category, key) in emojiCategoryTabs"
                    :key="key"
                    :class="['emoji-tab', { active: activeEmojiTab === key }]"
                    @click="activeEmojiTab = key"
                  >
                    <span class="tab-icon">{{ category.icon }}</span>
                  </div>
                </div>

                <!-- 表情内容区域 -->
                <div class="emoji-content">
                  <!-- 最近使用 -->
                  <div v-if="recentEmojis.length > 0">
                    <div class="emoji-category-title">
                      {{ emojiCategories.recent.name }}
                    </div>
                    <div class="emoji-grid">
                      <span
                        v-for="emoji in recentEmojis"
                        :key="emoji"
                        class="emoji-item"
                        @click="selectEmoji(emoji)"
                      >
                        {{ emoji }}
                      </span>
                    </div>
                  </div>

                  <!-- 当前选中的分类 -->
                  <div v-if="activeEmojiTab !== 'recent'">
                    <div class="emoji-category-title">
                      {{ emojiCategories[activeEmojiTab].name }}
                    </div>
                    <div class="emoji-grid">
                      <span
                        v-for="emoji in emojiCategories[activeEmojiTab].emojis.value || emojiCategories[activeEmojiTab].emojis"
                        :key="emoji"
                        class="emoji-item"
                        @click="selectEmoji(emoji)"
                      >
                        {{ emoji }}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <a-button type="primary" class="publish-btn" @click="handlePublish">
            发布
          </a-button>
        </div>
      </div>
    </div>

    <!-- 搜索区域 -->
    <div class="search-section">
      <div class="search-container">
        <a-input
          v-model:value="searchKeyword"
          placeholder="搜索帖子内容、标题或用户..."
          class="search-input"
          @pressEnter="handleSearch"
          @input="handleSearchInput"
        >
          <template #prefix>
            <SearchOutlined />
          </template>
        </a-input>
        <a-button
          type="primary"
          class="search-btn"
          @click="handleSearch"
          :loading="isSearching"
        >
          搜索
        </a-button>
      </div>
      <div v-if="searchKeyword && searchResults.length === 0 && !isSearching" class="no-results">
        没有找到相关内容
      </div>
    </div>

    <!-- 导航标签 -->
    <div class="nav-tabs">
      <a-button
        v-for="tab in navTabs"
        :key="tab.key"
        :type="activeTab === tab.key ? 'text' : 'text'"
        :class="['nav-tab', { active: activeTab === tab.key }]"
        @click="handleTabChange(tab.key)"
      >
        {{ tab.label }}
      </a-button>
      <div class="nav-right">
        <a-button type="text" class="settings-btn">设置</a-button>
      </div>
    </div>

    <!-- 帖子列表 -->
    <div class="posts-list">
      <template v-if="displayPosts && displayPosts.length > 0">
        <div v-for="post in displayPosts" :key="post.id" class="post-item" @click="handlePostClick(post.id)">
          <div class="post-header">
            <div class="user-info">
              <a-avatar :src="post.user?.userAvatar" :size="40">
                {{ post.user?.userName?.charAt(0) || 'U' }}
              </a-avatar>
              <div class="user-details">
                <div class="username">{{ post.user?.userName || '匿名用户' }}</div>
                <div class="post-meta">
                  <span class="post-time">{{ formatTime(post.createTime) }}</span>
                </div>
              </div>
            </div>
          </div>

          <div class="post-body">
            <div class="post-content">
              <h3 v-if="post.title" class="post-title" @click.stop="handlePostClick(post.id)">{{ post.title }}</h3>
              <div class="post-text">{{ getPreviewText(post.content) }}</div>
              <div
                v-if="isPreviewTruncated(post.content)"
                class="view-full"
                @click.stop="handlePostClick(post.id)"
              >
                查看全文
              </div>
              <div v-if="post.tags && post.tags.length > 0" class="post-tags">
                <a-tag v-for="tag in parseTags(post.tags)" :key="tag" color="blue" class="post-tag">
                  {{ tag }}
                </a-tag>
              </div>
              <div v-if="post.topComment" class="post-top-comment">
                <span class="top-comment-user">{{ post.topComment.user?.userName || '匿名用户' }}：</span>
                <span class="top-comment-text">{{ truncateText(post.topComment.content, 60) }}</span>
              </div>
            </div>
            <img v-if="getCoverImage(post)" :src="getCoverImage(post)" class="post-cover" alt="" />
          </div>

          <div class="post-actions">
            <a-button
              type="text"
              class="action-item"
              :class="{ active: post.hasThumb }"
              @click.stop="handleThumb(post)"
            >
              <template #icon>👍</template>
              {{ post.thumbNum || 0 }}
            </a-button>
            <a-button type="text" class="action-item" @click.stop="handlePostClick(post.id)">
              <template #icon>💬</template>
              评论
            </a-button>
            <a-button
              type="text"
              class="action-item"
              :class="{ active: post.hasFavour }"
              @click.stop="handleFavour(post)"
            >
              <template #icon>⭐</template>
              {{ post.favourNum || 0 }}
            </a-button>
            <a-button type="text" class="action-item" @click.stop="handleShare(post)">
              <template #icon>📤</template>
              分享
            </a-button>
          </div>
        </div>
      </template>

      <div v-else-if="!isLoading && !searchKeyword" class="no-posts">
        <p v-if="activeTab === 'myposts'">您还没有发布过帖子，快来发布第一个吧！</p>
        <p v-else-if="activeTab === 'favourites'">您还没有收藏过帖子，快去收藏感兴趣的内容吧！</p>
        <p v-else>暂无帖子，快来发布第一个吧！</p>
      </div>

      <!-- 加载更多提示 -->
      <div v-if="isLoading && posts.length > 0" class="loading-indicator">
        <a-spin />
        <span style="margin-left: 8px;">加载中...</span>
      </div>
      <div v-if="!isLoading && posts.length > 0 && posts.length >= pagination.total && pagination.total > 0" class="loading-indicator">
        <span>--- 我是有底线的 ---</span>
      </div>
    </div>

    <!-- 发布帖子弹窗 -->
    <PostForm
      :visible="showPostForm"
      @cancel="showPostForm = false"
      @success="handlePostSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { SearchOutlined } from '@ant-design/icons-vue'
import { listPostVoByPage, addPost, listMyPostVoByPage } from '@/api/postController'
import { doThumb } from '@/api/thumbController'
import { doPostFavour, listMyFavourPostByPage } from '@/api/postFavourController'
import { showSuccess, showError, showWarning } from '@/utils/message'

const router = useRouter()

const goToWriteArticle = () => {
  router.push('/post/write')
}

// 发布相关
const postContent = ref('')
const showPostForm = ref(false)
const showEmojiPicker = ref(false)
const activeEmojiTab = ref('face')

const emojiCategoryTabs = computed(() => {
  const tabs = { ...emojiCategories }
  delete tabs.recent
  return tabs
})

const recentEmojis = ref<string[]>(['👍', '😊', '😂', '❤️'])

// 导航标签
const activeTab = ref('recommend')
const navTabs = [
  { key: 'myposts', label: '帖子' },
  { key: 'follow', label: '关注' },
  { key: 'recommend', label: '推荐' },
  { key: 'featured', label: '精选' },
  { key: 'hot', label: '热门' },
  { key: 'favourites', label: '收藏' }
]

// 帖子数据
const posts = ref<API.PostVO[]>([])
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0
})
const isLoading = ref(false)

// 搜索相关
const searchKeyword = ref('')
const searchResults = ref<API.PostVO[]>([])
const isSearching = ref(false)

// 显示的帖子列表（搜索时显示搜索结果，否则显示全部帖子）
const displayPosts = computed(() => {
  return searchKeyword.value ? searchResults.value : posts.value
})

// 当前标签页标题
const currentTabLabel = computed(() => {
  const tab = navTabs.find(t => t.key === activeTab.value)
  return tab?.label || '推荐'
})

// 加载帖子
const loadPosts = async (loadMore = false) => {
  if (isLoading.value) return

  if (loadMore && posts.value.length >= pagination.total && pagination.total > 0) {
    return
  }

  isLoading.value = true

  if (loadMore) {
    pagination.current++
  } else {
    pagination.current = 1
  }

  try {
    const params: any = {
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc'
    }

    let res: any

    // 根据当前标签页选择不同的API
    if (activeTab.value === 'myposts') {
      // 获取我的帖子
      const response = await listMyPostVoByPage(params)
      res = response.data
    } else if (activeTab.value === 'favourites') {
      // 获取我的收藏
      const response = await listMyFavourPostByPage(params)
      res = response.data
    } else {
      // 获取所有帖子（推荐、关注、精选、热门等）
      const response = await listPostVoByPage(params)
      res = response.data
    }

    if (res?.code === 0) {
      const records = res.data?.records || []
      if (loadMore) {
        posts.value.push(...records)
      } else {
        posts.value = records
      }
      pagination.total = Number(res.data?.totalRow) || 0
    } else {
      if (loadMore) pagination.current--
      console.error('API返回格式错误:', res)
      if (res?.message && res.message.toLowerCase() !== 'ok') {
        showError(res.message)
      } else if (!res?.message && res?.code !== 0) {
        showError('加载帖子列表失败')
      }
    }
  } catch (error) {
    if (loadMore) pagination.current--
    console.error('加载帖子失败:', error)
    showError('网络错误，请稍后重试')
  } finally {
    isLoading.value = false
  }
}

// 发布帖子
const handlePublish = async () => {
  if (!postContent.value.trim()) {
    showWarning('请输入内容')
    return
  }

  try {
    const { data: res } = await addPost({
      title: '',
      content: postContent.value,
      tags: []
    })

    if (res.code === 0) {
      showSuccess('发布成功')
      postContent.value = ''
      loadPosts()
    } else {
      showError(res.message || '发布失败')
    }
  } catch (error) {
    showError('发布失败')
  }
}

// 帖子发布成功回调
const handlePostSuccess = () => {
  showPostForm.value = false
  loadPosts()
}

// 表情点击处理
const handleEmojiClick = (event: Event) => {
  event.stopPropagation()
  showEmojiPicker.value = !showEmojiPicker.value
}

// 选择表情
const selectEmoji = (emoji: string) => {
  postContent.value += emoji

  // 添加到最近使用
  if (!recentEmojis.value.includes(emoji)) {
    recentEmojis.value.unshift(emoji)
    if (recentEmojis.value.length > 8) {
      recentEmojis.value = recentEmojis.value.slice(0, 8)
    }
  }

  showEmojiPicker.value = false
}

// 表情分类
const emojiCategories = {
  recent: {
    name: '最近使用',
    icon: '🕐',
    emojis: recentEmojis
  },
  face: {
    name: '表情与角色',
    icon: '😊',
    emojis: ref([
      '😀', '😃', '😄', '😁', '😆', '😅', '🤣', '😂', '🙂',
      '🙃', '😉', '😊', '😇', '🥰', '😍', '🤩', '😘', '😗',
      '☺️', '😚', '😙', '🥲', '😋', '😛', '😜', '🤪', '😝',
      '🤑', '🤗', '🤭', '🤫', '🤔', '🤐', '🤨', '😐', '😑',
      '😶', '😏', '😒', '🙄', '😬', '🤥', '😔', '😪', '🤤',
      '😴', '😷', '🤒', '🤕', '🤢', '🤮', '🤧', '🥵', '🥶',
      '🥴', '😵', '🤯', '🤠', '🥳', '🥸', '😎', '🤓', '🧐'
    ])
  },
  gesture: {
    name: '手势',
    icon: '👍',
    emojis: ref([
      '👍', '👎', '👌', '🤌', '🤏', '✌️', '🤞', '🤟',
      '🤘', '🤙', '👈', '👉', '👆', '🖕', '👇', '☝️',
      '👋', '🤚', '🖐️', '✋', '🖖', '👏', '🙌', '🤝'
    ])
  },
  heart: {
    name: '爱心',
    icon: '❤️',
    emojis: ref([
      '❤️', '🧡', '💛', '💚', '💙', '💜', '🖤', '🤍',
      '🤎', '💔', '❣️', '💕', '💞', '💓', '💗', '💖',
      '💘', '💝', '💟', '♥️', '💯', '💢', '💥', '💫'
    ])
  }
}

// 监听标签页切换
const handleTabChange = (tabKey: string) => {
  activeTab.value = tabKey
  posts.value = [] // 立即清空以获得更好的用户体验
  // 重置分页状态
  pagination.current = 1
  pagination.total = 0
  // 清空搜索结果
  searchKeyword.value = ''
  searchResults.value = []
  loadPosts()
}

// 解析标签
const parseTags = (tags: string[] | string | undefined) => {
  if (!tags) return []
  if (Array.isArray(tags)) return tags
  if (typeof tags === 'string') {
    try {
      return JSON.parse(tags)
    } catch {
      return [tags]
    }
  }
  return []
}

// 提取列表缩略图：优先使用封面图，否则取正文 Markdown 中的第一张图片
const coverImageCache = new WeakMap<API.PostVO, string | null>()
const getCoverImage = (post: API.PostVO) => {
  if (coverImageCache.has(post)) {
    return coverImageCache.get(post)
  }
  const firstMarkdownImage = post.content?.match(/!\[[^\]]*\]\(([^)\s]+)\)/)?.[1]
  const cover = post.coverImage || firstMarkdownImage || null
  coverImageCache.set(post, cover)
  return cover
}

// 列表预览截断长度
const PREVIEW_LENGTH = 100

// 把正文 Markdown 剥离为纯文本（移除图片、保留链接文字、去掉标题/加粗等符号）
const stripMarkdown = (content: string | undefined) => {
  if (!content) return ''
  return content
    .replace(/!\[[^\]]*\]\([^)]*\)/g, '')     // 移除图片 ![alt](url)
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')   // 链接只保留文字 [text](url) -> text
    .replace(/^#{1,6}\s+/gm, '')               // 移除标题符号 #
    .replace(/(\*\*|__|\*|_|`|~~)/g, '')       // 移除加粗/斜体/行内代码/删除线符号
    .replace(/^>\s?/gm, '')                    // 移除引用符号 >
    .replace(/\s+/g, ' ')                      // 折叠所有空白为单个空格
    .trim()
}

// 列表预览文本
const getPreviewText = (content: string | undefined) => truncateText(stripMarkdown(content), PREVIEW_LENGTH)

// 预览是否被截断（用于决定是否展示"查看全文"）
const isPreviewTruncated = (content: string | undefined) => stripMarkdown(content).length > PREVIEW_LENGTH

// 截断文本
const truncateText = (text: string | undefined, length = 100) => {
  if (!text) return '';
  if (text.length > length) {
    return text.substring(0, length) + '...';
  }
  return text;
};

// 格式化时间
const formatTime = (time: string | undefined) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return `${Math.floor(diff / 86400000)}天前`
}

// 点击外部关闭表情选择器
const handleClickOutside = (event: Event) => {
  const target = event.target as HTMLElement
  if (!target.closest('.emoji-container')) {
    showEmojiPicker.value = false
  }
}

// 搜索功能
const handleSearch = async () => {
  if (!searchKeyword.value.trim()) {
    searchResults.value = []
    return
  }

  isSearching.value = true
  try {
    // 在本地帖子中搜索
    const keyword = searchKeyword.value.toLowerCase()
    searchResults.value = posts.value.filter(post => {
      const title = (post.title || '').toLowerCase()
      const content = (post.content || '').toLowerCase()
      const userName = (post.user?.userName || '').toLowerCase()
      const tags = parseTags(post.tags).join(' ').toLowerCase()

      return title.includes(keyword) ||
             content.includes(keyword) ||
             userName.includes(keyword) ||
             tags.includes(keyword)
    })
  } catch (error) {
    console.error('搜索失败:', error)
    showError('搜索失败，请稍后重试')
  } finally {
    isSearching.value = false
  }
}

const handleSearchInput = () => {
  // 实时搜索，防抖处理
  if (searchInputTimer.value) {
    clearTimeout(searchInputTimer.value)
  }
  searchInputTimer.value = setTimeout(() => {
    if (searchKeyword.value.trim()) {
      handleSearch()
    } else {
      searchResults.value = []
    }
  }, 300)
}

const searchInputTimer = ref<NodeJS.Timeout | null>(null)

// 点击帖子跳转到详情页
const handlePostClick = (postId: string | undefined) => {
  if (postId) {
    router.push(`/post/${postId}`)
  }
}

// 点赞处理
const handleThumb = async (post: API.PostVO) => {
  if (!post.id) return

  try {
    const { data: res } = await doThumb({
      targetId: post.id,
      type: 'POST'
    })

    if (res?.code === 0) {
      // 切换点赞状态
      post.hasThumb = !post.hasThumb
      post.thumbNum = (post.thumbNum || 0) + (post.hasThumb ? 1 : -1)
      showSuccess(post.hasThumb ? '点赞成功' : '取消点赞')
    } else {
      showError(res?.message || '操作失败')
    }
  } catch (error) {
    console.error('点赞失败:', error)
    showError('网络错误，请稍后重试')
  }
}

// 收藏处理
const handleFavour = async (post: API.PostVO) => {
  if (!post.id) return

  try {
    const { data: res } = await doPostFavour({
      postId: post.id
    })

    if (res?.code === 0) {
      // 切换收藏状态
      post.hasFavour = !post.hasFavour
      post.favourNum = (post.favourNum || 0) + (post.hasFavour ? 1 : -1)
      showSuccess(post.hasFavour ? '收藏成功' : '取消收藏')
    } else {
      showError(res?.message || '操作失败')
    }
  } catch (error) {
    console.error('收藏失败:', error)
    showError('网络错误，请稍后重试')
  }
}

// 分享处理
const handleShare = (post: API.PostVO) => {
  const shareUrl = `${window.location.origin}/post/${post.id}`
  const shareText = post.title || post.content?.substring(0, 50) + '...' || '分享帖子'

  if (navigator.share) {
    navigator.share({
      title: shareText,
      text: post.content?.substring(0, 100) + '...' || '',
      url: shareUrl
    }).catch(() => {
      // 如果分享失败，复制链接
      copyToClipboard(shareUrl)
    })
  } else {
    // 复制链接到剪贴板
    copyToClipboard(shareUrl)
  }
}

// 复制到剪贴板
const copyToClipboard = (text: string) => {
  if (navigator.clipboard) {
    navigator.clipboard.writeText(text).then(() => {
      showSuccess('链接已复制到剪贴板')
    }).catch(() => {
      showError('复制失败')
    })
  } else {
    // 兼容旧浏览器
    const textArea = document.createElement('textarea')
    textArea.value = text
    document.body.appendChild(textArea)
    textArea.select()
    try {
      document.execCommand('copy')
      showSuccess('链接已复制到剪贴板')
    } catch (err) {
      showError('复制失败')
    }
    document.body.removeChild(textArea)
  }
}

// 滚动加载
const handleScroll = () => {
  const { scrollTop, clientHeight, scrollHeight } = document.documentElement
  if (scrollTop + clientHeight >= scrollHeight - 100) {
    loadPosts(true)
  }
}

onMounted(async () => {
  console.log('组件挂载，开始加载帖子')
  await loadPosts()
  document.addEventListener('click', handleClickOutside)
  window.addEventListener('scroll', handleScroll)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  window.removeEventListener('scroll', handleScroll)
})
</script>

<style scoped>
.community-page {
  max-width: 1000px;
  margin: 0 auto;
  padding: 0;
  background: #f5f5f5;
  min-height: calc(100vh - 120px);
}

/* 搜索区域样式 */
.search-section {
  background: white;
  margin-bottom: 12px;
  border-radius: 8px;
  padding: 16px 20px;
}

.search-container {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-input {
  flex: 1;
  border-radius: 20px;
}

.search-btn {
  border-radius: 20px;
  padding: 4px 16px;
  height: 32px;
}

.no-results {
  text-align: center;
  color: #999;
  margin-top: 12px;
  font-size: 14px;
}

/* 发布区域 */
.publish-section {
  background: white;
  margin-bottom: 12px;
  border-radius: 8px;
  /* overflow: hidden; */ /* 解决表情选择器被遮挡的问题 */
}

.publish-tabs {
  display: flex;
  align-items: center;
  padding: 16px 20px 0;
  border-bottom: 1px solid #f0f0f0;
}

.tab-btn {
  margin-right: 16px;
  border: none;
  background: none;
  color: #666;
  font-weight: 500;
}

.tab-btn.active {
  color: #1890ff;
  background: #e6f7ff;
  border-radius: 16px;
}

.right-actions {
  margin-left: auto;
}

.ask-text {
  color: #1890ff;
  font-size: 14px;
}

.publish-input {
  padding: 16px 20px 20px;
}

.post-textarea {
  border: none;
  box-shadow: none;
  resize: none;
  font-size: 16px;
}

.post-textarea:focus {
  border: none;
  box-shadow: none;
}

.publish-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}

.action-buttons {
  display: flex;
  gap: 16px;
}

.action-btn {
  color: #666;
  border: none;
  padding: 4px 8px;
}

.publish-btn {
  border-radius: 20px;
  padding: 4px 20px;
}

/* 表情选择器 */
.emoji-container {
  position: relative;
}

.emoji-picker {
  position: absolute;
  top: 100%;
  left: 0;
  background: white;
  border: 1px solid #e8e8e8;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  z-index: 99999;
  margin-top: 8px;
  width: 320px;
  max-height: 400px;
  overflow: hidden;
}

.emoji-tabs {
  display: flex;
  border-bottom: 1px solid #f0f0f0;
  padding: 8px 12px;
  background: #fafafa;
}

.emoji-tab {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  cursor: pointer;
  margin-right: 4px;
  transition: background-color 0.2s;
}

.emoji-tab:hover {
  background-color: #f0f0f0;
}

.emoji-tab.active {
  background-color: #1890ff;
}

.emoji-tab.active .tab-icon {
  filter: grayscale(1) brightness(0) invert(1);
}

.tab-icon {
  font-size: 20px;
}

.emoji-content {
  padding: 12px;
  max-height: 320px;
  overflow-y: auto;
}

.emoji-category-title {
  font-size: 14px;
  font-weight: 500;
  color: #666;
  margin-bottom: 12px;
  padding-left: 4px;
}

.emoji-grid {
  display: grid;
  grid-template-columns: repeat(9, 1fr);
  gap: 4px;
}

.emoji-item {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 20px;
  transition: background-color 0.2s;
}

.emoji-item:hover {
  background-color: #f0f8ff;
  transform: scale(1.1);
}

/* 导航标签 */
.nav-tabs {
  display: flex;
  align-items: center;
  background: white;
  padding: 12px 20px;
  margin-bottom: 12px;
  border-radius: 8px;
}

.nav-tab {
  margin-right: 24px;
  border: none;
  background: none;
  color: #666;
  font-weight: 500;
  padding: 8px 0;
  position: relative;
}

.nav-tab.active {
  color: #1890ff;
}

.nav-tab.active::after {
  content: '';
  position: absolute;
  bottom: -12px;
  left: 50%;
  transform: translateX(-50%);
  width: 20px;
  height: 3px;
  background: #1890ff;
  border-radius: 2px;
}

.nav-right {
  margin-left: auto;
}

.settings-btn {
  color: #1890ff;
  border: none;
}

/* 帖子列表 */
.posts-list {
  background: white;
  border-radius: 8px;
}

.no-posts {
  padding: 40px 20px;
  text-align: center;
  color: #999;
}

.post-item {
  padding: 20px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background-color 0.2s;
}

.post-item:hover {
  background-color: #fafafa;
}

.post-item:last-child {
  border-bottom: none;
}

.post-header {
  margin-bottom: 12px;
}

.user-info {
  display: flex;
  align-items: center;
}

.user-details {
  margin-left: 12px;
}

.username {
  font-weight: 500;
  color: #333;
  margin-bottom: 4px;
}

.post-meta {
  font-size: 12px;
  color: #999;
}

.post-body {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}

.post-content {
  flex: 1;
  min-width: 0;
}

.post-cover {
  width: 200px;
  aspect-ratio: 16 / 9;
  flex-shrink: 0;
  object-fit: cover;
  border-radius: 8px;
  background: #f0f0f0;
}

.view-full {
  display: inline-block;
  margin-top: 4px;
  color: #1890ff;
  font-size: 13px;
  cursor: pointer;
}

.view-full:hover {
  text-decoration: underline;
}

.post-top-comment {
  margin-top: 12px;
  padding: 8px 12px;
  background: #fafafa;
  border-radius: 6px;
  font-size: 13px;
  color: #666;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.top-comment-user {
  color: #333;
  font-weight: 500;
}

.post-title {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
  line-height: 1.4;
  cursor: pointer;
  transition: color 0.2s;
}

.post-title:hover {
  color: #1890ff;
}

.post-text {
  color: #333;
  line-height: 1.6;
  font-size: 14px;
  margin-bottom: 12px;
}

.post-tags {
  margin-top: 12px;
}

.post-tag {
  margin-right: 8px;
  margin-bottom: 4px;
}

.post-actions {
  display: flex;
  gap: 24px;
}

.action-item {
  color: #666;
  border: none;
  padding: 4px 8px;
  font-size: 13px;
}

.action-item:hover {
  color: #1890ff;
  background: #f0f8ff;
}

.action-item.active {
  color: #1890ff;
  background: #f0f8ff;
}

.loading-indicator {
  padding: 20px;
  text-align: center;
  color: #999;
}

/* 响应式 */
@media (max-width: 768px) {
  .community-page {
    padding: 0 12px;
  }

  .publish-tabs {
    padding: 12px 16px 0;
  }

  .publish-input {
    padding: 12px 16px 16px;
  }

  .nav-tabs {
    padding: 8px 16px;
    overflow-x: auto;
  }

  .nav-tab {
    margin-right: 16px;
    white-space: nowrap;
  }

  .post-item {
    padding: 16px;
  }

  .post-cover {
    width: 120px;
  }
}
</style>
