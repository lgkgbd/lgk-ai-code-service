package com.lgk.lgkaicodeservice.service.word.ocr;

import com.lgk.lgkaicodeservice.constant.WordConstant;
import com.lgk.lgkaicodeservice.model.vo.WordOcrItemVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VL 输出解析测试（纯函数，不联网、不烧 token）
 * <p>
 * 这一层是拍照录入的<b>健壮性底线</b>：模型输出不可控，可能带 markdown 围栏、
 * 带解释文字、键名不对、甚至根本不是 JSON。任何一种都不允许抛异常炸掉整个识别任务，
 * 最差也只能是「识别不出东西」，让用户回退到手动录入。
 */
class WordOcrRecognizerParseTest {

    /**
     * 便捷断言：把结果压成 "word:translation" 便于比对
     */
    private static List<String> flat(List<WordOcrItemVO> items) {
        return items.stream()
                .map(i -> i.getWord() + ":" + (i.getTranslation() == null ? "" : i.getTranslation()))
                .collect(Collectors.toList());
    }

    // ==================== 主路径：标准 JSON ====================

    @Nested
    @DisplayName("标准 JSON 输出")
    class StandardJson {

        @Test
        @DisplayName("最理想情况：干净的 JSON 数组，词与释义都提取")
        void parseCleanJson() {
            String raw = """
                    [{"word":"apple","translation":"苹果"},{"word":"benefit","translation":"益处"}]
                    """;
            List<WordOcrItemVO> items = WordOcrRecognizer.parseModelOutput(raw);
            assertEquals(List.of("apple:苹果", "benefit:益处"), flat(items));
        }

        @Test
        @DisplayName("释义为空串：便利贴上只写了单词没写中文")
        void parseEmptyTranslation() {
            String raw = "[{\"word\":\"ubiquitous\",\"translation\":\"\"}]";
            List<WordOcrItemVO> items = WordOcrRecognizer.parseModelOutput(raw);
            assertEquals(1, items.size());
            assertEquals("ubiquitous", items.get(0).getWord());
            assertEquals("", items.get(0).getTranslation());
        }

        @Test
        @DisplayName("空数组：图里没有英文单词")
        void parseEmptyArray() {
            assertTrue(WordOcrRecognizer.parseModelOutput("[]").isEmpty());
        }

        @Test
        @DisplayName("translation 字段整个缺失，不能 NPE")
        void parseMissingTranslationKey() {
            String raw = "[{\"word\":\"resilient\"}]";
            List<WordOcrItemVO> items = WordOcrRecognizer.parseModelOutput(raw);
            assertEquals(1, items.size());
            assertEquals("resilient", items.get(0).getWord());
            assertEquals("", items.get(0).getTranslation());
        }

        @Test
        @DisplayName("translation 为 null 字面量")
        void parseNullTranslation() {
            String raw = "[{\"word\":\"vivid\",\"translation\":null}]";
            List<WordOcrItemVO> items = WordOcrRecognizer.parseModelOutput(raw);
            assertEquals("vivid:", flat(items).get(0));
        }
    }

    // ==================== 模型的各种「不听话」输出 ====================

    @Nested
    @DisplayName("模型不听话时的兼容")
    class MessyOutput {

        @Test
        @DisplayName("裹了 markdown 代码围栏 —— 最常见的不听话")
        void parseWithCodeFence() {
            String raw = """
                    ```json
                    [{"word":"diligent","translation":"勤勉的"}]
                    ```
                    """;
            assertEquals(List.of("diligent:勤勉的"), flat(WordOcrRecognizer.parseModelOutput(raw)));
        }

        @Test
        @DisplayName("JSON 前后夹带解释文字")
        void parseWithSurroundingProse() {
            String raw = """
                    好的，我识别到图片中有以下单词：
                    [{"word":"nostalgia","translation":"怀旧"}]
                    希望对你有帮助！
                    """;
            assertEquals(List.of("nostalgia:怀旧"), flat(WordOcrRecognizer.parseModelOutput(raw)));
        }

        @Test
        @DisplayName("偷懒返回纯字符串数组")
        void parseStringArray() {
            String raw = "[\"apple\", \"banana\"]";
            assertEquals(List.of("apple:", "banana:"), flat(WordOcrRecognizer.parseModelOutput(raw)));
        }

        @Test
        @DisplayName("用了别的键名 spelling / meaning")
        void parseAlternativeKeys() {
            String raw = "[{\"spelling\":\"gorgeous\",\"meaning\":\"华丽的\"}]";
            assertEquals(List.of("gorgeous:华丽的"), flat(WordOcrRecognizer.parseModelOutput(raw)));
        }

