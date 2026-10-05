package com.qinglan.common.crypto;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 身份字段的加密与不可逆哈希。
 *
 * <p>设计约束（AGENTS.md 不变量 #3）：真实身份（学号）以 AES-256-GCM 加密存储，
 * 运营管理员不可见；{@link #decrypt} 仅供"依法配合调查"的专用通道调用，
 * 不得被任何管理后台接口、日志或导出功能引用。</p>
 */
@Service
public class CryptoService {

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int AES_KEY_LENGTH = 32;

    private final SecretKeySpec secretKey;
    private final byte[] pepper;
    private final SecureRandom secureRandom = new SecureRandom();

    public CryptoService(CryptoProperties properties) {
        byte[] keyBytes = Base64.getDecoder().decode(properties.aesKey());
        if (keyBytes.length != AES_KEY_LENGTH) {
            throw new IllegalStateException("qinglan.crypto.aes-key 必须是 32 字节的 Base64 编码");
        }
        this.secretKey = new SecretKeySpec(keyBytes, AES_ALGORITHM);
        this.pepper = properties.emailPepper().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 加密身份字段。每次调用使用随机 IV，同一明文两次加密结果不同，无法被直接比对。
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("待加密内容不能为空");
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("身份字段加密失败", ex);
        }
    }

    /**
     * 解密身份字段。仅限依法配合调查的专用通道使用。
     */
    public String decrypt(String ciphertext) {
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext);
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, GCM_IV_LENGTH);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plainText = cipher.doFinal(payload, GCM_IV_LENGTH, payload.length - GCM_IV_LENGTH);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("身份字段解密失败：数据可能被篡改", ex);
        }
    }

    /**
     * 邮箱的不可逆哈希，用于唯一索引与登录查找（不保存明文邮箱）。
     */
    public String hashEmail(String email) {
        return sha256Hex(normalize(email));
    }

    /**
     * 学号的不可逆哈希，用于"一人一学号"唯一约束，不保存明文。
     */
    public String hashStudentId(String studentId) {
        return sha256Hex(studentId.trim());
    }

    private String normalize(String value) {
        return value.trim().toLowerCase();
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(pepper);
            digest.update(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
