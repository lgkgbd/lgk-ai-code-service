package com.lgk.lgkaicodeservice;


import cn.hutool.json.JSONUtil;
import com.lgk.lgkaicodeservice.model.entity.Picture;
import lombok.Data;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

//@SpringBootTest
public class CrawlerTest {


    @Test
    void testFetchPicture() throws IOException {
        int current = 1;
        String url = "https://cn.bing.com/images/search?q=小黑子&first=" + current;
        Document doc = Jsoup.connect(url).get();
        Elements elements = doc.select(".iuscp.isv");
        List<Picture> pictures = new ArrayList<>();
        for (Element element : elements) {
            // 取图片地址（murl）
            String m = element.select(".iusc").get(0).attr("m");
            Map<String, Object> map = JSONUtil.toBean(m, Map.class);
            String murl = (String) map.get("murl");
//            System.out.println(murl);
            // 取标题
            String title = element.select(".inflnk").get(0).attr("aria-label");
//            System.out.println(title);
            Picture picture = new Picture();
            picture.setTitle(title);
            picture.setUrl(murl);
            pictures.add(picture);
        }
        System.out.println(pictures);
    }


    @Test
    void testFetchMTPassageList() throws IOException {
        int current = 1;
        String url = "https://tech.meituan.com";
        if(current > 1){
            url = url + "/page/" + current + ".html";
        }

        // 添加请求头，模拟浏览器访问
        Document doc = Jsoup.connect(url)
                .timeout(10000)
                .get();

        // 获取文章列表容器
        Elements articles = doc.select(".post-container");

        List<ArticleInfo> articleList = new ArrayList<>();

        for (Element article : articles) {
            ArticleInfo articleInfo = new ArticleInfo();
            boolean hasValidContent = false;

            // 获取文章标题和URL
            Element titleElement = article.select(".post-title a").first();
            if (titleElement != null && !titleElement.text().trim().isEmpty()) {
                articleInfo.setTitle(titleElement.text().trim());
                articleInfo.setUrl(titleElement.attr("href"));
                hasValidContent = true;
            }

            // 获取文章摘要内容
            Element contentElement = article.select(".post-content.post-expect").first();
            if (contentElement != null) {
                // 移除"阅读全文"按钮的文本
                Element moreLink = contentElement.select(".more-link").first();
                if (moreLink != null) {
                    moreLink.remove();
                }
                String content = contentElement.text().trim();
                if (!content.isEmpty()) {
                    articleInfo.setContent(content);
                    hasValidContent = true;
                }
            }

            // 获取发布时间
            Element timeElement = article.select(".m-post-date").first();
            if (timeElement != null) {
                String timeText = timeElement.text().replaceAll("\\s+", " ").trim();
                if (!timeText.isEmpty()) {
                    articleInfo.setPublishTime(timeText);
                }
            }

            // 获取作者/部门信息
            Element authorElement = article.select(".m-post-nick").first();
            if (authorElement != null) {
                String authorText = authorElement.text().trim();
                if (!authorText.isEmpty()) {
                    articleInfo.setAuthor(authorText);
                }
            }

            // 获取标签
            Elements tagElements = article.select(".tag-links a");
            List<String> tags = new ArrayList<>();
            for (Element tag : tagElements) {
                String tagText = tag.text().trim();
                if (!tagText.isEmpty()) {
                    tags.add(tagText);
                }
            }
            articleInfo.setTags(tags);

            // 获取完整文章内容（可选）
//            try {
//                Document articleDoc = Jsoup.connect(articleInfo.getUrl())
//                        .timeout(10000)
//                        .get();
//
//                // 获取文章正文内容
//                Element contentDiv = articleDoc.select(".post-content").first();
//                if (contentDiv != null) {
//                    // 移除不需要的元素
//                    contentDiv.select(".more-link").remove();
//                    contentDiv.select(".meta-box").remove();
//
//                    String fullContent = contentDiv.text();
//                    articleInfo.setFullContent(fullContent);
//                }
//
//            } catch (Exception e) {
//                System.err.println("获取文章详情失败: " + e.getMessage());
//            }


            // 只添加有效的文章信息
            if (hasValidContent) {
                articleList.add(articleInfo);
            }
        }

        // 打印结果
        System.out.println("=== 美团技术博客文章列表 ===");
        System.out.println("总共找到 " + articleList.size() + " 篇文章");
        System.out.println();

        for (int i = 0; i < articleList.size(); i++) {
            ArticleInfo article = articleList.get(i);
            System.out.println("第 " + (i + 1) + " 篇文章：");
            System.out.println("标题: " + article.getTitle());
            System.out.println("URL: " + article.getUrl());
            System.out.println("发布时间: " + article.getPublishTime());
            System.out.println("作者/部门: " + article.getAuthor());
            System.out.println("标签: " + String.join(", ", article.getTags()));
            System.out.println("内容摘要: " + article.getContent());
            System.out.println("完整内容长度: " + article.fullContent.length() + " 字符");
            System.out.println("内容预览: " + article.fullContent);//article.fullContent.substring(0, Math.min(500, article.fullContent.length())) + "...")
            System.out.println("-------------------");
        }

    }

