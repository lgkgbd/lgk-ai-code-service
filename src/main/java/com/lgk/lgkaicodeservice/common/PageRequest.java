package com.lgk.lgkaicodeservice.common;

import com.lgk.lgkaicodeservice.constant.CommonConstant;
import lombok.Data;

@Data
public class PageRequest {

    /**
     * 当前页号
     */
    private long pageNum = 1;

    /**
     * 页面大小
     */
    private long pageSize = 10;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序顺序（默认降序）
     */
    private String sortOrder = CommonConstant.SORT_ORDER_DESC;
}
