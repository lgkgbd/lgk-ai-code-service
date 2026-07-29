package com.lgk.lgkaicodeservice.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 拍照识别任务快照
 * <p>
 * 既是 Redis 里的存储结构，也是直接返回给前端的 VO（字段完全一致，没必要再套一层转换）。
 * 生命周期只有 1 小时：用户确认入库后这份数据就没用了，故不落库。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WordOcrTaskVO implements Serializable {

    /**
     * 任务 id（UUID），前端拿它轮询结果
     */
    private String taskId;

    /**
     * 任务归属用户，查询时校验，防止拿别人的 taskId 偷看结果
     */
    private Long userId;

    /**
     * 状态，见 {@link com.lgk.lgkaicodeservice.model.enums.WordOcrStatusEnum}
     */
    private String status;

    /**
     * 图片总张数
     */
    private Integer totalImages;

    /**
     * 已识别完成的张数，前端据此显示「2/3 已识别」
     */
    private Integer doneImages;

    /**
     * 原图短链，确认入库时回传，作为该词的「遇见记录」来源，日后能点回去看原始便利贴
     */
    private List<String> imageUrls;

    /**
     * 识别出的候选词（多图已合并去重），仅 SUCCEED 时有值
     */
    private List<WordOcrItemVO> items;

    /**
     * 失败原因，仅 FAILED 时有值
     */
    private String errorMsg;

    /**
     * 任务创建时间戳（毫秒）
     */
    private Long createTime;

    private static final long serialVersionUID = 1L;
}
