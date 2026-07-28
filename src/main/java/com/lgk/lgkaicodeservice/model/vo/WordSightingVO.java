package com.lgk.lgkaicodeservice.model.vo;

import com.lgk.lgkaicodeservice.model.entity.UserWordSighting;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 单条遇见记录
 */
@Data
public class WordSightingVO implements Serializable {

    private Long id;

    /** 遇见时的原句 */
    private String sentence;

    /** 来源标题 */
    private String sourceTitle;

    /** 来源链接 */
    private String sourceUrl;

    /** 录入渠道 */
    private String channel;

    /** 遇见时间 */
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;

    public static WordSightingVO of(UserWordSighting sighting) {
        WordSightingVO vo = new WordSightingVO();
        BeanUtils.copyProperties(sighting, vo);
        return vo;
    }
}
