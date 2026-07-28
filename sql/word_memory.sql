-- ============================================================
-- 单词记忆功能 建表脚本
--
-- 设计要点：
--   1. 分四层：客观词条层(word_dict) / 词书层(word_book) / 订阅层(user_book) / 学习层(user_word)
--   2. user_word 对 (userId, dictId) 全局唯一 —— 一个词只有一份掌握度，
--      绝不因为「既是生词又是六级词」而产生两条记录、两条复习曲线
--   3. 生词本不是特例，它就是 word_book 里 type=0 的一条记录
--   4. 预留 lang 字段，以后记日语 / 专业术语不用洗数据
--
-- 命名沿用项目现有风格：驼峰列名 + createTime / updateTime / isDelete
--
-- ⚠️⚠️ 执行本脚本必须带 --default-character-set=utf8mb4：
--
--   mysql --local-infile=1 --default-character-set=utf8mb4 \
--         -uroot -p lgk_ai_code_service < sql/word_memory.sql
--
--   不带的话（尤其在非交互 shell 里），中文表注释、列注释、以及文末
--   insert 进 word_book 的词书名会被当 Latin-1 存成双重编码乱码。
--
--   更阴险的是：用同样缺参数的客户端查回来会「看起来正常」（两次错误抵消），
--   只有换 Navicat 或带 utf8mb4 的客户端才暴露。
--   验证中文务必查 hex，不要只看肉眼：
--     select name, hex(left(name,3)) from word_book;
--     -- 「大学英语六级」正确应为 E5A4A7E5ADA6E88BB1
--     -- 若出现 C3A5C2A4C2A7 开头即为双重编码，需 drop 表重建
-- ============================================================


-- ============================================================
-- 0. ECDICT 原始词库表（外部数据，本项目只读）
--
-- 来源：https://github.com/skywind3000/ECDICT  (MIT)
--
-- ⚠️ 不要下 release 里的 ecdict-sqlite-28.zip（216MB），
--    直接下仓库主分支的 ecdict.csv（66MB）导进来就行，路径更短：
--
--      curl -L --progress-bar -o ecdict.csv \
--        https://gh-proxy.com/https://raw.githubusercontent.com/skywind3000/ECDICT/master/ecdict.csv
--
--      实测各渠道速度：gh-proxy.com 862KB/s > ghfast.top 363KB/s
--                     > github.com/raw 40KB/s > raw.githubusercontent.com 13KB/s
--      Gitee 镜像有防盗链会 Access denied，jsDelivr 超过文件大小限制返回 403，都别用
--
--      下完核对：约 66MB、约 77 万行、表头 13 列
--      ls -lh ecdict.csv && wc -l ecdict.csv && head -1 ecdict.csv
--
-- ⚠️ 必须导进项目自己的库 lgk_ai_code_service，不要另建库，否则查不到
--
-- 关键字段：
--   tag       cet4 cet6 ky toefl ielts gre  → 六级词书直接由此生成，无需另外导词表
--   exchange  时态/复数/比较级等变形        → 词形还原（粘 running 认得 run）
--   frq/bnc   词频排名                      → 难度分级
--   collins   柯林斯星级(1-5)               → 重要度排序
-- ============================================================

-- ---- Step 0.1 建表（列顺序严格对应 ecdict.csv 的 13 列表头，不要调整顺序）----
create table if not exists stardict
(
    word        varchar(64)  not null comment '单词',
    phonetic    varchar(64)  null comment '音标',
    definition  text         null comment '英文释义',
    translation text         null comment '中文释义',
    pos         varchar(16)  null comment '词性占比',
    collins     smallint     null comment '柯林斯星级 0-5',
    oxford      smallint     null comment '是否牛津三千核心词',
    tag         varchar(64)  null comment '考试标签 zk/gk/cet4/cet6/ky/toefl/ielts/gre',
    bnc         int          null comment 'BNC 词频顺序',
    frq         int          null comment '当代语料库词频顺序',
    exchange    varchar(512) null comment '时态复数等变形',
    detail      text         null comment 'JSON 扩展信息',
    audio       varchar(512) null comment '读音地址',
    primary key (word),
    index idx_tag (tag),
    index idx_frq (frq)
) comment 'ECDICT 原始词库（外部数据源，只读）' collate = utf8mb4_unicode_ci;

