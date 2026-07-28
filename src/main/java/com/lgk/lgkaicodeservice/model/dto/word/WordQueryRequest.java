package com.lgk.lgkaicodeservice.model.dto.word;

import com.lgk.lgkaicodeservice.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 我的词库分页查询请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class WordQueryRequest extends PageRequest implements Serializable {

    /**
     * 按掌握度筛选 0=新词 1=学习中 2=复习中 3=已掌握，null=全部
     */
    private Integer mastery;

    /**
     * 关键词（按拼写模糊搜自己的词）
     */
    private String keyword;

    /**
     * 按词书筛选（二期），null=全部
     */
    private Long bookId;

    private static final long serialVersionUID = 1L;
}
