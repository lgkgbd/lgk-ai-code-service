package com.lgk.lgkaicodeservice.service;

import com.lgk.lgkaicodeservice.model.entity.WordBook;
import com.mybatisflex.core.service.IService;

/**
 * 词书服务
 * <p>
 * 生词本不是特例，只是 type=0 的一条记录。一期只用到个人生词本的懒创建与入池。
 */
public interface WordBookService extends IService<WordBook> {

    /**
     * 取当前用户的个人生词本，没有则创建（type=0, dailyNewLimit=-1 push 模式）
     *
     * @param userId 用户 id
     * @return 该用户的生词本
     */
    WordBook getOrCreatePersonalBook(long userId);

    /**
     * 把词条加入指定词书（幂等：uq_book_dict 冲突即忽略），并维护 wordCount 冗余计数
     *
     * @param bookId 词书 id
     * @param dictId 词条 id
     * @return true=本次新加入，false=词书里已存在
     */
    boolean addWord(long bookId, long dictId);
}
