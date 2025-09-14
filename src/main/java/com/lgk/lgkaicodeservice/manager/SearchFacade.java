package com.lgk.lgkaicodeservice.manager;

import com.lgk.lgkaicodeservice.datasource.*;
import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.model.dto.search.SearchRequest;
import com.lgk.lgkaicodeservice.model.entity.Picture;
import com.lgk.lgkaicodeservice.model.entity.Video;
import com.lgk.lgkaicodeservice.model.enums.SearchTypeEnum;
import com.lgk.lgkaicodeservice.model.vo.PostVO;
import com.lgk.lgkaicodeservice.model.vo.SearchVO;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.concurrent.CompletableFuture;

/**
 * 搜索门面类
 */
@Component
@Slf4j
public class SearchFacade {

    @Resource
    private PostDataSource postDataSource;

    @Resource
    private VideoDataSource videoDataSource;

    @Resource
    private PictureDataSource pictureDataSource;

    @Resource
    private DataSourceRegistry dataSourceRegistry;

    public SearchVO searchAll(@RequestBody SearchRequest searchRequest) {
        String type = searchRequest.getType();
        SearchTypeEnum searchTypeEnum = SearchTypeEnum.getEnumByValue(type);
        String searchText = searchRequest.getSearchText();
        long pageNum = searchRequest.getPageNum();
        long pageSize = searchRequest.getPageSize();
        SearchVO searchVO = new SearchVO();
        //找到所有的数据源
        if(searchTypeEnum == null){
            CompletableFuture<Page<PostVO>> postTask = CompletableFuture.supplyAsync(()->{
                Page<PostVO> postVOPage = postDataSource.doSearch(searchText, pageNum, pageSize);
                return postVOPage;
            });

            CompletableFuture<Page<Picture>> pictureTask = CompletableFuture.supplyAsync(()->{
                Page<Picture> picturePage = pictureDataSource.doSearch(searchText, pageNum, pageSize);
                return picturePage;
            });

            CompletableFuture<Page<Video>> videoTask = CompletableFuture.supplyAsync(()->{
                Page<Video> videoPage = videoDataSource.doSearch(searchText, pageNum, pageSize);
                return videoPage;
            });

            CompletableFuture.allOf(postTask, pictureTask, videoTask).join();
            try {
                Page<PostVO> postVOPage = postTask.join();
                Page<Picture> picturePage = pictureTask.join();
                Page<Video> videoPage = videoTask.join();
                searchVO.setPostList(postVOPage.getRecords());
                searchVO.setPictureList(picturePage.getRecords());
                searchVO.setVideoList(videoPage.getRecords());
            } catch (Exception e){
                log.error("查询异常", e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "查询异常");
            }
        }else {
            //找特定的数据源
            DataSource<?> dataSource = dataSourceRegistry.getDataSourceByType(type);
            Page<?> page = dataSource.doSearch(searchText, pageNum, pageSize);
            searchVO.setDataList(page.getRecords());
        }

        return searchVO;
    }



}
