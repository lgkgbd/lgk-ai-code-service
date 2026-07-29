package com.lgk.lgkaicodeservice.service.word;

import com.lgk.lgkaicodeservice.constant.RedisConstant;
import com.lgk.lgkaicodeservice.constant.WordConstant;
import com.lgk.lgkaicodeservice.mapper.UserWordReviewLogMapper;
import com.lgk.lgkaicodeservice.mapper.UserWordSightingMapper;
import com.lgk.lgkaicodeservice.mapper.WordBookItemMapper;
import com.lgk.lgkaicodeservice.mapper.WordBookMapper;
import com.lgk.lgkaicodeservice.model.dto.word.WordCaptureRequest;
import com.lgk.lgkaicodeservice.model.entity.User;
import com.lgk.lgkaicodeservice.model.entity.UserWord;
import com.lgk.lgkaicodeservice.model.entity.UserWordReviewLog;
import com.lgk.lgkaicodeservice.model.entity.UserWordSighting;
import com.lgk.lgkaicodeservice.model.entity.WordBook;
import com.lgk.lgkaicodeservice.model.entity.WordBookItem;
import com.lgk.lgkaicodeservice.model.entity.WordDict;
import com.lgk.lgkaicodeservice.model.vo.WordCardVO;
import com.lgk.lgkaicodeservice.service.WordDictService;
import com.lgk.lgkaicodeservice.service.WordService;
import com.lgk.lgkaicodeservice.utils.WordTextParser;
import com.mybatisflex.core.logicdelete.LogicDeleteManager;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 拍照录入「手写释义」落库规则（B 方案）集成测试
 * <p>
 * 便利贴上手写的中文释义是<b>用户自己抄的</b>，可能抄错、可能只是当下的私人理解，
 * 因此它只允许进入用户私有的 {@code user_word.note}，绝不允许写进全站共享的
 * {@code word_dict.translation}——否则一个人抄错，所有人跟着错。
 * <p>
 * 写 note 前有三道闸门（见 {@code WordServiceImpl#applyPrivateTranslation}），任一不过就跳过：
 * <ol>
 *   <li>没识别出释义</li>
 *   <li>平台词典已有释义 → 以词典为准（词典比手写准）</li>
 *   <li>用户已写过笔记 → 尊重用户，绝不覆盖</li>
 * </ol>
 * 本类逐条覆盖这三道闸门的「过」与「不过」，外加「共享词典零污染」这条底线。
 * <p>
 * 用合成 userId + 每次硬清理保证可重复执行；生造词（zzqw 前缀）保证与真实 ECDICT 词库不碰撞，
 * 且每个用例各用一个，互不干扰。
 */
@SpringBootTest
class WordCaptureOcrTranslationTest {

    private static final long USER_A = 990000101L;
    private static final long USER_B = 990000102L;
    private static final List<Long> TEST_USERS = List.of(USER_A, USER_B);

    @Resource
    private WordService wordService;

    @Resource
    private WordDictService wordDictService;

    @Resource
    private WordTextParser wordTextParser;

    @Resource
    private UserWordSightingMapper sightingMapper;

    @Resource
    private UserWordReviewLogMapper reviewLogMapper;

    @Resource
    private WordBookMapper wordBookMapper;

    @Resource
    private WordBookItemMapper wordBookItemMapper;

    @Resource
    private RedissonClient redissonClient;

    private User userA;
    private User userB;

    private User user(long id) {
        User u = new User();
        u.setId(id);
        u.setUserName("test-" + id);
        u.setUserRole("user");
        return u;
    }

    @BeforeEach
    void setUp() {
        userA = user(USER_A);
        userB = user(USER_B);
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    /**
     * 硬清理测试用户的所有痕迹。
     * <p>
     * 必须绕过逻辑删除：user_word 上有唯一索引 uq_user_dict，残留的软删行会让下一次
     * capture 走到「已存在」分支，用例就不可重复执行了。
     * <p>
     * word_dict 里的生造词占位行<b>故意不删</b>：它是全站共享的客观数据、不含任何用户信息，
     * 留着还能让重跑时省掉一次外部词典 API 的空查；而且「translation 始终为空」这条断言
     * 无论行是新建的还是复用的都成立。
     */
    private void cleanUp() {
        LogicDeleteManager.execWithoutLogicDelete(() -> {
            List<Long> personalBookIds = wordBookMapper.selectListByQuery(QueryWrapper.create()
                            .in(WordBook::getOwnerId, TEST_USERS))
                    .stream().map(WordBook::getId).collect(Collectors.toList());
            if (!personalBookIds.isEmpty()) {
                wordBookItemMapper.deleteByQuery(QueryWrapper.create()
                        .in(WordBookItem::getBookId, personalBookIds));
            }
            wordBookMapper.deleteByQuery(QueryWrapper.create()
                    .in(WordBook::getOwnerId, TEST_USERS));
            wordService.getMapper().deleteByQuery(QueryWrapper.create()
                    .in(UserWord::getUserId, TEST_USERS));
            sightingMapper.deleteByQuery(QueryWrapper.create()
                    .in(UserWordSighting::getUserId, TEST_USERS));
            reviewLogMapper.deleteByQuery(QueryWrapper.create()
                    .in(UserWordReviewLog::getUserId, TEST_USERS));
            return null;
        });
        TEST_USERS.forEach(id ->
                redissonClient.getScoredSortedSet(RedisConstant.getWordDueKey(id)).delete());
    }

    // ==================== 构造 / 断言辅助 ====================

    /**
     * 拍照录入请求：无原句、只有词 + 手写释义（+ 可选原图短链）
     */
    private WordCaptureRequest ocrReq(String text, Map<String, String> translations, String imageUrl) {
        WordCaptureRequest r = new WordCaptureRequest();
        r.setText(text);
        r.setChannel(WordConstant.CHANNEL_OCR);
        r.setTranslations(translations);
        r.setImageUrl(imageUrl);
        return r;
    }

    private WordCardVO captureOne(String text, Map<String, String> translations, User loginUser) {
        List<WordCardVO> cards = wordService.capture(ocrReq(text, translations, null), loginUser);
        assertEquals(1, cards.size(), "单个词应当只返回 1 张词卡");
        return cards.get(0);
    }

    /**
     * 回查数据库中的 note——只信落库结果，不信内存里的返回值
     */
    private String noteInDb(WordCardVO card) {
        UserWord w = wordService.getById(card.getUserWordId());
        assertNotNull(w, "词卡应已落库");
        return w.getNote();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static Map<String, String> translations(String... kv) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }

    // ==================== 闸门 1、2 都过：手写释义写入私有 note ====================

    @Nested
    @DisplayName("词典没有释义时，手写释义补进私有笔记")
    class WriteWhenDictEmpty {

        @Test
        @DisplayName("生造词（词典无释义）+ 有手写释义 → note 落库为手写释义")
        void handwrittenTranslationBecomesNote() {
            WordCardVO card = captureOne("zzqwnotefresh",
                    translations("zzqwnotefresh", "测试释义"), userA);

            // 前置：生造词只能走 manual 占位，词典本身没有中文释义
            assertTrue(isBlank(card.getTranslation()), "生造词不该在平台词典里有释义");
            assertEquals("测试释义", card.getNote(), "本次返回的词卡就该带上释义，用户确认页要直接显示");
            assertEquals("测试释义", noteInDb(card), "释义必须真的落库，而不是只挂在返回对象上");
        }

        @Test
        @DisplayName("释义 key 前后带空格 / 大小写不一致 → 归一化后照样命中")
        void translationKeyIsNormalized() {
            // 便利贴上的词由模型识别，大小写和空白都不可控，key 必须 trim + 转小写后再匹配
            WordCardVO card = captureOne("zzqwrawkey",
                    translations("  ZZQwRawKey  ", "  原词命中的释义  "), userA);

            assertEquals("原词命中的释义", noteInDb(card), "key 与 value 都应 trim 后再落库");
        }
    }

    // ==================== 闸门 2：词典已有释义 → 手写释义丢弃 ====================

    @Nested
    @DisplayName("★ 平台词典已有释义时，以词典为准")
    class DictWins {

        @Test
        @DisplayName("★ apple（词典有中文释义）+ 手写释义 → note 保持为空")
        void dictTranslationBeatsHandwritten() {
            WordCardVO card = captureOne("apple",
                    translations("apple", "我抄错的释义"), userA);

            assertEquals("apple", card.getSpelling());
            // 前置条件：本地 ECDICT 词库（stardict 表）应已导入，否则这条用例失去意义
            assertFalse(isBlank(card.getTranslation()),
                    "前置条件不满足：apple 应能从平台词典拿到中文释义，请确认 stardict/word_dict 已导入");
            // ★ B 方案的立身之本：词典的客观释义永远优先于用户手抄的释义
            assertTrue(isBlank(card.getNote()), "词典已有释义时，手写释义必须丢弃，不得写进 note");
            assertTrue(isBlank(noteInDb(card)), "落库的 note 同样必须为空");
        }

        @Test
        @DisplayName("★ 词典已有释义时，手写释义也不许把词典改掉")
        void dictTranslationIsNotOverwritten() {
            // 先不带释义录一次，确保词条已落库，再取词典此刻的释义作为基线
            captureOne("apple", null, userA);
            String before = dictTranslationOf("apple");
            assertFalse(isBlank(before), "前置条件不满足：apple 应有词典释义，请确认 stardict 已导入");

            captureOne("apple", translations("apple", "我抄错的释义"), userA);
            assertEquals(before, dictTranslationOf("apple"), "录入不得改动共享词典的释义");
        }

        private String dictTranslationOf(String spelling) {
            WordDict dict = wordDictService.getBySpelling(spelling, WordConstant.DEFAULT_LANG);
            return dict == null ? null : dict.getTranslation();
        }
    }

    // ==================== 闸门 3：用户已有笔记 → 绝不覆盖 ====================

    @Nested
    @DisplayName("用户已有笔记时，绝不覆盖")
    class UserNoteWins {

        @Test
        @DisplayName("第一次拍照写入的 note，第二次拍照的新释义不许覆盖")
        void secondCaptureKeepsFirstNote() {
            WordCardVO first = captureOne("zzqwkeepnote",
                    translations("zzqwkeepnote", "第一次写的释义"), userA);
            assertEquals("第一次写的释义", noteInDb(first));

            WordCardVO second = captureOne("zzqwkeepnote",
                    translations("zzqwkeepnote", "第二次写的释义"), userA);

            assertEquals(first.getUserWordId(), second.getUserWordId(), "重复录入不该新增词卡");
            assertEquals("第一次写的释义", noteInDb(second), "已有笔记是用户的地盘，识别结果不许覆盖");
        }

        @Test
        @DisplayName("用户手动写过笔记，再拍照录入同一个词也不许覆盖")
        void manualNoteIsNotOverwritten() {
            WordCardVO card = captureOne("zzqwmynote", null, userA);
            assertTrue(isBlank(noteInDb(card)));

            assertTrue(wordService.updateNote(card.getUserWordId(), "我自己写的助记", userA));

            captureOne("zzqwmynote", translations("zzqwmynote", "识别出来的释义"), userA);
            assertEquals("我自己写的助记", noteInDb(card), "用户手写的笔记优先级最高");
        }
    }

    // ==================== 闸门 1：没有手写释义 → note 不受影响 ====================

    @Nested
    @DisplayName("没有手写释义时，note 保持为空")
    class NoTranslation {

        @Test
        @DisplayName("translations 为 null → note 为空")
        void nullTranslations() {
            WordCardVO card = captureOne("zzqwnotrans", null, userA);
            assertTrue(isBlank(card.getNote()));
            assertTrue(isBlank(noteInDb(card)));
        }

        @Test
        @DisplayName("释义是空白字符串 / key 对不上 → note 仍为空")
        void blankOrMismatchedTranslations() {
            // 模型偶尔会给出空释义，或给出与词对不上的 key，都不能污染 note
            WordCardVO blank = captureOne("zzqwblanktrans",
                    translations("zzqwblanktrans", "   "), userA);
            assertTrue(isBlank(noteInDb(blank)), "空白释义应在归一化时被剔除");

            WordCardVO mismatched = captureOne("zzqwmisskey",
                    translations("someotherword", "别的词的释义"), userA);
            assertTrue(isBlank(noteInDb(mismatched)), "key 对不上时不许张冠李戴");
        }
    }

    // ==================== 底线：共享词典零污染 ====================

    @Nested
    @DisplayName("★ 手写释义绝不污染全站共享词典")
    class SharedDictNeverPolluted {

        @Test
        @DisplayName("★ 手写释义只进私有 note，word_dict.translation 依旧为空")
        void handwrittenNeverReachesWordDict() {
            WordCardVO card = captureOne("zzqwdictclean",
                    translations("zzqwdictclean", "私有的手写释义"), userA);
            assertEquals("私有的手写释义", noteInDb(card), "释义应写进私有笔记");

            WordDict dict = wordDictService.getBySpelling("zzqwdictclean", WordConstant.DEFAULT_LANG);
            assertNotNull(dict, "录入应当为该词建立词典占位条目");
            assertEquals(card.getDictId(), dict.getId(), "词卡应指向这条词典记录");
            // ★ 整个 B 方案的意义所在：一个人抄错，不能让所有人跟着错
            assertTrue(isBlank(dict.getTranslation()),
                    "手写释义绝不允许写进全用户共享的 word_dict.translation");
        }

        @Test
        @DisplayName("★ A 的手写释义对 B 不可见，B 的 note 仍为空")
        void noteIsPerUser() {
            WordCardVO cardA = captureOne("zzqwprivatenote",
                    translations("zzqwprivatenote", "A 抄的释义"), userA);
            assertEquals("A 抄的释义", noteInDb(cardA));

            // B 录入同一个词，没有手写释义：既拿不到 A 的笔记，也不该被 A 影响
            WordCardVO cardB = captureOne("zzqwprivatenote", null, userB);
            assertEquals(cardA.getDictId(), cardB.getDictId(), "两人共享同一条词典记录");
            assertTrue(isBlank(noteInDb(cardB)), "note 是私有的，A 写的不该出现在 B 的词卡上");
            assertEquals("A 抄的释义", noteInDb(cardA), "B 的录入不该反过来动 A 的笔记");

            WordDict dict = wordDictService.getBySpelling("zzqwprivatenote", WordConstant.DEFAULT_LANG);
            assertNotNull(dict);
            assertTrue(isBlank(dict.getTranslation()), "共享词典依旧干净");
        }
    }

    // ==================== 词形还原后的释义命中 ====================

    @Nested
    @DisplayName("词形还原后仍能命中释义")
    class LemmaLookup {

        /**
         * 便利贴上写的是 running，录入落到原形 run 上。
         * <p>
         * {@code pickTranslation} 先按用户原词（running）查，再按原形（run）查，这里只给原形 key，
         * 走的就是第二条路径。至于最终写不写 note，仍要看闸门 2：run 在 ECDICT 里有中文释义，
         * 那就以词典为准。两种数据情况都断言到位，不留「什么都没验」的空档。
         */
        @Test
        @DisplayName("原词 running 录成原形 run，释义按原形 key 查找")
        void translationFoundByLemma() {
            String lemma = wordTextParser.restoreLemma("running");
            assertEquals("run", lemma,
                    "前置条件不满足：ECDICT 的 exchange 应能把 running 还原成 run，请确认 stardict 已导入");

            WordCardVO card = captureOne("running", translations("run", "跑；奔跑"), userA);
            assertEquals(lemma, card.getSpelling(), "录入应落在词形还原后的原形上");

            if (isBlank(card.getTranslation())) {
                // 词典没收录释义 → 原形 key 命中，手写释义补进 note
                assertEquals("跑；奔跑", noteInDb(card), "按原形 key 应能命中手写释义");
            } else {
                // 词典已有释义 → 闸门 2 拦下，命中与否都不写 note
                assertTrue(isBlank(noteInDb(card)), "词典有释义时，即使命中手写释义也必须丢弃");
            }
        }

        @Test
        @DisplayName("生造词无词形变化：原词即原形，释义仍按原词 key 命中")
        void lemmaEqualsRawWord() {
            String word = "zzqwlemmaself";
            assertEquals(word, wordTextParser.restoreLemma(word), "词库里没有的词，词形还原应原样返回");

            // map 里混入别的词的释义，锁住「只取本词的 key」这条底线
            WordCardVO card = captureOne(word,
                    translations(word, "原词写的释义", "run", "干扰项"), userA);
            assertEquals("原词写的释义", noteInDb(card), "不许被 map 里其他词的释义顶替");
        }
    }

    // ==================== 原图短链 → sighting ====================

    @Nested
    @DisplayName("拍照来源留痕")
    class ImageSighting {

        private static final String IMAGE_URL = "https://static.example.com/ocr/zzqwsighting.jpg";

        @Test
        @DisplayName("只有原图没有原句 → 也要留一条 sighting，channel=ocr")
        void imageUrlProducesSighting() {
            List<WordCardVO> cards = wordService.capture(
                    ocrReq("zzqwsighting", translations("zzqwsighting", "拍照录入的释义"), IMAGE_URL), userA);
            assertEquals(1, cards.size());
            WordCardVO card = cards.get(0);

            List<UserWordSighting> sightings = sightingMapper.selectListByQuery(QueryWrapper.create()
                    .eq(UserWordSighting::getUserWordId, card.getUserWordId()));
            assertEquals(1, sightings.size(), "拍照录入即使没有原句，也该留下这次拍照的来源");

            UserWordSighting s = sightings.get(0);
            assertEquals(IMAGE_URL, s.getSourceUrl(), "sourceUrl 应指向原图短链，方便用户回看便利贴");
            assertEquals(WordConstant.CHANNEL_OCR, s.getChannel());
            assertEquals(USER_A, s.getUserId());
            assertTrue(isBlank(s.getSentence()), "拍照录入没有原句");
        }

        @Test
        @DisplayName("同时给了 sourceUrl 和 imageUrl → 以原图短链为准")
        void imageUrlBeatsSourceUrl() {
            WordCaptureRequest r = ocrReq("zzqwsightingboth", null, IMAGE_URL);
            r.setSourceUrl("https://static.example.com/other/page.html");
            List<WordCardVO> cards = wordService.capture(r, userA);

            UserWordSighting s = sightingMapper.selectOneByQuery(QueryWrapper.create()
                    .eq(UserWordSighting::getUserWordId, cards.get(0).getUserWordId()));
            assertNotNull(s);
            assertEquals(IMAGE_URL, s.getSourceUrl());
        }

        @Test
        @DisplayName("既无原句也无原图 → 不留 sighting")
        void noContextNoSighting() {
            WordCardVO card = captureOne("zzqwnosighting", null, userA);
            long count = sightingMapper.selectCountByQuery(QueryWrapper.create()
                    .eq(UserWordSighting::getUserWordId, card.getUserWordId()));
            assertEquals(0, count, "无上下文时不该留空壳 sighting");
        }
    }

    // ==================== 超长释义截断 ====================

    @Nested
    @DisplayName("释义长度边界")
    class LengthLimit {

        @Test
        @DisplayName("释义超过 1024 字 → 截断到上限，不撑爆 varchar(1024)")
        void overlongTranslationIsTruncated() {
            // 手写识别偶尔会把整段文字当成释义吐回来，不截断就会直接写库失败
            String overlong = "释".repeat(WordConstant.MAX_NOTE_LENGTH + 500);
            WordCardVO card = captureOne("zzqwtruncate",
                    translations("zzqwtruncate", overlong), userA);

            String note = noteInDb(card);
            assertNotNull(note);
            assertEquals(WordConstant.MAX_NOTE_LENGTH, note.length(), "note 必须被截断到 1024 字");
            assertEquals(overlong.substring(0, WordConstant.MAX_NOTE_LENGTH), note, "应保留开头而不是尾部");
        }

        @Test
        @DisplayName("释义正好 1024 字 → 原样保留")
        void exactLimitIsKept() {
            String exact = "释".repeat(WordConstant.MAX_NOTE_LENGTH);
            WordCardVO card = captureOne("zzqwexactlimit",
                    translations("zzqwexactlimit", exact), userA);
            assertEquals(WordConstant.MAX_NOTE_LENGTH, noteInDb(card).length());
        }
    }

    // ==================== 批量：一张便利贴多个词 ====================

    @Nested
    @DisplayName("多词批量各带各的释义")
    class BatchTranslations {

        @Test
        @DisplayName("一次录入多个词 → 每个词的 note 各自正确，互不串位")
        void eachWordGetsItsOwnNote() {
            Map<String, String> trans = translations(
                    "zzqwbatchalpha", "阿尔法的释义",
                    "zzqwbatchbeta", "贝塔的释义",
                    "zzqwbatchgamma", "伽马的释义");
            // delta 故意不给释义：批量里漏识别一个词，不能连累其他词，也不能被隔壁的释义顶上
            List<WordCardVO> cards = wordService.capture(ocrReq(
                    "zzqwbatchalpha, zzqwbatchbeta, zzqwbatchgamma, zzqwbatchdelta",
                    trans, null), userA);

            assertEquals(4, cards.size());
            Map<String, WordCardVO> bySpelling = cards.stream()
                    .collect(Collectors.toMap(WordCardVO::getSpelling, Function.identity()));

            trans.forEach((word, translation) -> {
                WordCardVO card = bySpelling.get(word);
                assertNotNull(card, "应录入 " + word);
                assertEquals(translation, card.getNote(), word + " 的释义串位了");
                assertEquals(translation, noteInDb(card), word + " 的释义未正确落库");
            });

            WordCardVO delta = bySpelling.get("zzqwbatchdelta");
            assertNotNull(delta);
            assertTrue(isBlank(noteInDb(delta)), "没识别出释义的词，note 应保持为空");
        }
    }
}
