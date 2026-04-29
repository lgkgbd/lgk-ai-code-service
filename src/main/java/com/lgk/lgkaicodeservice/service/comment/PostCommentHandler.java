package com.lgk.lgkaicodeservice.service.comment;

import com.mybatisflex.core.update.UpdateChain;
import com.lgk.lgkaicodeservice.model.entity.Post;
import com.lgk.lgkaicodeservice.service.PostService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class PostCommentHandler implements CommentHandler {

    @Resource
    private PostService postService;

    @Override
    public boolean checkTargetExists(Long targetId) {
        return postService.getById(targetId) != null;
    }

    @Override
    public boolean incrementCommentNum(Long targetId) {
        return UpdateChain.of(Post.class)
                .setRaw("commentNum", "commentNum + 1")
                .where("id = ?", targetId)
                .update();
    }

    @Override
    public boolean decrementCommentNum(Long targetId) {
        return UpdateChain.of(Post.class)
                .setRaw("commentNum", "commentNum - 1")
                .where("id = ?", targetId)
                .update();
    }
}
