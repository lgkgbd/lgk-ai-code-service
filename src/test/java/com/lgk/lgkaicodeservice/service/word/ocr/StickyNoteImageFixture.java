package com.lgk.lgkaicodeservice.service.word.ocr;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 生词便利贴测试图片夹具
 * <p>
 * 造图而不是塞一张固定的二进制图进仓库，好处是：用例自解释（期望词就写在代码里）、
 * 仓库不变胖、想调难度随时改。
 * <p>
 * 刻意做旧：整页倾斜、逐字符抖动、传感器噪点、光照渐变，再混入日期/箭头/涂鸦等干扰元素，
 * 逼近真实拍摄条件。字体优先挑系统里的手写体（macOS 上通常有 Bradley Hand / Noteworthy），
 * 挑不到才退回斜体无衬线——毕竟本功能要解决的正是<b>手写</b>识别。
 */
@Slf4j
public final class StickyNoteImageFixture {

    private StickyNoteImageFixture() {
    }

    /**
     * 便利贴上的一行：英文词 + 手写中文释义
     */
    public record Entry(String word, String translation) {
    }

    /**
     * 默认词表。选词偏中高难度，且都是 ECDICT 里确定有中文释义的常见考试词，
     * 便于同时验证「词典有释义时不写私有笔记」那条 B 方案规则
     */
    public static final List<Entry> DEFAULT_ENTRIES = List.of(
            new Entry("ubiquitous", "无处不在的"),
            new Entry("resilient", "有韧性的"),
            new Entry("nostalgia", "怀旧"),
            new Entry("diligent", "勤勉的"),
            new Entry("ambiguous", "模棱两可的"),
            new Entry("meticulous", "一丝不苟的"),
            new Entry("serendipity", "机缘巧合"),
            new Entry("eloquent", "雄辩的"));

    /**
     * 用户可以把自己真实拍的便利贴照片丢进这个目录，测试会优先用真实照片。
     * 目录不存在或为空则回退到合成图
     */
    private static final Path REAL_PHOTO_DIR = Path.of("src/test/resources/word-ocr");

    private static final List<String> IMAGE_SUFFIX = List.of(".jpg", ".jpeg", ".png", ".webp", ".bmp");

    /**
     * 用默认词表生成一张便利贴
     *
     * @return JPEG 字节
     */
    public static byte[] generate() {
        return generate(DEFAULT_ENTRIES, 42L);
    }

