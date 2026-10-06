package com.qinglan.common.moderation;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 本地硬拦截实现：敏感词库 + 隐私正则。
 *
 * <p>词库在启动时一次性读入内存，校验是纯字符串/正则匹配，无 IO、无外部依赖。词库或正则
 * 配置有误会直接让应用启动失败——静默降级成「什么都不拦」比启动失败危险得多。</p>
 *
 * <p>敏感词匹配前会去掉全部空白并转小写，抵消「代 写」这类插空绕写；隐私正则则在原文上匹配，
 * 因为分隔符会破坏号码本身的格式。</p>
 */
@Component
public class LocalContentGuard implements ContentGuard {

    private static final Logger log = LoggerFactory.getLogger(LocalContentGuard.class);

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final List<String> sensitiveWords;
    private final List<LabeledPattern> privacyPatterns;

    LocalContentGuard(ModerationProperties properties, ResourceLoader resourceLoader) {
        this.sensitiveWords = loadSensitiveWords(resourceLoader, properties.sensitiveWordFile());
        this.privacyPatterns = properties.privacyRules().stream()
                .map(rule -> new LabeledPattern(rule.label(), Pattern.compile(rule.regex())))
                .toList();
        log.info("本地硬拦截已加载：敏感词 {} 条，隐私规则 {} 条", sensitiveWords.size(), privacyPatterns.size());
    }

    @Override
    public void check(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        checkSensitiveWords(text);
        checkPrivacy(text);
    }

    private void checkSensitiveWords(String text) {
        String normalized = WHITESPACE.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
        for (String word : sensitiveWords) {
            if (normalized.contains(word)) {
                // 只记命中的词（系统词库里的词），不记用户原文
                log.info("内容命中敏感词 {}，已拦截", word);
                throw new BizException(ErrorCode.CONTENT_REJECTED);
            }
        }
    }

    private void checkPrivacy(String text) {
        for (LabeledPattern pattern : privacyPatterns) {
            if (pattern.regex().matcher(text).find()) {
                // 只记规则名，不记命中的号码（那是 PII）
                log.info("内容命中隐私规则 {}，已拦截", pattern.label());
                throw new BizException(ErrorCode.PRIVACY_CONTENT_BLOCKED);
            }
        }
    }

    private List<String> loadSensitiveWords(ResourceLoader resourceLoader, String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("未配置敏感词库位置（qinglan.moderation.sensitive-word-file）");
        }
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("敏感词库不存在: " + location);
        }
        List<String> words = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                if (word.isEmpty() || word.startsWith("#")) {
                    continue;
                }
                words.add(word.toLowerCase(Locale.ROOT));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("敏感词库读取失败: " + location, ex);
        }
        if (words.isEmpty()) {
            throw new IllegalStateException("敏感词库为空，拒绝以「零拦截」状态启动: " + location);
        }
        return List.copyOf(words);
    }

    private record LabeledPattern(String label, Pattern regex) {
    }
}
