package com.lgk.lgkaicodeservice.utils;

import com.lgk.lgkaicodeservice.constant.WordConstant;
import com.lgk.lgkaicodeservice.utils.ImageCompressor.Result;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 图片压缩测试（纯 JDK，不启 Spring、不联网、不读外部图片文件）
 * <p>
 * 这一层是「手机拍照 → 上传 → VL 识别」链路的<b>第一道关</b>，坏了整个功能就废了：
 * <ul>
 *   <li>MinIO 侧硬上限 1MB，手机直出照片普遍 3~5MB，压不下去 = 存图必失败</li>
 *   <li>压缩只是优化手段，绝不能成为阻断点——任何异常输入都必须原样放行而不是抛异常</li>
 * </ul>
 * 所有测试图片都在测试内用 java.awt 动态生成，固定 seed 保证可重复。
 * <p>
 * 注意：生成测试图<b>刻意不用纯色</b>——纯色 JPEG 压缩率极高，会把「压缩后变小」
 * 这类断言测成假阳性。这里统一用渐变 + 随机色块 + 笔迹线条制造真实的图像熵。
 */
class ImageCompressorTest {

    /**
     * 生产链路实际使用的参数，测试必须与之一致，否则测了个寂寞
     */
    private static final int MAX_EDGE = WordConstant.OCR_IMAGE_MAX_EDGE;

    private static final float QUALITY = WordConstant.OCR_IMAGE_JPEG_QUALITY;

    /**
     * MinIO 单文件上限 1MB，本类里最关键的一条红线
     */
    private static final int MINIO_MAX_BYTES = 1024 * 1024;

    /**
     * 4000x3000 的横向大图字节数较大，多个用例共用，只生成一次
     */
    private static byte[] phonePhotoLandscape;

    @BeforeAll
    static void setUpAll() {
        // 单测环境无显示设备，且不希望 ImageIO 落临时文件
        System.setProperty("java.awt.headless", "true");
        ImageIO.setUseCache(false);
    }

    @AfterAll
    static void tearDownAll() {
        // 大图字节数组早点释放，避免拖累同一 JVM 里的后续测试
        phonePhotoLandscape = null;
    }

    // ==================== 手机直出大图：功能成败的关键路径 ====================

    @Nested
    @DisplayName("手机直出大图")
    class PhonePhoto {

        @Test
        @DisplayName("4000x3000 压缩后最长边等于 1600，体积显著变小，输出 image/jpeg")
        void largePhotoIsScaledToMaxEdge() {
            byte[] raw = phonePhotoLandscape();

            Result result = ImageCompressor.compress(raw, "image/jpeg", MAX_EDGE, QUALITY);

            assertTrue(result.isCompressed(), "大图必须真的被压缩");
            assertEquals("image/jpeg", result.getContentType(), "压缩成功后 MIME 恒为 image/jpeg");

            BufferedImage out = readImage(result.getBytes());
            assertEquals(MAX_EDGE, Math.max(out.getWidth(), out.getHeight()),
                    "最长边必须正好压到 " + MAX_EDGE);
            assertEquals(MAX_EDGE, out.getWidth(), "横图的最长边是宽");
            assertEquals(1200, out.getHeight(), "4000x3000 等比缩放后应为 1600x1200");

            assertTrue(result.size() < raw.length,
                    "压缩后必须比原图小：" + raw.length + " -> " + result.size());
            assertTrue(result.size() < raw.length / 2,
                    "像素数降到 1/6.25，体积至少应腰斩：" + raw.length + " -> " + result.size());
        }

        @Test
        @DisplayName("关键回归：4000x3000 压缩后必须 < 1MB，否则 MinIO 存图直接失败")
        void largePhotoFitsInMinioLimit() {
            byte[] raw = phonePhotoLandscape();
            // 前提：手机直出照片本来就超过 MinIO 上限，这正是必须压缩的理由
            assertTrue(raw.length > MINIO_MAX_BYTES,
                    "测试前提不成立，构造的原图只有 " + raw.length + " 字节，没超过 1MB");

            Result result = ImageCompressor.compress(raw, "image/jpeg", MAX_EDGE, QUALITY);

            assertTrue(result.size() < MINIO_MAX_BYTES,
                    "压缩后 " + result.size() + " 字节，超过 MinIO 上限 " + MINIO_MAX_BYTES + " 字节，存图会失败");
        }

