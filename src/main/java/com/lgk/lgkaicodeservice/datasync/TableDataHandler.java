package com.lgk.lgkaicodeservice.datasync;

import com.alibaba.otter.canal.protocol.CanalEntry.EventType;
import com.alibaba.otter.canal.protocol.CanalEntry.RowData;

import java.util.List;

/**
 * 表数据处理器接口
 *
 * @author <a href="https://github.com/lgkgbd">程序员lgk</a>
 */
public interface TableDataHandler {

    /**
     * 处理数据变更
     *
     * @param schemaName 数据库名
     * @param tableName  表名
     * @param eventType  事件类型
     * @param rowDataList 行数据列表
     */
    void handleDataChange(String schemaName, String tableName, EventType eventType, List<RowData> rowDataList);
}