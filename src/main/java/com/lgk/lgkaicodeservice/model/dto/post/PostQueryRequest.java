package com.lgk.lgkaicodeservice.model.dto.post;

import com.lgk.lgkaicodeservice.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class PostQueryRequest extends PageRequest {

    /**
     * （模糊搜索）
     */
    private String searchText;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 创建用户id
     */
    private Long userId;

    /**
     * 优先级
     */
    private Integer priority;
}