-- ---- Step 0.2 导入 CSV ----
-- 【坑 1】客户端和服务端都要开 local_infile，只开一边会报
--         ERROR 3948: Loading local data is disabled; this must be enabled on
--         both the client and server sides
--   服务端先执行一次：mysql -uroot -p -e "set global local_infile = 1;"
--   客户端每次都要带：mysql --local-infile=1 -uroot -p lgk_ai_code_service
--
-- 【坑 2】导入时会刷出大量 Warning 1366 "Incorrect integer value: '' for
--         column 'collins'/'oxford'"，这是正常的：CSV 里这两列大量为空，
--         MySQL 自动转成 0，而 0 正好就是「无星级 / 非牛津核心词」的正确语义。
--         不用管，也不用改表结构。
--
-- 【坑 3】查询时若不带 --default-character-set=utf8mb4，音标里的 ә(U+04D9)
--         会显示成 ?。那只是终端显示问题，库里存的是正确的 UTF-8，
--         用 hex(phonetic) 可自证。不要因此去重导。
--
-- 实测：770611 行导入耗时约 12 秒
--
-- ⚠️ escaped by '"' 这句不能删！
--    ecdict.csv 的字段里存在【字面量】的 \n 和 \r\n（两/四个字符的文本，不是真换行），
--    MySQL 默认 escaped by '\\' 会把它们转成真换行符，释义会被打乱。
--    改成 escaped by '"' 后反斜杠不再是转义符，同时也正好匹配该 CSV 实际使用的
--    双引号双写（""）转义方式。
--
-- load data local infile '/绝对路径/ecdict.csv'
-- into table stardict
-- fields terminated by ',' optionally enclosed by '"' escaped by '"'
-- lines terminated by '\n'
-- ignore 1 lines
-- (word, phonetic, definition, translation, pos, collins, oxford,
--  tag, bnc, frq, exchange, detail, audio);
--
-- 导入后验证（对照 CSV 实测基线，数字对不上就是导入出问题了）：
--   select count(*) from stardict;                          -- 应为 770611
--   select count(*) from stardict where tag like '%cet6%';  -- 应为 5407
--   select word, phonetic, translation, exchange from stardict where word = 'running';
--     -- exchange 应为 0:run/1:i/i:  （0: 前缀即原形，词形还原依赖它）
--
-- ecdict.csv 实测基线（2026-07-27 抽查）：
--   770611 词条，全部 13 列无畸形行
--   考试标签：gre 7504 / toefl 6974 / cet6 5407 / ielts 5040
--            ky 4801 / cet4 3849 / gk 3677 / zk 1603
--   字段覆盖：中文释义 99.8% / 音标 28.3% / 词形变化 12.5%
--   ⚠️ 音标只有 28.3%，低频生僻词大多没有，前端必须能优雅处理音标为空
--
-- 只想导子集（决策 #1 的备选方案）：全量导入后执行
--   delete from stardict where (frq = 0 or frq is null) and (tag is null or tag = '');


