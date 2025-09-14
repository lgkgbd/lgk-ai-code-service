package com.lgk.lgkaicodeservice.datasource;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lgk.lgkaicodeservice.model.entity.Video;
import com.mybatisflex.core.paginate.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 搜索视频
 */
@Service
public class VideoDataSource implements DataSource {

    @Override
    public Page doSearch(String searchText, long pageNum, long pageSize) {

        // 1. 获取数据
        String url = String.format("https://api.bilibili.com/x/web-interface/wbi/search/type?category_id=&search_type=video&page=%d&page_size=%d&keyword=%s", pageNum, pageSize, searchText);
        String result = HttpRequest
                .get(url)
                .cookie()  // 添加单个 cookie
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", "https://www.bilibili.com/")
                .header("Origin", "https://www.bilibili.com")
                .execute()
                .body();

        // 2. json 转对象
        Map<String, Object> map = JSONUtil.toBean(result, Map.class);
        JSONObject data = (JSONObject) map.get("data");
        JSONArray records = (JSONArray) data.get("result");
        List<Video> videoList = new ArrayList<>();
        for (Object record : records) {
            JSONObject tempRecord = (JSONObject) record;
            Video video = new Video();
            video.setType(tempRecord.get("type").toString());
            video.setAuthor(tempRecord.get("author").toString());
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


        Page<Video> videoPage = new Page<>(pageNum,pageSize);
        videoPage.setRecords(videoList);

        return videoPage;
    }
}
