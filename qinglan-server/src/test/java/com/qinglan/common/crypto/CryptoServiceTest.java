package com.qinglan.common.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 身份字段加密与哈希的单元测试。
 */
class CryptoServiceTest {

    private static final String AES_KEY_BASE64 = "cWluZ2xhbi10ZXN0LWFlcy1rZXktMzItYnl0ZXMhISE=";
    private static final String PEPPER = "qinglan-test-pepper";

    private final CryptoService cryptoService = new CryptoService(new CryptoProperties(AES_KEY_BASE64, PEPPER));

    @Test
    @DisplayName("加密后可原样解密")
    void shouldRoundTripEncryption() {
        String plaintext = "2023010101";

        String ciphertext = cryptoService.encrypt(plaintext);

        assertThat(ciphertext).isNotEqualTo(plaintext);
        assertThat(cryptoService.decrypt(ciphertext)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("同一明文两次加密结果不同（随机 IV），但都能解回明文")
    void shouldProduceDifferentCiphertextForSamePlaintext() {
        String plaintext = "2023010101";

        String first = cryptoService.encrypt(plaintext);
        String second = cryptoService.encrypt(plaintext);

        assertThat(first).isNotEqualTo(second);
        assertThat(cryptoService.decrypt(first)).isEqualTo(plaintext);
        assertThat(cryptoService.decrypt(second)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("密文被篡改时解密失败，不返回错误明文")
    void shouldFailWhenCiphertextTampered() {
        String ciphertext = cryptoService.encrypt("2023010101");
        String tampered = ciphertext.substring(0, ciphertext.length() - 2) + "AA";

        assertThatThrownBy(() -> cryptoService.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("邮箱哈希大小写与首尾空格归一后一致")
    void shouldNormalizeEmailBeforeHashing() {
        assertThat(cryptoService.hashEmail("  Student@Qinglan.EDU.CN "))
                .isEqualTo(cryptoService.hashEmail("student@qinglan.edu.cn"));
    }

    @Test
    @DisplayName("邮箱/学号哈希为不可逆的 64 位十六进制，且不含原文")
    void shouldHashIrreversibly() {
        String emailHash = cryptoService.hashEmail("student@qinglan.edu.cn");
        String studentIdHash = cryptoService.hashStudentId("2023010101");

        assertThat(emailHash).hasSize(64).matches("[0-9a-f]{64}").doesNotContain("student");
        assertThat(studentIdHash).hasSize(64).matches("[0-9a-f]{64}").doesNotContain("2023010101");
    }
}