-- ============================================================
-- 1. 客观层：平台词条表（全站共享，一个词只存一份）
--
-- 数据来源优先级：ecdict(本地) > api(免费词典接口) > ai(大模型兜底) > manual(人工)
-- source 字段用于日后批量重刷某个来源的数据
-- ============================================================
create table if not exists word_dict
(
    id          bigint auto_increment comment '词条id' primary key,
    lang        varchar(16)  default 'en'          not null comment '语言，预留：en/ja/...',
    spelling    varchar(128)                       not null comment '单词原形',
    phonetic    varchar(128)                       null comment '音标',
    translation text                               null comment '中文释义（按行分隔的原始文本）',
    definition  text                               null comment '英文释义',
    pos         varchar(64)                        null comment '词性占比，如 n:52/v:48',
    exchange    varchar(512)                       null comment '词形变化，用于词形还原',
    tag         varchar(64)                        null comment '考试标签 cet4/cet6/ky/toefl/ielts/gre',
    frq         int          default 0             not null comment '当代语料词频排名，越小越常用，0=未知',
    bnc         int          default 0             not null comment 'BNC 词频排名',
    collins     tinyint      default 0             not null comment '柯林斯星级 0-5',
    oxford      tinyint      default 0             not null comment '是否牛津核心词 0/1',
    extInfo     json                               null comment '扩展信息：例句/搭配/同反义词/词源/助记等，结构可演进',
    source      varchar(32)  default 'ecdict'      not null comment '数据来源 ecdict/api/ai/manual',
    createTime  datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    -- 同一语言下单词唯一，查词的唯一入口
    unique key uq_lang_spelling (lang, spelling),
    -- 词书按考试标签筛词
    index idx_tag (tag),
    -- 按词频排序取常用词
    index idx_frq (frq)
) comment '平台词条表（客观数据，全站共享）' collate = utf8mb4_unicode_ci;


-- ============================================================
-- 2. 词书层：词的集合
--
-- type = 0 个人生词本（私有、动态增长、ownerId = 用户id）
-- type = 1 系统预置词书（公共、静态，如 CET-6、考研核心）
-- 生词本不是特例，只是 type=0 的一条记录，接口和代码路径完全共用
-- ============================================================
create table if not exists word_book
(
    id          bigint auto_increment comment '词书id' primary key,
    name        varchar(128)                       not null comment '词书名称',
    description varchar(512)                       null comment '词书简介',
    coverImage  varchar(512)                       null comment '封面图 URL',
    type        tinyint      default 0             not null comment '类型 0=个人生词本 1=系统预置词书',
    lang        varchar(16)  default 'en'          not null comment '语言',
    ownerId     bigint       default 0             not null comment '归属用户id，0=系统词书',
    wordCount   int          default 0             not null comment '词书词数（冗余，避免 count）',
    isPublic    tinyint      default 0             not null comment '是否公开 0/1',
    sortOrder   int          default 0             not null comment '展示排序，越小越前',
    createTime  datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint      default 0             not null comment '是否删除',
    index idx_owner_type (ownerId, type)
) comment '词书表' collate = utf8mb4_unicode_ci;


-- ============================================================
-- 3. 词书内容：词书包含哪些词
--
-- 个人生词本：录入一个词就插一条
-- 系统词书：由 ECDICT 的 tag 字段批量生成（见文末初始化脚本）
-- ============================================================
create table if not exists word_book_item
(
    id         bigint auto_increment comment '主键' primary key,
    bookId     bigint                             not null comment '词书id',
    dictId     bigint                             not null comment '词条id',
    seq        int      default 0                 not null comment '在词书中的顺序',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    -- 同一本书内一个词只出现一次
    unique key uq_book_dict (bookId, dictId),
    -- 按顺序翻词书
    index idx_book_seq (bookId, seq)
) comment '词书-词条关联表' collate = utf8mb4_unicode_ci;


-- ============================================================
-- 4. 订阅层：我在学哪几本词书
--
-- 两类词书的唯一差异就在这张表的 dailyNewLimit / learnCursor 上：
--   生词本  dailyNewLimit = -1，录入即入池（push）
--   预置词书 dailyNewLimit = 20，每天从 learnCursor 往后取 20 个新词入池（pull）
--
-- 注：字段叫 learnCursor 而不是 cursor，因为 cursor 是 MySQL 保留字
-- 除此之外所有逻辑共用一套
-- ============================================================
create table if not exists user_book
(
    id            bigint auto_increment comment '主键' primary key,
    userId        bigint                             not null comment '用户id',
    bookId        bigint                             not null comment '词书id',
    dailyNewLimit int      default 20                not null comment '每日新词投放量，-1=不限（生词本）',
    learnCursor   int      default 0                 not null comment '已投放到词书的第几个词（pull 类词书用）。不叫 cursor 是因为它是 MySQL 保留字',
    status        tinyint  default 0                 not null comment '状态 0=学习中 1=已暂停 2=已学完',
    createTime    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uq_user_book (userId, bookId),
    index idx_userId (userId)
) comment '用户订阅词书表' collate = utf8mb4_unicode_ci;


