package com.lgk.lgkaicodeservice.service.word.ocr;

import com.lgk.lgkaicodeservice.constant.RedisConstant;
import com.lgk.lgkaicodeservice.constant.WordConstant;
import com.lgk.lgkaicodeservice.exception.BusinessException;
import com.lgk.lgkaicodeservice.exception.ErrorCode;
import com.lgk.lgkaicodeservice.model.entity.User;
import com.lgk.lgkaicodeservice.model.enums.WordOcrStatusEnum;
import com.lgk.lgkaicodeservice.model.vo.WordOcrItemVO;
import com.lgk.lgkaicodeservice.model.vo.WordOcrTaskVO;
import com.lgk.lgkaicodeservice.service.WordOcrService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 拍照识别任务的状态机 + Redis 存取 + 提交鉴权 的集成测试
 * <p>
 * <b>为什么要 mock 掉 {@link WordOcrRecognizer}：</b>真调 qwen3-vl-plus 每跑一次测试都要烧 token、
 * 依赖公网和 API Key，还会因为模型输出不稳定让用例变成「薛定谔的红绿灯」。识别本身的健壮性由
 * {@link WordOcrRecognizerParseTest}（纯函数、覆盖各种脏输出）保证；本类只关心<b>任务编排</b>——
 * 状态怎么流转、进度怎么推进、结果怎么存取、越权能不能挡住。
 * <p>
 * 三块内容：
 * <ol>
 *   <li>{@link TaskManagerStateMachine}：直接操作 {@link WordOcrTaskManager}，不需要 mock，是最稳的一层</li>
 *   <li>{@link SubmitAndAuth}：入口校验与「别人的 taskId 一律当不存在」的越权防线</li>
 *   <li>{@link AsyncEndToEnd}：打桩 recognizer 后走完整异步链路，验证 pending → running → succeed/failed</li>
 * </ol>
 * <p>
 * 用合成 userId（99000010x）与带 {@code test-ocr-} 前缀的 taskId，配合 @AfterEach 清 key，
 * 保证跑多少遍都不会在 Redis 里留垃圾、也不会串到真实用户。
 * <p>
 * 注意：worker 里会顺手把原图存进 MinIO，但存图失败在代码里是被吞掉的（存图只为日后溯源），
 * 所以本类<b>不对 imageUrls 的内容做强断言</b>——本地没起 MinIO 时用例也必须是绿的。
 */
@SpringBootTest
class WordOcrTaskFlowTest {

    private static final long USER_A = 990000101L;
    private static final long USER_B = 990000102L;

    @Resource
    private WordOcrTaskManager taskManager;

    @Resource
    private WordOcrService wordOcrService;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 打桩识别器：不联网、不烧 token。
     * <p>
     * 用 Spring Framework 6.2（Boot 3.4+）的 @MockitoBean，旧的 @MockBean 已废弃。
     * 默认 MockReset.AFTER，每个用例结束自动复位，桩不会串场。
     */
    @MockitoBean
    private WordOcrRecognizer recognizer;

