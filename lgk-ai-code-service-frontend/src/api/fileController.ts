// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /file/upload */
export async function uploadFile(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.uploadFileParams,
  body: {},
  options?: { [key: string]: any }
) {
  const formData = new FormData()
  formData.append('file', body as Blob)

  return request<API.BaseResponseString>('/file/upload', {
    method: 'POST',
    params: {
      ...params,
    },
    data: formData,
    // By not specifying Content-Type, the browser will automatically set it to multipart/form-data with the correct boundary
    ...(options || {}),
  })
}