-- ============================================================
-- 5. 学习层：我对每个词的掌握状态  ★核心表★
--
-- unique key (userId, dictId) 是整个设计的地基：
--   同一个词无论来自生词本还是六级词书，掌握度只有一份、复习曲线只有一条。
--   这也是为什么这里没有 type / bookId 字段 —— 「词从哪来」属于
--   word_book_item + user_word_sighting，不属于学习状态。
--
-- SM-2 字段说明：
--   easeFactor   难度系数，初始 2.5，答错下调、答对上调，下限 1.3
--   intervalDays 当前复习间隔（天）
--   dueTime      下次复习时间，同时写入 Redis ZSet 做当日队列
-- ============================================================
create table if not exists user_word
(
    id             bigint auto_increment comment '主键' primary key,
    userId         bigint                             not null comment '用户id',
    dictId         bigint                             not null comment '词条id',
    spelling       varchar(128)                       not null comment '单词（冗余，列表页免 join）',
    mastery        tinyint      default 0             not null comment '掌握阶段 0=新词 1=学习中 2=复习中 3=已掌握',
    easeFactor     decimal(4, 2) default 2.50         not null comment 'SM-2 难度系数',
    intervalDays   int          default 0             not null comment '当前复习间隔（天）',
    reviewCount    int          default 0             not null comment '累计复习次数',
    lapseCount     int          default 0             not null comment '累计遗忘次数（答错）',
    encounterNum   int          default 1             not null comment '累计遇见次数（重复录入会 +1）',
    dueTime        datetime                           null comment '下次复习时间',
    lastReviewTime datetime                           null comment '上次复习时间',
    note           varchar(1024)                      null comment '私人笔记',
    createTime     datetime     default CURRENT_TIMESTAMP not null comment '首次录入时间',
    updateTime     datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete       tinyint      default 0             not null comment '是否删除',
    -- ★地基：一人一词只有一条学习记录
    unique key uq_user_dict (userId, dictId),
    -- 取当日待复习（Redis 失效时的 DB 兜底查询）
    index idx_user_due (userId, dueTime),
    -- 我的词库按掌握度筛选
    index idx_user_mastery (userId, mastery),
    -- 站内按拼写搜自己的词
    index idx_user_spelling (userId, spelling)
) comment '用户单词学习状态表（一人一词唯一）' collate = utf8mb4_unicode_ci;


-- ============================================================
-- 6. 遇见记录：我在哪句话里撞见过这个词
--
-- 这是本功能区别于普通背单词 App 的核心资产。
-- 一个词可以有多条 sighting —— 「这个词第 5 次绊倒你了」
-- 复习时优先用 sentence 挖空出题，而不是干巴巴的英译中
-- ============================================================
create table if not exists user_word_sighting
(
    id          bigint auto_increment comment '主键' primary key,
    userWordId  bigint                             not null comment '学习记录id',
    userId      bigint                             not null comment '用户id（冗余，便于按用户清理/统计）',
    sentence    varchar(1024)                      null comment '遇见时的原句',
    sourceTitle varchar(256)                       null comment '来源标题（页面标题/书名/播客名）',
    sourceUrl   varchar(1024)                      null comment '来源链接',
    channel     varchar(32)  default 'manual'      not null comment '录入渠道 manual/paste/bookmarklet/extension',
    createTime  datetime     default CURRENT_TIMESTAMP not null comment '遇见时间',
    -- 词卡详情页按时间倒序列出所有遇见记录
    index idx_userWord (userWordId, createTime),
    index idx_userId (userId)
) comment '单词遇见记录表（上下文快照）' collate = utf8mb4_unicode_ci;


