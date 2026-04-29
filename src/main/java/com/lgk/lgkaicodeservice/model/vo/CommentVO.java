package com.lgk.lgkaicodeservice.model.vo;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lgk.lgkaicodeservice.model.entity.Comment;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
public class CommentVO {

    private static final Gson GSON = new Gson();

    private Long id;

    private String content;

    /** 图片URL列表（已解析为List） */
    private List<String> images;

    private Integer targetType;

    private Long targetId;

    private Long userId;

    /** 评论者信息 */
    private UserVO user;

    /** 父评论ID，顶级评论为0 */
    private Long parentId;

    /** 根评论ID，顶级评论为0 */
    private Long rootId;

    /** 被回复的用户ID */
    private Long replyToUserId;

    /** 被回复的用户名（展示"回复 @xxx"） */
    private String replyToUserName;

    /** 被回复内容摘要 */
    private String replyToContent;

    private Integer thumbNum;

    private Integer replyNum;

    /** 当前登录用户是否已点赞该评论 */
    private Boolean hasThumb;

    private LocalDateTime createTime;

    /**
     * 顶级评论携带的前几条回复（列表页预加载，点"展开更多"再分页）。
     * 回复 VO 本身的 replies 字段为 null。
     */
    private List<CommentVO> replies;

    public static CommentVO objToVo(Comment comment) {
        if (comment == null) {
            return null;
        }
        CommentVO vo = new CommentVO();
        BeanUtils.copyProperties(comment, vo);
        // 解析 JSON 图片列表
        if (comment.getImages() != null) {
            vo.setImages(GSON.fromJson(comment.getImages(), new TypeToken<List<String>>() {}.getType()));
        } else {
            vo.setImages(Collections.emptyList());
        }
        return vo;
    }
}
