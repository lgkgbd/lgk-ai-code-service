package com.lgk.lgkaicodeservice.model.dto.word;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新私人笔记请求
 */
@Data
public class WordNoteUpdateRequest implements Serializable {

    /**
     * 学习记录 id（user_word.id）
     */
    private Long userWordId;

    /**
     * 私人笔记，最长 1024
     */
    private String note;

    private static final long serialVersionUID = 1L;
}
