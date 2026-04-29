// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /s/${param0} */
export async function resolve(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.resolveParams,
  options?: { [key: string]: any }
) {
  const { code: param0, ...queryParams } = params
  return request<API.StreamingResponseBody>(`/s/${param0}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 DELETE /s/${param0} */
export async function deleteShortLink(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteShortLinkParams,
  options?: { [key: string]: any }
) {
  const { code: param0, ...queryParams } = params
  return request<API.BaseResponseBoolean>(`/s/${param0}`, {
    method: 'DELETE',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /s/create */
export async function createShortLink(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.createShortLinkParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseString>('/s/create', {
    method: 'POST',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}
