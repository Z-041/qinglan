package com.qinglan.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 发布帖子请求。
 *
 * <p>{@code images} 是先调用上传接口拿到的地址列表，不是文件本身——文件走 multipart 单独上传，
 * 这样发帖请求保持轻量的 JSON（设计方案：图片直传 + 回调校验）。</p>
 */
public record CreatePostRequest(
        @NotNull(message = "请选择话题")
        Long topicId,

        @NotBlank(message = "正文不能为空")
        @Size(max = 2000, message = "正文不能超过 2000 字")
        String content,

        @Size(max = 200, message = "联系方式不能超过 200 字")
        String contact,

        @Size(max = 9, message = "最多上传 9 张图片")
        List<String> images
) {
}
