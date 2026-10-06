package com.qinglan.review.service;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.web.CursorPage;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.review.dto.AdminAppealItemResponse;
import com.qinglan.review.entity.Appeal;
import com.qinglan.review.entity.AppealStatus;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.repository.AppealRepository;
import com.qinglan.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 申诉队列的单元测试。
 *
 * <p>盯四件事：排序与分页键完全交给数据库（内存只装配）、只列待复审、队列里不出现申诉人
 * 身份（不变量 #1 的引申）、游标非法输入必须拒绝而不是静默从头开始。</p>
 */
@ExtendWith(MockitoExtension.class)
class AppealQueueServiceTest {

    private static final Instant BASE = Instant.parse("2026-10-06T00:00:00Z");

    @Mock
    private AppealRepository appealRepository;

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private AppealQueueService appealQueueService;

    @Test
    @DisplayName("没有待复审申诉时返回空页，不产生多余的装配查询")
    void shouldReturnEmptyPageWhenQueueEmpty() {
        stubFirstPage(List.of());

        CursorPage<AdminAppealItemResponse> page = appealQueueService.queue(null);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        verifyNoInteractions(postRepository);
    }

    @Test
    @DisplayName("装配帖子摘要、当前状态与原下架理由")
    void shouldAssemblePostContext() {
        stubFirstPage(List.of(appeal(1L, 7L, BASE)));
        when(postRepository.findAllById(List.of(7L)))
                .thenReturn(List.of(TestFixtures.takenDownPost(7L, "测试正文", "ABUSE: 人身攻击")));

        AdminAppealItemResponse item = appealQueueService.queue(null).items().get(0);

        assertThat(item.appealId()).isEqualTo(1L);
        assertThat(item.targetType()).isEqualTo(ReviewTargetType.POST);
        assertThat(item.targetId()).isEqualTo(7L);
        assertThat(item.preview()).isEqualTo("测试正文");
        assertThat(item.targetStatus()).isEqualTo("TAKEN_DOWN");
        assertThat(item.takenDownReason()).isEqualTo("ABUSE: 人身攻击");
        assertThat(item.appealReason()).isEqualTo("误判");
        assertThat(item.appealedAt()).isEqualTo(BASE);
    }

    @Test
    @DisplayName("超长内容截断为 80 字加省略号")
    void shouldTruncateLongPreview() {
        stubFirstPage(List.of(appeal(1L, 7L, BASE)));
        when(postRepository.findAllById(List.of(7L)))
                .thenReturn(List.of(TestFixtures.takenDownPost(7L, "违".repeat(120), "ABUSE")));

        String preview = appealQueueService.queue(null).items().get(0).preview();

        assertThat(preview).hasSize(83).endsWith("...");
    }

    @Test
    @DisplayName("帖子已被删除时该项仍返回，摘要为空而不是整页失败")
    void shouldTolerateMissingPost() {
        stubFirstPage(List.of(appeal(1L, 404L, BASE)));
        // 仓储默认返回空集合：目标已不存在

        AdminAppealItemResponse item = appealQueueService.queue(null).items().get(0);

        assertThat(item.targetId()).isEqualTo(404L);
        assertThat(item.preview()).isNull();
        assertThat(item.targetStatus()).isNull();
        assertThat(item.takenDownReason()).isNull();
    }

    @Test
    @DisplayName("多取一条判断还有下一页，游标为「申诉毫秒:申诉 id」")
    void shouldEmitOpaqueCursorForNextPage() {
        List<Appeal> appeals = IntStream.range(0, 21)
                .mapToObj(i -> appeal(100L - i, 7L, BASE.plusSeconds(i)))
                .toList();
        stubFirstPage(appeals);
        when(postRepository.findAllById(any())).thenReturn(List.of());

        CursorPage<AdminAppealItemResponse> page = appealQueueService.queue(null);

        assertThat(page.items()).hasSize(20);
        // 第 20 行（下标 19）的复合键：申诉毫秒 : 申诉 id
        assertThat(page.nextCursor()).isEqualTo(BASE.plusSeconds(19).toEpochMilli() + ":81");
    }

    @Test
    @DisplayName("合法游标解析后按「申诉毫秒 + 申诉 id」交给仓储")
    void shouldPassParsedCursorToRepository() {
        long millis = BASE.plusSeconds(5).toEpochMilli();
        when(appealRepository.findAdminPage(AppealStatus.PENDING,
                Instant.ofEpochMilli(millis), 7L, PageRequest.of(0, 21))).thenReturn(List.of());

        appealQueueService.queue(millis + ":7");

        verify(appealRepository).findAdminPage(AppealStatus.PENDING,
                Instant.ofEpochMilli(millis), 7L, PageRequest.of(0, 21));
    }

    @Test
    @DisplayName("缺段、非数字、非正数的游标一律按非法游标拒绝，且不触碰仓储")
    void shouldRejectMalformedCursor() {
        assertThatThrownBy(() -> appealQueueService.queue("not-a-cursor"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CURSOR_INVALID);
        // 段数不足（只有一段）同样拒绝，避免把旧格式游标当成合法输入静默按首页重来
        assertThatThrownBy(() -> appealQueueService.queue("123"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CURSOR_INVALID);
        assertThatThrownBy(() -> appealQueueService.queue("0:7"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CURSOR_INVALID);
        assertThatThrownBy(() -> appealQueueService.queue("1:0"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CURSOR_INVALID);

        verifyNoInteractions(appealRepository, postRepository);
    }

    @Test
    @DisplayName("不变量 #1：申诉队列响应不含申诉人（即作者）身份字段")
    void shouldKeepAppellantIdentityOutOfQueueItem() {
        Set<String> componentNames = Arrays.stream(AdminAppealItemResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertThat(componentNames).doesNotContain(
                "appellantId", "authorId", "studentId", "anonymousCode", "email", "reviewedBy");
    }

    // ------------------------------------------------------------------ 辅助

    private void stubFirstPage(List<Appeal> appeals) {
        when(appealRepository.findAdminPage(eq(AppealStatus.PENDING), any(), any(), any(Pageable.class)))
                .thenReturn(appeals);
    }

    private static Appeal appeal(long id, long targetId, Instant createdAt) {
        Appeal appeal = Appeal.submit(ReviewTargetType.POST, targetId, 100L, "误判");
        TestFixtures.setId(appeal, id);
        TestFixtures.setCreatedAt(appeal, createdAt);
        return appeal;
    }
}
