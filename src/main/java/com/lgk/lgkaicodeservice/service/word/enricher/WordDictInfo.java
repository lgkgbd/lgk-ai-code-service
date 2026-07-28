package com.lgk.lgkaicodeservice.service.word.enricher;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 补全责任链的产出：一个词的客观信息
 * <p>
 * 与 {@link com.lgk.lgkaicodeservice.model.entity.WordDict} 解耦——Enricher 只关心「查到了什么」，
 * 落库前由 WordDictService 组装成 WordDict 实体。extInfo 里放各来源的额外字段（collins/oxford/例句等），
 * 最终序列化进 word_dict.extInfo(json)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WordDictInfo {

    /** 单词原形 */
    private String spelling;

    /** 音标 */
    private String phonetic;

    /** 中文释义 */
    private String translation;

    /** 英文释义 */
    private String definition;

    /** 词性占比，如 n:52/v:48 */
    private String pos;

    /** 词形变化，用于词形还原 */
    private String exchange;

    /** 考试标签 cet4/cet6/... */
    private String tag;

    /** 当代语料词频排名，越小越常用，0=未知 */
    private Integer frq;

    /** BNC 词频排名 */
    private Integer bnc;

    /** 柯林斯星级 0-5 */
    private Integer collins;

    /** 是否牛津核心词 0/1 */
    private Integer oxford;

    /** 数据来源 ecdict/api/ai/manual，见 WordSourceEnum */
    private String source;

    /**
     * 扩展信息，最终序列化进 word_dict.extInfo(json)：
     * 例句 / 搭配 / 同反义词 / 词源 / 助记 / 原始 detail 等，结构可演进
     */
    private Map<String, Object> extInfo;
}
