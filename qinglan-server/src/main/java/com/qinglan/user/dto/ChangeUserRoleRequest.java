package com.qinglan.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 下放 / 收回管理权限的入参（P5 设计 7.3）。
 *
 * <p>理由必填：权限变更属高影响动作，审计里只留「谁在何时把谁改成了什么」而没有理由，
 * 事后无法回答「为什么是他」。</p>
 *
 * @param role 目标角色；类型是 {@link AssignableRole}，因此 {@code SUPER_ADMIN} 会在反序列化阶段被拒
 */
public record ChangeUserRoleRequest(
        @NotNull(message = "目标角色不能为空")
        AssignableRole role,

        @NotBlank(message = "理由不能为空")
        @Size(max = 200, message = "理由不超过 200 字")
        String reason
) {
}
