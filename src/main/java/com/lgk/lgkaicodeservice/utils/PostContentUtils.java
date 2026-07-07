package com.lgk.lgkaicodeservice.utils;

import cn.hutool.core.util.StrUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 帖子正文处理工具
 * <p>
 * 帖子正文以 Markdown 存储，列表页不需要返回完整正文（可能非常大）。
 * 这里提供把 Markdown 提取为纯文本摘要、以及提取首图作为封面的能力，
 * 与前端 {@code CommunityPage.vue} 中的处理逻辑保持一致。
 */
public class PostContentUtils {

    /**
     * 列表摘要默认长度
     */
    public static final int DEFAULT_PREVIEW_LENGTH = 100;

    /**
     * Markdown 图片语法：![alt](url)
     */
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[[^\\]]*]\\(([^)\\s]+)\\)");

    /**
     * 链接语法：[text](url)
     */
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]*)]\\([^)]*\\)");

    private PostContentUtils() {
    }

    /**
     * 从 Markdown 正文中提取第一张图片的 URL
     *
     * @param content Markdown 正文
     * @return 首图 URL；不存在时返回 null
     */
    public static String extractFirstImageUrl(String content) {
        if (StrUtil.isBlank(content)) {
            return null;
        }
        Matcher matcher = IMAGE_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 把 Markdown 正文剥离为纯文本（移除图片、保留链接文字、去掉标题/加粗等符号）
     *
     * @param content Markdown 正文
     * @return 纯文本
     */
    public static String toPlainText(String content) {
        if (StrUtil.isBlank(content)) {
            return "";
        }
        String text = IMAGE_PATTERN.matcher(content).replaceAll("");   // 移除图片
        text = LINK_PATTERN.matcher(text).replaceAll("$1");            // 链接只保留文字
        text = text.replaceAll("(?m)^#{1,6}\\s+", "");                // 移除标题符号 #
        text = text.replaceAll("(\\*\\*|__|\\*|_|`|~~)", "");         // 移除加粗/斜体/行内代码/删除线
        text = text.replaceAll("(?m)^>\\s?", "");                     // 移除引用符号 >
        text = text.replaceAll("\\s+", " ");                          // 折叠空白
        return text.trim();
    }

    /**
     * 生成列表页用的纯文本摘要（超出长度时截断并追加省略号）
     *
     * @param content   Markdown 正文
     * @param maxLength 最大长度
     * @return 纯文本摘要
     */
    public static String toPlainTextPreview(String content, int maxLength) {
        String plainText = toPlainText(content);
        if (plainText.length() <= maxLength) {
            return plainText;
        }
        return plainText.substring(0, maxLength) + "…";
    }
}
