package com.lgk.lgkaicodeservice.model.dto.post;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PostAddRequest implements Serializable {

    private String title;

    private String content;

    /**
     * 标签列表（入库的时候再序列化）
     */
    private List<String> tags;

    /**
     * 封面图片URL
     */
    private String coverImage;

    private static final long serialVersionUID = 1L;
}
