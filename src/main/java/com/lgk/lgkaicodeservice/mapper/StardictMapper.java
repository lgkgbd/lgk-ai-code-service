package com.lgk.lgkaicodeservice.mapper;

import com.lgk.lgkaicodeservice.model.entity.Stardict;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * ECDICT 原始词库（stardict）只读访问入口
 * <p>
 * stardict 是外部导入的数据源（770611 词条），本项目只读、不写、不建实体，
 * 因此这里不继承 BaseMapper，只按需暴露查询方法。
 * <p>
 * 目前仅供 {@link com.lgk.lgkaicodeservice.utils.WordTextParser#restoreLemma(String)} 做词形还原；
 * 二期 EcdictEnricher 需要整行词条时在此追加方法即可。
 */
public interface StardictMapper {

    /**
     * 查某个单词的词形变化串
     * <p>
     * 形如 {@code 0:run/1:i/i:running/s:runnings}，其中 {@code 0:} 前缀即原形。
     *
     * @param word 单词（调用方保证已转小写）
     * @return exchange 字段原文；单词不存在时返回 null
     */
    @Select("select exchange from stardict where word = #{word} limit 1")
    String selectExchangeByWord(@Param("word") String word);

    /**
     * 按单词查整行词条，供 EcdictEnricher 映射成 word_dict
     * <p>
     * stardict 主键即 word，大小写按 collate=utf8mb4_unicode_ci 不敏感匹配。
     *
     * @param word 单词（调用方保证已转小写）
     * @return 整行词条；不存在时返回 null
     */
    @Select("select word, phonetic, definition, translation, pos, collins, oxford, "
            + "tag, bnc, frq, exchange, detail, audio "
            + "from stardict where word = #{word} limit 1")
    Stardict selectByWord(@Param("word") String word);
}
