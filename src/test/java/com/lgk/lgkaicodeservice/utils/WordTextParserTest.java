package com.lgk.lgkaicodeservice.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用例清单见 docs/单词记忆功能-开发计划.md 6.1 节
 */
class WordTextParserTest {

    /**
     * 不依赖数据库的纯拆词解析器
     */
    private final WordTextParser parser = new WordTextParser();

    /**
     * exchange 取自本地 stardict 表真实数据（770611 词条）
     */
    private static final Map<String, String> EXCHANGE_FIXTURE = Map.of(
            "running", "0:run/1:i/i:running/s:runnings",
            "better", "0:good/1:r/d:bettered/s:betters/i:bettering/p:bettered/3:betters",
            "mice", "0:mouse/1:s",
            "ran", "0:run/1:p/s:rans",
            "run", "p:ran/i:running/d:run/0:run/1:d/3:runs/s:runs",
            "mouse", "s:mice/i:mousing/p:moused/3:mouses/d:moused",
            "data", "",
            "good", "t:best/r:better"
    );

    private final WordTextParser dictParser =
            WordTextParser.withLookup(EXCHANGE_FIXTURE::get);

    // ==================== 拆词 ====================

    @Test
    @DisplayName("单个单词")
    void parseSingleWord() {
        WordTextParser.ParseResult result = parser.parse("serendipity");
        assertIterableEquals(List.of("serendipity"), result.getWords());
        assertEquals(1, result.getTotalCount());
        assertFalse(result.isTruncated());
    }

    @Test
    @DisplayName("逗号分隔")
    void parseCommaSeparated() {
        assertIterableEquals(List.of("adapt", "adopt", "adept"),
                parser.parseWords("adapt, adopt, adept"));
    }

    @Test
    @DisplayName("换行分隔")
    void parseNewlineSeparated() {
        assertIterableEquals(List.of("adapt", "adopt", "adept"),
                parser.parseWords("adapt\nadopt\r\nadept"));
    }

    @Test
    @DisplayName("空格分隔")
    void parseSpaceSeparated() {
        assertIterableEquals(List.of("adapt", "adopt", "adept"),
                parser.parseWords("adapt  adopt\tadept"));
    }

    @Test
    @DisplayName("分号分隔")
    void parseSemicolonSeparated() {
        assertIterableEquals(List.of("adapt", "adopt", "adept"),
                parser.parseWords("adapt;adopt; adept"));
    }

    @Test
    @DisplayName("顿号与中文标点分隔")
    void parseChinesePunctuationSeparated() {
        assertIterableEquals(List.of("adapt", "adopt", "adept"),
                parser.parseWords("adapt、adopt，adept"));
    }

    @Test
    @DisplayName("混合分隔：逗号 + 换行 + 分号 + 顿号 + 空格")
    void parseMixedDelimiters() {
        assertIterableEquals(List.of("alpha", "beta", "gamma", "delta", "epsilon"),
                parser.parseWords("alpha, beta\ngamma; delta、epsilon"));
    }

    @Test
    @DisplayName("剔除首尾标点")
    void parseStripsPunctuation() {
        assertIterableEquals(List.of("hello", "world", "test", "quote"),
                parser.parseWords("hello, world! (test). \"quote\""));
    }

    @Test
    @DisplayName("统一转小写并去重")
    void parseLowercaseAndDistinct() {
        WordTextParser.ParseResult result = parser.parse("Apple APPLE apple ApPlE");
        assertIterableEquals(List.of("apple"), result.getWords());
        assertEquals(1, result.getTotalCount());
    }

    @Test
    @DisplayName("保留连字符与撇号：well-known / don't")
    void parseKeepsHyphenAndApostrophe() {
        assertIterableEquals(List.of("well-known", "don't", "state-of-the-art"),
                parser.parseWords("well-known, don't, state-of-the-art"));
    }

    @Test
    @DisplayName("中文右单引号 ’ 归一化为 '")
    void parseNormalizesCurlyApostrophe() {
        assertIterableEquals(List.of("don't"), parser.parseWords("don’t"));
    }

    @Test
    @DisplayName("整句英文按空格拆词（生词筛选交给上层）")
    void parseSentence() {
        List<String> words = parser.parseWords("The quick brown fox jumps over the lazy dog.");
        // the 出现两次，去重后 8 个
        assertIterableEquals(
                List.of("the", "quick", "brown", "fox", "jumps", "over", "lazy", "dog"), words);
    }

    @Test
    @DisplayName("空串 / null / 纯空白 → 空结果")
    void parseBlank() {
        for (String input : new String[]{null, "", "   ", "\n\t  \r\n"}) {
            WordTextParser.ParseResult result = parser.parse(input);
            assertTrue(result.isEmpty(), "输入 [" + input + "] 应返回空结果");
            assertEquals(0, result.getTotalCount());
            assertFalse(result.isTruncated());
        }
    }

    @Test
    @DisplayName("纯中文 → 空结果")
    void parsePureChinese() {
        assertTrue(parser.parse("你好，世界。这是一段中文。").isEmpty());
    }

