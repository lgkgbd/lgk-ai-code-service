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
 * 词书-词条关联表
 * <p>
 * 个人生词本：录入一个词就插一条；系统词书：由 ECDICT 的 tag 字段批量生成
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("word_book_item")
public class WordBookItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /** 词书id */
    @Column("bookId")
    private Long bookId;

    /** 词条id */
    @Column("dictId")
    private Long dictId;

    /** 在词书中的顺序 */
    private Integer seq;

    @Column("createTime")
    private LocalDateTime createTime;
}
