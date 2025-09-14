// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /search/all */
export async function searchAll(body: API.SearchRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseSearchVO>('/search/all', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
