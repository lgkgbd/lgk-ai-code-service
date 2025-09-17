package com.lgk.lgkaicodeservice.model.vo;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lgk.lgkaicodeservice.model.entity.Post;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PostVO {

    private final static Gson GSON = new Gson();

    /**
     * 帖子id
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 封面图片URL
     */
    private String coverImage;

    /**
     * 点赞数
     */
    private Integer thumbNum;

    /**
     * 收藏数
     */
    private Integer favourNum;

    /**
     * 浏览量
     */
    private Integer viewNum;

    /**
     * 创建用户id
     */
    private Long userId;

    /**
     * 创建用户的信息
     */
    private UserVO user;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 包装类转对象
     *
     * @param postVO
     * @return
     */
    public static Post voToObj(PostVO postVO) {
        if (postVO == null) {
            return null;
        }
        Post post = new Post();
        BeanUtils.copyProperties(postVO, post);
        List<String> tagList = postVO.getTags();
        if (tagList != null) {
            post.setTags(GSON.toJson(tagList));
        }
        return post;
    }

    /**
     * 对象转包装类
     *
     * @param post
     * @return
     */
    public static PostVO objToVo(Post post) {
        if (post == null) {
            return null;
        }
        PostVO postVO = new PostVO();
        BeanUtils.copyProperties(post, postVO);
        postVO.setTags(GSON.fromJson(post.getTags(), new TypeToken<List<String>>() {
        }.getType()));
        return postVO;
    }

}
