package com.lgk.lgkaicodeservice.datasync;

import com.alibaba.otter.canal.protocol.CanalEntry.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据同步处理器
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
@Component
@Slf4j
public class DataSyncProcessor {

    /**
     * 表处理器映射
     */
    private final Map<String, TableDataHandler> tableHandlers = new ConcurrentHashMap<>();

    @Resource
    private PostDataSyncHandler postDataSyncHandler;

    /**
     * 初始化表处理器
     */
    public void initTableHandlers() {
        // 注册Post表处理器
        tableHandlers.put("post", postDataSyncHandler);
        log.info("数据同步处理器初始化完成，注册表处理器数量: {}", tableHandlers.size());
    }

    /**
     * 处理Canal消息条目
     */
    public void processEntries(List<Entry> entries) {
        if (CollectionUtils.isEmpty(entries)) {
            return;
        }

        // 延迟初始化表处理器
        if (tableHandlers.isEmpty()) {
            initTableHandlers();
        }

        for (Entry entry : entries) {
            if (entry.getEntryType() == EntryType.TRANSACTIONBEGIN || 
                entry.getEntryType() == EntryType.TRANSACTIONEND) {
                continue;
            }

            try {
                RowChange rowChange = RowChange.parseFrom(entry.getStoreValue());
                EventType eventType = rowChange.getEventType();
                
                String schemaName = entry.getHeader().getSchemaName();
                String tableName = entry.getHeader().getTableName();
                
                log.debug("处理数据变更: 数据库={}, 表={}, 事件类型={}", 
                         schemaName, tableName, eventType);

                // 获取对应的表处理器
                TableDataHandler handler = tableHandlers.get(tableName);
                if (handler != null) {
                    handler.handleDataChange(schemaName, tableName, eventType, rowChange.getRowDatasList());
                } else {
                    log.debug("未找到表 {} 的处理器，跳过处理", tableName);
                }
            } catch (Exception e) {
                log.error("处理Canal数据变更异常: {}", entry.toString(), e);
            }
        }
    }
}