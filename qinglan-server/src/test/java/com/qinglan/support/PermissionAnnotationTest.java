package com.qinglan.support;

import com.qinglan.common.security.AdminPermission;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 把「权限点只能来自 {@link AdminPermission}」变成可执行门禁（P5 决策 D11）。
 *
 * <p>动机与 {@code ModuleBoundaryTest} 相同：{@code hasAuthority('POST_REVIWE')} 这种拼错
 * <b>编译期完全合法</b>，运行时表现为「谁都被拒绝」，只有点到那个按钮才会发现。而角色准入一旦
 * 退回 {@code hasRole(...)}，权限模型就被绕过了。两者都靠人眼看不住，交给断言。</p>
 *
 * <p>规则：每个 {@code @PreAuthorize} 要么是 {@code hasAuthority('已登记权限点')}，
 * 要么是显式的 {@code isAuthenticated()}；不允许 {@code hasRole} / {@code hasAnyRole}，
 * 也不允许写在类上（类级放行 + 方法级遗漏是最常见的越权来源）。</p>
 */
@AnalyzeClasses(packages = "com.qinglan", importOptions = ImportOption.DoNotIncludeTests.class)
class PermissionAnnotationTest {

    /** 唯一允许的权限点写法 */
    private static final Pattern PERMISSION_EXPRESSION = Pattern.compile("hasAuthority\\('([A-Z_]+)'\\)");
    /** 只要求「已认证」的例外，仅限「回答我是谁」这类自身不该要求权限点的端点 */
    private static final String AUTHENTICATED_ONLY = "isAuthenticated()";
    private static final List<String> ROLE_EXPRESSIONS = List.of("hasRole(", "hasAnyRole(");
    /**
     * 冒烟下限：带注解的方法少于它，说明扫描本身出了问题而不是「没有违规」。
     * 经验 #11：永远为真的断言等于没写。
     */
    private static final int MIN_ANNOTATED_METHODS = 15;

    @ArchTest
    static void 权限注解只能引用已登记的权限点且不得写角色(JavaClasses classes) {
        Set<String> registered = Arrays.stream(AdminPermission.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
        List<String> violations = new ArrayList<>();
        int annotatedMethods = 0;

        for (JavaClass javaClass : classes) {
            if (javaClass.isAnnotatedWith(PreAuthorize.class)) {
                violations.add("%s 把 @PreAuthorize 写在类上，应逐方法声明权限点"
                        .formatted(javaClass.getName()));
            }
            for (JavaMethod method : javaClass.getMethods()) {
                if (!method.getOwner().equals(javaClass) || !method.isAnnotatedWith(PreAuthorize.class)) {
                    continue;
                }
                annotatedMethods++;
                String expression = method.getAnnotationOfType(PreAuthorize.class).value().trim();
                violations.addAll(check(method.getFullName(), expression, registered));
            }
        }

        assertThat(violations).as("权限注解必须只用已登记的权限点").isEmpty();
        assertThat(annotatedMethods)
                .as("扫描到的 @PreAuthorize 方法数不应低于冒烟下限（否则是断言空转）")
                .isGreaterThanOrEqualTo(MIN_ANNOTATED_METHODS);
    }

    private static List<String> check(String methodName, String expression, Set<String> registered) {
        List<String> violations = new ArrayList<>();
        if (expression.equals(AUTHENTICATED_ONLY)) {
            return violations;
        }
        if (ROLE_EXPRESSIONS.stream().anyMatch(expression::contains)) {
            violations.add("%s 用角色表达式 %s 授权，应改为 hasAuthority('权限点')"
                    .formatted(methodName, expression));
            return violations;
        }
        Matcher matcher = PERMISSION_EXPRESSION.matcher(expression);
        if (!matcher.matches()) {
            violations.add("%s 的表达式 %s 不是 hasAuthority('X') 或 isAuthenticated()"
                    .formatted(methodName, expression));
            return violations;
        }
        String permission = matcher.group(1);
        if (!registered.contains(permission)) {
            violations.add("%s 引用了未登记的权限点 %s（可能拼错，或忘了加进 AdminPermission）"
                    .formatted(methodName, permission));
        }
        return violations;
    }
}
