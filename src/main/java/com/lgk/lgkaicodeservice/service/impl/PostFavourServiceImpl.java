package com.lgk.lgkaicodeservice.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.lgk.lgkaicodeservice.model.entity.PostFavour;
import com.lgk.lgkaicodeservice.mapper.PostFavourMapper;
import com.lgk.lgkaicodeservice.service.PostFavourService;
import org.springframework.stereotype.Service;

/**
 * 帖子收藏表 服务层实现。
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@Service
public class PostFavourServiceImpl extends ServiceImpl<PostFavourMapper, PostFavour>  implements PostFavourService{

}
