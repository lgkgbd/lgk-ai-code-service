package com.lgk.lgkaicodeservice.service.word;

/**
 * 书签小工具（bookmarklet）脚本生成
 * <p>
 * 生成一行 {@code javascript:} 书签，用户拖到书签栏后，在任意网页选中一个单词点击它即可：
 * <ol>
 *   <li>取 {@code window.getSelection()} 选中的词（未选中则弹输入框兜底）</li>
 *   <li>向前后扫描到最近的句子边界（. ! ? 。！？换行），截出所在整句作上下文</li>
 *   <li>带上 {@code document.title} 与 {@code location.href}</li>
 *   <li>{@code fetch} + {@code credentials:'include'} POST 到 {@code /api/word/capture}，channel=bookmarklet</li>
 * </ol>
 * <p>
 * 依赖后端 CORS 已开启 {@code allowedOriginPatterns("*") + allowCredentials(true)}（现有 CorsConfig 已满足）。
 * 少数站点 CSP 的 connect-src 会拦截跨站 fetch，此时前端保留站内手动粘贴入口兜底。
 */
public final class Bookmarklet {

    private Bookmarklet() {
    }

    /**
     * @param apiBase 后端 API 基址，如 {@code http://localhost:8123/api}
     * @return 完整的 {@code javascript:} 书签字符串，可直接作为 <a href> 供拖拽
     */
    public static String script(String apiBase) {
        String captureUrl = apiBase + "/word/capture";
        // 紧凑但不含 // 注释（javascript: URL 里换行安全，但注释会吞掉后续代码）
        return "javascript:(function(){"
                + "try{"
                + "var s=window.getSelection();"
                + "var w=(s?s.toString():'').trim();"
                + "if(!w){var p=prompt('输入要记的单词');if(!p){return;}w=p.trim();}"
                + "if(!w){return;}"
                + "var sentence='';"
                + "try{"
                + "if(s&&s.rangeCount>0&&s.anchorNode){"
                + "var t=s.anchorNode.textContent||'';"
                + "var i=t.toLowerCase().indexOf(w.toLowerCase());"
                + "if(i<0){i=(typeof s.anchorOffset==='number')?s.anchorOffset:0;}"
                + "var b=/[.!?。！？\\n]/;"
                + "var a=i;while(a>0&&!b.test(t.charAt(a-1))){a--;}"
                + "var e=i+w.length;while(e<t.length&&!b.test(t.charAt(e))){e++;}"
                + "sentence=t.substring(a,e).trim();"
                + "}"
                + "}catch(err){}"
                + "fetch('" + captureUrl + "',{"
                + "method:'POST',credentials:'include',"
                + "headers:{'Content-Type':'application/json'},"
                + "body:JSON.stringify({text:w,sentence:sentence,sourceTitle:document.title,sourceUrl:location.href,channel:'bookmarklet'})"
                + "}).then(function(r){return r.json();}).then(function(d){"
                + "var ok=d&&d.code===0;"
                + "alert(ok?('\\u5df2\\u8bb0\\u5f55: '+w):('\\u5f55\\u5165\\u5931\\u8d25: '+((d&&d.message)||'\\u8bf7\\u5148\\u767b\\u5f55')));"
                + "}).catch(function(er){alert('\\u5f55\\u5165\\u5931\\u8d25\\uff0c\\u8bf7\\u5148\\u5728\\u7ad9\\u5185\\u767b\\u5f55: '+er);});"
                + "}catch(ex){alert(''+ex);}"
                + "})();";
    }
}
