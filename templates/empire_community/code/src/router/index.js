import { createRouter, createWebHashHistory } from 'vue-router'
import Home from '@/pages/Home.vue'
import TopicDetail from '@/pages/TopicDetail.vue'
import CreatePost from '@/pages/CreatePost.vue'
import Profile from '@/pages/Profile.vue'

const routes = [
  { path: '/', name: 'Home', component: Home },
  { path: '/topic/:id', name: 'TopicDetail', component: TopicDetail },
  { path: '/create', name: 'CreatePost', component: CreatePost },
  { path: '/profile', name: 'Profile', component: Profile }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
