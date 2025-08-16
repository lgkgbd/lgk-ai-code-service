import ACCESS_ENUM from './accessEnum'
import checkAccess from './checkAccess'
import { useLoginUserStore } from '@/stores/loginUser'

type DirectiveBinding = {
  value: string
}

/**
 * 权限指令
 * 使用方式：v-access="ACCESS_ENUM.ADMIN"
 */
export const accessDirective = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const loginUserStore = useLoginUserStore()
    const needAccess = binding.value
    
    if (!checkAccess(loginUserStore.loginUser, needAccess)) {
      el.style.display = 'none'
    }
  },
  updated(el: HTMLElement, binding: DirectiveBinding) {
    const loginUserStore = useLoginUserStore()
    const needAccess = binding.value
    
    if (!checkAccess(loginUserStore.loginUser, needAccess)) {
      el.style.display = 'none'
    } else {
      el.style.display = ''
    }
  }
}

/**
 * 检查当前用户是否有指定权限
 * @param needAccess 需要的权限
 * @returns boolean
 */
export const hasAccess = (needAccess: string): boolean => {
  const loginUserStore = useLoginUserStore()
  return checkAccess(loginUserStore.loginUser, needAccess)
}

/**
 * 检查当前用户是否为管理员
 * @returns boolean
 */
export const isAdmin = (): boolean => {
  const loginUserStore = useLoginUserStore()
  return loginUserStore.loginUser?.userRole === ACCESS_ENUM.ADMIN
}

/**
 * 检查当前用户是否已登录
 * @returns boolean
 */
export const isLoggedIn = (): boolean => {
  const loginUserStore = useLoginUserStore()
  return loginUserStore.loginUser?.userRole !== ACCESS_ENUM.NOT_LOGIN
}

/**
 * 获取当前用户信息
 * @returns 用户信息
 */
export const getCurrentUser = () => {
  const loginUserStore = useLoginUserStore()
  return loginUserStore.loginUser
}

/**
 * 权限组合检查
 * @param permissions 权限数组，满足任一权限即可
 * @returns boolean
 */
export const hasAnyPermission = (permissions: string[]): boolean => {
  return permissions.some(permission => hasAccess(permission))
}

/**
 * 权限组合检查
 * @param permissions 权限数组，必须满足所有权限
 * @returns boolean
 */
export const hasAllPermissions = (permissions: string[]): boolean => {
  return permissions.every(permission => hasAccess(permission))
}

export { ACCESS_ENUM, checkAccess }
