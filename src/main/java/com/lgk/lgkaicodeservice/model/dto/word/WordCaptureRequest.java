package com.lgk.lgkaicodeservice.model.dto.word;

import lombok.Data;

import java.io.Serializable;

/**
 * 录入请求（单词 / 多词 / 整句统一入口）
 * <p>
 * 录入阻力接近零：用户只负责给 {@link #text}，其余全是系统的事。上下文由渠道自动带。
 */
@Data
public class WordCaptureRequest implements Serializable {

    /**
     * 用户粘贴的任意文本：单个词、逗号/换行/空格分隔的多词、或一整句英文
     */
    private String text;

    /**
     * 遇见时的原句（书签小工具/整句录入时带上，用作复习挖空的上下文）
     */
    private String sentence;

    /**
     * 来源标题（页面标题/书名/播客名）
     */
    private String sourceTitle;

    /**
     * 来源链接
     */
    private String sourceUrl;

    /**
     * 录入渠道 manual/paste/bookmarklet/extension，缺省 manual
     */
    private String channel;

    private static final long serialVersionUID = 1L;
}
