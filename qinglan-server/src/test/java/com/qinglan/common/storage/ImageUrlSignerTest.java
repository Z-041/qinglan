package com.qinglan.common.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 私有图片签名 URL 的签发与校验（T-30）。
 *
 * <p>覆盖：正确签名放行、篡改/过期/超上限一律拒绝、缺参数按未签名、TTL 硬上限收敛、密钥回落。
 * 全部为纯单元测试（不依赖 Spring 上下文），签名行为真实执行。</p>
 */
class ImageUrlSignerTest {

    private static final String KEY = "2026/10/" + "a".repeat(32) + ".jpg";
    private static final String IMAGE_URL = StorageUrl.publicUrl(KEY);
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String SECRET = "unit-test-image-signing-secret";

    private final ImageUrlSigner signer = signer(SECRET, TTL);

    @Test
    @DisplayName("正确签名：签出的 URL 带 exp/sig，且校验通过")
    void shouldSignAndVerify() {
        String signed = signer.sign(IMAGE_URL);

        assertThat(signed).startsWith(IMAGE_URL + "?").contains("exp=").contains("sig=");
        assertThat(signer.isSigned(param(signed, "exp"), param(signed, "sig"))).isTrue();
        assertThat(signer.verify(IMAGE_URL, param(signed, "exp"), param(signed, "sig"))).isTrue();
    }

    @Test
    @DisplayName("篡改签名：签名值改动一个字符即拒绝（常量时间比较，正确与近似错误都判对）")
    void shouldRejectTamperedSignature() {
        String signed = signer.sign(IMAGE_URL);
        String exp = param(signed, "exp");
        String sig = param(signed, "sig");

        char last = sig.charAt(sig.length() - 1);
        String nearMiss = sig.substring(0, sig.length() - 1) + (last == 'A' ? 'B' : 'A');
        assertThat(signer.verify(IMAGE_URL, exp, nearMiss)).isFalse();
        assertThat(signer.verify(IMAGE_URL, exp, "totally-wrong-signature")).isFalse();
        // 常量时间比较的正确性：相等必为 true、不等必为 false（实现用 MessageDigest.isEqual，不做提前返回）
        assertThat(signer.verify(IMAGE_URL, exp, sig)).isTrue();
    }

    @Test
    @DisplayName("过期签名：exp 早于当前时间即拒绝")
    void shouldRejectExpiredSignature() {
        long past = Instant.now().getEpochSecond() - 5;
        String expired = signer.signAt(IMAGE_URL, past);

        assertThat(signer.verify(IMAGE_URL, param(expired, "exp"), param(expired, "sig"))).isFalse();
    }

    @Test
    @DisplayName("exp 超上限：签名合法但有效期超过配置即拒绝（拒绝远期签名）")
    void shouldRejectSignatureBeyondCap() {
        long tooFar = Instant.now().getEpochSecond() + Duration.ofHours(2).toSeconds();
        String farFuture = signer.signAt(IMAGE_URL, tooFar);

        assertThat(signer.verify(IMAGE_URL, param(farFuture, "exp"), param(farFuture, "sig"))).isFalse();
    }

    @Test
    @DisplayName("TTL 硬上限：配置超过 1 小时时，签发有效期收敛到 1 小时")
    void shouldClampTtlToHardMax() {
        ImageUrlSigner longLived = signer(SECRET, Duration.ofHours(10));
        long now = Instant.now().getEpochSecond();

        long exp = Long.parseLong(param(longLived.sign(IMAGE_URL), "exp"));

        assertThat(exp).isBetween(now + Duration.ofMinutes(55).toSeconds(), now + 3600);
    }

    @Test
    @DisplayName("缺参数按未签名处理：只带 exp 或只带 sig 都不算签名，校验一律拒绝")
    void shouldTreatPartialParamsAsUnsigned() {
        String signed = signer.sign(IMAGE_URL);
        String exp = param(signed, "exp");
        String sig = param(signed, "sig");

        assertThat(signer.isSigned(exp, null)).isFalse();
        assertThat(signer.isSigned(null, sig)).isFalse();
        assertThat(signer.isSigned(null, null)).isFalse();
        assertThat(signer.isSigned("", "")).isFalse();
        assertThat(signer.isSigned(exp, sig)).isTrue();
        // exp 非法数字同样拒绝
        assertThat(signer.verify(IMAGE_URL, "not-a-number", sig)).isFalse();
    }

    @Test
    @DisplayName("签名与对象绑定：为 A 图签的名不能用于 B 图")
    void shouldBindSignatureToObjectKey() {
        String signed = signer.sign(IMAGE_URL);
        String otherUrl = StorageUrl.publicUrl("2026/10/" + "b".repeat(32) + ".jpg");

        assertThat(signer.verify(otherUrl, param(signed, "exp"), param(signed, "sig"))).isFalse();
    }

    @Test
    @DisplayName("存储地址解析忽略签名查询串：objectKey/contentType 只取路径段")
    void shouldIgnoreQueryStringWhenParsing() {
        String signed = signer.sign(IMAGE_URL);

        assertThat(StorageUrl.objectKey(signed)).contains(KEY);
        assertThat(StorageUrl.contentType(signed)).contains("image/jpeg");
    }

    @Test
    @DisplayName("密钥：显式配置优先；为空时回落到 qinglan.jwt.secret；两者皆空则构造失败")
    void shouldResolveSecret() {
        StorageProperties explicit =
                new StorageProperties(null, null, null, "explicit-signing-secret", null);
        assertThat(new ImageUrlSigner(explicit, new MockEnvironment()).sign(IMAGE_URL)).contains("sig=");

        StorageProperties blank = new StorageProperties(null, null, null, "  ", null);
        MockEnvironment env = new MockEnvironment().withProperty("qinglan.jwt.secret", "fallback-secret");
        assertThat(new ImageUrlSigner(blank, env).sign(IMAGE_URL)).contains("sig=");

        assertThatThrownBy(() -> new ImageUrlSigner(blank, new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("image-signing-secret");
    }

    /** 用显式密钥与有效期构造签名器（与 Spring 装配同一条构造路径） */
    private static ImageUrlSigner signer(String secret, Duration ttl) {
        return new ImageUrlSigner(new StorageProperties(null, null, null, secret, ttl), new MockEnvironment());
    }

    /** 取签名 URL 的查询参数值 */
    private static String param(String signedUrl, String name) {
        String query = signedUrl.substring(signedUrl.indexOf('?') + 1);
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv[0].equals(name)) {
                return kv[1];
            }
        }
        throw new IllegalArgumentException("缺少参数：" + name);
    }
}
