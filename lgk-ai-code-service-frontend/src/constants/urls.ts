import { CodeGenTypeEnum } from './codeGenType'

// 使用相对路径，通过vite代理解决同源问题

export const DEPLOY_DOMAIN = import.meta.env.VITE_DEPLOY_DOMAIN || 'http://localhost'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'
export const STATIC_BASE_URL = `${API_BASE_URL}/static`

// 获取部署应用的完整URL
export const getDeployUrl = (deployKey: string) => {
  return `${DEPLOY_DOMAIN}/${deployKey}`
}

// 获取静态资源预览URL
export const getStaticPreviewUrl = (codeGenType: string, appId: string) => {
  const baseUrl = `${STATIC_BASE_URL}/${codeGenType}_${appId}/`
  if (codeGenType === CodeGenTypeEnum.VUE_PROJECT) {
    return `${baseUrl}dist/index.html`
  }
  return baseUrl
}


