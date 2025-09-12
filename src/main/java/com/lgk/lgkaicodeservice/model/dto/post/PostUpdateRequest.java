package com.lgk.lgkaicodeservice.model.dto.post;

import lombok.Data;

import java.util.List;

@Data
public class PostUpdateRequest {

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
}