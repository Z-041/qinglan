package com.qinglan.auth.dto;

/**
 * @param devCode 仅当 qinglan.auth.expose-code=true（本地联调）时返回，生产恒为 null
 */
public record SendCodeResponse(int expiresInSeconds, String devCode) {
}
