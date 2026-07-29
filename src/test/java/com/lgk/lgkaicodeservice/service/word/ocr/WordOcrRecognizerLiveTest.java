package com.lgk.lgkaicodeservice.service.word.ocr;

import com.lgk.lgkaicodeservice.constant.WordConstant;
import com.lgk.lgkaicodeservice.model.vo.WordOcrItemVO;
import com.lgk.lgkaicodeservice.utils.ImageCompressor;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实调用 qwen3-vl-plus 的识别测试（会产生 API 费用与网络请求）
 * <p>
 * 与 {@link WordOcrRecognizerParseTest} 的分工：那边用假数据把解析逻辑的各种脏输入覆盖死，
 * 这边只回答一个问题——<b>真模型看真图片，到底认不认得出来</b>。这是解析测试永远替代不了的。
 * <p>
 * 断言口径刻意留了余量：不要求 100% 全中（模型对手写体本就会偶尔看走眼，产品设计上
 * 也正是因此才强制用户确认后才入库），只要求命中率达到阈值，避免用例因模型的正常波动而假红。
 */
@SpringBootTest
class WordOcrRecognizerLiveTest {

    /**
     * 命中率阈值。低于此值说明提示词或模型选型出了问题，不是正常波动
     */
    private static final double MIN_RECALL = 0.75;

    @Resource
    private WordOcrRecognizer recognizer;

    @Test
    @DisplayName("真实模型识别合成便利贴：命中率达标，且不把日期/涂鸦当成单词")
    void recognizeSyntheticStickyNote() {
        byte[] image = StickyNoteImageFixture.generate();
        System.out.println("合成便利贴大小：" + image.length / 1024 + "KB");

        List<WordOcrItemVO> items = recognizer.recognize(image, "image/jpeg");
        assertNotNull(items);
        print("合成便利贴", items);

        Set<String> got = items.stream().map(WordOcrItemVO::getWord).collect(Collectors.toSet());
        List<String> expected = StickyNoteImageFixture.DEFAULT_ENTRIES.stream()
                .map(StickyNoteImageFixture.Entry::word).toList();

        long hit = expected.stream().filter(got::contains).count();
        double recall = (double) hit / expected.size();
        System.out.printf("命中 %d/%d，召回率 %.0f%%，漏掉：%s%n",
                hit, expected.size(), recall * 100,
                expected.stream().filter(w -> !got.contains(w)).toList());

        assertTrue(recall >= MIN_RECALL,
                String.format("召回率 %.0f%% 低于阈值 %.0f%%，实际识别=%s",
                        recall * 100, MIN_RECALL * 100, got));

        // 干扰元素不该被当成单词。日期里的 P.27 最容易被误识别成词
        assertFalse(got.contains("p"), "把页码 P.27 误当成了单词");
    }

    @Test
    @DisplayName("中文释义能一并提取（B 方案的数据来源）")
    void extractsChineseTranslation() {
        byte[] image = StickyNoteImageFixture.generate();
        List<WordOcrItemVO> items = recognizer.recognize(image, "image/jpeg");

        long withTranslation = items.stream()
                .filter(i -> i.getTranslation() != null && !i.getTranslation().isBlank())
                .count();
        System.out.println("带中文释义的词数：" + withTranslation + "/" + items.size());

        // 图上每个词旁边都写了中文，至少要提到一半
        assertTrue(withTranslation >= items.size() / 2,
                "中文释义提取过少，只有 " + withTranslation + "/" + items.size()
                        + "，B 方案的私有笔记会大面积为空");
    }

    @Test
    @DisplayName("手机尺寸大图压缩后依然识别得出来 —— 压缩不能把字压糊")
    void recognizeAfterCompression() {
        // 必须用 4000px 的大图，常规合成图只有 1200px 触发不了压缩，测了等于没测
        byte[] original = StickyNoteImageFixture.generatePhoneSized();
        ImageCompressor.Result compressed = ImageCompressor.compress(
                original, "image/jpeg",
                WordConstant.OCR_IMAGE_MAX_EDGE, WordConstant.OCR_IMAGE_JPEG_QUALITY);
        System.out.printf("压缩：%dKB -> %dKB（compressed=%s）%n",
                original.length / 1024, compressed.size() / 1024, compressed.isCompressed());

        assertTrue(compressed.isCompressed(), "4000px 的图居然没被压缩，压缩链路有问题");
        assertTrue(compressed.size() < 1024 * 1024,
                "压缩后 " + compressed.size() / 1024 + "KB 仍超过 MinIO 的 1MB 上限，存图会失败");

        List<WordOcrItemVO> items = recognizer.recognize(
                compressed.getBytes(), compressed.getContentType());
        print("压缩后", items);

        Set<String> got = items.stream().map(WordOcrItemVO::getWord).collect(Collectors.toSet());
        List<String> expected = StickyNoteImageFixture.DEFAULT_ENTRIES.stream()
                .map(StickyNoteImageFixture.Entry::word).toList();
        double recall = (double) expected.stream().filter(got::contains).count() / expected.size();
        System.out.printf("压缩后召回率 %.0f%%%n", recall * 100);

        assertTrue(recall >= MIN_RECALL,
                String.format("压缩后召回率 %.0f%% 不达标，说明压缩参数把字迹压糊了，"
                        + "需调大 OCR_IMAGE_MAX_EDGE 或 OCR_IMAGE_JPEG_QUALITY", recall * 100));
    }

    @Test
    @DisplayName("空白图片不会瞎编单词")
    void blankImageYieldsNothing() {
        byte[] blank = StickyNoteImageFixture.generate(List.of(), 1L);
        List<WordOcrItemVO> items = recognizer.recognize(blank, "image/jpeg");
        assertNotNull(items);
        print("空白图", items);
        // 只有日期和涂鸦，不该识别出成篇的单词
        assertTrue(items.size() <= 2, "空白便利贴上凭空识别出了 " + items.size() + " 个词：" + items);
    }

    @Test
    @DisplayName("真实照片识别（把手机拍的便利贴放进 src/test/resources/word-ocr/ 后自动生效）")
    void recognizeRealPhotos() {
        List<byte[]> photos = StickyNoteImageFixture.realPhotos();
        if (photos.isEmpty()) {
            System.out.println("未提供真实照片，跳过。"
                    + "把手机拍的便利贴放进 src/test/resources/word-ocr/ 再跑本用例即可验证真实效果。");
            return;
        }
        for (int i = 0; i < photos.size(); i++) {
            ImageCompressor.Result compressed = ImageCompressor.compress(
                    photos.get(i), "image/jpeg",
                    WordConstant.OCR_IMAGE_MAX_EDGE, WordConstant.OCR_IMAGE_JPEG_QUALITY);
            List<WordOcrItemVO> items = recognizer.recognize(
                    compressed.getBytes(), compressed.getContentType());
            print("真实照片 #" + (i + 1) + "（" + photos.get(i).length / 1024 + "KB → "
                    + compressed.size() / 1024 + "KB）", items);
            assertNotNull(items);
        }
    }

    private static void print(String title, List<WordOcrItemVO> items) {
        System.out.println("---- " + title + "：识别出 " + items.size() + " 个词 ----");
        items.forEach(i -> System.out.printf("  %-16s %s%n",
                i.getWord(), i.getTranslation() == null ? "" : i.getTranslation()));
    }
}
