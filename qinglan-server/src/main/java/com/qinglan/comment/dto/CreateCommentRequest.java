package com.qinglan.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发表评论请求。
 *
 * @param parentId 一级评论传 null；回复时传同帖下一级评论的 id（只支持两级）
 */
public record CreateCommentRequest(
        @NotBlank(message = "评论不能为空")
        @Size(max = 500, message = "评论不能超过 500 字")
        String content,

        Long parentId
) {
}
