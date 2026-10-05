package com.qinglan.common.interaction;

import com.qinglan.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 点赞记录，多态目标。
 *
 * <p>{@code UNIQUE(target_type, target_id, user_id)} 保证同一用户对同一目标只有一条，
 * 这既让点赞天然幂等，也是后续举报防刷量（不变量 #4）要复用的思路。</p>
 */
@Getter
@Entity
@Table(
        name = "like_record",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_like_target_user",
                columnNames = {"target_type", "target_id", "user_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LikeRecord extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private LikeTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    public static LikeRecord of(LikeTargetType targetType, Long targetId, Long userId) {
        LikeRecord record = new LikeRecord();
        record.targetType = targetType;
        record.targetId = targetId;
        record.userId = userId;
        return record;
    }
}
