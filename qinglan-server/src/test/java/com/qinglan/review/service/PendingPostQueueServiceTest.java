package com.qinglan.review.service;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.storage.ImageUrlSigner;
import com.qinglan.common.storage.StorageProperties;
import com.qinglan.common.web.CursorPage;
import com.qinglan.post.entity.Post;
import com.qinglan.post.entity.PostImage;
import com.qinglan.post.entity.PostStatus;
import com.qinglan.post.repository.PostImageRepository;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.post.repository.UploadedFileRepository;
import com.qinglan.post.service.ImageUrlAssembler;
import com.qinglan.review.dto.PendingPostItemResponse;
import com.qinglan.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 待审帖子队列的单元测试。
 *
 * <p>盯四件事：只列待审状态（与看板口径一致）、排序与分页键完全交给数据库、队列里不出现作者
 * 身份（不变量 #1 的引申）、游标非法输入必须拒绝而不是静默从头开始。</p>
 */
@ExtendWith(MockitoExtension.class)
class PendingPostQueueServiceTest {

    private static final Instant BASE = Instant.parse("2026-10-06T00:00:00Z");

    /** 与看板 pendingPosts 同口径：两个状态都要捞 */
    private static final List<PostStatus> PENDING_STATUSES =
            List.of(PostStatus.PENDING, PostStatus.REVIEWING);

    /** 合法对外地址（签名器只接受本服务生成的形态） */
    private static final String IMAGE_A = "/uploads/2026/10/" + "a".repeat(32) + ".jpg";
    private static final String IMAGE_B = "/uploads/2026/10/" + "b".repeat(32) + ".jpg";
    private static final String IMAGE_C = "/uploads/2026/10/" + "c".repeat(32) + ".jpg";

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostImageRepository postImageRepository;

    @Mock
    private UploadedFileRepository uploadedFileRepository;

    private PendingPostQueueService pendingPostQueueService;

    @BeforeEach
    void setUp() {
        // 真实签名器：待审帖图必须真签，断言签名 URL 的形态才有意义（与 ImageUploadServiceTest 同法）
        ImageUrlSigner signer = new ImageUrlSigner(
                new StorageProperties(null, null, null, "unit-test-image-signing-secret", null),
                new MockEnvironment());
        // forPost 只用签名器，装配时不会触碰上传仓储
        ImageUrlAssembler assembler = new ImageUrlAssembler(signer, uploadedFileRepository, postRepository);
        pendingPostQueueService = new PendingPostQueueService(postRepository, postImageRepository, assembler);
    }

    @Test
    @DisplayName("没有待审帖子时返回空页与空游标，不产生多余的图片装配查询")
    void shouldReturnEmptyPageWhenQueueEmpty() {
        stubFirstPage(List.of());

        CursorPage<PendingPostItemResponse> page = pendingPostQueueService.queue(null);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        verifyNoInteractions(postImageRepository);
    }

    @Test
    @DisplayName("首页按「待审两个状态 + 空游标 + 多取一条」查库")
    void shouldQueryFirstPageWithPendingStatuses() {
        stubFirstPage(List.of());

        pendingPostQueueService.queue(null);

        verify(postRepository).findPendingPage(PENDING_STATUSES, null, null, PageRequest.of(0, 21));
    }

