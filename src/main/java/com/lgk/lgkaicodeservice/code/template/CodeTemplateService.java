package com.lgk.lgkaicodeservice.code.template;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.lgk.lgkaicodeservice.ai.model.enums.CodeGenTypeEnum;
import com.lgk.lgkaicodeservice.ai.model.message.AiResponseMessage;
import com.lgk.lgkaicodeservice.constant.AppConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 代码模板复用服务
 * <p>
 * 对于首页固定提示词，命中模板后直接复用 {@code templates/{key}} 下预生成好的代码产物，
 * 从而跳过 LLM 调用（既包括创建应用时的类型路由，也包括生成代码时的大模型调用）。
 */
@Slf4j
@Service
public class CodeTemplateService {

    /**
     * 每个模板目录下存放响应文本的文件名，重放给前端时用于展示 AI 回复内容
     */
    private static final String RESPONSE_FILE_NAME = "response.md";

    /**
     * 每个模板目录下存放待拷贝代码产物的子目录名
     */
    private static final String CODE_DIR_NAME = "code";

    /**
     * 重放时每个流式片段的字符数（模拟打字机效果，保持与真实生成一致的体验）
     */
    private static final int CHUNK_SIZE = 60;

    /**
     * 根据用户提示词匹配模板
     *
     * @param userPrompt 用户提示词
     * @return 命中的模板，未命中返回 null
     */
    public PromptTemplateEnum match(String userPrompt) {
        return PromptTemplateEnum.matchByPrompt(userPrompt);
    }

    /**
     * 尝试从模板复用代码。命中且尚未生成过时，拷贝预生成产物到应用目录并返回重放响应流；
     * 否则返回 null，交由调用方走正常的 LLM 生成流程。
     *
     * @param userMessage 用户消息（提示词）
     * @param appId       应用 ID
     * @return 重放响应流；未命中或应已生成过则返回 null
     */
    public Flux<String> tryServeFromTemplate(String userMessage, Long appId) {
        PromptTemplateEnum template = PromptTemplateEnum.matchByPrompt(userMessage);
        if (template == null) {
            return null;
        }
        // 校验模板产物是否存在
        File templateCodeDir = new File(templateCodeDirPath(template));
        if (!templateCodeDir.exists() || FileUtil.isEmpty(templateCodeDir)) {
            log.warn("提示词命中模板 [{}]，但模板产物目录不存在或为空: {}，回退到 LLM 生成",
                    template.getKey(), templateCodeDir.getAbsolutePath());
            return null;
        }
        // 若该应用已经生成过代码（例如用户重复发送同一提示词做增量修改），交给 LLM 处理，避免覆盖
        File outputDir = new File(outputDirPath(template.getCodeGenType(), appId));
        if (outputDir.exists() && !FileUtil.isEmpty(outputDir)) {
            return null;
        }
        // 拷贝预生成产物到应用目录
        FileUtil.copyContent(templateCodeDir, outputDir, true);
        log.info("提示词命中模板 [{}]，已复用预生成产物至 {}，跳过 LLM 调用",
                template.getKey(), outputDir.getAbsolutePath());
        // 重放响应文本
        String responseText = readResponseText(template);
        return buildReplayFlux(responseText, template.getCodeGenType());
    }

    /**
     * 读取模板的响应文本（展示给前端的 AI 回复内容）。
     * 优先读取模板目录下的 {@code response.md}；缺失时，HTML 模板会用产物代码拼出展示文本，
     * 与真实生成体验保持一致，避免把整段 HTML 冗余维护两份。
     */
    private String readResponseText(PromptTemplateEnum template) {
        File responseFile = new File(templateDirPath(template) + File.separator + RESPONSE_FILE_NAME);
        if (responseFile.exists()) {
            String text = FileUtil.readUtf8String(responseFile);
            if (StrUtil.isNotBlank(text)) {
                return text;
            }
        }
        // 兜底：HTML 模板直接用产物 index.html 拼出带代码块的展示文本
        if (template.getCodeGenType() == CodeGenTypeEnum.HTML) {
            File htmlFile = new File(templateCodeDirPath(template) + File.separator + "index.html");
            if (htmlFile.exists()) {
                String code = FileUtil.readUtf8String(htmlFile);
                return "已根据模板为你生成页面，代码如下：\n\n```html\n" + code + "\n```";
            }
        }
        return "已根据模板为你生成完成，可在右侧预览效果。";
    }

    /**
     * 把响应文本切分为若干片段并重放为流。
     * VUE_PROJECT 走 {@link com.lgk.lgkaicodeservice.code.handler.JsonMessageStreamHandler}，
     * 需要包装成 JSON 消息；HTML / MULTI_FILE 走简单文本处理器，直接输出文本片段即可。
     */
    private Flux<String> buildReplayFlux(String responseText, CodeGenTypeEnum codeGenType) {
        List<String> chunks = splitIntoChunks(responseText, CHUNK_SIZE);
        Flux<String> flux;
        if (codeGenType == CodeGenTypeEnum.VUE_PROJECT) {
            flux = Flux.fromIterable(chunks)
                    .map(chunk -> JSONUtil.toJsonStr(new AiResponseMessage(chunk)));
        } else {
            flux = Flux.fromIterable(chunks);
        }
        // 轻微延迟，模拟真实流式输出的打字机效果
        return flux.delayElements(Duration.ofMillis(20));
    }

    /**
     * 将文本按固定长度切分
     */
    private List<String> splitIntoChunks(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        if (StrUtil.isEmpty(text)) {
            return chunks;
        }
        int length = text.length();
        for (int start = 0; start < length; start += chunkSize) {
            chunks.add(text.substring(start, Math.min(start + chunkSize, length)));
        }
        return chunks;
    }

    private String templateDirPath(PromptTemplateEnum template) {
        return AppConstant.CODE_TEMPLATE_ROOT_DIR + File.separator + template.getKey();
    }

    private String templateCodeDirPath(PromptTemplateEnum template) {
        return templateDirPath(template) + File.separator + CODE_DIR_NAME;
    }

    private String outputDirPath(CodeGenTypeEnum codeGenType, Long appId) {
        return AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + codeGenType.getValue() + "_" + appId;
    }
}
