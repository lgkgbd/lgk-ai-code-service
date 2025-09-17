package com.lgk.lgkaicodeservice.datasync;

import cn.hutool.json.JSONUtil;
import com.alibaba.otter.canal.protocol.CanalEntry.*;
import com.lgk.lgkaicodeservice.model.dto.post.PostEsDTO;
import com.lgk.lgkaicodeservice.model.entity.Post;
import com.lgk.lgkaicodeservice.service.PostService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Post表数据同步处理器
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@Component
@Slf4j
public class PostDataSyncHandler implements TableDataHandler {

    @Resource
    private PostService postService;

    @Resource
    private ElasticsearchTemplate elasticsearchTemplate;

    @Override
    public void handleDataChange(String schemaName, String tableName, EventType eventType, List<RowData> rowDataList) {
        if (CollectionUtils.isEmpty(rowDataList)) {
            return;
        }

        log.info("处理Post表数据变更: 数据库={}, 表={}, 事件类型={}, 数据条数={}", 
                schemaName, tableName, eventType, rowDataList.size());

        for (RowData rowData : rowDataList) {
            try {
                switch (eventType) {
                    case INSERT:
                        handleInsert(rowData);
                        break;
                    case UPDATE:
                        handleUpdate(rowData);
                        break;
                    case DELETE:
                        handleDelete(rowData);
                        break;
                    default:
                        log.debug("不支持的事件类型: {}", eventType);
                }
            } catch (Exception e) {
                log.error("处理Post数据变更异常, 事件类型: {}", eventType, e);
            }
        }
    }

    /**
     * 处理插入事件
     */
    private void handleInsert(RowData rowData) {
        Post post = parsePostFromColumns(rowData.getAfterColumnsList());
        if (post != null && post.getId() != null) {
            // 同步到ES
            syncToElasticsearch(post);
            log.info("Post插入同步到ES成功, ID: {}", post.getId());
        }
    }

    /**
     * 处理更新事件
     */
    private void handleUpdate(RowData rowData) {
        Post post = parsePostFromColumns(rowData.getAfterColumnsList());
        if (post != null && post.getId() != null) {
            // 检查是否为逻辑删除
            if (post.getIsDelete() != null && post.getIsDelete() == 1) {
                // 逻辑删除，从ES中删除
                deleteFromElasticsearch(post.getId());
                log.info("Post逻辑删除，从ES删除成功, ID: {}", post.getId());
            } else {
                // 正常更新，同步到ES
                syncToElasticsearch(post);
                log.info("Post更新同步到ES成功, ID: {}", post.getId());
            }
        }
    }

    /**
     * 处理删除事件
     */
    private void handleDelete(RowData rowData) {
        Post post = parsePostFromColumns(rowData.getBeforeColumnsList());
        if (post != null && post.getId() != null) {
            // 从ES中删除
            deleteFromElasticsearch(post.getId());
            log.info("Post物理删除，从ES删除成功, ID: {}", post.getId());
        }
    }

    /**
     * 从列数据解析Post对象
     */
    private Post parsePostFromColumns(List<Column> columns) {
        if (CollectionUtils.isEmpty(columns)) {
            return null;
        }

        Map<String, String> columnMap = columns.stream()
                .collect(Collectors.toMap(Column::getName, Column::getValue));

        try {
            Post post = new Post();
            
            // 解析基本字段
            String idStr = columnMap.get("id");
            if (idStr != null) {
                post.setId(Long.parseLong(idStr));
            }
            
            post.setTitle(columnMap.get("title"));
            post.setContent(columnMap.get("content"));
            post.setTags(columnMap.get("tags"));
            post.setCoverImage(columnMap.get("coverImage"));
            
            // 解析数值字段
            String thumbNumStr = columnMap.get("thumbNum");
            if (thumbNumStr != null) {
                post.setThumbNum(Integer.parseInt(thumbNumStr));
            }
            
            String favourNumStr = columnMap.get("favourNum");
            if (favourNumStr != null) {
                post.setFavourNum(Integer.parseInt(favourNumStr));
            }
            
            String viewNumStr = columnMap.get("viewNum");
            if (viewNumStr != null) {
                post.setViewNum(Integer.parseInt(viewNumStr));
            }
            
            String userIdStr = columnMap.get("userId");
            if (userIdStr != null) {
                post.setUserId(Long.parseLong(userIdStr));
            }
            
            String priorityStr = columnMap.get("priority");
            if (priorityStr != null) {
                post.setPriority(Integer.parseInt(priorityStr));
            }
            
            String isDeleteStr = columnMap.get("isDelete");
            if (isDeleteStr != null) {
                post.setIsDelete(Integer.parseInt(isDeleteStr));
            }
            
            // 解析时间字段
            String createTimeStr = columnMap.get("createTime");
            if (createTimeStr != null) {
                post.setCreateTime(LocalDateTime.parse(createTimeStr.replace(" ", "T")));
            }
            
            String updateTimeStr = columnMap.get("updateTime");
            if (updateTimeStr != null) {
                post.setUpdateTime(LocalDateTime.parse(updateTimeStr.replace(" ", "T")));
            }
            
            return post;
        } catch (Exception e) {
            log.error("解析Post数据异常: {}", JSONUtil.toJsonStr(columnMap), e);
            return null;
        }
    }

    /**
     * 同步到Elasticsearch
     */
    private void syncToElasticsearch(Post post) {
        try {
            PostEsDTO postEsDTO = PostEsDTO.objToDto(post);
            if (postEsDTO != null) {
                // 转换时间格式
                if (post.getCreateTime() != null) {
                    postEsDTO.setCreateTime(Date.from(post.getCreateTime().atZone(ZoneId.systemDefault()).toInstant()));
                }
                if (post.getUpdateTime() != null) {
                    postEsDTO.setUpdateTime(Date.from(post.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant()));
                }
                
                elasticsearchTemplate.save(postEsDTO);
                log.debug("Post同步到ES成功: {}", post.getId());
            }
        } catch (Exception e) {
            log.error("Post同步到ES失败, ID: {}", post.getId(), e);
        }
    }

    /**
     * 从Elasticsearch删除
     */
    private void deleteFromElasticsearch(Long postId) {
        try {
            elasticsearchTemplate.delete(String.valueOf(postId), PostEsDTO.class);
            log.debug("Post从ES删除成功: {}", postId);
        } catch (Exception e) {
            log.error("Post从ES删除失败, ID: {}", postId, e);
        }
    }
}