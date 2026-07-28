package com.lgk.lgkaicodeservice.service.impl;

import com.lgk.lgkaicodeservice.constant.WordConstant;
import com.lgk.lgkaicodeservice.mapper.WordBookItemMapper;
import com.lgk.lgkaicodeservice.mapper.WordBookMapper;
import com.lgk.lgkaicodeservice.model.entity.WordBook;
import com.lgk.lgkaicodeservice.model.entity.WordBookItem;
import com.lgk.lgkaicodeservice.model.enums.WordBookTypeEnum;
import com.lgk.lgkaicodeservice.service.WordBookService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 词书服务实现
 */
@Slf4j
@Service
public class WordBookServiceImpl extends ServiceImpl<WordBookMapper, WordBook> implements WordBookService {

    @Resource
    private WordBookItemMapper wordBookItemMapper;

    @Override
    public WordBook getOrCreatePersonalBook(long userId) {
        WordBook book = this.getOne(QueryWrapper.create()
                .eq(WordBook::getOwnerId, userId)
                .eq(WordBook::getType, WordBookTypeEnum.PERSONAL.getValue()));
        if (book != null) {
            return book;
        }
        LocalDateTime now = LocalDateTime.now();
        WordBook personal = WordBook.builder()
                .name(WordConstant.PERSONAL_BOOK_NAME)
                .description("我平时遇到的生词")
                .type(WordBookTypeEnum.PERSONAL.getValue())
                .lang(WordConstant.DEFAULT_LANG)
                .ownerId(userId)
                .wordCount(0)
                .isPublic(0)
                .sortOrder(0)
                .createTime(now)
                .updateTime(now)
                .isDelete(0)
                .build();
        try {
            this.save(personal);
        } catch (DuplicateKeyException e) {
            // 并发首次录入：另一线程已建，回查
            WordBook raced = this.getOne(QueryWrapper.create()
                    .eq(WordBook::getOwnerId, userId)
                    .eq(WordBook::getType, WordBookTypeEnum.PERSONAL.getValue()));
            if (raced != null) {
                return raced;
            }
        }
        return personal;
    }

    @Override
    public boolean addWord(long bookId, long dictId) {
        // 幂等：uq_book_dict(bookId, dictId) 拦重复
        WordBookItem existing = wordBookItemMapper.selectOneByQuery(QueryWrapper.create()
                .eq(WordBookItem::getBookId, bookId)
                .eq(WordBookItem::getDictId, dictId));
        if (existing != null) {
            return false;
        }
        WordBookItem item = WordBookItem.builder()
                .bookId(bookId)
                .dictId(dictId)
                .seq(0)
                .createTime(LocalDateTime.now())
                .build();
        try {
            wordBookItemMapper.insert(item);
        } catch (DuplicateKeyException e) {
            // 并发插入同一词，视为已存在
            return false;
        }
        // 维护冗余词数
        UpdateChain.of(WordBook.class)
                .setRaw("wordCount", "wordCount + 1")
                .where("id = ?", bookId)
                .update();
        return true;
    }
}
