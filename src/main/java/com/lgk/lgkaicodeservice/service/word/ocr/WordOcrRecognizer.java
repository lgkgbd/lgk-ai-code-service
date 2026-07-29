package com.lgk.lgkaicodeservice.service.word.ocr;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lgk.lgkaicodeservice.constant.WordConstant;
import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.model.vo.WordOcrItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 便利贴照片 → 候选生词列表（通义千问 VL 多模态模型）
 * <p>
 * 为什么用 VL 而不是传统 OCR：用户的生词是<b>手写</b>在便利贴上的，传统 OCR（Tesseract 等）
 * 对手写体识别率很低，且便利贴上常混着中文释义、箭头、涂鸦，OCR 出来是一堆需要二次清洗的脏文本。
 * VL 模型能在一次调用里同时完成「识别 + 理解 + 结构化」，直接吐出 JSON。
 * <p>
 * 网络调用与结果解析刻意拆开：{@link #parseModelOutput(String)} 是纯静态函数，
 * 单测可以不依赖网络、不烧 token 就覆盖各种脏输出。
 */
@Slf4j
@Component
public class WordOcrRecognizer {

    private static final Gson GSON = new Gson();

    /**
     * 合法单词：纯英文字母，允许中间的连字符与撇号（well-known、don't）。
     * 与 {@link com.lgk.lgkaicodeservice.utils.WordTextParser} 的口径保持一致
     */
    private static final Pattern VALID_WORD = Pattern.compile("^[a-z]+(?:[-'][a-z]+)*$");

    /**
     * 剥离模型爱加的 markdown 代码围栏
     */
    private static final Pattern CODE_FENCE = Pattern.compile("^\\s*```(?:json|JSON)?\\s*|\\s*```\\s*$");

    /**
     * 最内层的完整 JSON 对象，用于从截断输出里打捞残存词条
     */
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[^{}]*}");

    /**
     * 提示词。要点：
     * <ol>
     *   <li>明确场景是「手写生词便利贴」，让模型对潦草字迹更宽容</li>
     *   <li>强调忽略涂鸦/页码/日期等噪声，减少前端纠错成本</li>
     *   <li>释义一并提取——用户便利贴上通常写了中文，能省一次查词</li>
     *   <li>严令只输出 JSON，不要解释、不要 markdown</li>
     * </ol>
     */
    private static final String PROMPT = """
            这是一张用户手写（或打印）的英语生词便利贴照片。请提取图中所有的英文单词或短语。

            要求：
            1. 只提取英文单词/短语，忽略页码、日期、箭头、涂鸦、装饰符号等无关内容
            2. 若某个英文单词旁边写有对应的中文释义，请一并提取到 translation 字段；没有则留空字符串
            3. 保持单词原本的拼写，不要自行修正或翻译成其他词
            4. 同一个词只返回一次
            5. 严格只输出 JSON 数组，不要输出任何解释文字，不要包裹 markdown 代码块

            输出格式示例：
            [{"word":"apple","translation":"苹果"},{"word":"benefit","translation":""}]

            如果图中没有任何英文单词，输出：[]
            """;

    @Value("${dashscope.api-key:}")
    private String apiKey;

    @Value("${dashscope.vl-model:qwen3-vl-plus}")
    private String vlModel;

    /**
     * 识别一张图片
     *
     * @param imageBytes  图片字节（建议已压缩）
     * @param contentType 图片 MIME，如 image/jpeg
     * @return 候选词列表，永不为 null
     * @throws BusinessException 模型调用失败时抛出，由上层任务标记为 FAILED
     */
    public List<WordOcrItemVO> recognize(byte[] imageBytes, String contentType) {
        if (imageBytes == null || imageBytes.length == 0) {
            return Collections.emptyList();
        }
        String raw = callModel(imageBytes, contentType);
        List<WordOcrItemVO> items = parseModelOutput(raw);
        log.info("VL 识别完成，model={}, 图片={}KB, 识别出 {} 个候选词",
                vlModel, imageBytes.length / 1024, items.size());
        return items;
    }

    /**
     * 调用 DashScope 多模态接口。图片以 base64 data URL 内联传输
     * <p>
     * 为什么不传 MinIO 短链：MinIO 部署在内网 / 本机，DashScope 的服务器根本访问不到，
     * 只能把字节直接内联给它。
     */
    private String callModel(byte[] imageBytes, String contentType) {
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("<")) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未配置 dashscope.api-key，无法使用拍照录入");
        }
        String mime = (contentType == null || contentType.isBlank()) ? "image/jpeg" : contentType;
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

        MultiModalMessage userMessage = MultiModalMessage.builder()
                .role(Role.USER.getValue())
                .content(List.of(
                        Map.of("image", dataUrl),
                        Map.of("text", PROMPT)))
                .build();
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(apiKey)
                .model(vlModel)
                .message(userMessage)
                // 识别任务要的是稳定复现，不是创意
                .temperature(0.01f)
                .build();
        try {
            MultiModalConversationResult result = new MultiModalConversation().call(param);
            return extractText(result);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("VL 模型调用失败，model={}", vlModel, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片识别失败：" + e.getMessage());
        }
    }

    /**
     * 从多模态返回结构里取出文本。content 是 List&lt;Map&lt;String,Object&gt;&gt;，
     * 文本在 key 为 text 的那一项里
     */
    private static String extractText(MultiModalConversationResult result) {
        if (result == null || result.getOutput() == null
                || result.getOutput().getChoices() == null
                || result.getOutput().getChoices().isEmpty()) {
            return null;
        }
        MultiModalMessage message = result.getOutput().getChoices().get(0).getMessage();
        if (message == null || message.getContent() == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> part : message.getContent()) {
            Object text = part.get("text");
            if (text != null) {
                sb.append(text);
            }
        }
        return sb.toString();
    }

    // ==================== 解析（纯函数，便于单测）====================

    /**
     * 解析模型输出为候选词列表
     * <p>
     * 模型再怎么被要求「只输出 JSON」也可能带上解释文字或代码围栏，所以这里分四层兜底：
     * <ol>
     *   <li>剥围栏后截取首个 {@code [} 到末个 {@code ]}，按 JSON 数组解析（主路径）</li>
     *   <li>数组不完整（如输出被 max_tokens 截断）则打捞其中完整的 <code>{...}</code> 对象</li>
     *   <li>再不行就按行扫描，捞出形如 {@code word 释义} 的行（纯文本降级）</li>
     *   <li>还不行返回空列表——宁可让用户手动录，也不能抛异常炸掉整个任务</li>
     * </ol>
     * 无论走哪条路径，都统一做归一化：转小写、校验词形、按首次出现去重、截断释义。
     *
     * @param raw 模型原始输出，可为 null
     * @return 候选词列表，永不为 null
     */
    public static List<WordOcrItemVO> parseModelOutput(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        String cleaned = CODE_FENCE.matcher(raw.trim()).replaceAll("").trim();

        List<WordOcrItemVO> items = parseAsJsonArray(cleaned);
        if (items == null) {
            // 输出被 max_tokens 截断时，先试着把缺的引号/括号补回去再解析
            items = parseAsJsonArray(repairTruncatedJson(cleaned));
        }
        if (items == null) {
            // 补不回来（中间就烂了）则退而求其次，逐个打捞完整对象。
            // 不能直接跳到按行解析——JSON 片段里的键名（word / translation）
            // 会被行解析误当成单词捞出来
            items = salvageJsonObjects(cleaned);
        }
        if (items == null) {
            log.warn("VL 输出不是合法 JSON，降级为按行解析。原始输出前 200 字：{}",
                    cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned);
            items = parseAsLines(cleaned);
        }
        return normalize(items);
    }

    /**
     * 修复被截断的 JSON：按需补回未闭合的字符串引号、花括号和方括号
     * <p>
     * 模型输出撞上 max_tokens 时会从任意位置断掉，最后往往剩个半拉对象
     * （如 {@code [{"word":"apple","translation":"苹果"}）。前面的数据都是好的，
     * 补齐闭合符就能救回来。
     *
     * @param text 已剥围栏的原始输出
     * @return 补齐后的字符串；无 {@code [} 时原样返回
     */
    private static String repairTruncatedJson(String text) {
        int start = text.indexOf('[');
        if (start < 0) {
            return text;
        }
        String body = text.substring(start);

        // 只要还有完整对象，就把尾部残缺的那个整个丢掉，只保留到最后一个 }。
        // 不去补全残缺对象是刻意的：单词被截成一半时（cherry → cher）补出来的是个
        // 看起来合法、实则不存在的词，宁可少给也不能凭空造词
        int lastBrace = body.lastIndexOf('}');
        if (lastBrace >= 0) {
            return body.substring(0, lastBrace + 1) + "]";
        }

        // 一个完整对象都没有：这是唯一的数据，尽力补齐总好过全丢
        // 逐字符扫描，统计括号深度并判断是否停在字符串内部（要跳过转义字符）
        boolean inString = false;
        boolean escaped = false;
        int braces = 0;
        int brackets = 0;
        for (char c : body.toCharArray()) {
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            switch (c) {
                case '{' -> braces++;
                case '}' -> braces--;
                case '[' -> brackets++;
                case ']' -> brackets--;
                default -> {
                }
            }
        }

        StringBuilder repaired = new StringBuilder(body);
        if (inString) {
            repaired.append('"');
        }
        // 去掉悬空的逗号与残缺的键名，例如 ...,"transl
        while (!repaired.isEmpty()) {
            char last = repaired.charAt(repaired.length() - 1);
            if (last == ',' || Character.isWhitespace(last)) {
                repaired.setLength(repaired.length() - 1);
            } else {
                break;
            }
        }
        repaired.append("}".repeat(Math.max(0, braces)));
        repaired.append("]".repeat(Math.max(0, brackets)));
        return repaired.toString();
    }

    /**
     * 打捞路径：从残缺文本里逐个抠出完整的 <code>{...}</code> 并解析
     * <p>
     * 典型场景是模型输出撞上 max_tokens 被拦腰截断，最后一个对象不完整但前面的都是好的，
     * 没理由因为尾巴烂了就把整批结果丢掉。
     *
     * @return 捞到至少一个对象则返回列表；一个都没有返回 null，交给下一层
     */
    private static List<WordOcrItemVO> salvageJsonObjects(String text) {
        java.util.regex.Matcher m = JSON_OBJECT.matcher(text);
        List<WordOcrItemVO> items = new ArrayList<>();
        while (m.find()) {
            try {
                JsonElement element = JsonParser.parseString(m.group());
                if (!element.isJsonObject()) {
                    continue;
                }
                WordOcrItemVO item = toItem(element.getAsJsonObject());
                if (item != null) {
                    items.add(item);
                }
            } catch (Exception ignored) {
                // 单个对象坏了就跳过，继续捞下一个
            }
        }
        if (items.isEmpty()) {
            return null;
        }
        log.info("VL 输出 JSON 不完整，已打捞出 {} 个完整词条", items.size());
        return items;
    }

    /**
     * 主路径：截取并解析 JSON 数组
     *
     * @return 解析失败返回 null（区别于「解析成功但空数组」）
     */
    private static List<WordOcrItemVO> parseAsJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(text.substring(start, end + 1));
            if (!parsed.isJsonArray()) {
                return null;
            }
            JsonArray array = parsed.getAsJsonArray();
            List<WordOcrItemVO> items = new ArrayList<>();
            for (JsonElement element : array) {
                if (element == null || element.isJsonNull()) {
                    continue;
                }
                // 容忍模型偷懒直接返回 ["apple","benefit"] 这种字符串数组
                if (element.isJsonPrimitive()) {
                    items.add(WordOcrItemVO.builder().word(element.getAsString()).translation("").build());
                    continue;
                }
                if (!element.isJsonObject()) {
                    continue;
                }
                WordOcrItemVO item = toItem(element.getAsJsonObject());
                if (item != null) {
                    items.add(item);
                }
            }
            return items;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * JSON 对象 → 候选词，兼容模型可能用的其它键名
     *
     * @return 取不到 word 时返回 null
     */
    private static WordOcrItemVO toItem(JsonObject obj) {
        String word = asString(obj, "word");
        if (word == null) {
            word = asString(obj, "spelling");
        }
        if (word == null) {
            return null;
        }
        String translation = asString(obj, "translation");
        if (translation == null) {
            translation = asString(obj, "meaning");
        }
        return WordOcrItemVO.builder().word(word).translation(translation).build();
    }

    private static String asString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) {
            return null;
        }
        return el.getAsString();
    }

    /**
     * 降级路径：按行扫描，取每行第一个英文串作为词，其余中文作为释义
     * <p>
     * 覆盖模型直接吐出「apple 苹果」「benefit - 益处」这类纯文本的情况
     */
    private static List<WordOcrItemVO> parseAsLines(String text) {
        List<WordOcrItemVO> items = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // 取行内第一个英文词串
            java.util.regex.Matcher m = Pattern.compile("[A-Za-z][A-Za-z\\-']*").matcher(trimmed);
            if (!m.find()) {
                continue;
            }
            String word = m.group();
            // 行内剩余的中文部分作为释义
            String rest = trimmed.substring(m.end()).replaceAll("^[\\s:：\\-—_、,，.。\"'\\[\\]]+", "").trim();
            items.add(WordOcrItemVO.builder().word(word).translation(rest).build());
        }
        return items;
    }

    /**
     * 归一化：转小写、剔首尾标点、校验词形、按首次出现去重、截断释义
     * <p>
     * 去重时保留<b>第一个带释义</b>的版本——多张图里同一个词可能只有一张写了中文。
     * 幂等，因此多图结果合并时可以直接拿它再跑一遍来做跨图去重。
     *
     * @param items 待归一化的候选词，可为 null
     * @return 归一化后的列表，永不为 null
     */
    public static List<WordOcrItemVO> normalize(List<WordOcrItemVO> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, WordOcrItemVO> distinct = new LinkedHashMap<>();
        for (WordOcrItemVO item : items) {
            if (item == null || item.getWord() == null) {
                continue;
            }
            String word = item.getWord().trim().toLowerCase().replace('’', '\'');
            word = word.replaceAll("^[^a-z]+", "").replaceAll("[^a-z]+$", "");
            if (word.isEmpty() || word.length() > WordConstant.MAX_SPELLING_LENGTH
                    || !VALID_WORD.matcher(word).matches()) {
                continue;
            }
            String translation = truncate(clean(item.getTranslation()), WordConstant.MAX_OCR_TRANSLATION_LENGTH);
            WordOcrItemVO existing = distinct.get(word);
            if (existing == null) {
                distinct.put(word, WordOcrItemVO.builder().word(word).translation(translation).build());
            } else if (isBlank(existing.getTranslation()) && !isBlank(translation)) {
                // 先出现的那条没释义，用后出现的补上
                existing.setTranslation(translation);
            }
            if (distinct.size() >= WordConstant.MAX_CAPTURE_WORDS) {
                log.info("单次识别达到 {} 词上限，忽略其余", WordConstant.MAX_CAPTURE_WORDS);
                break;
            }
        }
        return new ArrayList<>(distinct.values());
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
