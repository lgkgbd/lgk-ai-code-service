<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  addApp,
  listMyAppVoByPage,
  listGoodAppVoByPage
} from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'
import { getCodeGenTypeConfig } from '@/constants/codeGenType'

const router = useRouter()
const loginUserStore = useLoginUserStore()

// 用户提示词输入
const userPrompt = ref('')
const isCreating = ref(false)

// 我的应用列表
const myApps = reactive({
  data: [] as Record<string, any>[],
  loading: false,
  pagination: {
    current: 1,
    pageSize: 20,
    total: 0,
    showSizeChanger: false,
  },
  searchText: '',
})

// 精选应用列表
const featuredApps = reactive({
  data: [] as Record<string, any>[],
  loading: false,
  pagination: {
    current: 1,
    pageSize: 20,
    total: 0,
    showSizeChanger: false,
  },
  searchText: '',
})

// 快捷提示词 - 参考图片中的模板
const quickPrompts = reactive([
  {
    id: 1,
    title: '波普风电商页面',
    description: '创建一个具有波普艺术风格的电商页面，包含商品展示、购物车、用户登录等功能',
    content: '创建一个波普风电商页面，包含商品展示、购物车、用户登录、订单管理、支付流程等功能，界面要具有波普艺术风格，色彩鲜艳，设计独特'
  },
  {
    id: 2,
    title: '企业网站',
    description: '创建一个专业的企业官网，包含公司介绍、产品服务、新闻动态、联系方式等',
    content: '创建一个企业网站，包含公司介绍、产品服务、新闻动态、团队介绍、联系方式等模块，设计要专业简洁，体现企业形象'
  },
  {
    id: 3,
    title: '电商运营后台',
    description: '创建一个电商运营管理后台，包含订单管理、商品管理、用户管理、数据分析等',
    content: '创建一个电商运营后台，包含订单管理、商品管理、用户管理、库存管理、数据分析看板等功能，界面要简洁易用'
  },
  {
    id: 4,
    title: '暗黑话题社区',
    description: '创建一个暗黑主题的社区论坛，包含话题讨论、用户互动、内容分享等功能',
    content: '创建一个暗黑话题社区，包含话题讨论、用户互动、内容分享、评论系统、用户等级等功能，采用暗黑主题设计'
  },
])

// 创建应用
const handleCreateApp = async () => {
  if (!userPrompt.value.trim()) {
    message.warning({
      content: '请输入应用描述',
      duration: 3,
      closable: true,
      onClick: () => {
        message.destroy()
      },
    })
    return
  }

  if (!loginUserStore.loginUser || loginUserStore.loginUser.userName === '未登录') {
    message.warning({
      content: '请先登录',
      duration: 3,
      closable: true,
      onClick: () => {
        message.destroy()
      },
    })
    router.push('/user/login')
    return
  }

  isCreating.value = true
  try {
    const res = await addApp({ initPrompt: userPrompt.value })
    if (res.data.code === 0 && res.data.data) {
      message.success({
        content: '应用创建成功',
        duration: 3,
        closable: true,
        onClick: () => {
          message.destroy()
        },
      })
      // 跳转到对话页面，传递应用ID
      router.push(`/app/chat/${res.data.data}`)
    } else {
      message.error({
        content: res.data.message || '创建失败',
        duration: 3,
        closable: true,
        onClick: () => {
          message.destroy()
        },
      })
    }
  } catch (error) {
    message.error({
      content: '创建失败',
      duration: 3,
      closable: true,
      onClick: () => {
        message.destroy()
      },
    })
    console.error(error)
  } finally {
    isCreating.value = false
  }
}

// 加载我的应用列表
const loadMyApps = async (page = 1, searchText = '') => {
  if (!loginUserStore.loginUser || loginUserStore.loginUser.userName === '未登录') {
    return
  }

  myApps.loading = true
  try {
    const res = await listMyAppVoByPage({
      pageNum: page,
      pageSize: myApps.pagination.pageSize,
      appName: searchText,
    })
    if (res.data.code === 0 && res.data.data) {
      myApps.data = res.data.data.records || []
      myApps.pagination.total = res.data.data.totalRow || 0
      myApps.pagination.current = page
    }
  } catch (error) {
    console.error('加载我的应用失败:', error)
  } finally {
    myApps.loading = false
  }
}

