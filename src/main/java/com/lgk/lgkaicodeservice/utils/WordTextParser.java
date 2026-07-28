package com.lgk.lgkaicodeservice.utils;

import com.lgk.lgkaicodeservice.constant.WordConstant;
import com.lgk.lgkaicodeservice.mapper.StardictMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 用户粘贴文本 → 候选单词列表
 * <p>
 * 录入阻力必须接近零，所以这里对输入不做任何格式要求：单词、逗号分隔、整段英文都能进来。
 * 本类只负责「拆」，不负责判断哪些词是生词——那是上层 WordService 的事。
 * <p>
 * 拆分规则：
 * <ul>
 *   <li>分隔符：逗号 / 中文逗号 / 分号 / 中文分号 / 顿号 / 换行 / 空格 / Tab</li>
 *   <li>逐词 trim、转小写、剔除首尾标点</li>
 *   <li>只保留纯英文字母，允许中间的连字符和撇号（well-known、don't）</li>
 *   <li>按首次出现顺序去重</li>
 *   <li>单次上限 {@link WordConstant#MAX_CAPTURE_WORDS} 个，超出截断并在返回值里标记</li>
 * </ul>
 */
@Slf4j
@Component
public class WordTextParser {

    /**
     * 分隔符：逗号 / 中文逗号 / 分号 / 中文分号 / 顿号 / 任意空白（含换行、Tab）
     */
    private static final Pattern DELIMITER = Pattern.compile("[,;，；、\\s]+");

    /**
     * 合法单词：纯英文字母，允许中间出现连字符或撇号
     */
    private static final Pattern VALID_WORD = Pattern.compile("^[a-z]+(?:[-'][a-z]+)*$");

    /**
     * 首尾非字母字符（用于剔除标点：括号、引号、句号、破折号……）
     */
    private static final Pattern LEADING_NON_LETTER = Pattern.compile("^[^a-z]+");
    private static final Pattern TRAILING_NON_LETTER = Pattern.compile("[^a-z]+$");

    /**
     * exchange 中标记原形的键，形如 {@code 0:run/1:i}
     */
    private static final String LEMMA_KEY = "0";

    /**
     * 词形变化查询入口。抽成函数接口是为了让单元测试不依赖数据库。
     */
    @FunctionalInterface
    public interface ExchangeLookup {

        /**
         * @param spelling 已转小写的单词
         * @return exchange 字段原文，查不到返回 null
         */
        String find(String spelling);
    }

    private final ExchangeLookup exchangeLookup;

    /**
     * 无词典环境下的构造（纯拆词，restoreLemma 原样返回）
     */
    public WordTextParser() {
        this.exchangeLookup = spelling -> null;
    }

    /**
     * Spring 装配用：走本地 ECDICT 库
     */
    @Autowired
    public WordTextParser(StardictMapper stardictMapper) {
        this.exchangeLookup = stardictMapper::selectExchangeByWord;
    }

    /**
     * 自定义词源（单元测试用）。
     * <p>
     * 之所以是静态工厂而不是构造重载：{@link StardictMapper} 恰好也是「String → String」的
     * 单方法接口，再加一个 {@code WordTextParser(ExchangeLookup)} 重载会让 lambda 实参产生歧义。
     *
     * @param exchangeLookup 词形变化查询实现
     */
    public static WordTextParser withLookup(ExchangeLookup exchangeLookup) {
        return new WordTextParser(exchangeLookup);
    }

    private WordTextParser(ExchangeLookup exchangeLookup) {
        this.exchangeLookup = exchangeLookup;
    }

    /**
     * 拆词
     *
     * @param text 用户粘贴的任意文本，可为 null
     * @return 拆分结果，永不为 null
     */
    public ParseResult parse(String text) {
        if (text == null || text.isBlank()) {
            return ParseResult.empty();
        }
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String token : DELIMITER.split(text)) {
            String word = normalize(token);
            if (word != null) {
                distinct.add(word);
            }
        }
        int total = distinct.size();
        if (total == 0) {
            return ParseResult.empty();
        }
        boolean truncated = total > WordConstant.MAX_CAPTURE_WORDS;
        List<String> words = new ArrayList<>(distinct);
        if (truncated) {
            words = new ArrayList<>(words.subList(0, WordConstant.MAX_CAPTURE_WORDS));
        }
        return ParseResult.builder()
                .words(words)
                .totalCount(total)
                .truncated(truncated)
                .build();
    }

    /**
     * 只要词列表的便捷入口，截断信息一并丢弃
     *
     * @param text 用户粘贴的任意文本
     * @return 候选词列表，永不为 null
     */
    public List<String> parseWords(String text) {
        return parse(text).getWords();
    }

    /**
     * 词形还原：running → run、better → good、mice → mouse
     * <p>
     * 查 ECDICT 的 exchange 字段，取 {@code 0:} 段即原形。exchange 只覆盖约 12.5% 的词条，
     * 查不到、查出异常一律返回原词——录入链路绝不能被词形还原阻断。
     *
     * @param word 单词，可为 null
     * @return 原形；无法还原时返回入参（已 trim + 转小写）
     */
    public String restoreLemma(String word) {
        if (word == null || word.isBlank()) {
            return word;
        }
        String spelling = word.trim().toLowerCase();
        try {
            String lemma = extractLemma(exchangeLookup.find(spelling));
            return lemma == null ? spelling : lemma;
        } catch (Exception e) {
            log.warn("词形还原失败，按原词处理，word = {}", spelling, e);
            return spelling;
        }
    }

    /**
     * 从 exchange 串中取原形
     * <p>
     * exchange 形如 {@code 0:run/1:i/i:running/s:runnings}，{@code 0:} 段即原形。
     * 纯函数，便于单测。
     *
     * @param exchange ECDICT 的 exchange 字段原文
     * @return 原形；无 {@code 0:} 段或格式不可解析时返回 null
     */
    public static String extractLemma(String exchange) {
        if (exchange == null || exchange.isBlank()) {
            return null;
        }
        for (String segment : exchange.split("/")) {
            int colon = segment.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            if (!LEMMA_KEY.equals(segment.substring(0, colon).trim())) {
                continue;
            }
            String lemma = segment.substring(colon + 1).trim().toLowerCase();
            if (!lemma.isEmpty()) {
                return lemma;
            }
        }
        return null;
    }

    /**
     * 单个 token 归一化：转小写 → 剔除首尾标点 → 校验是否纯英文单词
     *
     * @return 合法单词；不合法返回 null
     */
    private static String normalize(String token) {
        if (token == null) {
            return null;
        }
        // 统一右单引号，粘贴自 Word / 网页的 don’t 才能匹配上
        String word = token.trim().toLowerCase().replace('’', '\'');
        word = LEADING_NON_LETTER.matcher(word).replaceFirst("");
        word = TRAILING_NON_LETTER.matcher(word).replaceFirst("");
        if (word.isEmpty() || word.length() > WordConstant.MAX_SPELLING_LENGTH) {
            return null;
        }
        return VALID_WORD.matcher(word).matches() ? word : null;
    }

    /**
     * 拆词结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParseResult {

        /**
         * 候选词列表，已去重、已截断到上限
         */
        private List<String> words;

        /**
         * 截断前的去重词数
         */
        private int totalCount;

        /**
         * 是否触发了 50 词上限截断
         */
        private boolean truncated;

        public static ParseResult empty() {
            return ParseResult.builder()
                    .words(Collections.emptyList())
                    .totalCount(0)
                    .truncated(false)
                    .build();
        }

        /**
         * 被截断丢弃的词数
         */
        public int getDroppedCount() {
            return truncated ? totalCount - words.size() : 0;
        }

        public boolean isEmpty() {
            return words == null || words.isEmpty();
        }
    }
}
