package com.lgk.lgkaicodeservice.service.thumb;

import com.lgk.lgkaicodeservice.model.enums.ThumbTypeEnum;
import com.mybatisflex.core.update.UpdateChain;
import com.lgk.lgkaicodeservice.mapper.CommentMapper;
import com.lgk.lgkaicodeservice.model.entity.Comment;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class CommentThumbHandler implements ThumbHandler {

    @Resource
    private CommentMapper commentMapper;

    @Override
    public ThumbTypeEnum getType() {
        return ThumbTypeEnum.COMMENT;
    }

    @Override
    public boolean checkTargetExists(Long targetId) {
        return commentMapper.selectOneById(targetId) != null;
    }

    @Override
    public Integer incrementThumb(Long targetId) {
        boolean result = UpdateChain.of(Comment.class)
                .setRaw("thumbNum", "thumbNum + 1")
                .where("id = ?", targetId)
                .update();
        return result ? 1 : 0;
    }

    @Override
    public Integer decrementThumb(Long targetId) {
        boolean result = UpdateChain.of(Comment.class)
                .setRaw("thumbNum", "thumbNum - 1")
                .where("id = ?", targetId)
                .update();
        return result ? -1 : 0;
    }
}
