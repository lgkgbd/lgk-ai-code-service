# Canal数据同步模块

## 功能说明

本模块实现了基于Canal的MySQL数据变更监听和Elasticsearch同步功能，主要针对Post表的数据同步。

## 核心组件

### 1. CanalConfig
Canal客户端配置类，支持通过application.yml配置Canal连接参数。

### 2. CanalClient
Canal客户端核心类，负责：
- 连接Canal服务器
- 监听数据库变更
- 异步处理数据同步

### 3. DataSyncProcessor
数据同步处理器，负责：
- 解析Canal消息
- 分发到对应的表处理器

### 4. TableDataHandler
表数据处理器接口，定义了数据变更处理的标准。

### 5. PostDataSyncHandler
Post表专用数据同步处理器，负责：
- 处理Post表的INSERT、UPDATE、DELETE事件
- 同步数据到Elasticsearch
- 处理逻辑删除和物理删除

### 6. PostDataSyncService
Post数据同步服务，提供：
- 全量同步功能
- 单条数据同步
- 数据删除功能

### 7. DataSyncController
数据同步管理接口，提供：
- Canal客户端重启
- 全量数据同步
- 单条数据同步和删除

## 配置说明

在application.yml中添加Canal配置：

```yaml
canal:
  host: localhost              # Canal服务器地址
  port: 11111                 # Canal服务器端口
  destination: example        # Canal实例名称
  username: ""                # 用户名
  password: ""                # 密码
  subscribe: "lgk_ai_code_service.post"  # 订阅的表
  batch-size: 1000           # 批量获取数据大小
  enabled: true              # 是否启用Canal同步
  empty-count: 120           # 空数据重试次数
  sleep-time: 1000           # 空数据等待时间（毫秒）
```

## 使用方式

1. **自动同步**：应用启动后自动连接Canal服务器，监听数据变更并同步到ES

2. **手动同步**：通过API接口进行手动同步
   - `POST /api/datasync/post/sync-all` - 全量同步
   - `POST /api/datasync/post/sync/{id}` - 同步指定Post
   - `DELETE /api/datasync/post/es/{id}` - 从ES删除指定Post

3. **Canal管理**：
   - `POST /api/datasync/canal/restart` - 重启Canal客户端

## 注意事项

1. 确保Canal服务器已正确配置并启动
2. 确保Elasticsearch服务正常运行
3. Post表的数据变更会自动同步到ES，无需手动干预
4. 支持逻辑删除和物理删除的处理
5. 异常情况下可通过API进行手动同步修复

## 扩展说明

如需支持其他表的同步，只需：
1. 实现TableDataHandler接口
2. 在DataSyncProcessor中注册新的表处理器
3. 更新Canal订阅配置包含新表