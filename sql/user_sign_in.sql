
# 用于持久化保存，但是现阶段直接用redis就行，不过reids也有持久化机制
CREATE TABLE if not exists user_sign_in (
                              id BIGINT AUTO_INCREMENT PRIMARY KEY,  -- 主键，自动递增
                              userId BIGINT NOT NULL,               -- 用户ID，关联用户表
                              signDate DATE NOT NULL,            -- 签到日期
                              createdTime TIMESTAMP DEFAULT CURRENT_TIMESTAMP,  -- 记录创建时间
                              UNIQUE KEY uq_user_date (userId, signDate)  -- 用户ID和签到日期的唯一性约束
);