    @Test
    void testFetchMTMTPassageDetail() throws IOException {
        try {
            Document articleDoc = Jsoup.connect("https://tech.meituan.com/2025/09/01/longcat-flash-chat.html")
                    .timeout(10000)
                    .get();

            // 创建文章信息对象
            ArticleDetail articleDetail = new ArticleDetail();

            // 提取文章标题
            Element titleElement = articleDoc.select(".post-title a").first();
            if (titleElement != null) {
                articleDetail.setTitle(titleElement.text().trim());
                articleDetail.setUrl(titleElement.attr("href"));
            }

            // 提取元信息
            Elements metaElements = articleDoc.select(".meta-box span");
            for (Element meta : metaElements) {
                String metaText = meta.text().trim();
                if (metaText.contains("年") && metaText.contains("月") && metaText.contains("日")) {
                    articleDetail.setPublishDate(metaText.replaceAll(".*?([0-9]{4}年[0-9]{1,2}月[0-9]{1,2}日).*", "$1"));
                } else if (metaText.startsWith("作者:")) {
                    articleDetail.setAuthor(metaText.replace("作者:", "").trim());
                } else if (metaText.contains("字")) {
                    articleDetail.setWordCount(metaText);
                } else if (metaText.contains("分钟阅读")) {
                    articleDetail.setReadingTime(metaText);
                }
            }

            // 提取文章正文内容
            Element contentElement = articleDoc.select(".post-content .content").first();
            if (contentElement != null) {
                // 获取纯文本内容
                String textContent = contentElement.text();
                articleDetail.setTextContent(textContent);

                // 获取HTML内容（保留格式）
                String htmlContent = contentElement.html();
                articleDetail.setHtmlContent(htmlContent);

                // 提取图片链接
                Elements images = contentElement.select("img");
                List<String> imageUrls = new ArrayList<>();
                for (Element img : images) {
                    String imgSrc = img.attr("src");
                    if (!imgSrc.isEmpty()) {
                        imageUrls.add(imgSrc);
                    }
                }
                articleDetail.setImageUrls(imageUrls);

                // 提取链接
                Elements links = contentElement.select("a[href]");
                List<String> linkUrls = new ArrayList<>();
                for (Element link : links) {
                    String linkHref = link.attr("href");
                    if (!linkHref.isEmpty()) {
                        linkUrls.add(linkHref + " (" + link.text() + ")");
                    }
                }
                articleDetail.setLinks(linkUrls);
            }

            // 提取标签
            Elements tagElements = articleDoc.select(".tag-links a");
            List<String> tags = new ArrayList<>();
            for (Element tag : tagElements) {
                String tagText = tag.text().trim();
                if (!tagText.isEmpty()) {
                    tags.add(tagText);
                }
            }
            articleDetail.setTags(tags);

            // 输出结果
            System.out.println("=== 文章详情 ===");
            System.out.println("标题: " + articleDetail.getTitle());
            System.out.println("URL: " + articleDetail.getUrl());
            System.out.println("发布日期: " + articleDetail.getPublishDate());
            System.out.println("作者: " + articleDetail.getAuthor());
            System.out.println("字数: " + articleDetail.getWordCount());
            System.out.println("阅读时间: " + articleDetail.getReadingTime());
            System.out.println("标签: " + String.join(", ", articleDetail.getTags()));
            System.out.println("图片数量: " + articleDetail.getImageUrls().size());
            System.out.println("链接数量: " + articleDetail.getLinks().size());
            System.out.println("内容长度: " + articleDetail.getTextContent().length() + " 字符");
            System.out.println("\n=== 文章内容预览 ===");
            System.out.println(articleDetail.getTextContent().substring(0, Math.min(500, articleDetail.getTextContent().length())) + "...");

            System.out.println("\n=== 图片链接 ===");
            for (String imageUrl : articleDetail.getImageUrls()) {
                System.out.println(imageUrl);
            }

            System.out.println("\n=== 文章链接 ===");
            for (String link : articleDetail.getLinks()) {
                System.out.println(link);
            }

        } catch (Exception e) {
            System.err.println("获取文章详情失败: " + e.getMessage());
            e.printStackTrace();
        }
    }


    // 辅助类用于存储文章信息
    @Data
    class ArticleInfo {
        private String title;
        private String url;
        private String content;
        private String fullContent;
        private String publishTime;
        private String author;
        private List<String> tags;
    }

    // 文章详情类
    @Data
    class ArticleDetail {
        private String title;
        private String url;
        private String publishDate;
        private String author;
        private String wordCount;
        private String readingTime;
        private String textContent;
        private String htmlContent;
        private List<String> tags;
        private List<String> imageUrls;
        private List<String> links;
    }


    }
