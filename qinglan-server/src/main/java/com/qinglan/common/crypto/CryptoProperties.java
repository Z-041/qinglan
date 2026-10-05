package com.qinglan.common.crypto;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 身份字段加密与哈希配置。缺失即启动失败（fail-fast），避免明文身份字段落库。
 */
@Validated
@ConfigurationProperties(prefix = "qinglan.crypto")
public record CryptoProperties(
        @NotBlank String aesKey,
        @NotBlank String emailPepper
) {
}
