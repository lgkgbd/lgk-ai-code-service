package com.lgk.lgkaicodeservice.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * VL 模型从便利贴照片中识别出的一个候选词
 * <p>
 * 注意这只是「候选」：手写识别不可能 100% 准，必须经用户在前端确认/改错后
 * 才调 /word/capture 真正入库。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WordOcrItemVO implements Serializable {

    /**
     * 识别出的英文单词（已 trim + 转小写）
     */
    private String word;

    /**
     * 便利贴上手写的中文释义，图中没写则为空。
     * <p>
     * 入库时按 B 方案处理：仅当平台词典查不到该词的释义时，才作为
     * <b>该用户私有的</b> user_word.note 落库，绝不写进共享的 word_dict
     */
    private String translation;

    private static final long serialVersionUID = 1L;
}
