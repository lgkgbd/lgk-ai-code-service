package com.lgk.lgkaicodeservice.service;

import com.mybatisflex.core.service.IService;
import com.lgk.lgkaicodeservice.model.dto.comment.AddCommentRequest;
import com.lgk.lgkaicodeservice.model.dto.comment.CommentQueryRequest;
import com.lgk.lgkaicodeservice.model.entity.Comment;
import com.lgk.lgkaicodeservice.model.entity.User;
import com.lgk.lgkaicodeservice.model.vo.CommentVO;
import com.mybatisflex.core.paginate.Page;

import jakarta.servlet.http.HttpServletRequest;

public interface CommentService extends IService<Comment> {

    Long addComment(AddCommentRequest request, User loginUser);

    Boolean deleteComment(Long commentId, User loginUser);

    /** 分页查顶级评论（按热度降序），每条携带前几条回复预览 */
    Page<CommentVO> listTopCommentVOByPage(CommentQueryRequest request, HttpServletRequest httpRequest);

    /** 分页查某条顶级评论下的所有回复 */
    Page<CommentVO> listReplyVOByPage(CommentQueryRequest request, HttpServletRequest httpRequest);
}
