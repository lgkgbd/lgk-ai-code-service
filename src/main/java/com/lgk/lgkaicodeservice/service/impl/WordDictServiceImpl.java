package com.lgk.lgkaicodeservice.service.impl;

import com.google.gson.Gson;
import com.lgk.lgkaicodeservice.constant.WordConstant;
import com.lgk.lgkaicodeservice.mapper.WordDictMapper;
import com.lgk.lgkaicodeservice.model.entity.WordDict;
import com.lgk.lgkaicodeservice.model.enums.WordSourceEnum;
import com.lgk.lgkaicodeservice.service.WordDictService;
import com.lgk.lgkaicodeservice.service.word.enricher.WordDictInfo;
import com.lgk.lgkaicodeservice.service.word.enricher.WordEnricher;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 平台词条服务实现
 */
@Slf4j
@Service
public class WordDictServiceImpl extends ServiceImpl<WordDictMapper, WordDict> implements WordDictService {

    private static final Gson GSON = new Gson();

    /**
     * Spring 注入所有 Enricher，构造后按 order 升序固定顺序
     */
    @Resource
    private List<WordEnricher> enrichers;

    @PostConstruct
    public void sortEnrichers() {
        enrichers = enrichers.stream()
                .sorted(Comparator.comparingInt(WordEnricher::order))
                .toList();
        log.info("词条补全责任链装配完成：{}",
                enrichers.stream().map(e -> e.getClass().getSimpleName() + "(" + e.order() + ")").toList());
    }

    @Override
    public WordDict resolveAndSave(String spelling, String lang) {
        // 1. 先查库，命中直接返回
        WordDict existing = getBySpelling(spelling, lang);
        if (existing != null) {
            return existing;
        }

        // 2. 走责任链补全
        WordDict dict = buildFromEnrichers(spelling, lang);

        // 3. 落库；并发下唯一索引 uq_lang_spelling 兜底，冲突则回查
        try {
            this.save(dict);
        } catch (DuplicateKeyException e) {
            log.info("词条并发写入冲突，回查已存在的记录，spelling={}", spelling);
            WordDict raced = getBySpelling(spelling, lang);
            return raced != null ? raced : dict;
        }
        return dict;
    }

    /**
     * 依次尝试 Enricher，命中即用；全都没命中则建 manual 占位
     */
    private WordDict buildFromEnrichers(String spelling, String lang) {
        for (WordEnricher enricher : enrichers) {
            if (!enricher.supports(lang)) {
                continue;
            }
            Optional<WordDictInfo> hit;
            try {
                hit = enricher.enrich(spelling);
            } catch (Exception e) {
                // Enricher 内部理应自吞异常，这里再兜一层，绝不阻断录入
                log.warn("Enricher {} 执行异常，跳过，spelling={}",
                        enricher.getClass().getSimpleName(), spelling, e);
                continue;
            }
            if (hit.isPresent()) {
                return toEntity(hit.get(), spelling, lang);
            }
        }
        // 全都没命中 → manual 占位，卡片显示「暂无释义，点击补充」
        return manualPlaceholder(spelling, lang);
    }

    private WordDict toEntity(WordDictInfo info, String spelling, String lang) {
        LocalDateTime now = LocalDateTime.now();
        return WordDict.builder()
                .lang(lang)
                .spelling(StringUtils.hasText(info.getSpelling()) ? info.getSpelling() : spelling)
                .phonetic(info.getPhonetic())
                .translation(info.getTranslation())
                .definition(info.getDefinition())
                .pos(info.getPos())
                .exchange(info.getExchange())
                .tag(info.getTag())
                .frq(info.getFrq() == null ? 0 : info.getFrq())
                .bnc(info.getBnc() == null ? 0 : info.getBnc())
                .collins(info.getCollins() == null ? 0 : info.getCollins())
                .oxford(info.getOxford() == null ? 0 : info.getOxford())
                .extInfo(info.getExtInfo() == null ? null : GSON.toJson(info.getExtInfo()))
                .source(StringUtils.hasText(info.getSource()) ? info.getSource() : WordSourceEnum.MANUAL.getValue())
                .createTime(now)
                .updateTime(now)
                .build();
    }

    private WordDict manualPlaceholder(String spelling, String lang) {
        LocalDateTime now = LocalDateTime.now();
        return WordDict.builder()
                .lang(lang)
                .spelling(spelling)
                .frq(0).bnc(0).collins(0).oxford(0)
                .source(WordSourceEnum.MANUAL.getValue())
                .createTime(now)
                .updateTime(now)
                .build();
    }

    @Override
    public WordDict getBySpelling(String spelling, String lang) {
        if (!StringUtils.hasText(spelling)) {
            return null;
        }
        return this.getOne(QueryWrapper.create()
                .eq(WordDict::getLang, lang)
                .eq(WordDict::getSpelling, spelling));
    }

    @Override
    public List<WordDict> search(String keyword, String lang, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return new ArrayList<>();
        }
        String kw = keyword.trim().toLowerCase();
        int size = limit <= 0 ? 10 : Math.min(limit, WordConstant.MAX_CAPTURE_WORDS);
        // 前缀匹配 + 常用词优先（frq 越小越常用，0=未知排最后）
        return this.list(QueryWrapper.create()
                .eq(WordDict::getLang, lang)
                .like(WordDict::getSpelling, kw + "%")
                .orderBy("if(frq = 0, 999999, frq)", true)
                .limit(size));
    }
}
