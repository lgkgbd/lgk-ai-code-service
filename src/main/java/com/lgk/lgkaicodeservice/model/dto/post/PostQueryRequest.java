package com.lgk.lgkaicodeservice.model.dto.post;

import com.lgk.lgkaicodeservice.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class PostQueryRequest extends PageRequest {

    /**
     * 标题（模糊搜索）
     */
    private String title;

    /**
     * 内容（模糊搜索）
     */
    private String content;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 创建用户id
     */
    private Long userId;

    /**
     * 是否加精（0-普通，1-加精）
     */
    private Integer isFeatured;
}