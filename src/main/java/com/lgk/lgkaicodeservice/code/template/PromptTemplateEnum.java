package com.lgk.lgkaicodeservice.code.template;

import cn.hutool.core.util.StrUtil;
import com.lgk.lgkaicodeservice.ai.model.enums.CodeGenTypeEnum;
import lombok.Getter;

/**
 * 预生成提示词模板注册表
 * <p>
 * 首页固定的几个快捷提示词会被大量用户重复点击，每次都调用 LLM 生成几乎相同的代码非常浪费。
 * 这里把这些固定提示词登记为模板：命中后直接复用 {@code templates/{key}} 下预生成好的产物，
 * 既跳过创建应用时的路由 LLM，也跳过生成代码时的大模型调用。
 * <p>
 * 匹配方式：对用户提示词做 trim 后与登记的原文做全等比较。前端首页点击卡片时会把
 * {@code prompt.content} 原样填入并作为首条消息发送，因此全等匹配足够可靠。
 */
@Getter
public enum PromptTemplateEnum {

    SNAKE_GAME("snake_game", CodeGenTypeEnum.HTML,
            "创建一个贪吃蛇游戏，支持方向键控制蛇移动、吃到食物后身体变长并计分、撞墙或撞到自身则游戏结束并可重新开始，界面简洁美观、操作流畅，不超过200行代码"),

    GENTLE_CLOCK("gentle_clock", CodeGenTypeEnum.HTML,
            "创建一个温柔风格的时钟页面，实时显示当前时间和日期，采用柔和的配色和平滑的过渡动画，整体氛围温馨舒适、界面精致美观，不超过200行代码"),

    GLASS_LOGIN("glass_login", CodeGenTypeEnum.HTML,
            "创建一个毛玻璃（Glassmorphism）风格的登录界面，包含账号密码输入框、记住我、忘记密码、登录按钮和第三方登录入口，采用半透明磨砂玻璃质感和柔和渐变背景，设计精致现代，不超过200行代码"),

    EMPIRE_COMMUNITY("empire_community", CodeGenTypeEnum.VUE_PROJECT,
            "创建一个\"帝国隐忍\"风格的话题社区，包含话题讨论、发帖回帖、用户互动、内容分享、评论系统等功能，采用深沉、大气、内敛克制的视觉风格，色调厚重庄重，不超过200行代码");

    /**
     * 模板目录名（对应 {@code templates/{key}}）
     */
    private final String key;

    /**
     * 该模板固定的代码生成类型（跳过路由 LLM）
     */
    private final CodeGenTypeEnum codeGenType;

    /**
     * 登记的提示词原文
     */
    private final String prompt;

    PromptTemplateEnum(String key, CodeGenTypeEnum codeGenType, String prompt) {
        this.key = key;
        this.codeGenType = codeGenType;
        this.prompt = prompt;
    }

    /**
     * 根据用户提示词匹配模板
     *
     * @param userPrompt 用户输入的提示词
     * @return 命中的模板，未命中返回 null
     */
    public static PromptTemplateEnum matchByPrompt(String userPrompt) {
        if (StrUtil.isBlank(userPrompt)) {
            return null;
        }
        String normalized = userPrompt.trim();
        for (PromptTemplateEnum template : values()) {
            if (template.prompt.equals(normalized)) {
                return template;
            }
        }
        return null;
    }
}
