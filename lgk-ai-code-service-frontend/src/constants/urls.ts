import { CodeGenTypeEnum } from './codeGenType'

export const API_BASE_URL = 'http://localhost:8123/api'
export const STATIC_BASE_URL = `${API_BASE_URL}/static`

// 获取静态资源预览URL
export const getStaticPreviewUrl = (codeGenType: string, appId: string) => {
  const baseUrl = `${STATIC_BASE_URL}/${codeGenType}_${appId}/`
  if (codeGenType === CodeGenTypeEnum.VUE_PROJECT) {
    return `${baseUrl}dist/index.html`
  }
  return baseUrl
}


