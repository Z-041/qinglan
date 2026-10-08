package com.qinglan.user.dto;

import java.util.List;

/**
 * 当前管理端身份（P5 设计 7.2）。
 *
 * <p>为什么不并入学生端的 {@code UserProfileResponse}：那是双端共用 DTO，把管理端的权限点塞进去
 * 等于把管理概念泄给学生端（前端会以为学生也有权限点）。管理端自己拉一个。</p>
 *
 * @param userId      当前用户 id
 * @param role        数据库里的**当前**角色，不是令牌里的——令牌在签发时固化，被收回或降级后
 *                    旧令牌仍带着旧角色；前端要显示的是「你现在是什么」，不是「你签到时是什么」
 * @param permissions 该角色对应的权限点名称，前端据此渲染菜单与按钮（顺序稳定，便于测试断言）
 */
public record AdminMeResponse(Long userId, String role, List<String> permissions) {
}
