// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /word/bookmarklet/script */
export async function bookmarkletScript(options?: { [key: string]: any }) {
  return request<string>('/word/bookmarklet/script', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /word/capture */
export async function capture(body: API.WordCaptureRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseListWordCardVO>('/word/capture', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /word/delete */
export async function deleteWord(body: API.DeleteRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/word/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /word/dict/search */
export async function dictSearch(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.dictSearchParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListWordDict>('/word/dict/search', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /word/get/vo */
export async function getWordCardVo(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getWordCardVOParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseWordCardVO>('/word/get/vo', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /word/list/page */
export async function listMyWordsByPage(
  body: API.WordQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageWordCardVO>('/word/list/page', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /word/note/update */
export async function updateNote(
  body: API.WordNoteUpdateRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/word/note/update', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /word/statistics */
export async function statistics(options?: { [key: string]: any }) {
  return request<API.BaseResponseWordStatisticsVO>('/word/statistics', {
    method: 'GET',
    ...(options || {}),
  })
}
