package com.lgk.lgkaicodeservice.model.vo;

import com.lgk.lgkaicodeservice.model.entity.Picture;
import com.lgk.lgkaicodeservice.model.entity.Video;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 聚合搜索
 *
 */
@Data
public class SearchVO implements Serializable {

    private List<Video> videoList;

    private List<PostVO> postList;

    private List<Picture> pictureList;

    private List<?> dataList;

    private static final long serialVersionUID = 1L;

}
