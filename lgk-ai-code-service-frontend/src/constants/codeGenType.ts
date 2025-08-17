/**
 * 代码生成类型枚举
 */
export enum CodeGenTypeEnum {
  HTML = 'html',
  MULTI_FILE = 'multi_file'
}

/**
 * 代码生成类型配置
 */
export const CODE_GEN_TYPE_CONFIG = {
  [CodeGenTypeEnum.HTML]: {
    label: '原生 HTML 模式',
    value: CodeGenTypeEnum.HTML,
    color: '#1890ff',
    icon: 'html5-outlined'
  },
  [CodeGenTypeEnum.MULTI_FILE]: {
    label: '原生多文件模式',
    value: CodeGenTypeEnum.MULTI_FILE,
    color: '#52c41a',
    icon: 'file-outlined'
  }
} as const

/**
 * 获取代码生成类型配置
 * @param type 生成类型
 * @returns 类型配置
 */
export function getCodeGenTypeConfig(type: string) {
  return CODE_GEN_TYPE_CONFIG[type as CodeGenTypeEnum] || {
    label: '未知类型',
    value: type,
    color: '#d9d9d9',
    icon: 'question-circle-outlined'
  }
}

/**
 * 获取所有代码生成类型选项
 * @returns 类型选项数组
 */
export function getCodeGenTypeOptions() {
  return Object.values(CODE_GEN_TYPE_CONFIG).map(config => ({
    label: config.label,
    value: config.value
  }))
}
