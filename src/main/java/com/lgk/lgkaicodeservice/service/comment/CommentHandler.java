package com.lgk.lgkaicodeservice.service.comment;

/**
 * 评论目标处理器接口。
 * 每种可被评论的实体（帖子、应用等）各自实现，
 * 负责检查目标存在性以及维护目标上的评论计数。
 */
public interface CommentHandler {

    boolean checkTargetExists(Long targetId);

    boolean incrementCommentNum(Long targetId);

    boolean decrementCommentNum(Long targetId);
}
