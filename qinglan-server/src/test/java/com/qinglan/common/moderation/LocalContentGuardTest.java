package com.qinglan.common.moderation;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 用真实 Spring 上下文加载守卫，顺带验证 {@code application.yml} 里的词库路径与隐私正则本身可用
 * ——配置写错（路径不存在、正则非法、词库为空）都应在启动期失败，而不是静默放行。
 */
@SpringBootTest
@ActiveProfiles("test")
class LocalContentGuardTest {

    @Autowired
    private ContentGuard contentGuard;

    @Test
    @DisplayName("命中敏感词：抛内容不合规")
    void shouldBlockSensitiveWord() {
        assertThatThrownBy(() -> contentGuard.check("群里有人发代写广告，需要的私聊"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CONTENT_REJECTED);
    }

    @Test
    @DisplayName("词中插空白仍被拦：绕写不生效")
    void shouldBlockWordWithWhitespaceInBetween() {
        assertThatThrownBy(() -> contentGuard.check("代 写 论 文"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CONTENT_REJECTED);
    }

    @Test
    @DisplayName("命中身份证号：抛隐私拦截")
    void shouldBlockIdCardNumber() {
        assertThatThrownBy(() -> contentGuard.check("捡到一张卡，号码 110101199003071234"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.PRIVACY_CONTENT_BLOCKED);
    }

    @Test
    @DisplayName("命中银行卡号：抛隐私拦截")
    void shouldBlockBankCardNumber() {
        assertThatThrownBy(() -> contentGuard.check("打款到 6222021234567890 这个卡上"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.PRIVACY_CONTENT_BLOCKED);
    }

    @Test
    @DisplayName("11 位手机号不误拦：这就是学号正则刻意留空的原因")
    void shouldNotBlockPhoneNumber() {
        assertThatCode(() -> contentGuard.check("联系我 13800000000"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("正常内容与空内容放行")
    void shouldPassNormalAndBlankContent() {
        assertThatCode(() -> contentGuard.check("出个二手自行车，9 成新")).doesNotThrowAnyException();
        assertThatCode(() -> contentGuard.check(null)).doesNotThrowAnyException();
        assertThatCode(() -> contentGuard.check("   ")).doesNotThrowAnyException();
    }
}
