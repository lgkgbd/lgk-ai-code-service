// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /thumb/ */
export async function doThumb(body: API.ThumbAddRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseInteger>('/thumb/', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
