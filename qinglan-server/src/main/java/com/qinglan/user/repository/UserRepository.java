package com.qinglan.user.repository;

import com.qinglan.user.entity.User;
import com.qinglan.user.entity.UserRole;
import com.qinglan.user.entity.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailHash(String emailHash);

    boolean existsByEmailHash(String emailHash);

    boolean existsByStudentIdHash(String studentIdHash);

    /**
     * 管理端用户列表：键集分页 + 可选筛选。
     *
     * <p>排序键 {@code (created_at desc, id desc)}，游标是这两列的复合值；四个比较条件必须与
     * {@code order by} 严格同序。首页传 {@code cursorCreatedAt = null}，由第一个分支恒真兜住，
     * 因此首页与后续页共用同一条查询，不必维护两份近乎相同的 JPQL。</p>
     *
     * <p>刻意不提供身份维度（邮箱 / 学号）的查询条件：两者都是哈希或密文存储，按它们检索会
     * 形成「探测某学号是否存在」的侧信道（不变量 #3 的精神）。</p>
     *
     * <p>必须给 {@code Pageable}：管理端全量用户是无界集合，安全红线禁止无限制全表扫描。</p>
     */
    @Query("""
            select u from User u
            where (:status is null or u.status = :status)
              and (:role is null or u.role = :role)
              and (:minCredit is null or u.creditScore >= :minCredit)
              and (:maxCredit is null or u.creditScore <= :maxCredit)
              and (:createdFrom is null or u.createdAt >= :createdFrom)
              and (:createdTo is null or u.createdAt <= :createdTo)
              and (:cursorCreatedAt is null
                   or u.createdAt < :cursorCreatedAt
                   or (u.createdAt = :cursorCreatedAt and u.id < :cursorId))
            order by u.createdAt desc, u.id desc
            """)
    List<User> findAdminPage(
            @Param("status") UserStatus status,
            @Param("role") UserRole role,
            @Param("minCredit") Integer minCredit,
            @Param("maxCredit") Integer maxCredit,
            @Param("createdFrom") Instant createdFrom,
            @Param("createdTo") Instant createdTo,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable limit);

    long countByStatusIn(Collection<UserStatus> statuses);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

    /**
     * 委员会成员（管理员 + 评议员），按加入时间升序。
     *
     * <p>刻意不分页：成员数受 {@code UserRole#MAX_COMMITTEE_SIZE} 硬约束，是一个有界集合
     * （最多 6 行）。安全红线禁的是「无限制全表扫描」，不是「有界小集合」。</p>
     */
    List<User> findByRoleInOrderByCreatedAtAsc(Collection<UserRole> roles);

    long countByRoleIn(Collection<UserRole> roles);

    /**
     * 以唯一超管那一行为互斥锁，串行化「委员会名额」的授予。
     *
     * <p>为什么需要一个互斥点：上限校验是「先数再插」，而并发下两个请求可能都数到 5，于是把第 7 个人
     * 放进来。锁住行数为 1 的超管行，就把所有授予动作排成队（超管只有一个，它天然是单一的争用点）。
     * 计数本身不需要加锁——它已被锁在这段临界区里。</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = :role")
    List<User> findForUpdate(@Param("role") UserRole role);
}