        @Test
        @DisplayName("竖拍 3000x4000：最长边是高，压缩后 height == 1600")
        void portraitPhotoScalesByHeight() {
            byte[] raw = toJpeg(photo(3000, 4000, 7788L), 1.0f);

            Result result = ImageCompressor.compress(raw, "image/jpeg", MAX_EDGE, QUALITY);

            assertTrue(result.isCompressed());
            BufferedImage out = readImage(result.getBytes());
            assertEquals(MAX_EDGE, out.getHeight(), "竖图应按高压到 1600");
            assertEquals(1200, out.getWidth(), "3000x4000 等比缩放后应为 1200x1600");
            assertTrue(result.size() < MINIO_MAX_BYTES,
                    "竖拍同样要压进 MinIO 上限，实际 " + result.size() + " 字节");
        }

        @Test
        @DisplayName("压缩产物是合法 JPEG：magic bytes 正确且能被 ImageIO 读回")
        void outputIsReadableJpeg() {
            byte[] raw = phonePhotoLandscape();

            Result result = ImageCompressor.compress(raw, "image/jpeg", MAX_EDGE, QUALITY);
            byte[] bytes = result.getBytes();

            assertTrue(bytes.length > 3, "输出不应为空");
            assertEquals((byte) 0xFF, bytes[0], "JPEG SOI 首字节应为 0xFF");
            assertEquals((byte) 0xD8, bytes[1], "JPEG SOI 次字节应为 0xD8");
            assertEquals((byte) 0xFF, bytes[2], "SOI 之后应紧跟下一个 marker 的 0xFF");

            BufferedImage out = assertDoesNotThrow(() -> ImageIO.read(new ByteArrayInputStream(bytes)));
            assertNotNull(out, "压缩产物必须能被 ImageIO 正常解码");
            assertEquals(result.size(), bytes.length, "Result.size() 应等于字节长度");
        }
    }

    // ==================== 等比缩放 ====================

    @Nested
    @DisplayName("等比缩放")
    class AspectRatio {

        @Test
        @DisplayName("宽图 3000x1000 → 1600x533，宽高比保持不变（允许 ±1 像素舍入）")
        void wideImageKeepsAspectRatio() {
            byte[] raw = toJpeg(photo(3000, 1000, 4321L), 1.0f);

            Result result = ImageCompressor.compress(raw, "image/jpeg", MAX_EDGE, QUALITY);

            assertTrue(result.isCompressed());
            BufferedImage out = readImage(result.getBytes());
            assertEquals(MAX_EDGE, out.getWidth(), "宽图按宽压到 1600");
            // 1000 * (1600/3000) = 533.33 → 四舍五入 533
            assertTrue(Math.abs(out.getHeight() - 533) <= 1,
                    "高应在 533 ±1 像素，实际 " + out.getHeight());

            double srcRatio = 3000d / 1000d;
            double dstRatio = (double) out.getWidth() / out.getHeight();
            assertTrue(Math.abs(srcRatio - dstRatio) < 0.02,
                    "宽高比必须保持：原 " + srcRatio + "，现 " + dstRatio);
        }

        @Test
        @DisplayName("小图 100x100 不放大，输出仍是 100x100")
        void smallImageIsNotUpscaled() {
            byte[] raw = toPng(noise(100, 100, 555L));

            Result result = ImageCompressor.compress(raw, "image/png", MAX_EDGE, QUALITY);

            BufferedImage out = readImage(result.getBytes());
            assertEquals(100, out.getWidth(), "小图不得被放大到 maxEdge");
            assertEquals(100, out.getHeight(), "小图不得被放大到 maxEdge");
        }
    }

    // ==================== 重编码反而变大：必须退回原图 ====================

    @Test
    @DisplayName("已经极小的高压缩 JPEG 重编码后变大 → 原样返回，compressed=false")
    void alreadyTinyJpegIsReturnedAsIs() {
        // 64x64 逐像素噪声 + 质量 0.05：体积压到极致，用 0.82 重编码必然变大
        byte[] raw = toJpeg(noise(64, 64, 999L), 0.05f);

        Result result = ImageCompressor.compress(raw, "image/jpeg", MAX_EDGE, QUALITY);

        assertFalse(result.isCompressed(), "重编码后变大时不应声称压缩成功");
        assertSame(raw, result.getBytes(), "应原样带回入参字节，不做任何改写");
        assertEquals("image/jpeg", result.getContentType(), "未压缩时 MIME 保持原值");
        assertEquals(raw.length, result.size());
    }