        @Test
        @DisplayName("完全不是 JSON，降级为按行解析")
        void fallbackToLineParsing() {
            String raw = """
                    apple 苹果
                    benefit - 益处
                    resilient：有韧性的
                    """;
            List<WordOcrItemVO> items = WordOcrRecognizer.parseModelOutput(raw);
            assertEquals(List.of("apple:苹果", "benefit:益处", "resilient:有韧性的"), flat(items));
        }

        @Test
        @DisplayName("JSON 语法坏掉（缺右括号），降级不抛异常")
        void brokenJsonDoesNotThrow() {
            String raw = "[{\"word\":\"apple\",\"translation\":\"苹果\"";
            List<WordOcrItemVO> items = WordOcrRecognizer.parseModelOutput(raw);
            assertNotNull(items);
            // 降级按行解析仍应捞到 apple
            assertTrue(items.stream().anyMatch(i -> "apple".equals(i.getWord())));
        }

        @Test
        @DisplayName("输出撞上 max_tokens 被截断，前面完整的词条要救回来")
        void salvageTruncatedArray() {
            String raw = """
                    [{"word":"apple","translation":"苹果"},{"word":"benefit","translation":"益处"},{"word":"cher\
                    """;
            List<WordOcrItemVO> items = WordOcrRecognizer.parseModelOutput(raw);
            // 前两个完整的必须保住，最后半拉的丢掉即可
            assertEquals(List.of("apple:苹果", "benefit:益处"), flat(items));
        }

        @Test
        @DisplayName("截断在字符串中间（引号都没闭合），补齐后仍能解析")
        void repairUnterminatedString() {
            String raw = "[{\"word\":\"apple\",\"translation\":\"苹";
            List<WordOcrItemVO> items = WordOcrRecognizer.parseModelOutput(raw);
            assertEquals(1, items.size());
            assertEquals("apple", items.get(0).getWord());
        }

        @Test
        @DisplayName("模型说「图中没有英文单词」这类纯中文回复，结果为空但不报错")
        void pureChineseReply() {
            List<WordOcrItemVO> items = WordOcrRecognizer.parseModelOutput("图片中没有识别到任何英文单词。");
            assertNotNull(items);
            assertTrue(items.isEmpty());
        }
    }

    // ==================== 归一化 ====================

    @Nested
    @DisplayName("归一化与清洗")
    class Normalization {

        @Test
        @DisplayName("统一转小写：便利贴上常写大写或首字母大写")
        void lowercase() {
            String raw = "[{\"word\":\"Apple\"},{\"word\":\"BENEFIT\"}]";
            assertEquals(List.of("apple:", "benefit:"), flat(WordOcrRecognizer.parseModelOutput(raw)));
        }

        @Test
        @DisplayName("剔除首尾标点：识别常带上句号、引号、序号点")
        void stripPunctuation() {
            String raw = "[{\"word\":\"\\\"apple\\\".\"},{\"word\":\"(benefit)\"}]";
            assertEquals(List.of("apple:", "benefit:"), flat(WordOcrRecognizer.parseModelOutput(raw)));
        }

        @Test
        @DisplayName("保留合法的连字符与撇号")
        void keepHyphenAndApostrophe() {
            String raw = "[{\"word\":\"well-known\"},{\"word\":\"don't\"}]";
            assertEquals(List.of("well-known:", "don't:"), flat(WordOcrRecognizer.parseModelOutput(raw)));
        }

        @Test
        @DisplayName("中文右单引号 ’ 归一为 '，否则 don’t 会被判为非法词丢掉")
        void normalizeCurlyApostrophe() {
            String raw = "[{\"word\":\"don’t\"}]";
            assertEquals(List.of("don't:"), flat(WordOcrRecognizer.parseModelOutput(raw)));
        }

        @Test
        @DisplayName("剔除非英文词：中文、数字、纯符号、日期")
        void dropNonEnglish() {
            String raw = """
                    [{"word":"苹果"},{"word":"123"},{"word":"2026-07-29"},
                     {"word":"→"},{"word":""},{"word":"valid"}]
                    """;
            assertEquals(List.of("valid:"), flat(WordOcrRecognizer.parseModelOutput(raw)));
        }

        @Test
        @DisplayName("去重：同一个词在多处出现只留一个")
        void deduplicate() {
            String raw = "[{\"word\":\"apple\"},{\"word\":\"Apple\"},{\"word\":\"apple\"}]";
            assertEquals(1, WordOcrRecognizer.parseModelOutput(raw).size());
        }

