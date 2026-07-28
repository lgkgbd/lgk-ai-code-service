package com.lgk.lgkaicodeservice.service;

import com.lgk.lgkaicodeservice.model.entity.WordDict;
import com.mybatisflex.core.service.IService;

import java.util.List;

/**
 * 平台词条服务：查词、补全落库、搜索联想
 */
public interface WordDictService extends IService<WordDict> {

    /**
     * 解析并落库一个词条（录入主链路调用）
     * <p>
     * 先按原形查 word_dict；未命中则走 Enricher 责任链补全并 insert（幂等：唯一索引冲突时回查）。
     * 全都没命中则建一条 source='manual' 的占位词条，绝不返回 null。
     *
     * @param spelling 单词（已 trim + 转小写）
     * @param lang     语言，如 en
     * @return 已落库的词条，永不为 null
     */
    WordDict resolveAndSave(String spelling, String lang);

    /**
     * 按原形精确查词条（不触发补全）
     *
     * @return 命中的词条；无则 null
     */
    WordDict getBySpelling(String spelling, String lang);

    /**
     * 查词联想（录入前预览）：按拼写前缀 + 词频排序
     *
     * @param keyword 关键词
     * @param limit   条数上限
     */
    List<WordDict> search(String keyword, String lang, int limit);
}
