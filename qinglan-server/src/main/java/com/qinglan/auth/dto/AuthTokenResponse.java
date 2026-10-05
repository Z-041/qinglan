package com.qinglan.auth.dto;

import com.qinglan.user.dto.UserProfileResponse;

public record AuthTokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserProfileResponse user
) {
}
