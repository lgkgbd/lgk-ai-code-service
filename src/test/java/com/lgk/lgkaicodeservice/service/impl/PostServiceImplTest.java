package com.lgk.lgkaicodeservice.service.impl;

import com.lgk.lgkaicodeservice.model.entity.Post;
import com.lgk.lgkaicodeservice.service.PostService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PostServiceImplTest {

    @Resource
    public PostService postService;

    // 并发测试
    @Test
    public void testConcurrentViewIncrement() throws InterruptedException {
        Long postId = 323962101364756480L;
        int threadCount = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                postService.incrementViewCountAsync(postId);
                latch.countDown();
            }).start();
        }

        latch.await();

        // 如果是原子操作，最终 viewNum 应该正确增加 100
        Post post = postService.getById(postId);
        assertEquals( 100, post.getViewNum());
    }

}