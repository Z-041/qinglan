package com.qinglan.post.entity;

import com.qinglan.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 预设话题。只读字典，由 Flyway 种入，运行期不允许用户自定义
 * （设计方案第三章：话题预设，不可自定义敏感词）。
 */
@Getter
@Entity
@Table(name = "topic", uniqueConstraints = @UniqueConstraint(name = "uk_topic_name", columnNames = "name"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Topic extends BaseEntity {

    @Column(name = "name", nullable = false, length = 20)
    private String name;

    /** 该话题的发布规则，发布页展示；无规则话题不允许上线 */
    @Column(name = "rules", nullable = false, length = 500)
    private String rules;

    /** 二手 / 失物招领 / 拼车 / 兼职为 true，发布时必须留联系方式 */
    @Column(name = "requires_contact", nullable = false)
    private boolean requiresContact;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
