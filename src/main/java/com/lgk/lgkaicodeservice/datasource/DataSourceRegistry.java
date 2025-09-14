package com.lgk.lgkaicodeservice.datasource;

import com.lgk.lgkaicodeservice.model.enums.SearchTypeEnum;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 数据源注册器
 *
 */
@Component
public class DataSourceRegistry {

    @Resource
    private PostDataSource postDataSource;

    @Resource
    private PictureDataSource pictureDataSource;

    @Resource
    private VideoDataSource videoDataSource;

    private Map<String, DataSource> typeDataSourceMap;


    //标记一个方法在依赖注入完成后自动执行
    @PostConstruct
    public void init() {
        System.out.println("DataSourceRegistry init");
        typeDataSourceMap = new HashMap(){{
            put(SearchTypeEnum.POST.getValue(), postDataSource);
            put(SearchTypeEnum.PICTURE.getValue(), pictureDataSource);
            put(SearchTypeEnum.VIDEO.getValue(), videoDataSource);
        }};
    }

    public DataSource getDataSourceByType(String type) {
        if(typeDataSourceMap == null) {
            return null;
        }
        return typeDataSourceMap.get(type);
    }

}
