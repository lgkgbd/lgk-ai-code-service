package com.lgk.lgkaicodeservice.model.dto.thumb;

import com.lgk.lgkaicodeservice.model.enums.ThumbTypeEnum;
import lombok.Data;

import java.io.Serializable;

@Data
public class ThumbAddRequest implements Serializable {
    private Long targetId;
    private ThumbTypeEnum type;
    private static final long serialVersionUID = 1L;
}
