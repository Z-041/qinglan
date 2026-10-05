package com.qinglan.auth.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * 校园邮箱认证配置。
 *
 * @param codeStore            验证码存储实现：memory（单机）/ redis（集群）
 * @param codeTtl              验证码有效期
 * @param codeLength           验证码位数
 * @param maxVerifyAttempts    单个验证码允许的最大失败次数
 * @param sendInterval         同一邮箱两次发送的最小间隔
 * @param allowedEmailDomains  校园邮箱域名白名单，未配置即拒绝全部邮箱（fail-closed）
 * @param exposeCode           仅本地联调使用：为 true 时响应体回显验证码
 */
@Validated
@ConfigurationProperties(prefix = "qinglan.auth")
public record AuthProperties(
        @NotBlank String codeStore,
        @NotNull Duration codeTtl,
        @Min(4) @Max(8) int codeLength,
        @Min(1) int maxVerifyAttempts,
        @NotNull Duration sendInterval,
        @NotNull List<String> allowedEmailDomains,
        boolean exposeCode
) {
}
