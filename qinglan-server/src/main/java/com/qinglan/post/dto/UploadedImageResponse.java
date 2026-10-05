package com.qinglan.post.dto;

/**
 * 上传成功后的图片地址，发帖时把它填进 {@link CreatePostRequest#images()}。
 */
public record UploadedImageResponse(String url) {
}
