package com.qinglan.post.dto;

/**
 * 话题。发布页选话题时展示 {@link #rules()}，并在 {@link #requiresContact()} 为真时要求填联系方式。
 */
public record TopicResponse(
        Long id,
        String name,
        String rules,
        boolean requiresContact
) {
}
