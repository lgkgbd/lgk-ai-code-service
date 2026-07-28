package com.lgk.lgkaicodeservice.model.entity;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * ECDICT 原始词库行（外部数据源 stardict，只读）
 * <p>
 * 这是纯查询用的贫血 POJO，不交给 MyBatis-Flex 托管（stardict 主键是 word 而非雪花 id，
 * 且本项目对它只读），字段名与列名一一对应，靠 {@link com.lgk.lgkaicodeservice.mapper.StardictMapper}
 * 的显式 {@code @Select} 自动映射。
 */
@Data
public class Stardict implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 单词 */
    private String word;

    /** 音标 */
    private String phonetic;

    /** 英文释义 */
    private String definition;

    /** 中文释义 */
    private String translation;

    /** 词性占比 */
    private String pos;

    /** 柯林斯星级 0-5 */
    private Integer collins;

    /** 是否牛津三千核心词 */
    private Integer oxford;

    /** 考试标签 zk/gk/cet4/cet6/ky/toefl/ielts/gre */
    private String tag;

    /** BNC 词频顺序 */
    private Integer bnc;

    /** 当代语料库词频顺序 */
    private Integer frq;

    /** 时态复数等变形 */
    private String exchange;

    /** JSON 扩展信息 */
    private String detail;

    /** 读音地址 */
    private String audio;
}