        @Test
        @DisplayName("去重时用后出现的释义补全先出现的空释义（多图场景的关键）")
        void dedupeFillsMissingTranslation() {
            String raw = """
                    [{"word":"apple","translation":""},{"word":"apple","translation":"苹果"}]
                    """;
            assertEquals(List.of("apple:苹果"), flat(WordOcrRecognizer.parseModelOutput(raw)));
        }

        @Test
        @DisplayName("先出现的已有释义则不被后者覆盖")
        void dedupeKeepsFirstTranslation() {
            String raw = """
                    [{"word":"apple","translation":"苹果"},{"word":"apple","translation":"错误释义"}]
                    """;
            assertEquals(List.of("apple:苹果"), flat(WordOcrRecognizer.parseModelOutput(raw)));
        }

        @Test
        @DisplayName("超长释义截断到上限")
        void truncateLongTranslation() {
            String longText = "释".repeat(WordConstant.MAX_OCR_TRANSLATION_LENGTH + 50);
            String raw = "[{\"word\":\"apple\",\"translation\":\"" + longText + "\"}]";
            List<WordOcrItemVO> items = WordOcrRecognizer.parseModelOutput(raw);
            assertEquals(WordConstant.MAX_OCR_TRANSLATION_LENGTH, items.get(0).getTranslation().length());
        }

        @Test
        @DisplayName("超过 50 词上限时截断，保护下游批量录入")
        void capAtMaxWords() {
            String json = IntStream.range(0, WordConstant.MAX_CAPTURE_WORDS + 20)
                    .mapToObj(i -> "{\"word\":\"word" + toLetters(i) + "\"}")
                    .collect(Collectors.joining(",", "[", "]"));
            List<WordOcrItemVO> items = WordOcrRecognizer.parseModelOutput(json);
            assertEquals(WordConstant.MAX_CAPTURE_WORDS, items.size());
        }

        /**
         * 数字转字母，避免生成含数字的非法词
         */
        private static String toLetters(int i) {
            StringBuilder sb = new StringBuilder();
            int n = i;
            do {
                sb.append((char) ('a' + n % 26));
                n /= 26;
            } while (n > 0);
            return sb.toString();
        }
    }

    // ==================== 边界 ====================

    @Nested
    @DisplayName("边界输入")
    class EdgeCases {

        @Test
        @DisplayName("null / 空串 / 空白，一律返回空列表而非 null")
        void nullAndBlank() {
            assertTrue(WordOcrRecognizer.parseModelOutput(null).isEmpty());
            assertTrue(WordOcrRecognizer.parseModelOutput("").isEmpty());
            assertTrue(WordOcrRecognizer.parseModelOutput("   \n  ").isEmpty());
        }

        @Test
        @DisplayName("normalize 幂等：跑两遍结果不变（多图合并依赖这点）")
        void normalizeIsIdempotent() {
            List<WordOcrItemVO> once = WordOcrRecognizer.parseModelOutput(
                    "[{\"word\":\"Apple\",\"translation\":\"苹果\"},{\"word\":\"benefit\"}]");
            List<WordOcrItemVO> twice = WordOcrRecognizer.normalize(once);
            assertEquals(flat(once), flat(twice));
        }

        @Test
        @DisplayName("多图结果合并：normalize 直接用于跨图去重")
        void mergeAcrossImages() {
            List<WordOcrItemVO> image1 = WordOcrRecognizer.parseModelOutput(
                    "[{\"word\":\"apple\",\"translation\":\"\"},{\"word\":\"benefit\",\"translation\":\"益处\"}]");
            List<WordOcrItemVO> image2 = WordOcrRecognizer.parseModelOutput(
                    "[{\"word\":\"apple\",\"translation\":\"苹果\"},{\"word\":\"cherry\",\"translation\":\"樱桃\"}]");

            List<WordOcrItemVO> all = new java.util.ArrayList<>();
            all.addAll(image1);
            all.addAll(image2);
            List<WordOcrItemVO> merged = WordOcrRecognizer.normalize(all);

            assertEquals(3, merged.size());
            // 第一张图没写释义的 apple，被第二张图的释义补上了
            assertEquals(List.of("apple:苹果", "benefit:益处", "cherry:樱桃"), flat(merged));
        }

        @Test
        @DisplayName("数组里混入 null 元素与嵌套数组，跳过而非崩溃")
        void skipMalformedElements() {
            String raw = "[null, [1,2], {\"word\":\"apple\"}, 42]";
            List<WordOcrItemVO> items = WordOcrRecognizer.parseModelOutput(raw);
            assertFalse(items.isEmpty());
            assertTrue(items.stream().anyMatch(i -> "apple".equals(i.getWord())));
        }
    }
}