    // ==================== PNG 透明通道：转 JPEG 后不能变黑 ====================

    @Test
    @DisplayName("PNG 透明区转 JPEG 后铺白底（不是黑底），且不报错")
    void transparentPngGetsWhiteBackground() {
        // 2400x2400，四周 500px 全透明，中间画满细节：
        // 尺寸超过 maxEdge 保证一定走压缩分支，才能验证透明区的处理结果
        byte[] raw = toPng(transparentBordered(2400, 500, 20260729L));

        Result result = ImageCompressor.compress(raw, "image/png", MAX_EDGE, QUALITY);

        assertTrue(result.isCompressed(), "带细节的大 PNG 转 JPEG 必然变小");
        assertEquals("image/jpeg", result.getContentType());

        BufferedImage out = readImage(result.getBytes());
        assertEquals(MAX_EDGE, out.getWidth());
        assertEquals(MAX_EDGE, out.getHeight());

        // 四个角原本全透明，铺白底后应接近纯白；若实现漏了 fillRect(WHITE) 这里会是近黑
        assertCornerIsWhite(out, 0, 0, "左上");
        assertCornerIsWhite(out, out.getWidth() - 1, 0, "右上");
        assertCornerIsWhite(out, 0, out.getHeight() - 1, "左下");
        assertCornerIsWhite(out, out.getWidth() - 1, out.getHeight() - 1, "右下");
    }

    private static void assertCornerIsWhite(BufferedImage image, int x, int y, String corner) {
        int rgb = image.getRGB(x, y);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        boolean nearlyBlack = r < 32 && g < 32 && b < 32;
        assertTrue(r >= 235 && g >= 235 && b >= 235,
                corner + "角透明区应铺白底，实际 rgb(" + r + "," + g + "," + b + ")"
                        + (nearlyBlack ? "——这是典型的「PNG 透明区转 JPEG 变黑」回归" : ""));
    }

    // ==================== 异常输入：只能退化，不能抛 ====================

    @Nested
    @DisplayName("异常输入退化")
    class Degradation {

        @Test
        @DisplayName("不可解码的字节 → 原样返回，保留原 contentType，不抛异常")
        void undecodableBytesArePassedThrough() {
            byte[] raw = "not an image".getBytes(StandardCharsets.UTF_8);

            Result result = assertDoesNotThrow(
                    () -> ImageCompressor.compress(raw, "image/heic", MAX_EDGE, QUALITY));

            assertFalse(result.isCompressed(), "解不开的格式不算压缩成功");
            assertSame(raw, result.getBytes(), "应原样放行，交由上层判大小");
            assertArrayEquals("not an image".getBytes(StandardCharsets.UTF_8), result.getBytes(),
                    "字节内容不得被改写");
            assertEquals("image/heic", result.getContentType(), "原 MIME 必须带回，iPhone HEIC 走的就是这条路");
            assertEquals(raw.length, result.size());
        }

        @Test
        @DisplayName("null 字节 → 安全返回，不抛异常")
        void nullBytesAreSafe() {
            Result result = assertDoesNotThrow(
                    () -> ImageCompressor.compress(null, "image/jpeg", MAX_EDGE, QUALITY));

            assertNotNull(result, "永不返回 null");
            assertNull(result.getBytes());
            assertFalse(result.isCompressed());
            assertEquals("image/jpeg", result.getContentType());
            assertEquals(0, result.size(), "null 字节的 size() 应为 0 而不是 NPE");
        }

        @Test
        @DisplayName("空数组 / null contentType → 安全返回，不抛异常")
        void emptyBytesAreSafe() {
            byte[] raw = new byte[0];

            Result result = assertDoesNotThrow(
                    () -> ImageCompressor.compress(raw, null, MAX_EDGE, QUALITY));

            assertNotNull(result);
            assertSame(raw, result.getBytes());
            assertFalse(result.isCompressed());
            assertNull(result.getContentType());
            assertEquals(0, result.size());
        }
    }

    // ==================== 测试图片生成（固定 seed，可重复） ====================

