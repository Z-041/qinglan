package com.qinglan.common.storage;

import java.util.Optional;

/**
 * 允许上传的图片类型。
 *
 * <p>按文件头字节判定真实类型，<b>不信任</b> Content-Type 与文件名——两者都由客户端提供，
 * 把脚本改名成 {@code .jpg}、或伪造 {@code Content-Type: image/jpeg} 都能骗过扩展名检查。
 * 扩展名同样由本枚举给出，绝不取自用户输入，避免路径穿越（AGENTS.md 第九章）。</p>
 */
public enum ImageType {

    JPEG("jpg"),
    PNG("png"),
    WEBP("webp");

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final String RIFF_MAGIC = "RIFF";
    private static final String WEBP_MAGIC = "WEBP";

    private final String extension;

    ImageType(String extension) {
        this.extension = extension;
    }

    /** 落盘用的扩展名，不带点 */
    public String extension() {
        return extension;
    }

    /** 识别真实图片类型；不是受支持的图片时返回空 */
    public static Optional<ImageType> detect(byte[] content) {
        if (content == null || content.length < 12) {
            return Optional.empty();
        }
        if (startsWith(content, PNG_MAGIC)) {
            return Optional.of(PNG);
        }
        if ((content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if (startsWithAscii(content, 0, RIFF_MAGIC) && startsWithAscii(content, 8, WEBP_MAGIC)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        if (content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithAscii(byte[] content, int offset, String ascii) {
        if (content.length < offset + ascii.length()) {
            return false;
        }
        for (int i = 0; i < ascii.length(); i++) {
            if (content[offset + i] != (byte) ascii.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
