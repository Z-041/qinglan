package com.qinglan.post.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 「批量换取可用图片地址」请求（草稿恢复等场景）。
 *
 * <p>入参是一组对外图片地址（通常是草稿里保存的原 URL，不带过期签名）；服务端按当前 viewer
 * 逐个判定并按需签发短时签名 URL。上限与单帖图片上限同量级，避免用小请求放大成大量查库。</p>
 */
public record ImageUrlRefreshRequest(
        @NotEmpty(message = "请提供要换取的图片地址")
        @Size(max = 20, message = "一次最多换取 20 张图片")
        List<String> urls
) {
}
