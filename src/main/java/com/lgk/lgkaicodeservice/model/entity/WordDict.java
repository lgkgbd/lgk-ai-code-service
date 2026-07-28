package com.lgk.lgkaicodeservice.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 平台词条表（客观数据，全站共享，一个词只存一份）
 * <p>
 * 数据来源优先级：ecdict(本地) &gt; api(免费词典接口) &gt; ai(大模型兜底) &gt; manual(人工)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("word_dict")
public class WordDict implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /** 语言，预留：en/ja/... */
    private String lang;

    /** 单词原形 */
    private String spelling;

    /** 音标（ECDICT 仅 28.3% 覆盖，前端需能处理为空） */
    private String phonetic;

    /** 中文释义（按行分隔的原始文本） */
    private String translation;

    /** 英文释义 */
    private String definition;

    /** 词性占比，如 n:52/v:48 */
    private String pos;

    /** 词形变化，用于词形还原，如 0:run/1:i */
    private String exchange;

    /** 考试标签 cet4/cet6/ky/toefl/ielts/gre */
    private String tag;

    /** 当代语料词频排名，越小越常用，0=未知 */
    private Integer frq;

    /** BNC 词频排名 */
    private Integer bnc;

    /** 柯林斯星级 0-5 */
    private Integer collins;

    /** 是否牛津核心词 0/1 */
    private Integer oxford;

    /** 扩展信息 JSON：例句/搭配/同反义词/词源/助记等 */
    @Column("extInfo")
    private String extInfo;

    /** 数据来源 ecdict/api/ai/manual，见 WordSourceEnum */
    private String source;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;
}
