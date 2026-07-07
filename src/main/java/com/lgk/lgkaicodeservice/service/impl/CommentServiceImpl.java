package com.lgk.lgkaicodeservice.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.google.gson.Gson;
import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.exception.ThrowUtils;
import com.lgk.lgkaicodeservice.mapper.CommentMapper;
import com.lgk.lgkaicodeservice.mapper.ThumbMapper;
import com.lgk.lgkaicodeservice.model.dto.comment.AddCommentRequest;
import com.lgk.lgkaicodeservice.model.dto.comment.CommentQueryRequest;
import com.lgk.lgkaicodeservice.model.entity.Comment;
import com.lgk.lgkaicodeservice.model.entity.Thumb;
import com.lgk.lgkaicodeservice.model.entity.User;
import com.lgk.lgkaicodeservice.model.enums.CommentTargetTypeEnum;
import com.lgk.lgkaicodeservice.model.enums.ThumbTypeEnum;
import com.lgk.lgkaicodeservice.model.enums.UserRoleEnum;
import com.lgk.lgkaicodeservice.model.vo.CommentVO;
import com.lgk.lgkaicodeservice.service.CommentService;
import com.lgk.lgkaicodeservice.service.RedisLuaScriptService;
import com.lgk.lgkaicodeservice.service.UserService;
import com.lgk.lgkaicodeservice.service.comment.CommentHandler;
import com.lgk.lgkaicodeservice.service.comment.CommentHandlerFactory;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    private static final Gson GSON = new Gson();

    /** 顶级评论每页预加载的回复条数 */
    private static final int PRELOAD_REPLY_LIMIT = 3;

    @Resource
    private UserService userService;

    @Resource
    private ThumbMapper thumbMapper;

    @Resource
    private CommentHandlerFactory commentHandlerFactory;

    @Resource
    private RedisLuaScriptService redisLuaScriptService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addComment(AddCommentRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(!StringUtils.hasText(request.getContent()), ErrorCode.PARAMS_ERROR, "评论内容不能为空");
        ThrowUtils.throwIf(request.getContent().length() > 1000, ErrorCode.PARAMS_ERROR, "评论内容过长");

        CommentTargetTypeEnum targetType = CommentTargetTypeEnum.getEnumByValue(request.getTargetType());
        ThrowUtils.throwIf(targetType == null, ErrorCode.PARAMS_ERROR, "不支持的目标类型");

        Long targetId = request.getTargetId();
        ThrowUtils.throwIf(targetId == null || targetId <= 0, ErrorCode.PARAMS_ERROR, "目标ID不合法");

        CommentHandler handler = commentHandlerFactory.getHandler(targetType);
        ThrowUtils.throwIf(!handler.checkTargetExists(targetId), ErrorCode.NOT_FOUND_ERROR, "目标不存在");

        Comment comment = new Comment();
        comment.setContent(request.getContent());
        comment.setTargetType(request.getTargetType());
        comment.setTargetId(targetId);
        comment.setUserId(loginUser.getId());
        comment.setThumbNum(0);
        comment.setReplyNum(0);
        comment.setCreateTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());

        if (CollUtil.isNotEmpty(request.getImages())) {
            comment.setImages(GSON.toJson(request.getImages()));
        }

        Long parentId = request.getParentId();
        if (parentId != null && parentId > 0) {
            Comment parentComment = this.getById(parentId);
            ThrowUtils.throwIf(parentComment == null, ErrorCode.NOT_FOUND_ERROR, "父评论不存在");

            // 两级嵌套：rootId 始终指向顶级评论
            long rootId = parentComment.getRootId() == 0 ? parentId : parentComment.getRootId();
            comment.setParentId(parentId);
            comment.setRootId(rootId);

            // 被回复人（优先取请求传入的，其次取父评论发布者）
            Long replyToUserId = (request.getReplyToUserId() != null && request.getReplyToUserId() > 0)
                    ? request.getReplyToUserId()
                    : parentComment.getUserId();
            comment.setReplyToUserId(replyToUserId);

            // 被回复内容快照，截断防止过长
            String parentContent = parentComment.getContent();
            comment.setReplyToContent(parentContent.length() > 200
                    ? parentContent.substring(0, 200) : parentContent);

            // 父评论回复数 +1
            UpdateChain.of(Comment.class)
                    .setRaw("replyNum", "replyNum + 1")
                    .where("id = ?", parentId)
                    .update();
        } else {
            comment.setParentId(0L);
            comment.setRootId(0L);
            comment.setReplyToUserId(0L);
            comment.setReplyToContent("");
        }

        boolean saved = this.save(comment);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "发表评论失败");

        handler.incrementCommentNum(targetId);

        // 同步到 Redis 存在性 Set，供评论点赞功能校验目标是否存在
        try {
            redisLuaScriptService.addToExistsSet(ThumbTypeEnum.COMMENT, comment.getId());
        } catch (Exception e) {
            log.error("同步评论到 Redis 失败: commentId={}", comment.getId(), e);
            // 不影响主流程，记录日志即可
        }

        return comment.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteComment(Long commentId, User loginUser) {
        ThrowUtils.throwIf(commentId == null || commentId <= 0, ErrorCode.PARAMS_ERROR);

        Comment comment = this.getById(commentId);
        ThrowUtils.throwIf(comment == null, ErrorCode.NOT_FOUND_ERROR, "评论不存在");

        boolean isOwner = comment.getUserId().equals(loginUser.getId());
        boolean isAdmin = UserRoleEnum.ADMIN.getValue().equals(loginUser.getUserRole());
        ThrowUtils.throwIf(!isOwner && !isAdmin, ErrorCode.NO_AUTH_ERROR);

        boolean removed = this.removeById(commentId);
        ThrowUtils.throwIf(!removed, ErrorCode.OPERATION_ERROR, "删除评论失败");

        // 如果是回复，父评论回复数 -1
        if (comment.getParentId() > 0) {
            UpdateChain.of(Comment.class)
                    .setRaw("replyNum", "replyNum - 1")
                    .where("id = ?", comment.getParentId())
                    .update();
        }

        // 目标评论数 -1
        CommentTargetTypeEnum targetType = CommentTargetTypeEnum.getEnumByValue(comment.getTargetType());
        if (targetType != null) {
            commentHandlerFactory.getHandler(targetType).decrementCommentNum(comment.getTargetId());
        }

        // 同步清理 Redis 存在性 Set
        try {
            redisLuaScriptService.removeFromExistsSet(ThumbTypeEnum.COMMENT, commentId);
        } catch (Exception e) {
            log.warn("删除评论后清理 Redis 失败: commentId={}", commentId, e);
        }

        return true;
    }

    @Override
    public Page<CommentVO> listTopCommentVOByPage(CommentQueryRequest request, HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTargetType() == null, ErrorCode.PARAMS_ERROR, "目标类型不能为空");
        ThrowUtils.throwIf(request.getTargetId() == null || request.getTargetId() <= 0,
                ErrorCode.PARAMS_ERROR, "目标ID不合法");

        long pageSize = Math.min(request.getPageSize(), 20);

        // 顶级评论，按热度（点赞+回复数）降序，同热度按时间降序
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq(Comment::getTargetType, request.getTargetType())
                .eq(Comment::getTargetId, request.getTargetId())
                .eq(Comment::getParentId, 0L)
                .orderByUnSafely("(thumbNum + replyNum) DESC", "createTime DESC");

        Page<Comment> commentPage = this.page(new Page<>(request.getPageNum(), pageSize), queryWrapper);
        Page<CommentVO> voPage = new Page<>(commentPage.getPageNumber(), commentPage.getPageSize(),
                commentPage.getTotalRow());

        if (CollectionUtils.isEmpty(commentPage.getRecords())) {
            voPage.setRecords(Collections.emptyList());
            return voPage;
        }

        voPage.setRecords(enrichCommentVOList(commentPage.getRecords(), httpRequest, true));
        return voPage;
    }

    @Override
    public Page<CommentVO> listReplyVOByPage(CommentQueryRequest request, HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getParentId() == null || request.getParentId() <= 0,
                ErrorCode.PARAMS_ERROR, "父评论ID不合法");

        long pageSize = Math.min(request.getPageSize(), 20);

        // 回复按时间升序，保留对话顺序感
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq(Comment::getParentId, request.getParentId())
                .orderBy(Comment::getCreateTime, true);

        Page<Comment> replyPage = this.page(new Page<>(request.getPageNum(), pageSize), queryWrapper);
        Page<CommentVO> voPage = new Page<>(replyPage.getPageNumber(), replyPage.getPageSize(),
                replyPage.getTotalRow());

        if (CollectionUtils.isEmpty(replyPage.getRecords())) {
            voPage.setRecords(Collections.emptyList());
            return voPage;
        }

        voPage.setRecords(enrichCommentVOList(replyPage.getRecords(), httpRequest, false));
        return voPage;
    }

    /**
     * 批量填充评论VO：用户信息、点赞状态，可选预加载前几条回复。
     */
    private List<CommentVO> enrichCommentVOList(List<Comment> commentList,
                                                 HttpServletRequest httpRequest,
                                                 boolean preloadReplies) {
        if (CollectionUtils.isEmpty(commentList)) {
            return Collections.emptyList();
        }

        // 1. 收集所有需要查询的用户ID（评论者 + 被回复人）
        Set<Long> userIdSet = commentList.stream()
                .flatMap(c -> Stream.of(c.getUserId(), c.getReplyToUserId()))
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());

        Map<Long, User> userMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 2. 已登录则批量查 hasThumb
        Map<Long, Boolean> commentHasThumbMap = new HashMap<>();
        User loginUser = userService.getLoginUserPermitNull(httpRequest);
        if (loginUser != null) {
            Set<Long> commentIdSet = commentList.stream().map(Comment::getId).collect(Collectors.toSet());
            QueryWrapper thumbQuery = new QueryWrapper();
            thumbQuery.eq("type", ThumbTypeEnum.COMMENT.getValue());
            thumbQuery.in("targetId", commentIdSet);
            thumbQuery.eq("userId", loginUser.getId());
            List<Thumb> thumbList = thumbMapper.selectListByQuery(thumbQuery);
            thumbList.forEach(t -> commentHasThumbMap.put(t.getTargetId(), true));
        }

        // 3. 预加载每条顶级评论的前 N 条回复
        Map<Long, List<CommentVO>> repliesMap = new HashMap<>();
        if (preloadReplies) {
            Set<Long> topCommentIds = commentList.stream().map(Comment::getId).collect(Collectors.toSet());

            List<Comment> allReplies = this.list(
                    QueryWrapper.create()
                            .in(Comment::getParentId, topCommentIds)
                            .orderBy(Comment::getCreateTime, true)
            );

            if (!allReplies.isEmpty()) {
                // 补充回复中出现的新用户
                Set<Long> replyUserIds = allReplies.stream()
                        .flatMap(r -> Stream.of(r.getUserId(), r.getReplyToUserId()))
                        .filter(id -> id != null && id > 0 && !userMap.containsKey(id))
                        .collect(Collectors.toSet());
                if (!replyUserIds.isEmpty()) {
                    userService.listByIds(replyUserIds).forEach(u -> userMap.put(u.getId(), u));
                }

                // 批量查回复的 hasThumb
                Map<Long, Boolean> replyHasThumbMap = new HashMap<>();
                if (loginUser != null) {
                    Set<Long> replyIds = allReplies.stream().map(Comment::getId).collect(Collectors.toSet());
                    QueryWrapper tw = new QueryWrapper();
                    tw.eq("type", ThumbTypeEnum.COMMENT.getValue());
                    tw.in("targetId", replyIds);
                    tw.eq("userId", loginUser.getId());
                    thumbMapper.selectListByQuery(tw).forEach(t -> replyHasThumbMap.put(t.getTargetId(), true));
                }

                // 按 parentId 分组，每组截取前 N 条
                allReplies.stream()
                        .collect(Collectors.groupingBy(Comment::getParentId))
                        .forEach((pid, replies) -> {
                            List<CommentVO> preloaded = replies.stream()
                                    .limit(PRELOAD_REPLY_LIMIT)
                                    .map(r -> buildCommentVO(r, userMap, replyHasThumbMap))
                                    .collect(Collectors.toList());
                            repliesMap.put(pid, preloaded);
                        });
            }
        }

        // 4. 组装最终 VO
        return commentList.stream()
                .map(comment -> {
                    CommentVO vo = buildCommentVO(comment, userMap, commentHasThumbMap);
                    if (preloadReplies) {
                        vo.setReplies(repliesMap.getOrDefault(comment.getId(), Collections.emptyList()));
                    }
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private CommentVO buildCommentVO(Comment comment, Map<Long, User> userMap,
                                      Map<Long, Boolean> thumbMap) {
        CommentVO vo = CommentVO.objToVo(comment);
        vo.setUser(userService.getUserVO(userMap.get(comment.getUserId())));
        if (comment.getReplyToUserId() != null && comment.getReplyToUserId() > 0) {
            User replyToUser = userMap.get(comment.getReplyToUserId());
            if (replyToUser != null) {
                vo.setReplyToUserName(replyToUser.getUserName());
            }
        }
        vo.setHasThumb(thumbMap.getOrDefault(comment.getId(), false));
        return vo;
    }
}
