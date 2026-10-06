package com.qinglan.review.repository;

import com.qinglan.common.audit.AuditLog;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 审核上下文的跨表聚合。
 *
 * <p>为什么声明在 {@code review}：查询要同时碰审计表与帖子/评论，而 AGENTS.md 第三章白名单里
 * 只有 {@code review} 能同时读 {@code post} / {@code comment}；{@code common} 不得反向依赖业务模块。</p>
 *
 * <p>沿用审计仓储「只读不写」的做法：继承最小化的 {@link Repository}，编译期就不存在
 * delete / update 入口（不变量 #7）。</p>
 */
public interface ReviewContextRepository extends Repository<AuditLog, Long> {

    /**
     * 该作者被处置过的累计次数（含后来被申诉恢复的历史）。
     *
     * <p>取只增不改的审计表而非「当前是否处于下架态」：帖子可能已被恢复，按当前状态统计
     * 会把历史违规抹掉。只取处置类动作，不含 {@code POST_APPROVE} / {@code POST_RESTORE}。</p>
     */
    @Query("""
            select count(l) from AuditLog l
            where l.action in (com.qinglan.common.audit.AuditAction.POST_TAKEDOWN,
                               com.qinglan.common.audit.AuditAction.POST_AUTO_TAKEDOWN,
                               com.qinglan.common.audit.AuditAction.COMMENT_REMOVE)
              and ((l.targetType = com.qinglan.common.audit.AuditTargetType.POST
                    and l.targetId in (select p.id from Post p where p.authorId = :authorId))
                or (l.targetType = com.qinglan.common.audit.AuditTargetType.COMMENT
                    and l.targetId in (select c.id from Comment c where c.authorId = :authorId)))
            """)
    long countAuthorViolations(@Param("authorId") Long authorId);
}