-- ============================================================
-- 7. 复习日志（可选，二期上）
--
-- 一期不做也能跑，但有了它才能画「记忆曲线」和年度报告。
-- 数据量 = 用户数 × 词数 × 复习次数，建议按月分区或定期归档。
-- ============================================================
create table if not exists user_word_review_log
(
    id            bigint auto_increment comment '主键' primary key,
    userWordId    bigint                             not null comment '学习记录id',
    userId        bigint                             not null comment '用户id',
    quality       tinyint                            not null comment '本次作答质量 0=完全忘记 1=模糊 2=想起来了 3=秒答',
    costMs        int      default 0                 not null comment '作答耗时(ms)',
    intervalDays  int      default 0                 not null comment '本次复习后的新间隔',
    easeFactor    decimal(4, 2)                      null comment '本次复习后的难度系数',
    reviewTime    datetime default CURRENT_TIMESTAMP not null comment '复习时间',
    index idx_user_time (userId, reviewTime),
    index idx_userWord (userWordId)
) comment '单词复习日志表' collate = utf8mb4_unicode_ci;


-- ============================================================
-- 初始化数据
-- ============================================================

-- 系统预置词书骨架（词条内容由下方脚本从 ECDICT 灌入）
insert into word_book (name, description, type, ownerId, isPublic, sortOrder)
select * from (
    select '大学英语六级' as name, 'CET-6 核心词汇，来源 ECDICT tag=cet6' as description,
           1 as type, 0 as ownerId, 1 as isPublic, 10 as sortOrder
    union all
    select '大学英语四级', 'CET-4 核心词汇，来源 ECDICT tag=cet4', 1, 0, 1, 20
    union all
    select '考研英语', '考研核心词汇，来源 ECDICT tag=ky', 1, 0, 1, 30
) t
where not exists (select 1 from word_book where type = 1 and name = t.name);


-- ============================================================
-- 词书灌词参考脚本（导入 ECDICT 之后手动执行一次）
--
-- 注意：以下语句默认 ECDICT 已导入且表名为 stardict。
--      先把需要的词灌进 word_dict，再建立词书关联。
--      六级约 5000 词，四级约 4000 词，执行很快。
-- ============================================================

-- Step 1. 把六级词灌进 word_dict（幂等，重复执行不会产生重复行）
-- insert ignore into word_dict
--     (lang, spelling, phonetic, translation, definition, pos, exchange, tag, frq, bnc, collins, oxford, source)
-- select 'en', s.word, s.phonetic, s.translation, s.definition, s.pos, s.exchange,
--        s.tag, ifnull(s.frq, 0), ifnull(s.bnc, 0), ifnull(s.collins, 0), ifnull(s.oxford, 0), 'ecdict'
-- from stardict s
-- where s.tag like '%cet6%';

-- Step 2. 建立词书关联，按词频从高到低排序（常用词先学）
-- insert ignore into word_book_item (bookId, dictId, seq)
-- select b.id,
--        d.id,
--        row_number() over (order by if(d.frq = 0, 999999, d.frq) asc)
-- from word_dict d
-- cross join (select id from word_book where type = 1 and name = '大学英语六级' limit 1) b
-- where d.tag like '%cet6%';

-- Step 3. 回填词书词数
-- update word_book b
-- set b.wordCount = (select count(*) from word_book_item i where i.bookId = b.id)
-- where b.type = 1;


-- ============================================================
-- Redis Key 约定（不建表，写在此处备查，实现时放进 RedisConstant）
--
--   word:due:{userId}              ZSet   member=userWordId, score=dueTime 时间戳
--                                         取当日待复习：ZRANGEBYSCORE 0 now
--   word:newquota:{userId}:{date}  String 当日已投放新词数，用于 dailyNewLimit 限流，TTL 48h
--   word:reviewdays:{year}:{userId} Bitmap 复习打卡热力图，复用 user_sign_in 的 bitmap 思路
--   word:dict:{lang}:{spelling}    String 词条缓存（可选），TTL 7d
-- ============================================================