// 加载精选应用列表
const loadFeaturedApps = async (page = 1, searchText = '') => {
  featuredApps.loading = true
  try {
    const res = await listGoodAppVoByPage({
      pageNum: page,
      pageSize: featuredApps.pagination.pageSize,
      appName: searchText,
    })
    if (res.data.code === 0 && res.data.data) {
      featuredApps.data = res.data.data.records || []
      featuredApps.pagination.total = res.data.data.totalRow || 0
      featuredApps.pagination.current = page
    }
  } catch (error) {
    console.error('加载精选应用失败:', error)
  } finally {
    featuredApps.loading = false
  }
}

// 我的应用分页变化
const handleMyAppsPageChange = (page: number) => {
  loadMyApps(page, myApps.searchText)
}

// 精选应用分页变化
const handleFeaturedAppsPageChange = (page: number) => {
  loadFeaturedApps(page, featuredApps.searchText)
}

// 我的应用搜索
const handleMyAppsSearch = (value: string) => {
  myApps.searchText = value
  loadMyApps(1, value)
}

// 精选应用搜索
const handleFeaturedAppsSearch = (value: string) => {
  featuredApps.searchText = value
  loadFeaturedApps(1, value)
}

// 查看应用对话
const viewAppChat = (appId: string | number) => {
  // 检查是否是自己的作品
  const isOwnApp = myApps.data.some(app => app.id === appId)
  const isFeaturedApp = featuredApps.data.some(app => app.id === appId)

  // 如果不是自己的作品，添加view参数
  if (!isOwnApp && isFeaturedApp) {
    router.push(`/app/chat/${appId}?view=1`)
  } else {
    router.push(`/app/chat/${appId}`)
  }
}

// 查看应用作品
const viewAppWork = (deployKey: string) => {
  const deployUrl = `http://localhost/${deployKey}`
  window.open(deployUrl, '_blank')
}

// 格式化时间
const formatTime = (timeStr: string) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  const hours = Math.floor(diff / (1000 * 60 * 60))
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))
  const weeks = Math.floor(days / 7)

  if (hours < 24) {
    return `${hours}小时前`
  } else if (days < 7) {
    return `${days}天前`
  } else if (weeks < 4) {
    return `${weeks}周前`
  } else {
    return date.toLocaleDateString()
  }
}

onMounted(() => {
  loadMyApps()
  loadFeaturedApps()
})
</script>

