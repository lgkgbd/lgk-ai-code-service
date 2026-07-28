-- ============================================================
-- 单词记忆功能 · 上线初始化数据脚本
--
-- 前置：word_memory.sql 已执行（8 张表建好 + 3 本系统词书骨架已 seed）
-- 本脚本做两件事：
--   A. 把 ECDICT 的 ecdict.csv 导进 stardict 表（外部词库，只读）
--   B. 从 stardict 把六级/四级/考研的词灌进 word_dict，并建立词书关联
--
-- 依赖说明：
--   · A（stardict 导入）是【必需】的 —— EcdictEnricher 靠它给录入的词补释义，
--     WordTextParser.restoreLemma 靠它做词形还原（running→run）。不导则每个词都成 manual 空词条。
--   · B（灌系统词书）是【可选】的 —— 只影响 CET6/CET4/考研 预置词书有没有内容；
--     个人生词本是录入时按需写 word_dict，不依赖 B。
-- ============================================================


-- ============================================================
-- A. 导入 ECDICT 到 stardict（约 770611 行，实测约 12 秒）
--
-- 【坑 1】local_infile 客户端+服务端都要开：
--   服务端先执行一次： set global local_infile = 1;
--   客户端连接要带：    mysql --local-infile=1 -uroot -p lgk_ai_code_service
--
-- 【坑 2】escaped by '"' 不能删！ecdict.csv 字段里有字面量 \n，
--         用默认的 escaped by '\\' 会把它转成真换行，释义会被打乱。
--
-- 【坑 3】导入时刷出的大量 Warning 1366（collins/oxford 空转 0）是正常的，不用管。
--
-- ⚠️ 把下面的路径换成服务器上 ecdict.csv 的【绝对路径】再执行
-- ============================================================

-- load data local infile '/opt/lgk-ai-code-service/ecdict.csv'
-- into table stardict
-- fields terminated by ',' optionally enclosed by '"' escaped by '"'
-- lines terminated by '\n'
-- ignore 1 lines
-- (word, phonetic, definition, translation, pos, collins, oxford,
--  tag, bnc, frq, exchange, detail, audio);

-- 导入后自检（对不上就是导入出问题了）：
--   select count(*) from stardict;                          -- 应为 770611
--   select count(*) from stardict where tag like '%cet6%';  -- 应为 5407


-- ============================================================
-- B. 灌系统词书（导入 stardict 之后执行；全部幂等，可重复跑）
-- ============================================================

-- ---- Step 1. 把六级/四级/考研三档的词一次性灌进 word_dict ----
-- insert ignore 靠 uq_lang_spelling 去重：一个词同时属于多档也只存一份
insert ignore into word_dict
    (lang, spelling, phonetic, translation, definition, pos, exchange, tag, frq, bnc, collins, oxford, source)
select 'en', s.word, s.phonetic, s.translation, s.definition, s.pos, s.exchange,
       s.tag, ifnull(s.frq, 0), ifnull(s.bnc, 0), ifnull(s.collins, 0), ifnull(s.oxford, 0), 'ecdict'
from stardict s
where s.tag like '%cet6%'
   or s.tag like '%cet4%'
   or s.tag like '%ky%';

-- ---- Step 2. 建立词书—词条关联，按词频升序编 seq（常用词排前面）----
-- 六级
insert ignore into word_book_item (bookId, dictId, seq)
select b.id, d.id, row_number() over (order by if(d.frq = 0, 999999, d.frq) asc)
from word_dict d
cross join (select id from word_book where type = 1 and name = '大学英语六级' limit 1) b
where d.tag like '%cet6%';

-- 四级
insert ignore into word_book_item (bookId, dictId, seq)
select b.id, d.id, row_number() over (order by if(d.frq = 0, 999999, d.frq) asc)
from word_dict d
cross join (select id from word_book where type = 1 and name = '大学英语四级' limit 1) b
where d.tag like '%cet4%';

-- 考研
insert ignore into word_book_item (bookId, dictId, seq)
select b.id, d.id, row_number() over (order by if(d.frq = 0, 999999, d.frq) asc)
from word_dict d
cross join (select id from word_book where type = 1 and name = '考研英语' limit 1) b
where d.tag like '%ky%';

-- ---- Step 3. 回填每本系统词书的词数（冗余字段，避免每次 count）----
update word_book b
set b.wordCount = (select count(*) from word_book_item i where i.bookId = b.id)
where b.type = 1;

-- ---- 自检 ----
-- select name, wordCount from word_book where type = 1;
--   大学英语六级 ≈ 5407 / 大学英语四级 ≈ 3849 / 考研英语 ≈ 4801
