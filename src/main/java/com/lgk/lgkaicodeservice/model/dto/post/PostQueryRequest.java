package com.lgk.lgkaicodeservice.model.dto.post;

import com.lgk.lgkaicodeservice.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class PostQueryRequest extends PageRequest {

    /**
     * id
     */
    private Long id;

    /**
     * id
     */
    private Long notId;

    /**
     * （模糊搜索）
     */
    private String searchText;

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
     * 至少有一个标签
     */
    private List<String> orTags;

    /**
     * 创建用户id
     */
    private Long userId;

    /**
     * 收藏用户 id
     */
    private Long favourUserId;

    /**
     * 优先级
     */
    private Integer priority;
}