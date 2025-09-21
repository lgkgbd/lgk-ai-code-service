// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /postFavour/ */
export async function doPostFavour(
  body: API.PostFavourAddRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseInteger>('/postFavour/', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /postFavour/my/list/page */
export async function listMyFavourPostByPage(
  body: API.PostQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePagePostVO>('/postFavour/my/list/page', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
