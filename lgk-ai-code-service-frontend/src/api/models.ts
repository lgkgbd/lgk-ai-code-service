export type Picture = {
  title?: string;
  url?: string;
};

export type PostVO = {
  content?: string;
  createTime?: string;
  id?: string;
  thumbNum?: number;
  title?: string;
  updateTime?: string;
  userId?: string;
};

export type UserVO = {
  createTime?: string;
  id?: string;
  updateTime?: string;
  userAvatar?: string;
  userName?: string;
  userProfile?: string;
  userRole?: string;
};

export type Video = {
  cover?: string;
  url?: string;
  title?: string;
  playCount?: string;
  duration?: string;
  author?: string;
  arcurl?: string;
};

export type SearchVO = {
  postList?: PostVO[];
  userList?: UserVO[];
  pictureList?: Picture[];
  videoList?: Video[];
  dataList?: any[];
};

export type BaseResponseSearchVO = {
  code?: number;
  data?: SearchVO;
  message?: string;
};

export type SearchRequest = {
  current?: number;
  pageSize?: number;
  searchText?: string;
  sortField?: string;
  sortOrder?: string;
  type?: string;
};