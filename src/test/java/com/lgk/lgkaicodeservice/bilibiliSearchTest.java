package com.lgk.lgkaicodeservice;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.HttpCookie;
import java.util.*;
import java.util.stream.Collectors;

import static org.openqa.selenium.net.Urls.urlEncode;

//@SpringBootTest
public class bilibiliSearchTest {


    @Test
    void testFetchVidio() {
        // 1. 获取数据
        String url = "https://api.bilibili.com/x/web-interface/wbi/search/type?category_id=&search_type=video&page=1&page_size=6&keyword=test";
        String result = HttpRequest
                .get(url)
                .cookie()  // 添加单个 cookie
                //.cookie()  // 添加多个 cookie
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", "https://www.bilibili.com/")
                .header("Origin", "https://www.bilibili.com")
                .execute()
                .body();
//        System.out.println(result);
        // 2. json 转对象
        Map<String, Object> map = JSONUtil.toBean(result, Map.class);
        JSONObject data = (JSONObject) map.get("data");
        JSONArray records = (JSONArray) data.get("result");
        List<VideoResult> videoList = new ArrayList<>();
        for (Object record : records) {
            JSONObject tempRecord = (JSONObject) record;
            VideoResult video = new VideoResult();
            video.setType(tempRecord.get("type").toString());
            video.setAuthor(tempRecord.get("author").toString());
            video.setTypename(tempRecord.get("typename").toString());
            video.setArcurl(tempRecord.get("arcurl").toString());
            video.setTitle(tempRecord.get("title").toString());
            video.setDescription(tempRecord.get("description").toString());
            video.setPic(tempRecord.get("pic").toString());
            video.setPlay((int)tempRecord.get("play"));
            video.setVideoReview((int)tempRecord.get("video_review"));
            video.setTag(tempRecord.get("tag").toString());
            video.setDuration(tempRecord.get("duration").toString());
            videoList.add(video);
        }
        System.out.println(videoList);
        // 3. 数据入库
    }

    /**
     *
     {
     "type": "video",
     "author": "大吉文ovo",
     "typename": "电子竞技",
     "arcurl": "http://www.bilibili.com/video/av114916547627764",
     "title": "【炫神】\u003cem class=\"keyword\"\u003e哈哈\u003c/em\u003e｜算我一个",
     "description": "虚拟偶像……也可以这么美丽么……\n前情提要:【炫神】呀❤️～没收住-哔哩哔哩】 https://b23.tv/WgMBl33",
     "pic": "//i1.hdslb.com/bfs/archive/9dcbc00b10a8b90f0aea45ec4176740a44ad6939.jpg",
     "play": 139160,
     "video_review": 111,
     "tag": "搞笑,可爱,虚拟偶像,抽象,炫神,末日保护者,不是我喜欢的类型，直接拒绝,还是不是好兄弟了不去打丧尸忙得要死哈哈算我一个这是发光跳跳鱼,不是我喜欢的类型直接拒绝那就厉害了呀没收住，男人也可以这么美",
     "duration": "0:39",
     }
     */

    @Data
    class VideoResult {
        private String type;
        private String author;
        private String typename;
        private String arcurl;
        private String title;
        private String description;
        private String pic;
        private int play;
        @JsonProperty("video_review")
        private int videoReview;
        private String tag;
        private String duration;
    }
}