<template>
  <div class="home-page">
    <!-- 科技感背景 -->
    <div class="tech-background">
      <div class="gradient-overlay"></div>
      <div class="tech-pattern"></div>
    </div>

    <!-- 主要内容 -->
    <div class="main-content">
      <!-- 标题区域 -->
      <div class="hero-section">
        <h1 class="main-title">
          一句话 呈所想
          <span class="title-icon">🐱</span>
        </h1>
        <p class="sub-title">与 AI 对话轻松创建应用和网站</p>
      </div>

      <!-- 输入区域 -->
      <div class="input-section">
        <div class="input-container">
          <div class="input-wrapper">
            <a-input
              v-model:value="userPrompt"
              placeholder="帮我创建个人博客网站"
              size="large"
              :disabled="isCreating"
              @press-enter="handleCreateApp"
            />
          </div>
          <a-button
            type="primary"
            size="large"
            :loading="isCreating"
            @click="handleCreateApp"
            class="submit-btn"
          >
            <span class="submit-icon">↑</span>
          </a-button>
        </div>
      </div>

      <!-- 快捷提示词 -->
      <div class="quick-prompts">
        <div class="prompts-grid">
          <div
            v-for="prompt in quickPrompts"
            :key="prompt.id"
            class="prompt-card"
            @click="() => { userPrompt = prompt.content }"
          >
            {{ prompt.title }}
          </div>
        </div>
      </div>

      <!-- 我的应用 -->
      <div v-if="loginUserStore.loginUser" class="apps-section">
        <div class="section-header">
          <h2>我的应用</h2>
          <a-input-search
            v-model:value="myApps.searchText"
            placeholder="搜索应用名称"
            style="width: 200px"
            @search="handleMyAppsSearch"
          />
        </div>

        <div class="apps-grid">
          <div
            v-for="app in myApps.data"
            :key="app.id"
            class="app-card"
          >
            <div class="app-cover">
              <img
                v-if="app.cover"
                :src="app.cover"
                :alt="app.appName"
                class="cover-image"
              />
              <div v-else class="cover-placeholder">
                <code-outlined />
              </div>

              <!-- 操作按钮覆盖层 -->
              <div class="app-actions">
                <a-button
                  type="primary"
                  size="small"
                  @click.stop="viewAppChat(app.id!)"
                >
                  查看对话
                </a-button>
                <a-button
                  v-if="app.deployKey"
                  size="small"
                  @click.stop="viewAppWork(app.deployKey!)"
                >
                  查看作品
                </a-button>
              </div>
            </div>
            <div class="app-info">
              <div class="app-header">
                <div class="user-avatar">
                  <img
                    v-if="app.user?.userAvatar"
                    :src="app.user.userAvatar"
                    :alt="app.user.userName"
                    class="avatar-image"
                  />
                  <div v-else class="avatar-placeholder">
                    <user-outlined />
                  </div>
                </div>
                <div class="app-details">
                  <h3 class="app-name">{{ app.appName || '未命名应用' }}</h3>
                  <p class="app-creator">{{ app.user?.userName || '未知用户' }}</p>
                  <div class="app-tags">
                    <a-tag
                      v-if="app.codeGenType"
                      :color="getCodeGenTypeConfig(app.codeGenType)?.color"
                      size="small"
                    >
                      {{ getCodeGenTypeConfig(app.codeGenType)?.label }}
                    </a-tag>
                  </div>
                </div>
              </div>
              <p class="app-time">创建于 {{ formatTime(app.createTime!) }}</p>
            </div>
          </div>
        </div>

        <div class="pagination">
          <a-pagination
            v-model:current="myApps.pagination.current"
            :total="myApps.pagination.total"
            :page-size="myApps.pagination.pageSize"
            :show-size-changer="myApps.pagination.showSizeChanger"
            @change="handleMyAppsPageChange"
          />
        </div>
      </div>

      <!-- 精选应用 -->
      <div class="apps-section">
        <div class="section-header">
          <h2>精选应用</h2>
          <a-input-search
            v-model:value="featuredApps.searchText"
            placeholder="搜索应用名称"
            style="width: 200px"
            @search="handleFeaturedAppsSearch"
          />
        </div>

        <div class="apps-grid">
          <div
            v-for="app in featuredApps.data"
            :key="app.id"
            class="app-card"
          >
            <div class="app-cover">
              <img
                v-if="app.cover"
                :src="app.cover"
                :alt="app.appName"
                class="cover-image"
              />
              <div v-else class="cover-placeholder">
                <code-outlined />
              </div>

              <!-- 操作按钮覆盖层 -->
              <div class="app-actions">
                <a-button
                  type="primary"
                  size="small"
                  @click.stop="viewAppChat(app.id!)"
                >
                  查看对话
                </a-button>
                <a-button
                  v-if="app.deployKey"
                  size="small"
                  @click.stop="viewAppWork(app.deployKey!)"
                >
                  查看作品
                </a-button>
              </div>
            </div>
            <div class="app-info">
              <div class="app-header">
                <div class="user-avatar">
                  <img
                    v-if="app.user?.userAvatar"
                    :src="app.user.userAvatar"
                    :alt="app.user.userName"
                    class="avatar-image"
                  />
                  <div v-else class="avatar-placeholder">
                    <user-outlined />
                  </div>
                </div>
                <div class="app-details">
                  <h3 class="app-name">{{ app.appName || '未命名应用' }}</h3>
                  <p class="app-creator">{{ app.user?.userName || '未知用户' }}</p>
                  <div class="app-tags">
                    <a-tag
                      v-if="app.codeGenType"
                      :color="getCodeGenTypeConfig(app.codeGenType)?.color"
                      size="small"
                    >
                      {{ getCodeGenTypeConfig(app.codeGenType)?.label }}
                    </a-tag>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="pagination">
          <a-pagination
            v-model:current="featuredApps.pagination.current"
            :total="featuredApps.pagination.total"
            :page-size="featuredApps.pagination.pageSize"
            :show-size-changer="featuredApps.pagination.showSizeChanger"
            @change="handleFeaturedAppsPageChange"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
  position: relative;
  overflow-x: hidden;
}

/* 背景 */
.tech-background {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: -1;
}

.gradient-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(180deg, #ffffff 0%, #e6f3ff 30%, #4a90e2 100%);
}

.tech-pattern {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-image: repeating-linear-gradient(
    45deg,
    transparent,
    transparent 10px,
    rgba(255, 255, 255, 0.1) 10px,
    rgba(255, 255, 255, 0.1) 20px
  );
}

