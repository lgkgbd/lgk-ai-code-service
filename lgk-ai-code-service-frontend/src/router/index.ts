import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '@/pages/HomePage.vue'
import UserLoginPage from '@/pages/user/UserLoginPage.vue'
import UserRegisterPage from '@/pages/user/UserRegisterPage.vue'
import UserManagePage from '@/pages/admin/UserManagePage.vue'
import AppManagePage from '@/pages/admin/AppManagePage.vue'
import ChatManagePage from '@/pages/admin/ChatManagePage.vue'
import AppChatPage from '@/pages/AppChatPage.vue'
import AppEditPage from '@/pages/AppEditPage.vue'
import AboutPage from '@/pages/AboutPage.vue'
import ACCESS_ENUM from '@/access/accessEnum.ts'
import { useLoginUserStore } from '@/stores/loginUser'
import checkAccess from '@/access/checkAccess'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: '主页',
      component: HomePage,
      meta: {
        access: ACCESS_ENUM.NOT_LOGIN,
      },
    },
    {
      path: '/user/login',
      name: '用户登录',
      component: UserLoginPage,
      meta: {
        access: ACCESS_ENUM.NOT_LOGIN,
      },
    },
    {
      path: '/user/register',
      name: '用户注册',
      component: UserRegisterPage,
      meta: {
        access: ACCESS_ENUM.NOT_LOGIN,
      },
    },
    {
      path: '/admin/userManage',
      name: 'adminUserManage',
      component: UserManagePage,
      meta: {
        access: ACCESS_ENUM.ADMIN,
      },
    },
    {
      path: '/admin/appManage',
      name: 'adminAppManage',
      component: AppManagePage,
      meta: {
        access: ACCESS_ENUM.ADMIN,
      },
    },
    {
      path: '/admin/chatManage',
      name: 'adminChatManage',
      component: ChatManagePage,
      meta: {
        access: ACCESS_ENUM.ADMIN,
      },
    },
    {
      path: '/app/chat/:id',
      name: 'appChat',
      component: AppChatPage,
      meta: {
        access: ACCESS_ENUM.USER,
      },
    },
    {
      path: '/app/edit/:id',
      name: 'appEdit',
      component: AppEditPage,
      meta: {
        access: ACCESS_ENUM.USER,
      },
    },
    {
      path: '/about',
      name: '关于',
      component: AboutPage,
      meta: {
        access: ACCESS_ENUM.NOT_LOGIN,
      },
    },
  ],
})

// 路由守卫
router.beforeEach(async (to, from, next) => {
  const loginUserStore = useLoginUserStore()

  // 获取当前登录用户信息
  if (!loginUserStore.loginUser || loginUserStore.loginUser.userName === '未登录') {
    await loginUserStore.fetchLoginUser()
  }

  // 检查权限
  const needAccess = (to.meta?.access as string) ?? ACCESS_ENUM.NOT_LOGIN
  const hasAccess = checkAccess(loginUserStore.loginUser, needAccess)

  if (!hasAccess) {
    // 无权限，跳转到登录页
    if (needAccess !== ACCESS_ENUM.NOT_LOGIN) {
      next('/user/login')
      return
    }
  }

  next()
})

export default router
