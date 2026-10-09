package com.qinglan.support;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 把 AGENTS.md 第三章的模块边界变成可执行门禁。
 *
 * <p>写它的直接原因是 R-16：`comment → user` 依赖一直存在，但白名单漏写，既没有编译错误也没有测试拦截，
 * 直到人工静态扫描才浮出来。此类「规则与实现不一致」只能靠自动化比对，人眼迟早会漏。</p>
 *
 * <p>规则有意做成**动态发现模块**：从被分析的类里现取顶层包名，凡是 {@code ALLOWED} 里查不到的模块
 * 一律按「不允许依赖任何其他模块」处理。因此新增模块或新增跨模块方向时，即便忘了更新本文件，
 * CI 也会先红——逼着改动者回来把白名单补齐。</p>
 */
@AnalyzeClasses(packages = "com.qinglan", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    private static final String ROOT = "com.qinglan";

    /** 公共模块：所有模块都可依赖它，但它不得反向依赖任何业务模块 */
    private static final String COMMON = "common";

    /**
     * 跨模块方向白名单，与 AGENTS.md 第三章逐条对应，不得反向。
     *
     * <p>P2（审核体系）起，审计日志底座下沉到 {@code common.audit}，{@code post}/{@code user}
     * 不再依赖 {@code review}；{@code review} 升为审核编排者，单向依赖业务模块。</p>
     *
     * <p>{@code ALLOWED} 只登记「本模块允许依赖谁」；表里没有的模块默认取值空集，
     * 即除 {@code common} 外不主动依赖任何其他模块。确需新增方向时先改 AGENTS.md，再同步这里。</p>
     */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "auth", Set.of("user"),
            "comment", Set.of("user", "post"),
            "post", Set.of("user", "announcement"),
            "review", Set.of("user", "post", "comment"),
            "user", Set.of(),
            COMMON, Set.of()
    );

    @ArchTest
    static void 跨模块调用只允许白名单方向(JavaClasses classes) {
        Set<String> modules = modulesOf(classes);
        for (String module : modules) {
            Set<String> allowed = allowedDependenciesOf(module);
            String[] forbidden = modules.stream()
                    .filter(other -> !other.equals(module) && !allowed.contains(other))
                    .map(other -> ROOT + "." + other + "..")
                    .toArray(String[]::new);
            if (forbidden.length == 0) {
                continue;
            }
            noClasses()
                    .that().resideInAPackage(ROOT + "." + module + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(forbidden)
                    .because("AGENTS.md 第三章：模块 %s 只允许依赖 %s，不得反向".formatted(module, allowed))
                    .check(classes);
        }
    }

    @ArchTest
    static void 控制器只依赖服务不依赖仓储(JavaClasses classes) {
        noClasses()
                .that().resideInAPackage(ROOT + "..controller..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + "..repository..")
                .because("AGENTS.md 第三章：Controller 只做参数校验与 DTO 装配，不得注入 Repository")
                .check(classes);
    }

    /** common 是全体模块的公共底座；其余模块按白名单取，白名单未登记的一律只允许 common */
    private static Set<String> allowedDependenciesOf(String module) {
        Set<String> allowed = new LinkedHashSet<>(ALLOWED.getOrDefault(module, Set.of()));
        if (!COMMON.equals(module)) {
            allowed.add(COMMON);
        }
        return allowed;
    }

    /** 从被分析的类里取出顶层模块名，如 com.qinglan.post.service.PostService → post */
    private static Set<String> modulesOf(JavaClasses classes) {
        Set<String> modules = new TreeSet<>();
        String prefix = ROOT + ".";
        for (JavaClass javaClass : classes) {
            String packageName = javaClass.getPackageName();
            if (!packageName.startsWith(prefix)) {
                continue;
            }
            String remainder = packageName.substring(prefix.length());
            int dot = remainder.indexOf('.');
            String module = dot < 0 ? remainder : remainder.substring(0, dot);
            if (!module.isEmpty()) {
                modules.add(module);
            }
        }
        return modules;
    }
}
