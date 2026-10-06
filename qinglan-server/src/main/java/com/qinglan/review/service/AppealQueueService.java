package com.qinglan.review.service;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.web.CursorCodec;
import com.qinglan.common.web.CursorPage;
import com.qinglan.post.entity.Post;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.review.dto.AdminAppealItemResponse;
import com.qinglan.review.entity.Appeal;
import com.qinglan.review.entity.AppealStatus;
import com.qinglan.review.repository.AppealRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 待复审申诉队列：供管理端工作台逐条复审（设计书第 113 行「换管理员复审」）。
 *
 * <p>只列 {@code PENDING}，按申诉时间升序（先到先审）。与 {@link ReviewQueueService} 同构：
 * 排序与分页交给数据库，一页之内用一次批量查询补齐帖子摘要，杜绝逐行查询造成的 N+1。</p>
 *
 * <p>队列里不出现申诉人的任何信息——申诉人就是作者，作者身份不出队列（不变量 #1 的引申）。</p>
 */
@Service
@RequiredArgsConstructor
public class AppealQueueService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int PREVIEW_LENGTH = 80;
    private static final String ELLIPSIS = "...";
    private static final String KEY_SEPARATOR = ":";
    /** 游标段数：申诉微秒 : 申诉 id */
    private static final int CURSOR_SEGMENTS = 2;

    private final AppealRepository appealRepository;
    private final PostRepository postRepository;

    @Transactional(readOnly = true)
    public CursorPage<AdminAppealItemResponse> queue(String cursorRaw) {
        AppealCursor cursor = parseCursor(cursorRaw);
        // 多取一条判断是否还有下一页，避免额外 count 查询
        List<Appeal> rows = appealRepository.findAdminPage(AppealStatus.PENDING,
                cursor.createdAt(), cursor.id(), PageRequest.of(0, DEFAULT_PAGE_SIZE + 1));

        boolean hasMore = rows.size() > DEFAULT_PAGE_SIZE;
        List<Appeal> page = hasMore ? List.copyOf(rows.subList(0, DEFAULT_PAGE_SIZE)) : rows;
        if (page.isEmpty()) {
            return CursorPage.empty();
        }

        Map<Long, Post> posts = loadPosts(page);
        List<AdminAppealItemResponse> items = page.stream()
                .map(appeal -> toItem(appeal, posts.get(appeal.getTargetId())))
                .toList();
        String nextCursor = hasMore ? formatCursor(page.get(page.size() - 1)) : null;
        return CursorPage.of(items, nextCursor);
    }

    // ---------------------------------------------------------------- 内部方法

    private Map<Long, Post> loadPosts(List<Appeal> page) {
        List<Long> ids = page.stream().map(Appeal::getTargetId).distinct().toList();
        return postRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Post::getId, post -> post));
    }

    private AdminAppealItemResponse toItem(Appeal appeal, Post post) {
        return new AdminAppealItemResponse(
                appeal.getId(),
                appeal.getTargetType(),
                appeal.getTargetId(),
                preview(post == null ? null : post.getContent()),
                post == null ? null : post.getStatus().name(),
                post == null ? null : post.getReviewReason(),
                appeal.getReason(),
                appeal.getCreatedAt());
    }

    private String preview(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= PREVIEW_LENGTH ? content : content.substring(0, PREVIEW_LENGTH) + ELLIPSIS;
    }

    /**
     * 解析游标。游标对客户端不透明，只要求原样回传；格式为「申诉微秒:申诉 id」，
     * 两段必须齐全且为正数，缺段、错位或非数字一律按非法游标拒绝。
     */
    private AppealCursor parseCursor(String raw) {
        if (raw == null || raw.isBlank()) {
            return new AppealCursor(null, null);
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
            return new AppealCursor(CursorCodec.decode(micros), id);
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.CURSOR_INVALID);
        }
    }

    private String formatCursor(Appeal appeal) {
        return CursorCodec.encode(appeal.getCreatedAt()) + KEY_SEPARATOR + appeal.getId();
    }

    /** 申诉队列游标：与 {@link AppealRepository#findAdminPage} 的排序键逐一对应 */
    private record AppealCursor(Instant createdAt, Long id) {
    }
}