    /**
     * 4000x3000 模拟手机直出照片，字节较大，跨用例复用
     */
    private static synchronized byte[] phonePhotoLandscape() {
        if (phonePhotoLandscape == null) {
            // 质量 1.0 让原图体积逼近真实手机直出（数 MB），才能验证「必须压进 1MB」
            phonePhotoLandscape = toJpeg(photo(4000, 3000, 20260729L), 1.0f);
        }
        return phonePhotoLandscape;
    }

    /**
     * 生成一张有真实图像熵的「便利贴照片」：纸张渐变底 + 随机色块 + 手写笔迹线条。
     * <p>
     * 刻意避开纯色和逐像素噪声：纯色会让 JPEG 压缩率虚高，逐像素噪声在缩放后
     * 又会把输出撑爆，两者都会让断言失真。
     */
    private static BufferedImage photo(int width, int height, long seed) {
        Random random = new Random(seed);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // 纸张受光不均的渐变底
            g.setPaint(new GradientPaint(0, 0, new Color(252, 246, 214),
                    width, height, new Color(186, 172, 128)));
            g.fillRect(0, 0, width, height);
            paintEntropy(g, 0, 0, width, height, random);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * 生成一张带透明边框的 PNG：四周 border 像素全透明，中间画满细节。
     * 用于验证 PNG → JPEG 时透明区被铺成白底
     */
    private static BufferedImage transparentBordered(int size, int border, long seed) {
        Random random = new Random(seed);
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // TYPE_INT_ARGB 初始即全透明，只在中间区域画不透明内容
            int inner = size - border * 2;
            g.setPaint(new GradientPaint(border, border, new Color(240, 120, 60),
                    size - border, size - border, new Color(40, 90, 200)));
            g.fillRect(border, border, inner, inner);
            g.setClip(border, border, inner, inner);
            paintEntropy(g, border, border, inner, inner, random);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * 在指定矩形区域内涂随机色块与笔迹，制造图像熵
     */
    private static void paintEntropy(Graphics2D g, int x0, int y0, int width, int height, Random random) {
        int blocks = 400;
        for (int i = 0; i < blocks; i++) {
            g.setColor(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
            int w = 8 + random.nextInt(Math.max(2, width / 8));
            int h = 8 + random.nextInt(Math.max(2, height / 8));
            int x = x0 + random.nextInt(Math.max(1, width - 1));
            int y = y0 + random.nextInt(Math.max(1, height - 1));
            if (random.nextBoolean()) {
                g.fillRect(x, y, w, h);
            } else {
                g.fillOval(x, y, w, h);
            }
        }
        // 手写笔迹：细长深色折线，模拟便利贴上的单词
        int strokes = 200;
        for (int i = 0; i < strokes; i++) {
            g.setColor(new Color(random.nextInt(90), random.nextInt(90), random.nextInt(120)));
            g.setStroke(new BasicStroke(1f + random.nextFloat() * 4f));
            int x1 = x0 + random.nextInt(Math.max(1, width));
            int y1 = y0 + random.nextInt(Math.max(1, height));
            int x2 = x0 + random.nextInt(Math.max(1, width));
            int y2 = y0 + random.nextInt(Math.max(1, height));
            g.drawLine(x1, y1, x2, y2);
        }
    }

    /**
     * 逐像素随机噪声，几乎不可压缩。只用于小图，避免撑大断言
     */
    private static BufferedImage noise(int width, int height, long seed) {
        Random random = new Random(seed);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }
        return image;
    }

    // ==================== 编解码小工具 ====================

    /**
     * 按指定质量编码 JPEG（ImageIO.write 无法控制质量，必须走 ImageWriter）
     */
    private static byte[] toJpeg(BufferedImage image, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        assertTrue(writers.hasNext(), "当前 JDK 缺少 JPEG ImageWriter，测试环境异常");
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
            ios.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("测试图片 JPEG 编码失败", e);
        } finally {
            writer.dispose();
        }
    }

    private static byte[] toPng(BufferedImage image) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            assertTrue(ImageIO.write(image, "png", bos), "PNG 编码失败，测试环境异常");
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("测试图片 PNG 编码失败", e);
        }
    }

    /**
     * 读回压缩产物以校验真实像素尺寸
     */
    private static BufferedImage readImage(byte[] bytes) {
        assertNotNull(bytes, "待解码字节不应为 null");
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new IllegalStateException("压缩产物无法解码", e);
        }
        assertNotNull(image, "压缩产物必须是能被 ImageIO 解码的合法图片");
        return image;
    }
}
