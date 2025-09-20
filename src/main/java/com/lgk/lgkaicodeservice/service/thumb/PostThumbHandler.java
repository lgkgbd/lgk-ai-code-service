package com.lgk.lgkaicodeservice.service.thumb;

import com.lgk.lgkaicodeservice.model.entity.Post;
import com.lgk.lgkaicodeservice.service.PostService;
import com.mybatisflex.core.update.UpdateChain;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

// 帖子点赞处理器
@Component
public class PostThumbHandler implements ThumbHandler {

    @Resource
    private PostService postService;


    @Override
    public boolean checkTargetExists(Long targetId) {
        return postService.getById(targetId) != null;
    }

    @Override
    public Integer incrementThumb(Long targetId) {
        // 使用原子操作更新数据库
        // MyBatis-Flex 方式：使用 UpdateChain
        boolean result = UpdateChain.of(Post.class)
                .setRaw("thumbNum", "thumbNum + 1")
                .where("id = ?", targetId)  // 直接使用字符串条件
                .update();

        return result ? 1 : 0;
    }

    @Override
    public Integer decrementThumb(Long targetId) {

        boolean result = UpdateChain.of(Post.class)
                .setRaw("thumbNum", "thumbNum - 1")
                .where("id = ?", targetId)  // 直接使用字符串条件
                .update();

        return result ? -1 : 0;
    }
}