    /**
     * 生成手机直出尺寸（约 4000px 宽）的便利贴照片
     * <p>
     * 存在的意义是让压缩链路真正被触发：常规尺寸的合成图只有 1200px，
     * 根本够不着 1600px 的压缩阈值，拿它测压缩等于什么都没测。
     * 真实手机照片普遍 3000~4000px、3~5MB，这才是压缩要面对的输入。
     *
     * @return JPEG 字节
     */
    public static byte[] generatePhoneSized() {
        byte[] base = generate();
        try {
            BufferedImage src = ImageIO.read(new java.io.ByteArrayInputStream(base));
            int targetW = 4000;
            int targetH = Math.round(src.getHeight() * (targetW / (float) src.getWidth()));
            BufferedImage big = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = big.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(src, 0, 0, targetW, targetH, null);
            } finally {
                g.dispose();
            }
            return toJpeg(big);
        } catch (IOException e) {
            throw new IllegalStateException("生成手机尺寸测试图失败", e);
        }
    }

    /**
     * 生成一张仿手写生词便利贴
     *
     * @param entries 要写上去的词条
     * @param seed    随机种子，固定后每次生成的图完全一致，保证用例可重复
     * @return JPEG 字节
     */
    public static byte[] generate(List<Entry> entries, long seed) {
        int width = 1200;
        int height = Math.max(600, 230 + entries.size() * 145 + 220);
        Random rnd = new Random(seed);

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 便利贴淡黄底 + 渐变，模拟拍照时的光照不均
            g.setPaint(new GradientPaint(0, 0, new Color(255, 249, 196),
                    width, height, new Color(253, 236, 150)));
            g.fillRect(0, 0, width, height);

            // 整页轻微倾斜，模拟手持拍摄
            g.rotate(Math.toRadians(-1.6), width / 2.0, height / 2.0);

            // 干扰元素：日期与页码。模型应当忽略，不能把 P 或数字当成单词
            g.setColor(new Color(120, 110, 80));
            g.setFont(handwritingFont(34));
            g.drawString("2026-03-14  周五  P.27", 90, 110);

            Font wordFont = handwritingFont(58);
            Font cnFont = new Font("PingFang SC", Font.PLAIN, 38);

            int y = 230;
            for (Entry entry : entries) {
                AffineTransform saved = g.getTransform();
                // 每行独立小角度抖动，模拟手写不齐
                g.rotate(Math.toRadians(rnd.nextDouble() * 2.4 - 1.2), 100, y);

                g.setColor(new Color(28, 36, 84));
                g.setFont(wordFont);
                drawJittered(g, entry.word(), 100, y, rnd);

                g.setColor(new Color(190, 60, 50));
                g.setFont(cnFont);
                g.drawString(entry.translation(), 640, y);

                // 手绘箭头（干扰元素）
                g.setColor(new Color(150, 140, 110));
                g.setStroke(new BasicStroke(3f));
                g.drawLine(560, y - 14, 615, y - 14);
                g.drawLine(615, y - 14, 602, y - 24);
                g.drawLine(615, y - 14, 602, y - 4);

                g.setTransform(saved);
                y += 145;
            }

            // 底部涂鸦（干扰元素）
            g.setColor(new Color(160, 150, 120));
            g.setStroke(new BasicStroke(4f));
            g.drawOval(width - 320, height - 160, 130, 80);
            g.drawLine(120, height - 100, 300, height - 140);
        } finally {
            g.dispose();
        }

        addSensorNoise(img, rnd, 24000);
        return toJpeg(img);
    }

    /**
     * 优先取用户放进 {@link #REAL_PHOTO_DIR} 的真实照片，没有则返回空列表。
     * <p>
     * 合成图终究是「印刷体加抖动」，真手写的连笔、涂改、透光是模拟不出来的。
     * 想验证真实效果，把手机拍的便利贴丢进那个目录再跑一次即可。
     *
     * @return 真实照片字节列表，按文件名排序
     */
    public static List<byte[]> realPhotos() {
        List<byte[]> photos = new ArrayList<>();
        if (!Files.isDirectory(REAL_PHOTO_DIR)) {
            return photos;
        }
        try (var stream = Files.list(REAL_PHOTO_DIR)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return IMAGE_SUFFIX.stream().anyMatch(name::endsWith);
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path file : files) {
                photos.add(Files.readAllBytes(file));
            }
        } catch (IOException e) {
            log.warn("读取真实照片目录失败：{}", REAL_PHOTO_DIR, e);
        }
        return photos;
    }

    /**
     * 逐字符加随机抖动，避免呈现为规整印刷体
     */
    private static void drawJittered(Graphics2D g, String text, int x, int y, Random rnd) {
        int cursor = x;
        for (char c : text.toCharArray()) {
            int dy = rnd.nextInt(9) - 4;
            g.drawString(String.valueOf(c), cursor, y + dy);
            cursor += g.getFontMetrics().charWidth(c) + rnd.nextInt(5) - 2;
        }
    }

    /**
     * 撒椒盐噪点，模拟相机传感器噪声
     */
    private static void addSensorNoise(BufferedImage img, Random rnd, int points) {
        for (int i = 0; i < points; i++) {
            int x = rnd.nextInt(img.getWidth());
            int y = rnd.nextInt(img.getHeight());
            int rgb = img.getRGB(x, y);
            int delta = rnd.nextInt(26) - 13;
            int r = clamp(((rgb >> 16) & 0xFF) + delta);
            int g = clamp(((rgb >> 8) & 0xFF) + delta);
            int b = clamp((rgb & 0xFF) + delta);
            img.setRGB(x, y, (r << 16) | (g << 8) | b);
        }
    }

    /**
     * 优先使用系统里的手写体——本功能要解决的正是手写识别
     */
    private static Font handwritingFont(int size) {
        List<String> preferred = List.of("Bradley Hand", "Noteworthy", "Marker Felt",
                "Chalkboard", "Comic Sans MS", "Segoe Script");
        List<String> available = List.of(GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames());
        for (String name : preferred) {
            if (available.contains(name)) {
                return new Font(name, Font.PLAIN, size);
            }
        }
        return new Font(Font.SANS_SERIF, Font.ITALIC, size);
    }

    private static byte[] toJpeg(BufferedImage img) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "jpg", bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成测试图片失败", e);
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
