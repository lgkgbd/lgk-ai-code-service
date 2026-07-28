// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /word/review/count */
export async function count(options?: { [key: string]: any }) {
  return request<API.BaseResponseLong>('/word/review/count', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /word/review/submit */
export async function submit(body: API.WordReviewSubmitRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseInteger>('/word/review/submit', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /word/review/today */
export async function today(options?: { [key: string]: any }) {
  return request<API.BaseResponseListWordReviewCardVO>('/word/review/today', {
    method: 'GET',
    ...(options || {}),
  })
}
