package com.lgk.lgkaicodeservice.model.dto.postfavour;

import lombok.Data;

import java.io.Serializable;

@Data
public class PostFavourAddRequest implements Serializable {

    /**
     * 帖子 id
     */
    private Long postId;

    private static final long serialVersionUID = 1L;
}