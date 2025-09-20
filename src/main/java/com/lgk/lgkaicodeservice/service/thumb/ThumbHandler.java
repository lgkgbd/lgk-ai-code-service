package com.lgk.lgkaicodeservice.service.thumb;

public interface ThumbHandler {

    /**
     * 校验目标是否存在（比如帖子、评论等）
     */
    boolean checkTargetExists(Long targetId);

    /**
     * 点赞 +1
     */
    Integer incrementThumb(Long targetId);

    /**
     * 取消点赞 -1
     */
    Integer decrementThumb(Long targetId);
}
