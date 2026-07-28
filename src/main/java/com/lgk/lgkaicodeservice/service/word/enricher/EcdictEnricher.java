package com.lgk.lgkaicodeservice.service.word.enricher;

import com.lgk.lgkaicodeservice.constant.WordConstant;
import com.lgk.lgkaicodeservice.mapper.StardictMapper;
import com.lgk.lgkaicodeservice.model.entity.Stardict;
import com.lgk.lgkaicodeservice.model.enums.WordSourceEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 本地 ECDICT 词库补全器（order=0，主力）
 * <p>
 * 查本地 stardict 表（770611 词条），零 HTTP、零延迟、零成本。命中即映射成 {@link WordDictInfo}，
 * 把 collins/oxford/detail/audio 等塞进 extInfo(json)。只支持英文。
 */
@Slf4j
@Component
public class EcdictEnricher implements WordEnricher {

    @Resource
    private StardictMapper stardictMapper;

    @Override
    public int order() {
        return WordSourceEnum.ECDICT.getOrder();
    }

    @Override
    public boolean supports(String lang) {
        return WordConstant.DEFAULT_LANG.equalsIgnoreCase(lang);
    }

    @Override
    public Optional<WordDictInfo> enrich(String spelling) {
        Stardict row = stardictMapper.selectByWord(spelling);
        if (row == null || row.getWord() == null) {
            return Optional.empty();
        }

        // 客观字段进 extInfo（json），后续前端/AI 可直接取
        Map<String, Object> extInfo = new LinkedHashMap<>();
        putIfNotEmpty(extInfo, "detail", row.getDetail());
        putIfNotEmpty(extInfo, "audio", row.getAudio());

        WordDictInfo info = WordDictInfo.builder()
                .spelling(row.getWord())
                .phonetic(row.getPhonetic())
                .translation(row.getTranslation())
                .definition(row.getDefinition())
                .pos(row.getPos())
                .exchange(row.getExchange())
                .tag(row.getTag())
                .frq(nvl(row.getFrq()))
                .bnc(nvl(row.getBnc()))
                .collins(nvl(row.getCollins()))
                .oxford(nvl(row.getOxford()))
                .source(WordSourceEnum.ECDICT.getValue())
                .extInfo(extInfo.isEmpty() ? null : extInfo)
                .build();
        return Optional.of(info);
    }

    private static int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    private static void putIfNotEmpty(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
