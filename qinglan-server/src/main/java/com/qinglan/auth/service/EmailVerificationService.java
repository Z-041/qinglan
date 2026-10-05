package com.qinglan.auth.service;

import com.qinglan.auth.config.AuthProperties;
import com.qinglan.auth.dto.SendCodeResponse;
import com.qinglan.auth.mail.VerificationMailSender;
import com.qinglan.auth.store.VerificationCodeStore;
import com.qinglan.common.crypto.CryptoService;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

/**
 * 校园邮箱验证码的发送与校验。
 *
 * <p>域名白名单未配置时一律拒绝（fail-closed），避免生产环境误放行任意邮箱。</p>
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final VerificationCodeStore codeStore;
    private final VerificationMailSender mailSender;
    private final CryptoService cryptoService;
    private final AuthProperties authProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public SendCodeResponse sendCode(String email) {
        String normalized = normalize(email);
        assertDomainAllowed(normalized);
        String emailHash = cryptoService.hashEmail(normalized);
        if (!codeStore.tryAcquireSendSlot(emailHash, authProperties.sendInterval())) {
            throw new BizException(ErrorCode.CODE_SEND_TOO_FREQUENT);
        }
        String code = generateCode();
        codeStore.saveCode(emailHash, code, authProperties.codeTtl());
        mailSender.sendVerificationCode(normalized, code, authProperties.codeTtl());
        return new SendCodeResponse(
                (int) authProperties.codeTtl().toSeconds(),
                authProperties.exposeCode() ? code : null
        );
    }

    /**
     * 校验验证码，成功即一次性销毁，返回该邮箱的不可逆哈希。
     */
    public String verifyCode(String email, String code) {
        String normalized = normalize(email);
        assertDomainAllowed(normalized);
        String emailHash = cryptoService.hashEmail(normalized);

        String stored = codeStore.findCode(emailHash)
                .orElseThrow(() -> new BizException(ErrorCode.VERIFICATION_CODE_EXPIRED));

        if (!stored.equals(code)) {
            long attempts = codeStore.incrementAttempts(emailHash, authProperties.codeTtl());
            if (attempts >= authProperties.maxVerifyAttempts()) {
                codeStore.removeCode(emailHash);
                throw new BizException(ErrorCode.VERIFY_ATTEMPTS_EXCEEDED);
            }
            throw new BizException(ErrorCode.VERIFICATION_CODE_INVALID);
        }

        codeStore.removeCode(emailHash);
        return emailHash;
    }

    private void assertDomainAllowed(String email) {
        int at = email.indexOf('@');
        if (at < 0 || at == email.length() - 1) {
            throw new BizException(ErrorCode.EMAIL_DOMAIN_NOT_ALLOWED);
        }
        String domain = email.substring(at + 1);
        List<String> allowedDomains = authProperties.allowedEmailDomains().stream()
                .filter(configured -> !configured.isBlank())
                .map(configured -> configured.trim().toLowerCase(Locale.ROOT))
                .toList();
        if (!allowedDomains.contains(domain)) {
            throw new BizException(ErrorCode.EMAIL_DOMAIN_NOT_ALLOWED);
        }
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, authProperties.codeLength());
        return String.format("%0" + authProperties.codeLength() + "d", secureRandom.nextInt(bound));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
