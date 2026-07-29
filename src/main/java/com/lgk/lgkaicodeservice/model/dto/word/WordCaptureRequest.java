package com.lgk.lgkaicodeservice.model.dto.word;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

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
     * 录入渠道 manual/paste/bookmarklet/extension/ocr，缺省 manual
     */
    private String channel;

    /**
     * 词 → 中文释义。目前由拍照录入使用：便利贴上手写的释义随识别结果一路带过来。
     * <p>
     * 落库策略（刻意保守）：<b>只在平台词典查不到该词释义时</b>，才把它写进
     * <b>该用户私有的</b> {@code user_word.note}。
     * <p>
     * 为什么不写进共享的 {@code word_dict.translation}：那张表是全用户共用的，
     * 而手写释义可能抄错、也可能是很个人化的理解，写进去会污染其他人的数据。
     * 私有笔记既满足了「词典没有就用自己写的」，又不影响任何人。
     */
    private Map<String, String> translations;

    /**
     * 来源图片链接（拍照录入时带上原图短链），作为遇见记录的溯源
     */
    private String imageUrl;

    private static final long serialVersionUID = 1L;
}
