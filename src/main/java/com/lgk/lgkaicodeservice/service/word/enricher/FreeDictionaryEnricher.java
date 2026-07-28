package com.lgk.lgkaicodeservice.service.word.enricher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lgk.lgkaicodeservice.constant.WordConstant;
import com.lgk.lgkaicodeservice.model.enums.WordSourceEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 免费词典 API 兜底补全器（order=10）
 * <p>
 * 调 <a href="https://api.dictionaryapi.dev/api/v2/entries/en/{word}">Free Dictionary API</a>，
 * 免费、无需 key，用于兜住 ECDICT 没收录的生僻词。该接口只返回英文释义，无中文。
 * <p>
 * <b>可用性铁律</b>：连接/读取超时统一 3 秒，任何异常（超时、404、非 200、解析失败）
 * 一律吞掉并返回 {@link Optional#empty()}，绝不能因为这个外部依赖挂了而阻断录入。
 */
@Slf4j
@Component
public class FreeDictionaryEnricher implements WordEnricher {

    private static final String API = "https://api.dictionaryapi.dev/api/v2/entries/en/";

    /** 连接 + 读取超时，各 3 秒 */
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    @Override
    public int order() {
        return WordSourceEnum.API.getOrder();
    }

    @Override
    public boolean supports(String lang) {
        return WordConstant.DEFAULT_LANG.equalsIgnoreCase(lang);
    }

    @Override
    public Optional<WordDictInfo> enrich(String spelling) {
        try {
            String url = API + URLEncoder.encode(spelling, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // 404 = 该词未收录，属正常情况，不打 error
                log.debug("Free Dictionary 未命中 {}，status={}", spelling, response.statusCode());
                return Optional.empty();
            }
            return parse(spelling, response.body());
        } catch (Exception e) {
            // 超时 / 网络异常 / 解析异常一律吞掉，交给下一环或 manual 占位
            log.warn("Free Dictionary 调用失败，跳过，word={}", spelling, e);
            return Optional.empty();
        }
    }

    /**
     * 解析响应体：形如 {@code [{word, phonetic, phonetics:[{text}], meanings:[{partOfSpeech, definitions:[{definition, example}]}]}]}
     */
    private Optional<WordDictInfo> parse(String spelling, String body) {
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonArray() || root.getAsJsonArray().isEmpty()) {
            return Optional.empty();
        }
        JsonObject entry = root.getAsJsonArray().get(0).getAsJsonObject();

        String phonetic = extractPhonetic(entry);
        List<String> definitions = new ArrayList<>();
        List<String> examples = new ArrayList<>();
        List<String> posList = new ArrayList<>();

        JsonArray meanings = getArray(entry, "meanings");
        for (JsonElement me : meanings) {
            JsonObject meaning = me.getAsJsonObject();
            String pos = getString(meaning, "partOfSpeech");
            if (pos != null && !posList.contains(pos)) {
                posList.add(pos);
            }
            for (JsonElement de : getArray(meaning, "definitions")) {
                JsonObject def = de.getAsJsonObject();
                String definition = getString(def, "definition");
                if (definition != null && !definition.isBlank()) {
                    definitions.add(pos == null ? definition : "(" + pos + ") " + definition);
                }
                String example = getString(def, "example");
                if (example != null && !example.isBlank()) {
                    examples.add(example);
                }
            }
        }

        if (definitions.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> extInfo = new LinkedHashMap<>();
        if (!examples.isEmpty()) {
            extInfo.put("examples", examples);
        }

        WordDictInfo info = WordDictInfo.builder()
                .spelling(spelling)
                .phonetic(phonetic)
                // 该 API 无中文释义
                .translation(null)
                .definition(String.join("\n", definitions))
                .pos(String.join("/", posList))
                .source(WordSourceEnum.API.getValue())
                .frq(0)
                .bnc(0)
                .collins(0)
                .oxford(0)
                .extInfo(extInfo.isEmpty() ? null : extInfo)
                .build();
        return Optional.of(info);
    }

    private static String extractPhonetic(JsonObject entry) {
        String top = getString(entry, "phonetic");
        if (top != null && !top.isBlank()) {
            return top;
        }
        for (JsonElement pe : getArray(entry, "phonetics")) {
            String text = getString(pe.getAsJsonObject(), "text");
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private static JsonArray getArray(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return el != null && el.isJsonArray() ? el.getAsJsonArray() : new JsonArray();
    }

    private static String getString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : null;
    }
}