    @Test
    @DisplayName("装配摘要、状态、提交时间与待处理举报数")
    void shouldAssembleItem() {
        Post post = TestFixtures.pendingPost(7L, 100L, 1L, "测试正文");
        TestFixtures.setCreatedAt(post, BASE);
        TestFixtures.setReportCount(post, 2);
        stubFirstPage(List.of(post));

        PendingPostItemResponse item = pendingPostQueueService.queue(null).items().get(0);

        assertThat(item.postId()).isEqualTo(7L);
        assertThat(item.preview()).isEqualTo("测试正文");
        // 无图时是空列表而非 null（前端无需判空）
        assertThat(item.images()).isEmpty();
        assertThat(item.targetStatus()).isEqualTo(PostStatus.PENDING.name());
        assertThat(item.submittedAt()).isEqualTo(BASE);
        assertThat(item.reportCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("待审帖图按排序签为短时 URL（管理端 <img> 不带令牌也能取到），且整页只批量查一次（杜绝 N+1）")
    void shouldSignPrivateImagesInOrderWithSingleBatchQuery() {
        Post first = TestFixtures.pendingPost(7L, 100L, 1L, "带图帖子");
        TestFixtures.setCreatedAt(first, BASE);
        Post second = TestFixtures.pendingPost(8L, 101L, 1L, "另一帖子");
        TestFixtures.setCreatedAt(second, BASE.plusSeconds(1));
        stubFirstPage(List.of(first, second));
        when(postImageRepository.findByPostIdInOrderBySortOrderAsc(List.of(7L, 8L))).thenReturn(List.of(
                image(7L, IMAGE_A, 0),
                image(7L, IMAGE_B, 1),
                image(8L, IMAGE_C, 0)));

        List<PendingPostItemResponse> items = pendingPostQueueService.queue(null).items();

        // 待审帖图属私有：全部签为带 exp/sig 的短时 URL，且保持原排序与归属
        assertThat(items.get(0).images()).hasSize(2);
        assertThat(items.get(0).images().get(0)).startsWith(IMAGE_A + "?").contains("exp=").contains("sig=");
        assertThat(items.get(0).images().get(1)).startsWith(IMAGE_B + "?").contains("exp=").contains("sig=");
        assertThat(items.get(1).images()).hasSize(1);
        assertThat(items.get(1).images().get(0)).startsWith(IMAGE_C + "?").contains("exp=").contains("sig=");
        // 整页一次批量查询，而不是逐帖一次
        verify(postImageRepository, times(1)).findByPostIdInOrderBySortOrderAsc(anyList());
    }

    @Test
    @DisplayName("无图帖子返回空列表而不是 null")
    void shouldReturnEmptyImagesWhenPostHasNoImage() {
        Post post = TestFixtures.pendingPost(7L, 100L, 1L, "纯文字");
        TestFixtures.setCreatedAt(post, BASE);
        stubFirstPage(List.of(post));
        // 仓储默认返回空集合：该帖无图

        assertThat(pendingPostQueueService.queue(null).items().get(0).images()).isEmpty();
    }

    @Test
    @DisplayName("超长内容截断为 80 字加省略号")
    void shouldTruncateLongPreview() {
        Post post = TestFixtures.pendingPost(7L, 100L, 1L, "违".repeat(120));
        stubFirstPage(List.of(post));

        String preview = pendingPostQueueService.queue(null).items().get(0).preview();

        assertThat(preview).hasSize(83).endsWith("...");
    }

    @Test
    @DisplayName("多取一条判断还有下一页，游标为「提交微秒:帖子 id」")
    void shouldEmitOpaqueCursorForNextPage() {
        List<Post> posts = IntStream.range(0, 21)
                .mapToObj(i -> pendingPost(100L - i, BASE.plusSeconds(i)))
                .toList();
        stubFirstPage(posts);

        CursorPage<PendingPostItemResponse> page = pendingPostQueueService.queue(null);

        assertThat(page.items()).hasSize(20);
        // 第 20 行（下标 19）的复合键：提交微秒 : 帖子 id
        assertThat(page.nextCursor()).isEqualTo(BASE.plusSeconds(19).getEpochSecond() * 1_000_000L + ":81");
    }

    @Test
    @DisplayName("合法游标解析后按「提交微秒 + 帖子 id」交给仓储")
    void shouldPassParsedCursorToRepository() {
        long micros = BASE.plusSeconds(5).getEpochSecond() * 1_000_000L;
        when(postRepository.findPendingPage(PENDING_STATUSES, BASE.plusSeconds(5), 7L,
                PageRequest.of(0, 21))).thenReturn(List.of());

        pendingPostQueueService.queue(micros + ":7");

        verify(postRepository).findPendingPage(PENDING_STATUSES, BASE.plusSeconds(5), 7L,
                PageRequest.of(0, 21));
    }

    @Test
    @DisplayName("缺段、非数字、非正数的游标一律按非法游标拒绝，且不触碰仓储")
    void shouldRejectMalformedCursor() {
        assertThatThrownBy(() -> pendingPostQueueService.queue("not-a-cursor"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CURSOR_INVALID);
        // 段数不足（只有一段）同样拒绝，避免把旧格式游标当成合法输入静默按首页重来
        assertThatThrownBy(() -> pendingPostQueueService.queue("123"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CURSOR_INVALID);
        assertThatThrownBy(() -> pendingPostQueueService.queue("0:7"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CURSOR_INVALID);
        assertThatThrownBy(() -> pendingPostQueueService.queue("1:0"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CURSOR_INVALID);

        verifyNoInteractions(postRepository);
    }

    @Test
    @DisplayName("不变量 #1：待审队列响应不含作者身份字段")
    void shouldKeepAuthorIdentityOutOfQueueItem() {
        Set<String> componentNames = Arrays.stream(PendingPostItemResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertThat(componentNames).doesNotContain(
                "authorId", "studentId", "anonymousCode", "email", "reviewBy");
    }

    @Test
    @DisplayName("游标带微秒：边界行与下一行同毫秒时，续页仍按边界行的原始时刻")
    void shouldKeepMicrosWhenPostsShareTheSameMillisecond() {
        Instant boundary = BASE.plusSeconds(19).plusNanos(234_000);
        Instant next = BASE.plusSeconds(19).plusNanos(235_000);
        List<Post> posts = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            posts.add(pendingPost(100L - i, i < 19 ? BASE.plusSeconds(i) : (i == 19 ? boundary : next)));
        }
        stubFirstPage(posts);

        CursorPage<PendingPostItemResponse> page = pendingPostQueueService.queue(null);

        assertThat(posts.get(19).getCreatedAt().toEpochMilli())
                .isEqualTo(posts.get(20).getCreatedAt().toEpochMilli());
        assertThat(page.nextCursor())
                .isEqualTo((boundary.getEpochSecond() * 1_000_000L + 234L) + ":81");

        pendingPostQueueService.queue(page.nextCursor());

        verify(postRepository).findPendingPage(PENDING_STATUSES, boundary, 81L, PageRequest.of(0, 21));
    }

    // ------------------------------------------------------------------ 辅助

    private void stubFirstPage(List<Post> posts) {
        when(postRepository.findPendingPage(eq(PENDING_STATUSES), any(), any(), any(Pageable.class)))
                .thenReturn(posts);
    }

    private static Post pendingPost(long id, Instant createdAt) {
        Post post = TestFixtures.pendingPost(id, 100L, 1L, "测试正文");
        TestFixtures.setCreatedAt(post, createdAt);
        return post;
    }

    private static PostImage image(long postId, String url, int sortOrder) {
        return PostImage.of(postId, url, sortOrder);
    }
}
