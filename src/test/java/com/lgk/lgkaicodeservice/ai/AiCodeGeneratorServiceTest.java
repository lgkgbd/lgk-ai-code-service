package com.lgk.lgkaicodeservice.ai;

import com.lgk.lgkaicodeservice.ai.model.entity.HtmlCodeResult;
import com.lgk.lgkaicodeservice.ai.model.entity.MultiFileCodeResult;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AiCodeGeneratorServiceTest {

    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;

    @Test
    void generateHtmlCode() {
        HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode("做个程序员的工作记录小工具，不超过20行代码");
        Assertions.assertNotNull(result);
    }

    @Test
    void generateMultiFileCode() {
        MultiFileCodeResult multiFileCode = aiCodeGeneratorService.generateMultiFileCode("做个程序员的留言板，不超过20行代码");
        Assertions.assertNotNull(multiFileCode);
    }

}
