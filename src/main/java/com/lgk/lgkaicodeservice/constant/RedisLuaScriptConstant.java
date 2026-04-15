package com.lgk.lgkaicodeservice.constant;

/**
 * Redis Lua 脚本常量类
 * 统一管理所有 Lua 脚本，便于维护和版本控制
 * <p>
 * Key 格式（通用化，支持多类型扩展）：
 * <ul>
 *   <li>KEYS[1]: thumb:exists:{type}              → 目标存在性 Set</li>
 *   <li>KEYS[2]: thumb:user:{userId}:{type}      → 用户点赞 Hash</li>
 *   <li>KEYS[3]: thumb:count:{type}:{targetId}   → 点赞计数</li>
 * </ul>
 */
public interface RedisLuaScriptConstant {

    // ==================== 点赞相关 Lua 脚本 ====================

    /**
     * 点赞操作 Lua 脚本
     * 原子性执行：检查目标存在性 -> 检查是否已点赞 -> 执行点赞
     *
     * KEYS[1]: thumb:exists:{type}               → 目标存在性 Set
     * KEYS[2]: thumb:user:{userId}:{type}        → 用户点赞 Hash
     * KEYS[3]: thumb:count:{type}:{targetId}     → 目标点赞计数
     * ARGV[1]: targetId                          → 目标ID
     * ARGV[2]: thumbId                           → 点赞记录ID
     *
     * 返回值:
     *   1  - 点赞成功
     *  -1  - 目标不存在
     *  -2  - 已经点赞过了
     */
    String THUMB_DO_LUA = """
            local exists = redis.call('SISMEMBER', KEYS[1], ARGV[1])
            if exists == 0 then
                return -1
            end
            local hasThumb = redis.call('HEXISTS', KEYS[2], ARGV[1])
            if hasThumb == 1 then
                return -2
            end
            redis.call('HSET', KEYS[2], ARGV[1], ARGV[2])
            redis.call('INCR', KEYS[3])
            return 1
            """;

    /**
     * 取消点赞操作 Lua 脚本
     * 原子性执行：检查是否已点赞 -> 执行取消点赞
     *
     * KEYS[1]: thumb:user:{userId}:{type}        → 用户点赞 Hash
     * KEYS[2]: thumb:count:{type}:{targetId}     → 目标点赞计数
     * ARGV[1]: targetId                          → 目标ID
     *
     * 返回值:
     *   1  - 取消成功
     *  -1  - 没有点赞过
     */
    String THUMB_UNDO_LUA = """
            local hasThumb = redis.call('HEXISTS', KEYS[1], ARGV[1])
            if hasThumb == 0 then
                return -1
            end
            redis.call('HDEL', KEYS[1], ARGV[1])
            redis.call('DECR', KEYS[2])
            return 1
            """;

    /**
     * 检查目标是否存在 Lua 脚本
     *
     * KEYS[1]: thumb:exists:{type}               → 目标存在性 Set
     * ARGV[1]: targetId                          → 目标ID
     *
     * 返回值:
     *   1  - 存在
     *   0  - 不存在
     */
    String EXISTS_LUA = """
            return redis.call('SISMEMBER', KEYS[1], ARGV[1])
            """;

    /**
     * 批量添加目标到存在性集合 Lua 脚本
     *
     * KEYS[1]: thumb:exists:{type}               → 目标存在性 Set
     * ARGV: 变长参数，所有要添加的目标ID
     *
     * 返回值: 成功添加的数量
     */
    String BATCH_ADD_EXISTS_LUA = """
            local count = 0
            for i = 1, #ARGV do
                local added = redis.call('SADD', KEYS[1], ARGV[i])
                count = count + added
            end
            return count
            """;

    /**
     * 从存在性集合移除目标 Lua 脚本
     *
     * KEYS[1]: thumb:exists:{type}               → 目标存在性 Set
     * ARGV[1]: targetId                          → 目标ID
     *
     * 返回值:
     *   1  - 移除成功
     *   0  - 目标不存在于集合中
     */
    String REMOVE_EXISTS_LUA = """
            return redis.call('SREM', KEYS[1], ARGV[1])
            """;

    // ==================== 点赞数相关 Lua 脚本 ====================

    /**
     * 获取目标点赞数 Lua 脚本
     *
     * KEYS[1]: thumb:count:{type}:{targetId}    → 目标点赞计数
     *
     * 返回值: 当前点赞数，如果不存在返回 0
     */
    String COUNT_GET_LUA = """
            local count = redis.call('GET', KEYS[1])
            if count == false then
                return 0
            end
            return tonumber(count)
            """;

    /**
     * 初始化目标点赞数 Lua 脚本
     * 仅在键不存在时设置值
     *
     * KEYS[1]: thumb:count:{type}:{targetId}     → 目标点赞计数
     * ARGV[1]: initialCount                      → 初始点赞数
     *
     * 返回值:
     *   1  - 设置成功
     *   0  - 键已存在，未设置
     */
    String COUNT_INIT_LUA = """
            return redis.call('SETNX', KEYS[1], ARGV[1])
            """;

    /**
     * 批量初始化目标点赞数 Lua 脚本
     *
     * KEYS: 变长参数，每个目标的点赞数 key
     * ARGV: 变长参数，与 KEYS 一一对应的初始值
     *
     * 返回值: 成功初始化的数量
     */
    String COUNT_BATCH_INIT_LUA = """
            local count = 0
            for i = 1, #KEYS do
                local result = redis.call('SETNX', KEYS[i], ARGV[i])
                count = count + result
            end
            return count
            """;

    // ==================== 用户点赞记录相关 Lua 脚本 ====================

    /**
     * 检查用户是否已点赞 Lua 脚本
     *
     * KEYS[1]: thumb:user:{userId}:{type}       → 用户点赞 Hash
     * ARGV[1]: targetId                          → 目标ID
     *
     * 返回值:
     *   1  - 已点赞
     *   0  - 未点赞
     */
    String USER_THUMB_CHECK_LUA = """
            return redis.call('HEXISTS', KEYS[1], ARGV[1])
            """;

    /**
     * 获取用户所有点赞记录 Lua 脚本
     *
     * KEYS[1]: thumb:user:{userId}:{type}       → 用户点赞 Hash
     *
     * 返回值: 包含所有 field-value 的数组
     */
    String USER_THUMB_GET_ALL_LUA = """
            return redis.call('HGETALL', KEYS[1])
            """;

    /**
     * 批量检查用户点赞状态 Lua 脚本
     *
     * KEYS[1]: thumb:user:{userId}:{type}       → 用户点赞 Hash
     * ARGV: 变长参数，所有要检查的目标ID
     *
     * 返回值: 数组，每个元素对应 ARGV 中的ID，1表示已点赞，0表示未点赞
     */
    String USER_THUMB_BATCH_CHECK_LUA = """
            local result = {}
            for i = 1, #ARGV do
                local exists = redis.call('HEXISTS', KEYS[1], ARGV[i])
                table.insert(result, exists)
            end
            return result
            """;
}
