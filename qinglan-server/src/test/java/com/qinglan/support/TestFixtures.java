package com.qinglan.support;

import com.qinglan.common.persistence.BaseEntity;
import com.qinglan.post.entity.Post;
import com.qinglan.post.entity.ReviewState;
import com.qinglan.post.entity.RiskLevel;
import com.qinglan.post.entity.Topic;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;

/**
 * 测试夹具。
 *
 * <p>主帖与话题都没有 setter（这是刻意的：状态只能走状态机、话题运行期只读，见 AGENTS.md
 * 不变量 #5），单元测试无法用常规方式构造，只能借反射填字段。把这些反射逻辑收在一处，
 * 避免散落到各个测试类里。</p>
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    /** 已审核通过、对所有人可见的主帖 */
    public static Post approvedPost(Long id, Long authorId, Long topicId, String contact) {
        Post post = Post.draft(authorId, topicId, "测试正文", contact);
        post.submit(RiskLevel.LOW);
        post.transitionTo(new ReviewState.Reviewing(9L, Instant.now()));
        post.transitionTo(new ReviewState.Approved(9L, Instant.now()));
        setId(post, id);
        post.assignAnonymousCode("银杏#1a2b");
        return post;
    }

    /** 预设话题：运行期只读，由 Flyway 种入，测试里只能反射构造 */
    public static Topic topic(Long id, String name, boolean requiresContact) {
        Topic topic = newInstance(Topic.class);
        setId(topic, id);
        set(topic, "name", name);
        set(topic, "rules", name + "的发布规则");
        set(topic, "requiresContact", requiresContact);
        set(topic, "enabled", true);
        set(topic, "sortOrder", 1);
        return topic;
    }

    /** 主键由数据库生成，单元测试里 mock 掉仓储后需要手工补一个 */
    public static void setId(BaseEntity entity, Long id) {
        set(entity, "id", id);
    }

    /** 时间字段由 @PrePersist 填充，单元测试里 mock 掉仓储后需要手工补一个 */
    public static void setCreatedAt(BaseEntity entity, Instant createdAt) {
        set(entity, "createdAt", createdAt);
    }

    /** 已下架、带下架理由的主帖（申诉队列的典型目标） */
    public static Post takenDownPost(Long id, String content, String reviewReason) {
        Post post = Post.draft(7L, 1L, content, null);
        post.submit(RiskLevel.LOW);
        post.transitionTo(new ReviewState.Reviewing(9L, Instant.now()));
        post.transitionTo(new ReviewState.Approved(9L, Instant.now()));
        post.transitionTo(new ReviewState.TakenDown(9L, reviewReason, Instant.now()));
        setId(post, id);
        post.assignAnonymousCode("银杏#1a2b");
        return post;
    }

    /** 待处理举报数由 review 模块原子维护，单测里 mock 掉仓储后需要手工给一个初始值 */
    public static void setReportCount(Post post, int reportCount) {
        set(post, "reportCount", reportCount);
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (InvocationTargetException | NoSuchMethodException | InstantiationException
                 | IllegalAccessException ex) {
            throw new IllegalStateException("无法实例化 " + type.getName(), ex);
        }
    }

    private static void set(Object target, String fieldName, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ex) {
                type = type.getSuperclass();
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("无法写入字段 " + fieldName, ex);
            }
        }
        throw new IllegalStateException("字段不存在：" + fieldName);
    }
}
