package com.qinglan.common.anonymous;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 匿名代号派生。
 *
 * <p>不变量 #1（AGENTS.md）：{@code anonymous_code = hash(post_id + author_id + salt)}，
 * 同一用户在不同帖子下代号必然不同，且对外接口不得同时返回代号与 author_id。
 * 本类保持无状态纯函数，调用方自行提供用户级 salt（存于 user.anonymous_salt）。</p>
 *
 * <p>归属说明：P0 时放在 auth 模块，P1 起 post 与 comment 都要用它。若留在 auth，
 * post 就得新增一条 post → auth 的横向依赖；而它本身不含业务规则、只做哈希，
 * 故下沉到 common 供各模块共用，依赖图保持无环。</p>
 */
@Service
public class AnonymousCodeService {

    private static final String[] CAMPUS_WORDS = {
            "银杏", "青竹", "荷塘", "梧桐", "木樨", "石楠", "丁香", "海棠",
            "樟树", "樱花", "松柏", "玉兰", "紫藤", "白鹭", "松鼠", "苦楝",
            "芦苇", "石榴", "枇杷", "枫杨"
    };

    public String deriveCode(long postId, long authorId, String anonymousSalt) {
        String digest = sha256Hex(postId + ":" + authorId + ":" + anonymousSalt);
        int wordIndex = (int) (Long.parseUnsignedLong(digest.substring(0, 8), 16) % CAMPUS_WORDS.length);
        return CAMPUS_WORDS[wordIndex] + "#" + digest.substring(8, 12);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
