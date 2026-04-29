// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 创建桶 POST /minio/bucket/create */
export async function createBucket(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.createBucketParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseString>('/minio/bucket/create', {
    method: 'POST',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 删除桶（桶必须为空） DELETE /minio/bucket/delete */
export async function deleteBucket(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteBucketParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseString>('/minio/bucket/delete', {
    method: 'DELETE',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 检查桶是否存在 GET /minio/bucket/exists */
export async function bucketExists(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.bucketExistsParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/minio/bucket/exists', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 列出所有桶 GET /minio/bucket/list */
export async function listBuckets(options?: { [key: string]: any }) {
  return request<API.BaseResponseListString>('/minio/bucket/list', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 删除对象 DELETE /minio/object/delete */
export async function deleteObject(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteObjectParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseString>('/minio/object/delete', {
    method: 'DELETE',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 列举桶内对象 GET /minio/object/list */
export async function listObjects(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listObjectsParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListObjectInfo>('/minio/object/list', {
    method: 'GET',
    params: {
      // maxKeys has a default value: 100
      maxKeys: '100',
      ...params,
    },
    ...(options || {}),
  })
}

/** 上传对象，返回短链访问 URL POST /minio/object/upload */
export async function uploadObject(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.uploadObjectParams,
  body: {},
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseString>('/minio/object/upload', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    params: {
      // prefix has a default value: uploads
      prefix: 'uploads',
      ...params,
    },
    data: body,
    ...(options || {}),
  })
}

/** 获取对象临时预签名 URL（管理侧使用） GET /minio/object/url */
export async function getPresignedUrl(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getPresignedUrlParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseString>('/minio/object/url', {
    method: 'GET',
    params: {
      // expireSeconds has a default value: 604800
      expireSeconds: '604800',
      ...params,
    },
    ...(options || {}),
  })
}
