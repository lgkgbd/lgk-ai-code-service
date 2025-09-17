# Canal数据同步功能使用指南

## 概述

本项目已集成Canal数据同步功能，可以实时监听MySQL数据库中Post表的变更，并自动同步到Elasticsearch。

## 功能特性

- ✅ 实时监听Post表的INSERT、UPDATE、DELETE操作
- ✅ 自动同步数据到Elasticsearch
- ✅ 支持逻辑删除和物理删除
- ✅ 提供手动同步和管理接口
- ✅ 健康检查和监控
- ✅ 异常处理和重试机制

## 部署步骤

### 1. 安装和配置Canal Server

#### 下载Canal
```bash
# 下载Canal Server
wget https://github.com/alibaba/canal/releases/download/canal-1.1.7/canal.deployer-1.1.7.tar.gz

# 解压
tar -zxvf canal.deployer-1.1.7.tar.gz
cd canal.deployer-1.1.7
```

#### 配置MySQL
确保MySQL开启binlog，在my.cnf中添加：
```ini
[mysqld]
log-bin=mysql-bin
binlog-format=ROW
server-id=1
```

创建Canal用户：
```sql
CREATE USER 'canal'@'%' IDENTIFIED BY 'canal';
GRANT SELECT, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'canal'@'%';
FLUSH PRIVILEGES;
```

#### 配置Canal Server
编辑 `conf/example/instance.properties`：
```properties
# 数据库连接信息
canal.instance.master.address=127.0.0.1:3306
canal.instance.dbUsername=canal
canal.instance.dbPassword=canal

# 监听的数据库和表
canal.instance.filter.regex=lgk_ai_code_service\\.post
```

#### 启动Canal Server
```bash
sh bin/startup.sh
```

### 2. 配置应用

在 `application.yml` 中配置Canal客户端：
```yaml
canal:
  host: localhost              # Canal服务器地址
  port: 11111                 # Canal服务器端口
  destination: example        # Canal实例名称
  username: ""                # 用户名（如果Canal配置了认证）
  password: ""                # 密码
  subscribe: "lgk_ai_code_service.post"  # 订阅的表
  batch-size: 1000           # 批量获取数据大小
  enabled: true              # 是否启用Canal同步
  empty-count: 120           # 空数据重试次数
  sleep-time: 1000           # 空数据等待时间（毫秒）
```

### 3. 启动应用

启动Spring Boot应用，Canal客户端会自动连接并开始监听数据变更。

## API接口

### 数据同步管理接口

#### 1. 重启Canal客户端
```http
POST /api/datasync/canal/restart
```

#### 2. 全量同步Post数据到ES
```http
POST /api/datasync/post/sync-all
```

#### 3. 同步指定Post到ES
```http
POST /api/datasync/post/sync/{id}
```

#### 4. 从ES删除指定Post
```http
DELETE /api/datasync/post/es/{id}
```

### 健康检查
```http
GET /actuator/health
```
查看Canal客户端的健康状态。

## 使用示例

### 1. 创建Post（自动同步）
```java
Post post = new Post();
post.setTitle("测试标题");
post.setContent("测试内容");
post.setUserId(1L);
// ... 设置其他字段

postService.save(post); // 保存后会自动同步到ES
```

### 2. 更新Post（自动同步）
```java
Post post = postService.getById(1L);
post.setTitle("更新后的标题");
post.setUpdateTime(LocalDateTime.now());

postService.updateById(post); // 更新后会自动同步到ES
```

### 3. 删除Post（自动同步）
```java
// 逻辑删除
postService.removeById(1L); // 会自动从ES中删除

// 物理删除
postService.getBaseMapper().deleteById(1L); // 也会自动从ES中删除
```

### 4. 手动全量同步
```java
@Resource
private PostDataSyncService postDataSyncService;

// 全量同步所有Post到ES
long count = postDataSyncService.syncAllPostsToEs();
```

## 监控和日志

### 日志级别配置
在 `application.yml` 中配置日志级别：
```yaml
logging:
  level:
    com.lgk.lgkaicodeservice.datasync: DEBUG
```

### 关键日志信息
- Canal客户端连接状态
- 数据同步成功/失败记录
- 异常处理和重试信息

## 故障排查

### 1. Canal客户端连接失败
- 检查Canal Server是否正常运行
- 检查网络连接和端口
- 检查Canal配置是否正确

### 2. 数据同步失败
- 检查Elasticsearch是否正常运行
- 检查Post数据格式是否正确
- 查看应用日志中的错误信息

### 3. 数据不一致
- 使用全量同步接口重新同步数据
- 检查Canal监听的表配置是否正确

## 性能优化

### 1. 批量处理
- 调整 `canal.batch-size` 参数
- 根据数据量和网络情况优化批次大小

### 2. 异步处理
- Canal数据处理已使用异步方式
- 可根据需要调整线程池配置

### 3. 监控指标
- 监控Canal客户端连接状态
- 监控数据同步延迟
- 监控ES写入性能

## 扩展功能

### 支持其他表同步
1. 实现 `TableDataHandler` 接口
2. 在 `DataSyncProcessor` 中注册新的处理器
3. 更新Canal订阅配置

### 自定义同步逻辑
- 继承或修改 `PostDataSyncHandler`
- 实现特定的业务逻辑

## 注意事项

1. **数据一致性**：Canal同步是最终一致性，可能存在短暂延迟
2. **异常处理**：应用重启后Canal会自动重连并继续同步
3. **数据量**：大量数据变更时注意ES的写入性能
4. **监控**：建议配置监控告警，及时发现同步异常

## 测试验证

运行测试类验证功能：
```bash
mvn test -Dtest=PostDataSyncTest
```

或在IDE中运行 `PostDataSyncTest` 类中的测试方法。