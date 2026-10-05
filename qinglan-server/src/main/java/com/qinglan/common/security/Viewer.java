package com.qinglan.common.security;

/**
 * 当前访问者身份。
 *
 * <p>只读接口（首页信息流、帖子详情）对游客开放，但仍需区分「游客」与「已认证学生」——
 * 联系方式只对已认证者返回（不变量 #2）。放在 common 而非 post 模块，使 post 与 comment
 * 都能使用同一个类型，不必互相依赖。</p>
 *
 * @param userId 当前用户 id；游客为 {@code null}。对外接口不得回传该值（不变量 #1）
 */
public record Viewer(Long userId) {

    /** 游客：未携带有效访问令牌 */
    public static Viewer anonymous() {
        return new Viewer(null);
    }

    public boolean authenticated() {
        return userId != null;
    }

    /** 判断某个作者是否就是当前访问者，用于计算「是否本人」而非暴露 authorId */
    public boolean is(Long authorId) {
        return userId != null && userId.equals(authorId);
    }
}
