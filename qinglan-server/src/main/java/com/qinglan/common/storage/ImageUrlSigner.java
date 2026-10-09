package com.qinglan.common.storage;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * 私有图片签名 URL 的签发与校验。
 *
 * <p><b>为什么需要它</b>：受控图片下发端点按 accessor 判可见（T-27①），但浏览器 {@code <img>} 请求
 * <b>不带 Bearer 令牌</b>，等价于游客。于是「发帖页预览未绑定上传」「作者看自己待审/已下架帖的图」
 * 全部 404 破图。解法不是让前端带令牌（{@code <img>} 做不到），而是服务端在下发 DTO 时把私有图
 * 换成<b>短时签名 URL</b>——HTTP 缓存、断点续传、CSP {@code img-src 'self'} 全部照旧，前端零改动。</p>
 *
 * <p><b>签名形态</b>：{@code /uploads/...jpg?exp=<epochSeconds>&sig=<base64url(HMAC-SHA256(objectKey + ":" + exp, secret))>}。
 * 校验成功即放行，<b>不依赖 viewer 身份</b>；校验失败与「不存在」同码同状态 404，不泄露存在性。
 * 无签名（或只带其一）时回落到原有的身份判定路径，行为不变。</p>
 *
 * <p><b>密钥</b>：{@code qinglan.storage.image-signing-secret}。留空时回落 {@code qinglan.jwt.secret}
 * ——这是<b>权宜之计</b>，只为本地/测试省一次配置；两者用途不同，生产必须显式配置独立密钥，
 * 由 {@link com.qinglan.common.config.ProdEnvironmentGuard} 在 prod 下拒绝「只靠回落」的启动。
 * 本类不在日志/错误信息里打印密钥或签名值。</p>
 */
@Component
public class ImageUrlSigner {

    /** 签名有效期硬上限：即使配置写得更大，也收敛到 1 小时——限制签名被泄露/转发后的可用窗口 */
    static final Duration HARD_MAX_TTL = Duration.ofHours(1);

    /** 未显式配置时的默认有效期：够浏览器把图取回来即可，越短泄漏面越小 */
    static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** 签名载荷分隔符：{@code objectKey + ":" + exp} */
    private static final String SEPARATOR = ":";

    /** 回落密钥的配置键（与 JwtProperties 的 prefix 一致） */
    private static final String JWT_SECRET_KEY = "qinglan.jwt.secret";

    private final byte[] secret;
    private final Duration ttl;

    public ImageUrlSigner(StorageProperties properties, Environment environment) {
        String resolved = resolveSecret(properties, environment);
        if (resolved == null || resolved.isBlank()) {
            throw new IllegalStateException("图片签名密钥缺失：请配置 qinglan.storage.image-signing-secret"
                    + "（环境变量 QINGLAN_STORAGE_IMAGE_SIGNING_SECRET），或至少配置 qinglan.jwt.secret 作为回落");
        }
        this.secret = resolved.getBytes(StandardCharsets.UTF_8);
        Duration configuredTtl = properties == null ? null : properties.imageSigningTtl();
        Duration resolvedTtl = configuredTtl == null || configuredTtl.isZero() || configuredTtl.isNegative()
                ? DEFAULT_TTL
                : configuredTtl;
        this.ttl = resolvedTtl.compareTo(HARD_MAX_TTL) > 0 ? HARD_MAX_TTL : resolvedTtl;
    }

    /**
     * 给对外地址签发短时签名 URL。
     *
     * @param url 不含查询串的对外地址（{@code /uploads/...jpg}）
     * @return 追加了 {@code exp} 与 {@code sig} 的签名 URL
     */
    public String sign(String url) {
        long exp = Instant.now().plus(ttl).getEpochSecond();
        return signAt(url, exp);
    }

    /** 请求是否携带完整签名参数；只带 exp 或只带 sig 都看作「未签名」，走身份路径 */
    public boolean isSigned(String expRaw, String sigRaw) {
        return !isBlank(expRaw) && !isBlank(sigRaw);
    }

    /**
     * 校验签名。以下任一情况返回 false：exp/sig 解析失败、地址非法、已过期、exp 超出上限、签名不匹配。
     * 调用方对 false 一律按「不存在」处理（404），不区分原因，避免给出可区分信号。
     */
    public boolean verify(String path, String expRaw, String sigRaw) {
        Long exp = parseExp(expRaw);
        if (exp == null || isBlank(sigRaw)) {
            return false;
        }
        String objectKey = StorageUrl.objectKey(path).orElse(null);
        if (objectKey == null) {
            return false;
        }
        long now = Instant.now().getEpochSecond();
        if (exp < now) {
            return false;
        }
        // 拒绝远期签名：即便攻击者拿到了密钥，也无法签发一张「一签永逸」的链接
        if (exp > now + ttl.getSeconds()) {
            return false;
        }
        String expected = hmac(objectKey, exp);
        // 常量时间比较：避免按字节短路比较被用于逐字节试探签名（MessageDigest.isEqual 不做提前返回）
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), sigRaw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 用指定 exp 签发（包内可见，供单测构造「已过期 / 超上限」样本；生产路径只经 {@link #sign}）。
     */
    String signAt(String url, long expEpochSeconds) {
        String objectKey = StorageUrl.objectKey(url)
                .orElseThrow(() -> new IllegalArgumentException("无法为非法图片地址签名"));
        return StorageUrl.withSignature(url, expEpochSeconds, hmac(objectKey, expEpochSeconds));
    }

    private String hmac(String objectKey, long exp) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal((objectKey + SEPARATOR + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            // 只带算法信息，绝不把密钥或明文载荷带进异常
            throw new IllegalStateException("图片签名计算失败", ex);
        }
    }

    private Long parseExp(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String resolveSecret(StorageProperties properties, Environment environment) {
        String configured = properties == null ? null : properties.imageSigningSecret();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return environment == null ? null : environment.getProperty(JWT_SECRET_KEY);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
