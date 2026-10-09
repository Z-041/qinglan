package com.qinglan.review.service;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.web.CursorCodec;
import com.qinglan.common.web.CursorPage;
import com.qinglan.common.web.TextPreview;
import com.qinglan.post.entity.Post;
import com.qinglan.post.entity.PostImage;
import com.qinglan.post.entity.PostStatus;
import com.qinglan.post.repository.PostImageRepository;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.post.service.ImageUrlAssembler;
import com.qinglan.review.dto.PendingPostItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 待审帖子队列：先审后发（设计方案第一章红线 2）下，唯一能把 {@code PENDING} 帖子捞给管理端的地方。
 *
 * <p>存在理由：{@link ReviewQueueService} 按「被举报目标」聚合，新帖没有人举报、因此永远不会
 * 出现在工作台上，管理端也就无从放行——队列有了，入口不能没有。</p>
 *
 * <p>与另外两个队列同构：排序与分页全交给数据库（{@code created_at asc, id asc}，先到先审），
 * 内存只做装配。行本身来自帖子实体，图片则按本页帖子 id 一次性批量查出——放行前必须看得见图，
 * 否则图片随帖直接放行。</p>
 *
 * <p><b>图片地址按帖子状态签发</b>：待审帖图是私有图，而管理端 {@code <img>} 请求不带 Bearer 令牌
 * （等价游客），干净 URL 只会 404 破图——管理员看不到图就无法审图。故装配时统一经
 * {@link ImageUrlAssembler#forPost} 处理：待审（非 APPROVED）签为短时签名 URL，前端零改动即可显示。
 * 状态随帖子实体已在手，不额外查库，也不产生 N+1。</p>
 */
@Service
@RequiredArgsConstructor
public class PendingPostQueueService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int PREVIEW_LENGTH = 80;
    private static final String KEY_SEPARATOR = ":";
    /** 游标段数：提交微秒 : 帖子 id */
    private static final int CURSOR_SEGMENTS = 2;

    /**
     * 与看板 {@code content.pendingPosts} 同口径（{@code PENDING} + {@code REVIEWING}）：
     * 两处若不一致，管理员会看到「看板说 3 条、队列只列出 2 条」这种对不上的情况。
     */
    private static final List<PostStatus> PENDING_STATUSES =
            List.of(PostStatus.PENDING, PostStatus.REVIEWING);

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final ImageUrlAssembler imageUrlAssembler;

    @Transactional(readOnly = true)
    public CursorPage<PendingPostItemResponse> queue(String cursorRaw) {
        PendingPostCursor cursor = parseCursor(cursorRaw);
        // 多取一条判断是否还有下一页，避免额外 count 查询
        List<Post> rows = postRepository.findPendingPage(PENDING_STATUSES,
                cursor.createdAt(), cursor.id(), PageRequest.of(0, DEFAULT_PAGE_SIZE + 1));

        boolean hasMore = rows.size() > DEFAULT_PAGE_SIZE;
        List<Post> page = hasMore ? List.copyOf(rows.subList(0, DEFAULT_PAGE_SIZE)) : rows;
        if (page.isEmpty()) {
            return CursorPage.empty();
        }

        Map<Long, List<String>> images = loadImages(page);
        List<PendingPostItemResponse> items = page.stream()
                .map(post -> toItem(post, images.getOrDefault(post.getId(), List.of())))
                .toList();
        String nextCursor = hasMore ? formatCursor(page.get(page.size() - 1)) : null;
        return CursorPage.of(items, nextCursor);
    }

    // ---------------------------------------------------------------- 内部方法

    /**
     * 一页之内按帖子 id 批量取图：一次 {@code findByPostIdInOrderBySortOrderAsc} 覆盖整页，
     * 杜绝逐行查询造成的 N+1。仓储已按 {@code sort_order} 升序返回，分组时用有序 Map 保序。
     */
    private Map<Long, List<String>> loadImages(List<Post> page) {
        List<Long> postIds = page.stream().map(Post::getId).toList();
        Map<Long, List<String>> images = new LinkedHashMap<>();
        for (PostImage image : postImageRepository.findByPostIdInOrderBySortOrderAsc(postIds)) {
            images.computeIfAbsent(image.getPostId(), ignored -> new ArrayList<>()).add(image.getUrl());
        }
        return images;
    }

    private PendingPostItemResponse toItem(Post post, List<String> images) {
        return new PendingPostItemResponse(
                post.getId(),
                preview(post.getContent()),
                // 待审帖图属私有：签为短时签名 URL，管理端 <img> 不带令牌也能取到（不另写一份判断）
                imageUrlAssembler.forPost(post.getStatus(), images),
                post.getStatus().name(),
                post.getCreatedAt(),
                post.getReportCount());
    }

    private String preview(String content) {
        return TextPreview.of(content, PREVIEW_LENGTH);
    }

    /**
     * 解析游标。游标对客户端不透明，只要求原样回传；格式为「提交微秒:帖子 id」，
     * 两段必须齐全且为正数，缺段、错位或非数字一律按非法游标拒绝。
     */
    private PendingPostCursor parseCursor(String raw) {
        if (raw == null || raw.isBlank()) {
            return new PendingPostCursor(null, null);
        }
        String[] parts = raw.split(KEY_SEPARATOR);
        if (parts.length != CURSOR_SEGMENTS) {
            throw new BizException(ErrorCode.CURSOR_INVALID);
        }
        try {
            long micros = Long.parseLong(parts[0]);
            long id = Long.parseLong(parts[1]);
            if (micros <= 0 || id <= 0) {
                throw new NumberFormatException("游标各段不合法");
            }
            return new PendingPostCursor(CursorCodec.decode(micros), id);
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.CURSOR_INVALID);
        }
    }

    private String formatCursor(Post post) {
        return CursorCodec.encode(post.getCreatedAt()) + KEY_SEPARATOR + post.getId();
    }

    /** 待审队列游标：与 {@link PostRepository#findPendingPage} 的排序键逐一对应 */
    private record PendingPostCursor(Instant createdAt, Long id) {
    }
}
