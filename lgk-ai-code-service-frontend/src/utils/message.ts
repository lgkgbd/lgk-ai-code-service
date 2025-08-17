import { message } from 'ant-design-vue'

// 统一的消息配置
const messageConfig = {
  duration: 3,
  closable: true,
  onClick: () => {
    message.destroy()
  },
}

// 成功消息
export const showSuccess = (content: string) => {
  message.success({
    content,
    ...messageConfig,
  })
}

// 错误消息
export const showError = (content: string) => {
  message.error({
    content,
    ...messageConfig,
  })
}

// 警告消息
export const showWarning = (content: string) => {
  message.warning({
    content,
    ...messageConfig,
  })
}

// 信息消息
export const showInfo = (content: string) => {
  message.info({
    content,
    ...messageConfig,
  })
}