/* 主要内容 */
.main-content {
  position: relative;
  z-index: 1;
  padding: 40px 20px;
  max-width: 1200px;
  margin: 0 auto;
}

/* 标题区域 */
.hero-section {
  text-align: center;
  margin-bottom: 60px;
  padding: 60px 0;
}

.main-title {
  font-size: 3.5rem;
  font-weight: 700;
  color: #1a1a1a;
  margin: 0 0 20px 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.title-icon {
  font-size: 2rem;
}

.sub-title {
  font-size: 1.5rem;
  color: #1a1a1a;
  margin: 0;
  font-weight: 400;
}

/* 输入区域 */
.input-section {
  margin-bottom: 60px;
}

.input-container {
  display: flex;
  max-width: 600px;
  margin: 0 auto;
  gap: 12px;
  align-items: flex-end;
}

.input-wrapper {
  flex: 1;
  position: relative;
}

.input-container .ant-input {
  background: rgba(255, 255, 255, 0.9);
  border: none;
  border-radius: 12px;
  font-size: 16px;
  padding: 16px 20px;
  backdrop-filter: blur(10px);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.input-container .ant-input:focus {
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 4px 25px rgba(0, 0, 0, 0.15);
}

.input-container .ant-input::placeholder {
  color: rgba(0, 0, 0, 0.6);
}

.submit-btn {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  background: #4a90e2;
  border: none;
  box-shadow: 0 4px 15px rgba(74, 144, 226, 0.4);
  transition: all 0.3s ease;
}

.submit-btn:hover {
  background: #357abd;
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(74, 144, 226, 0.5);
}

.submit-icon {
  font-size: 18px;
  color: white;
}

/* 快捷提示词 */
.quick-prompts {
  margin-bottom: 60px;
}

.prompts-grid {
  display: flex;
  justify-content: center;
  gap: 16px;
  max-width: 800px;
  margin: 0 auto;
  flex-wrap: wrap;
}

.prompt-card {
  background: rgba(255, 255, 255, 0.9);
  border: none;
  border-radius: 12px;
  padding: 12px 20px;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);
  color: #1a1a1a;
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  backdrop-filter: blur(10px);
}

.prompt-card:hover {
  background: rgba(255, 255, 255, 0.95);
  transform: translateY(-3px);
  box-shadow: 0 6px 25px rgba(0, 0, 0, 0.15);
}

/* 应用区域 */
.apps-section {
  margin-bottom: 60px;
  padding: 40px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
}

.section-header h2 {
  font-size: 2rem;
  color: #1a1a1a;
  margin: 0;
  font-weight: 600;
  text-shadow: 0 1px 2px rgba(255, 255, 255, 0.8);
}

.apps-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 24px;
  margin-bottom: 30px;
}

.app-card {
  background: rgba(255, 255, 255, 0.9);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 15px rgba(0,0,0,0.1);
  transition: all 0.3s ease;
  position: relative;
  backdrop-filter: blur(10px);
}

.app-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 25px rgba(0,0,0,0.15);
  background: rgba(255, 255, 255, 0.95);
}

.app-cover {
  height: 160px;
  background: #f5f5f5;
  position: relative;
  overflow: hidden;
}

.cover-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ccc;
  font-size: 2rem;
}

.app-actions {
  position: absolute;
  top: 10px;
  right: 10px;
  display: flex;
  gap: 8px;
  z-index: 10;
}

.app-info {
  padding: 20px;
}

.app-header {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  overflow: hidden;
  margin-right: 10px;
  flex-shrink: 0;
}

.avatar-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #e0e0e0;
  color: #666;
  font-size: 1.2rem;
}

.app-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.app-name {
  font-size: 1.1rem;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-creator {
  font-size: 0.9rem;
  color: #666;
  margin: 0;
}

.app-tags {
  display: flex;
  gap: 4px;
  margin-top: 4px;
}

.app-time {
  font-size: 0.8rem;
  color: #999;
  margin: 0;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 30px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .main-title {
    font-size: 2.5rem;
  }

  .sub-title {
    font-size: 1.2rem;
  }

  .input-container {
    flex-direction: column;
  }

  .prompts-grid {
    grid-template-columns: 1fr;
  }

  .apps-grid {
    grid-template-columns: 1fr;
  }

  .section-header {
    flex-direction: column;
    gap: 16px;
    align-items: stretch;
  }
}
</style>