    @Test
    @DisplayName("纯数字与符号被剔除")
    void parseDropsNonLetters() {
        assertTrue(parser.parse("123, 3.14, ###, @@@, ---").isEmpty());
    }

    @Test
    @DisplayName("中英混排只留英文词")
    void parseMixedChineseEnglish() {
        assertIterableEquals(List.of("serendipity", "epiphany"),
                parser.parseWords("今天遇到 serendipity 和 epiphany 两个词"));
    }

    @Test
    @DisplayName("超长文本不报错，仍按规则拆词")
    void parseVeryLongText() {
        String paragraph = "the quick brown fox jumps over the lazy dog ".repeat(500);
        WordTextParser.ParseResult result = parser.parse(paragraph);
        // 去重后只有 8 个不同的词，不触发截断
        assertEquals(8, result.getTotalCount());
        assertFalse(result.isTruncated());
    }

    @Test
    @DisplayName("超过 50 词上限：截断到 50 并标记 truncated")
    void parseTruncatesAtLimit() {
        String text = IntStream.range(0, 60)
                .mapToObj(i -> "word" + (char) ('a' + i / 26) + (char) ('a' + i % 26))
                .collect(Collectors.joining(", "));
        WordTextParser.ParseResult result = parser.parse(text);
        assertEquals(50, result.getWords().size());
        assertEquals(60, result.getTotalCount());
        assertTrue(result.isTruncated());
        assertEquals(10, result.getDroppedCount());
    }

    @Test
    @DisplayName("正好 50 词不触发截断")
    void parseExactlyAtLimit() {
        String text = IntStream.range(0, 50)
                .mapToObj(i -> "word" + (char) ('a' + i / 26) + (char) ('a' + i % 26))
                .collect(Collectors.joining(" "));
        WordTextParser.ParseResult result = parser.parse(text);
        assertEquals(50, result.getWords().size());
        assertFalse(result.isTruncated());
        assertEquals(0, result.getDroppedCount());
    }

    // ==================== 词形还原 ====================

    @Nested
    @DisplayName("词形还原 restoreLemma")
    class RestoreLemma {

        @Test
        @DisplayName("running → run")
        void running() {
            assertEquals("run", dictParser.restoreLemma("running"));
        }

        @Test
        @DisplayName("better → good")
        void better() {
            assertEquals("good", dictParser.restoreLemma("better"));
        }

        @Test
        @DisplayName("mice → mouse")
        void mice() {
            assertEquals("mouse", dictParser.restoreLemma("mice"));
        }

        @Test
        @DisplayName("ran → run")
        void ran() {
            assertEquals("run", dictParser.restoreLemma("ran"));
        }

        @Test
        @DisplayName("data 无 exchange，不可还原 → 原样返回")
        void data() {
            assertEquals("data", dictParser.restoreLemma("data"));
        }

        @Test
        @DisplayName("mouse 的 exchange 无 0: 段（它本身就是原形）→ 原样返回")
        void mouse() {
            assertEquals("mouse", dictParser.restoreLemma("mouse"));
        }

        @Test
        @DisplayName("原形自身还原后仍是自己")
        void lemmaItself() {
            assertEquals("run", dictParser.restoreLemma("run"));
            assertEquals("good", dictParser.restoreLemma("good"));
        }

        @Test
        @DisplayName("词库里没有的词 → 原样返回")
        void unknownWord() {
            assertEquals("zzzznotaword", dictParser.restoreLemma("zzzznotaword"));
        }

        @Test
        @DisplayName("大写与首尾空格照样能还原")
        void caseInsensitive() {
            assertEquals("run", dictParser.restoreLemma("  Running "));
        }

        @Test
        @DisplayName("null / 空串不抛异常")
        void nullSafe() {
            assertNull(dictParser.restoreLemma(null));
            assertEquals("", dictParser.restoreLemma(""));
        }

        @Test
        @DisplayName("查询抛异常时不阻断录入，返回原词")
        void lookupFailureDoesNotBlock() {
            WordTextParser broken = WordTextParser.withLookup(word -> {
                throw new IllegalStateException("db down");
            });
            assertEquals("running", broken.restoreLemma("running"));
        }

        @Test
        @DisplayName("无词典环境下退化为原样返回")
        void noDictionary() {
            assertEquals("running", new WordTextParser().restoreLemma("running"));
        }

        @Test
        @DisplayName("extractLemma 解析畸形 exchange 不抛异常")
        void extractLemmaMalformed() {
            assertNull(WordTextParser.extractLemma(null));
            assertNull(WordTextParser.extractLemma(""));
            assertNull(WordTextParser.extractLemma("   "));
            assertNull(WordTextParser.extractLemma("garbage"));
            assertNull(WordTextParser.extractLemma("///"));
            assertNull(WordTextParser.extractLemma("0:"));
            assertNull(WordTextParser.extractLemma(":run"));
            assertEquals("run", WordTextParser.extractLemma("1:i/0:run"));
        }
    }
}
