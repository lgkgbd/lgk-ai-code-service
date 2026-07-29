package com.lgk.lgkaicodeservice.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

/**
 * 图片等比压缩（纯 JDK 实现，无第三方依赖）
 * <p>
 * 为什么必须压：手机直出照片普遍 3~5MB、4000px 宽，而
 * <ul>
 *   <li>MinIO 侧有 1MB 上限（storage.minio.max-file-size-bytes）</li>
 *   <li>VL 模型按图片尺寸计费/耗时，4000px 纯属浪费</li>
 * </ul>
 * 压到最长边 1600px + JPEG 0.82，实测手写字迹依然清晰可辨，体积通常降到 300~500KB。
 * <p>
 * 所有异常都自吞并返回原图——压缩只是优化手段，绝不能成为录入链路的阻断点。
 */
@Slf4j
public class ImageCompressor {

    private ImageCompressor() {
    }

    /**
     * 压缩结果
     */
    @Data
    @AllArgsConstructor
    public static class Result {

        /**
         * 压缩后字节；无法压缩时为原图字节
         */
        private byte[] bytes;

        /**
         * 压缩后的 MIME，压缩成功恒为 image/jpeg；未压缩则为原 MIME
         */
        private String contentType;

        /**
         * 是否真的压缩了（false 表示原样返回）
         */
        private boolean compressed;

        public int size() {
            return bytes == null ? 0 : bytes.length;
        }
    }

    /**
     * 等比压缩到指定最长边
     *
     * @param raw             原图字节
     * @param originContentType 原图 MIME，压缩失败时原样带回
     * @param maxEdge         最长边像素上限
     * @param quality         JPEG 质量 0~1
     * @return 压缩结果，永不为 null；任何异常都退化为原图
     */
    public static Result compress(byte[] raw, String originContentType, int maxEdge, float quality) {
        if (raw == null || raw.length == 0) {
            return new Result(raw, originContentType, false);
        }
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(raw));
            if (src == null) {
                // ImageIO 不认识的格式（如 iPhone 原生 HEIC），原样放行交由上层判大小
                log.info("图片格式无法解码，跳过压缩，contentType={}, size={}KB",
                        originContentType, raw.length / 1024);
                return new Result(raw, originContentType, false);
            }

            int w = src.getWidth();
            int h = src.getHeight();
            double scale = Math.min(1.0, (double) maxEdge / Math.max(w, h));
            int targetW = Math.max(1, (int) Math.round(w * scale));
            int targetH = Math.max(1, (int) Math.round(h * scale));

            // 缩放 + 拍平透明通道（PNG 转 JPEG 时透明区会变黑，先铺白底）
            BufferedImage dst = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dst.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, targetW, targetH);
                g.drawImage(src, 0, 0, targetW, targetH, null);
            } finally {
                g.dispose();
            }

            byte[] jpeg = writeJpeg(dst, quality);
            if (jpeg == null || jpeg.length == 0) {
                return new Result(raw, originContentType, false);
            }
            // 小图重编码后反而变大时保留原图（例如本来就是高压缩率的小 JPEG）
            if (jpeg.length >= raw.length) {
                log.debug("压缩后体积未减小，保留原图，{}KB -> {}KB", raw.length / 1024, jpeg.length / 1024);
                return new Result(raw, originContentType, false);
            }
            log.info("图片压缩完成：{}x{} {}KB -> {}x{} {}KB",
                    w, h, raw.length / 1024, targetW, targetH, jpeg.length / 1024);
            return new Result(jpeg, "image/jpeg", true);
        } catch (Exception e) {
            log.warn("图片压缩异常，退化为原图", e);
            return new Result(raw, originContentType, false);
        }
    }

    /**
     * 按指定质量编码 JPEG。用 ImageWriter 而非 ImageIO.write 才能控制压缩率
     */
    private static byte[] writeJpeg(BufferedImage image, float quality) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            return null;
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(Math.max(0.1f, Math.min(1.0f, quality)));
            }
            writer.write(null, new IIOImage(image, null, null), param);
            ios.flush();
            return bos.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}