    /**
     * 本次用例创建过的 taskId，@AfterEach 统一清理。
     * 异步 worker 也会写这些 key，所以用同步集合。
     */
    private final List<String> createdTaskIds = Collections.synchronizedList(new ArrayList<>());

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        userA = user(USER_A);
        userB = user(USER_B);
        createdTaskIds.clear();
    }

    @AfterEach
    void tearDown() {
        for (String taskId : new ArrayList<>(createdTaskIds)) {
            taskManager.remove(taskId);
        }
        createdTaskIds.clear();
    }

    // ==================== 测试夹具 ====================

    private static User user(long id) {
        User u = new User();
        u.setId(id);
        u.setUserName("test-" + id);
        u.setUserRole("user");
        return u;
    }

    /**
     * 申领一个受管理的 taskId，注册进清理列表
     */
    private String newTaskId() {
        String taskId = "test-ocr-" + UUID.randomUUID().toString().replace("-", "");
        createdTaskIds.add(taskId);
        return taskId;
    }

    /**
     * 创建一个 taskId 已注册清理的 PENDING 任务
     */
    private String createTask(long userId, int totalImages) {
        String taskId = newTaskId();
        taskManager.create(taskId, userId, totalImages);
        return taskId;
    }

    private static WordOcrItemVO item(String word, String translation) {
        return WordOcrItemVO.builder().word(word).translation(translation).build();
    }

    /**
     * 压成 "word:translation" 便于整体比对，顺带保证顺序也被验证
     */
    private static List<String> flat(List<WordOcrItemVO> items) {
        return items.stream()
                .map(i -> i.getWord() + ":" + (i.getTranslation() == null ? "" : i.getTranslation()))
                .collect(Collectors.toList());
    }

    /**
     * 动态生成一张小 JPEG，避免测试依赖外部图片文件
     */
    private static byte[] jpeg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(Color.BLACK);
            g.drawString("apple", 20, height / 2);
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            assertTrue(ImageIO.write(image, "jpg", out), "测试图片写出失败");
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("测试图片生成失败", e);
        }
    }

    private static MockMultipartFile photo(String filename) {
        return new MockMultipartFile("files", filename, "image/jpeg", jpeg(200, 200));
    }

    /**
     * 轮询直到任务进入终态。submit 是异步的，立刻断言必然抢跑
     */
    private WordOcrTaskVO awaitFinalTask(String taskId) {
        await("识别任务进入终态")
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> {
                    WordOcrTaskVO t = taskManager.get(taskId);
                    if (t == null) {
                        return false;
                    }
                    WordOcrStatusEnum status = WordOcrStatusEnum.getEnumByValue(t.getStatus());
                    return status != null && status.isFinal();
                });
        WordOcrTaskVO task = taskManager.get(taskId);
        assertNotNull(task, "终态任务不应读不出来");
        return task;
    }

    // ==================== 一、状态机（不依赖 mock）====================

    @Nested
    @DisplayName("任务状态机与 Redis 存取")
    class TaskManagerStateMachine {

        @Test
        @DisplayName("create：初始为 pending，进度 0/N，归属正确")
        void createInitialState() {
            String taskId = createTask(USER_A, 3);

            WordOcrTaskVO task = taskManager.get(taskId);
            assertNotNull(task, "刚创建就该读得到");
            assertEquals(taskId, task.getTaskId());
            assertEquals(WordOcrStatusEnum.PENDING.getValue(), task.getStatus());
            assertEquals(3, task.getTotalImages());
            assertEquals(0, task.getDoneImages());
            assertEquals(USER_A, task.getUserId());
            // items/imageUrls 初始化为空集合而非 null，前端不用做判空
            assertNotNull(task.getItems());
            assertNotNull(task.getImageUrls());
            assertTrue(task.getItems().isEmpty());
        }

        @Test
        @DisplayName("markRunning：状态转 running，原图短链写入")
        void markRunning() {
            String taskId = createTask(USER_A, 2);
            List<String> urls = List.of("/api/s/abc123", "/api/s/def456");

            taskManager.markRunning(taskId, urls);

            WordOcrTaskVO task = taskManager.get(taskId);
            assertEquals(WordOcrStatusEnum.RUNNING.getValue(), task.getStatus());
            assertEquals(urls, task.getImageUrls());
        }

        @Test
        @DisplayName("advance：进度可读，前端靠它显示「2/3 已识别」")
        void advanceProgress() {
            String taskId = createTask(USER_A, 3);
            taskManager.markRunning(taskId, List.of());

            taskManager.advance(taskId, 2);

            WordOcrTaskVO task = taskManager.get(taskId);
            assertEquals(2, task.getDoneImages());
            assertEquals(3, task.getTotalImages());
            // 推进进度不改状态，仍在识别中
            assertEquals(WordOcrStatusEnum.RUNNING.getValue(), task.getStatus());
        }

        @Test
        @DisplayName("markSucceed：状态 succeed，items 写入，进度被拉平到总数")
        void markSucceed() {
            String taskId = createTask(USER_A, 3);
            taskManager.markRunning(taskId, List.of());
            taskManager.advance(taskId, 1);

            taskManager.markSucceed(taskId, List.of(item("apple", "苹果"), item("benefit", "益处")));

            WordOcrTaskVO task = taskManager.get(taskId);
            assertEquals(WordOcrStatusEnum.SUCCEED.getValue(), task.getStatus());
            assertEquals(List.of("apple:苹果", "benefit:益处"), flat(task.getItems()));
            // 即便中间有图识别失败没走 advance，终态也必须是 3/3，否则前端进度条永远卡着
            assertEquals(task.getTotalImages(), task.getDoneImages());
            assertNull(task.getErrorMsg());
        }

        @Test
        @DisplayName("markFailed：状态 failed，errorMsg 原样保留给前端提示")
        void markFailed() {
            String taskId = createTask(USER_A, 1);
            taskManager.markRunning(taskId, List.of());

            taskManager.markFailed(taskId, "图片识别失败：模型调用超时");

            WordOcrTaskVO task = taskManager.get(taskId);
            assertEquals(WordOcrStatusEnum.FAILED.getValue(), task.getStatus());
            assertEquals("图片识别失败：模型调用超时", task.getErrorMsg());
            assertTrue(WordOcrStatusEnum.getEnumByValue(task.getStatus()).isFinal(), "failed 是终态，前端应停止轮询");
        }

        @Test
        @DisplayName("get 不存在的 taskId 返回 null 而不抛异常（TTL 过期是常态，不是异常）")
        void getMissingReturnsNull() {
            assertNull(taskManager.get("test-ocr-not-exist-" + UUID.randomUUID()));
            // 空参数同样走 null 分支，不能 NPE
            assertNull(taskManager.get(null));
            assertNull(taskManager.get("  "));
        }

        @Test
        @DisplayName("remove 后再 get 为 null（确认入库后主动清理，不等 TTL）")
        void removeThenGet() {
            String taskId = createTask(USER_A, 1);
            assertNotNull(taskManager.get(taskId));

            taskManager.remove(taskId);

            assertNull(taskManager.get(taskId));
        }

        @Test
        @DisplayName("save 必须带 TTL：识别结果是临时数据，绝不能在 Redis 里长住")
        void saveSetsTtl() {
            String taskId = createTask(USER_A, 1);

            long ttlMillis = redissonClient
                    .getBucket(RedisConstant.getWordOcrTaskKey(taskId), StringCodec.INSTANCE)
                    .remainTimeToLive();

            // remainTimeToLive 单位毫秒；-1 表示永不过期、-2 表示 key 不存在，两者都是 bug
            assertTrue(ttlMillis > 0, "任务 key 必须设置过期时间，实际 remainTimeToLive=" + ttlMillis);
            assertTrue(ttlMillis <= WordConstant.OCR_TASK_TTL_SECONDS * 1000,
                    "TTL 不应超过约定的 " + WordConstant.OCR_TASK_TTL_SECONDS + " 秒，实际 " + ttlMillis + "ms");

            // 每次写回都要续期，否则长任务跑到一半 key 就没了
            taskManager.advance(taskId, 1);
            long afterSave = redissonClient
                    .getBucket(RedisConstant.getWordOcrTaskKey(taskId), StringCodec.INSTANCE)
                    .remainTimeToLive();
            assertTrue(afterSave > 0, "写回后 TTL 应被续期");
        }

        @Test
        @DisplayName("序列化往返：中文释义不乱码、字段不丢（Redis 里存的是 JSON 串）")
        void itemsSurviveSerialization() {
            String taskId = createTask(USER_A, 2);
            List<WordOcrItemVO> items = List.of(
                    item("serendipity", "意外发现珍奇事物的本领"),
                    item("well-known", "众所周知的"),
                    item("don't", ""),
                    item("naive", null));
            taskManager.markRunning(taskId, List.of("/api/s/abc123"));
            taskManager.markSucceed(taskId, items);

            WordOcrTaskVO task = taskManager.get(taskId);

            assertEquals(4, task.getItems().size());
            assertEquals("意外发现珍奇事物的本领", task.getItems().get(0).getTranslation(), "中文必须原样回来");
            assertEquals("well-known", task.getItems().get(1).getWord(), "连字符不能被吃掉");
            assertEquals("don't", task.getItems().get(2).getWord(), "撇号不能被转义坏");
            assertNull(task.getItems().get(3).getTranslation(), "null 释义保持 null，不要凭空造值");
            // 其它字段一并回来
            assertEquals(USER_A, task.getUserId());
            assertEquals(2, task.getTotalImages());
            assertEquals(List.of("/api/s/abc123"), task.getImageUrls());
            assertNotNull(task.getCreateTime());
        }

        @Test
        @DisplayName("对已过期/不存在的任务做 mark 操作静默返回，不能反向复活一个残缺任务")
        void markOnMissingTaskIsNoop() {
            String ghost = "test-ocr-ghost-" + UUID.randomUUID();

            taskManager.markRunning(ghost, List.of("/api/s/x"));
            taskManager.advance(ghost, 1);
            taskManager.markSucceed(ghost, List.of(item("apple", "苹果")));
            taskManager.markFailed(ghost, "boom");

            assertNull(taskManager.get(ghost), "任务过期后任何写操作都不该重建 key");
        }
    }

    // ==================== 二、提交校验与越权 ====================

    @Nested
    @DisplayName("提交校验与任务归属")
    class SubmitAndAuth {

        @Test
        @DisplayName("一张图都不传：null 与空数组都要拦住（前端漏传是常见 bug）")
        void submitEmpty() {
            BusinessException nullEx = assertThrows(BusinessException.class,
                    () -> wordOcrService.submit(null, userA));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), nullEx.getCode());

            BusinessException emptyEx = assertThrows(BusinessException.class,
                    () -> wordOcrService.submit(new MultipartFile[0], userA));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), emptyEx.getCode());
        }

        @Test
        @DisplayName("超过 5 张：在读字节之前就拦掉，别让超量请求把内存撑爆")
        void submitTooManyImages() {
            MultipartFile[] files = new MultipartFile[WordConstant.MAX_OCR_IMAGES + 1];
            for (int i = 0; i < files.length; i++) {
                files[i] = photo("note" + i + ".jpg");
            }

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> wordOcrService.submit(files, userA));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
            assertTrue(ex.getMessage().contains(String.valueOf(WordConstant.MAX_OCR_IMAGES)),
                    "错误信息应告诉用户上限是几张，实际：" + ex.getMessage());
        }

        @Test
        @DisplayName("非图片后缀：.txt 直接拒绝，不能让任意文件流进 VL 与 MinIO")
        void submitNonImageSuffix() {
            MockMultipartFile txt = new MockMultipartFile(
                    "files", "words.txt", "text/plain", "apple 苹果".getBytes());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> wordOcrService.submit(new MultipartFile[]{txt}, userA));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("没有后缀名的文件同样拒绝，避免 getSuffix 返回空串时漏网")
        void submitNoSuffix() {
            MockMultipartFile noSuffix = new MockMultipartFile(
                    "files", "photo", "image/jpeg", jpeg(50, 50));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> wordOcrService.submit(new MultipartFile[]{noSuffix}, userA));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("超过 10MB：手机原图直出很容易超，要在入口给出明确提示")
        void submitOversizedImage() {
            byte[] huge = new byte[(int) WordConstant.MAX_OCR_UPLOAD_BYTES + 1];
            MockMultipartFile file = new MockMultipartFile("files", "huge.jpg", "image/jpeg", huge);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> wordOcrService.submit(new MultipartFile[]{file}, userA));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
            assertTrue(ex.getMessage().contains("MB"), "错误信息应告诉用户体积上限，实际：" + ex.getMessage());
        }

        @Test
        @DisplayName("内容为空的文件（0 字节）拒绝")
        void submitEmptyFile() {
            MockMultipartFile empty = new MockMultipartFile("files", "note.jpg", "image/jpeg", new byte[0]);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> wordOcrService.submit(new MultipartFile[]{empty}, userA));
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("★ 拿别人的 taskId 查：报 NOT_FOUND 而不是 NO_AUTH，不泄露任务是否存在")
        void getTaskOfAnotherUser() {
            String taskId = createTask(USER_A, 1);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> wordOcrService.getTask(taskId, userB));
            // 用 NOT_FOUND 而非 NO_AUTH：后者等于告诉攻击者「这个 id 是有效的」
            assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());

            // 本人查同一个 id 必须正常
            assertEquals(taskId, wordOcrService.getTask(taskId, userA).getTaskId());
        }

        @Test
        @DisplayName("查不存在的 taskId：与越权返回同一个错误码，两者对外不可区分")
        void getTaskNotExist() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> wordOcrService.getTask("test-ocr-not-exist-" + UUID.randomUUID(), userA));
            assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("taskId 为空/空白：参数错误")
        void getTaskBlankId() {
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(),
                    assertThrows(BusinessException.class, () -> wordOcrService.getTask(null, userA)).getCode());
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(),
                    assertThrows(BusinessException.class, () -> wordOcrService.getTask("   ", userA)).getCode());
        }
    }

    // ==================== 三、异步链路端到端（recognizer 已打桩）====================

    @Nested
    @DisplayName("异步识别链路端到端")
    class AsyncEndToEnd {

        @Test
        @DisplayName("单图成功：submit 立即返回 taskId，轮询到 succeed，items 与识别结果一致")
        void singleImageSucceed() {
            when(recognizer.recognize(any(byte[].class), any()))
                    .thenReturn(List.of(item("apple", "苹果"), item("benefit", "益处")));

            String taskId = wordOcrService.submit(new MultipartFile[]{photo("note.jpg")}, userA);
            createdTaskIds.add(taskId);
            assertNotNull(taskId, "submit 必须同步返回 taskId，用户不用干等 VL");

            WordOcrTaskVO task = awaitFinalTask(taskId);

            assertEquals(WordOcrStatusEnum.SUCCEED.getValue(), task.getStatus());
            assertEquals(List.of("apple:苹果", "benefit:益处"), flat(task.getItems()));
            assertEquals(1, task.getTotalImages());
            assertEquals(1, task.getDoneImages());
            assertNull(task.getErrorMsg());
            // 存图失败被有意吞掉（只为溯源），所以只断言字段存在，不断言里面有几条
            assertNotNull(task.getImageUrls());
            verify(recognizer, times(1)).recognize(any(byte[].class), any());
        }

        @Test
        @DisplayName("识别结果能通过 getTask 带鉴权读出，且别人依旧读不到")
        void succeedTaskReadableByOwnerOnly() {
            when(recognizer.recognize(any(byte[].class), any()))
                    .thenReturn(List.of(item("resilient", "有韧性的")));

            String taskId = wordOcrService.submit(new MultipartFile[]{photo("note.jpg")}, userA);
            createdTaskIds.add(taskId);
            awaitFinalTask(taskId);

            WordOcrTaskVO mine = wordOcrService.getTask(taskId, userA);
            assertEquals(List.of("resilient:有韧性的"), flat(mine.getItems()));
            assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(),
                    assertThrows(BusinessException.class, () -> wordOcrService.getTask(taskId, userB)).getCode());
        }

        @Test
        @DisplayName("★ 模型抛异常：任务落到 failed 且 errorMsg 非空，绝不能永远卡在 running")
        void recognizerThrowsLeadsToFailed() {
            when(recognizer.recognize(any(byte[].class), any()))
                    .thenThrow(new RuntimeException("模型调用超时"));

            String taskId = wordOcrService.submit(new MultipartFile[]{photo("note.jpg")}, userA);
            createdTaskIds.add(taskId);

            WordOcrTaskVO task = awaitFinalTask(taskId);

            assertEquals(WordOcrStatusEnum.FAILED.getValue(), task.getStatus());
            assertNotNull(task.getErrorMsg(), "失败必须带原因，否则前端只能干瞪眼");
            assertFalse(task.getErrorMsg().isBlank());
            assertTrue(task.getErrorMsg().contains("模型调用超时"), "原始失败原因应透出，实际：" + task.getErrorMsg());
        }

        @Test
        @DisplayName("★ 多图：结果跨图合并去重，先出现的空释义被后一张图补上")
        void multipleImagesMergedAndDeduped() {
            // 第一张只写了 apple 没写释义，第二张写了 apple 的释义 —— 这正是用户分几张拍的真实情形
            when(recognizer.recognize(any(byte[].class), any()))
                    .thenReturn(List.of(item("apple", ""), item("benefit", "益处")))
                    .thenReturn(List.of(item("apple", "苹果"), item("cherry", "樱桃")));

            String taskId = wordOcrService.submit(
                    new MultipartFile[]{photo("note1.jpg"), photo("note2.jpg")}, userA);
            createdTaskIds.add(taskId);

            WordOcrTaskVO task = awaitFinalTask(taskId);

            assertEquals(WordOcrStatusEnum.SUCCEED.getValue(), task.getStatus());
            assertEquals(2, task.getTotalImages());
            assertEquals(2, task.getDoneImages());
            // apple 只出现一次，且释义由第二张图补全
            assertEquals(List.of("apple:苹果", "benefit:益处", "cherry:樱桃"), flat(task.getItems()));
            verify(recognizer, times(2)).recognize(any(byte[].class), any());
        }

        @Test
        @DisplayName("多图里坏一张：另外几张的结果照样交付，不能因为一张废图让整批白拍")
        void oneImageFailsOthersStillDeliver() {
            when(recognizer.recognize(any(byte[].class), any()))
                    .thenThrow(new RuntimeException("这张糊了"))
                    .thenReturn(List.of(item("cherry", "樱桃")));

            String taskId = wordOcrService.submit(
                    new MultipartFile[]{photo("blurry.jpg"), photo("clear.jpg")}, userA);
            createdTaskIds.add(taskId);

            WordOcrTaskVO task = awaitFinalTask(taskId);

            assertEquals(WordOcrStatusEnum.SUCCEED.getValue(), task.getStatus(), "部分失败仍算成功");
            assertEquals(List.of("cherry:樱桃"), flat(task.getItems()));
            assertEquals(2, task.getDoneImages(), "失败的那张也要计入进度，否则进度条走不满");
        }

        @Test
        @DisplayName("模型一个词都没识别出来：任务是 succeed + 空 items，而非 failed（让前端提示「没认出来，手动录入吧」）")
        void emptyRecognitionIsStillSucceed() {
            when(recognizer.recognize(any(byte[].class), any())).thenReturn(List.of());

            String taskId = wordOcrService.submit(new MultipartFile[]{photo("blank.jpg")}, userA);
            createdTaskIds.add(taskId);

            WordOcrTaskVO task = awaitFinalTask(taskId);

            assertEquals(WordOcrStatusEnum.SUCCEED.getValue(), task.getStatus());
            assertNotNull(task.getItems());
            assertTrue(task.getItems().isEmpty());
        }

        @Test
        @DisplayName("并发提交的两个任务互不串台（同一个线程池里跑，taskId 必须完全隔离）")
        void concurrentTasksAreIsolated() {
            when(recognizer.recognize(any(byte[].class), any()))
                    .thenReturn(List.of(item("apple", "苹果")));

            String task1 = wordOcrService.submit(new MultipartFile[]{photo("a.jpg")}, userA);
            String task2 = wordOcrService.submit(new MultipartFile[]{photo("b.jpg")}, userB);
            createdTaskIds.add(task1);
            createdTaskIds.add(task2);

            WordOcrTaskVO t1 = awaitFinalTask(task1);
            WordOcrTaskVO t2 = awaitFinalTask(task2);

            assertEquals(USER_A, t1.getUserId());
            assertEquals(USER_B, t2.getUserId());
            assertEquals(WordOcrStatusEnum.SUCCEED.getValue(), t1.getStatus());
            assertEquals(WordOcrStatusEnum.SUCCEED.getValue(), t2.getStatus());
        }
    }
}
