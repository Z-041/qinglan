package com.qinglan.common.storage;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 上传地址的唯一格式定义与解析。
 *
 * <p>放宽到只看 {@code /uploads/} 前缀是不够的：{@code /uploads/anything}、{@code /uploads/../../x}
 * 同样以此开头。对外只接受**本服务生成**的形态 {@code /uploads/{yyyy}/{MM}/{32 位 hex}.{ext}}，
 * 其余一律视为非法（图片下发端点按此拒绝路径穿越）。发帖与图片下发共用本类，避免两处各写一份正则。</p>
 *
 * <p>对象 key = URL 去掉 {@code /uploads/} 前缀后的路径段（{@code yyyy/MM/name.ext}），
 * 与两种存储实现落盘/落桶时的 key 一致，图片下发据此反查归属。</p>
 *
 * <p><b>签名 URL 的唯一来源</b>：私有图片（待审 / 下架 / 未绑定上传）在浏览器里 {@code <img>} 不带
 * Bearer 令牌，等价于游客而取不到图。为此在下发 DTO 时按 viewer 追加短时签名，形态为
 * {@code /uploads/...jpg?exp=<epochSeconds>&sig=<base64url(HMAC-SHA256(objectKey + ":" + exp, secret))>}。
 * 签名的拼装（{@link #withSignature}）与解析（{@link #objectKey}/{@link #contentType} 会忽略查询串）
 * 都收敛在本类，签名值的计算见 {@link ImageUrlSigner}，别在多处手拼字符串。</p>
 */
public final class StorageUrl {

    /** 对外访问前缀（含结尾斜杠） */
    public static final String PUBLIC_PREFIX = "/uploads/";

    /** 签名查询参数：过期时间（epoch 秒） */
    public static final String PARAM_EXP = "exp";

    /** 签名查询参数：签名值（base64url，无填充） */
    public static final String PARAM_SIG = "sig";

    private static final Pattern PATTERN =
            Pattern.compile("^/uploads/(\\d{4})/(\\d{2})/([0-9a-f]{32})\\.(jpg|png|webp)$");

    private StorageUrl() {
    }

    /** 解析出对象 key；非法地址返回空。携带签名查询串时只取路径段 */
    public static Optional<String> objectKey(String url) {
        Matcher matcher = match(url);
        if (matcher == null) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1) + "/" + matcher.group(2) + "/"
                + matcher.group(3) + "." + matcher.group(4));
    }

    /** 由扩展名推出 Content-Type；非法地址返回空。携带签名查询串时只取路径段 */
    public static Optional<String> contentType(String url) {
        Matcher matcher = match(url);
        if (matcher == null) {
            return Optional.empty();
        }
        return Optional.of(switch (matcher.group(4)) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            default -> "image/webp";
        });
    }

    /** 对象 key → 对外 URL（回收孤儿对象时用它把 key 还原成 {@link StorageService#delete} 的入参） */
    public static String publicUrl(String objectKey) {
        return PUBLIC_PREFIX + objectKey;
    }

    /**
     * 给对外地址追加短时签名查询串。签名值由 {@link ImageUrlSigner} 按 {@code objectKey + ":" + exp}
     * 计算后传入；本方法只负责拼装，保证签名 URL 的形态只有一个出处。
     *
     * @param url              不含查询串的对外地址（{@link #publicUrl} 的产物）
     * @param expEpochSeconds  过期时间（epoch 秒）
     * @param signature        base64url 编码的 HMAC-SHA256 签名
     */
    public static String withSignature(String url, long expEpochSeconds, String signature) {
        return url + "?" + PARAM_EXP + "=" + expEpochSeconds + "&" + PARAM_SIG + "=" + signature;
    }

    private static Matcher match(String url) {
        if (url == null) {
            return null;
        }
        // 请求可能携带签名查询串（?exp=..&sig=..）：对象 key 只认路径段，查询串一律忽略。
        // 这样「已签名 URL」也能在同一处还原出 objectKey（发帖引用、批量换取都依赖它）。
        int query = url.indexOf('?');
        String path = query >= 0 ? url.substring(0, query) : url;
        Matcher matcher = PATTERN.matcher(path);
        return matcher.matches() ? matcher : null;
    }
}
