package com.lgk.lgkaicodeservice.service.word.enricher;

import java.util.Optional;

/**
 * 词条补全器（责任链的一环）
 * <p>
 * 由 Spring 注入 {@code List<WordEnricher>}，按 {@link #order()} 升序依次尝试，命中即返回。
 * 链路：EcdictEnricher(0, 本地表) → FreeDictionaryEnricher(10, 免费 API 兜生僻词) → 三期 AiEnricher(100)。
 * <p>
 * 铁律：任何一环都不得因为自身故障（网络超时、解析失败）而抛异常阻断录入，
 * 查不到一律返回 {@link Optional#empty()}，交给下一环或最终的 manual 占位。
 */
public interface WordEnricher {

    /**
     * 优先级，越小越先尝试
     */
    int order();

    /**
     * 是否支持该语言
     *
     * @param lang en/ja/...
     */
    boolean supports(String lang);

    /**
     * 补全一个词
     *
     * @param spelling 单词原形（已转小写、trim）
     * @return 命中则返回词条信息，未命中返回空
     */
    Optional<WordDictInfo> enrich(String spelling);
}
