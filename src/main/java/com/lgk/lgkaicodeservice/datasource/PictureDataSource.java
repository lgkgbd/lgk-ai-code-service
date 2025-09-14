package com.lgk.lgkaicodeservice.datasource;

import cn.hutool.json.JSONUtil;
import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.model.entity.Picture;
import com.mybatisflex.core.paginate.Page;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 搜索图片
 */
@Service
public class PictureDataSource implements DataSource<Picture>{

    @Override
    public Page<Picture> doSearch(String searchText, long pageNum, long pageSize) {
        long current = (pageNum - 1) * pageSize;
        String url = String.format("https://cn.bing.com/images/search?q=%s&first=%s",searchText,current);
        Document doc;
        try {
            doc = Jsoup.connect(url).get();
        } catch (Exception e){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"数据获取异常");
        }

        Elements elements = doc.select(".iuscp.isv");
        List<Picture> pictures = new ArrayList<>();
        for(Element element :elements){
            //获取图片的 url
            String m = element.select(".iusc").get(0).attr("m");
            Map<String,Object> map = JSONUtil.toBean(m,Map.class);
            String murl = (String)map.get("murl");

            //获取标题
            String title = element.select(".inflnk").get(0).attr("aria-label");

            Picture picture = new Picture();
            picture.setUrl(murl);
            picture.setTitle(title);
            pictures.add(picture);

            if (pictures.size() >= pageSize) {
                break;
            }
        }

        Page<Picture> picturePage = new Page<>(pageNum,pageSize);
        picturePage.setRecords(pictures);
        return picturePage;
    }

}
