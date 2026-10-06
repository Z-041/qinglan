package com.qinglan.auth.security;

import com.qinglan.auth.config.JwtProperties;
import com.qinglan.user.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * JWT 签发与校验。访问令牌与邮箱认证令牌使用同一密钥、不同 typ 声明，互不通用。
 */
@Service
public class JwtService {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_ROLE = "role";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_EMAIL_VERIFY = "email_verify";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey secretKey;
    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        byte[] secretBytes = Base64.getDecoder().decode(jwtProperties.secret());
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("qinglan.jwt.secret 至少需要 32 字节的 Base64 编码");
        }
        this.secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
        this.jwtProperties = jwtProperties;
    }

    public String issueAccessToken(Long userId, UserRole role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(jwtProperties.accessTokenTtl())))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public String issueEmailToken(String emailHash) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject(emailHash)
                // jti 一次性标识：绑定成功后按 jti 记录已消费，重复提交同一令牌直接拒绝
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TYPE, TYPE_EMAIL_VERIFY)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(jwtProperties.emailTokenTtl())))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public Optional<AccessTokenPayload> parseAccessToken(String token) {
        return parseClaims(token)
                .filter(claims -> TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class)))
                .flatMap(this::toPayload);
    }

    public Optional<EmailTokenPayload> parseEmailToken(String token) {
        return parseClaims(token)
                .filter(claims -> TYPE_EMAIL_VERIFY.equals(claims.get(CLAIM_TYPE, String.class)))
                .flatMap(claims -> Optional.ofNullable(claims.getId())
                        .map(jti -> new EmailTokenPayload(claims.getSubject(), jti)));
    }

    public long accessTokenTtlSeconds() {
        return jwtProperties.accessTokenTtl().toSeconds();
    }

    /** 邮箱认证令牌的有效期，用于设定一次性消费记录的存活时长 */
    public Duration emailTokenTtl() {
        return jwtProperties.emailTokenTtl();
    }

    private Optional<AccessTokenPayload> toPayload(Claims claims) {
        try {
            return Optional.of(new AccessTokenPayload(
                    Long.valueOf(claims.getSubject()),
                    UserRole.valueOf(claims.get(CLAIM_ROLE, String.class))
            ));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private Optional<Claims> parseClaims(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(jwtProperties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
