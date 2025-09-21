package com.lgk.lgkaicodeservice.mapper;

import com.lgk.lgkaicodeservice.model.entity.Post;
import com.mybatisflex.core.BaseMapper;
import com.lgk.lgkaicodeservice.model.entity.PostFavour;
import com.mybatisflex.core.paginate.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.constant.FuncName;

/**
 * 帖子收藏表 映射层。
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
public interface PostFavourMapper extends BaseMapper<PostFavour> {

//    /**
//     * 分页查询收藏帖子列表
//     *
//     * @param page
//     * @param queryWrapper
//     * @param favourUserId
//     * @return
//     */
//    // 修改方法参数
//    // 在 PostFavourMapper.java 中
//@Select("""
//    SELECT p.* FROM post p
//    INNER JOIN post_favour pf ON p.id = pf.post_id
//    WHERE pf.user_id = #{favourUserId}
//    ${ew.customSqlSegment}
//    """)
//Page<Post> listFavourPostByPage(Page<Post> page, @Param("ew") QueryWrapper queryWrapper,
//                                long favourUserId);


}
