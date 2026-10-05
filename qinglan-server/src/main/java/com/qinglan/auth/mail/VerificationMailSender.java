package com.qinglan.auth.mail;

import java.time.Duration;

/**
 * 验证码投递通道。
 *
 * <p>P0 只提供日志实现，接入真实 SMTP 时新增实现类替换，不改动业务代码。</p>
 */
public interface VerificationMailSender {

    void sendVerificationCode(String email, String code, Duration ttl);
}
