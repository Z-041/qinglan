# AGENTS.md

本文件是给 AI 编码 Agent 的项目规范入口。复制后请替换 `[...]` 占位内容为项目真实命令。

## 项目概览

[一句话说明本项目做什么。]

技术栈：[Java 17 + Spring Boot 3 / .NET 8 / Vue 3 / Go / Python / PHP 8.2 / C11 等，按实际保留]

## 命令

- 安装依赖：`[...]`
- 构建：`[...]`
- 测试（全部）：`[...]`
- 测试（单个）：`[...]`
- Lint：`[...]`
- 格式化：`[...]`
- 类型检查：`[...]`

## 语言规范：按需加载对应 Skill

Agent 在改动以下文件或目录时，自动加载对应 skill：

| 触发条件 | 加载的 Skill |
| --- | --- |
| `*.java`、`pom.xml`、`build.gradle` | `java-springboot` |
| `*.cs`、`*.csproj`、`*.sln` | `dotnet-best-practices` |
| `*.vue`、Vue 相关的 `*.ts` | `vue-best-practices`（含 references） |
| `*.go`、`go.mod` | `go-rust-systems` |
| `*.py`、`pyproject.toml` | `python-development` |
| `*.php`、`composer.json` | `php-general-engineer` |
| `*.c`、`*.h`、`Makefile`、`CMakeLists.txt` | `c-coding-standards` |

## 通用工程红线（MUST）

- 改动前先读完整文件与相关测试；禁止基于猜测改代码。
- 不改动本次任务范围外的代码；发现额外问题先报告，不擅自修复。
- 新代码必须随附测试（正常路径 + 边界 + 失败路径）。
- 交付前必须真实运行：格式化 → lint/静态分析 → 类型检查 → 测试，并贴出实际输出；不允许只说"应该能过"。
- 禁止用 `@ts-ignore`、`# type: ignore`、`SuppressWarnings`、`@SuppressWarnings`、空 `catch`、`TODO` 等方式绕过错误。
- 错误必须显式处理：不使用裸返回码混用、不吞异常、不做防御性冗余判空。
- 删除死代码，禁止注释掉旧代码留作"备用"。
- 注释只解释"为什么"，不重复代码本身。
- 命名用完整英文单词，禁止拼音混写与缩写。

## 安全红线（MUST NOT）

- 禁止拼接 SQL / 命令 / 模板字符串，一律用参数化或预编译。
- 禁止硬编码密钥、Token、密码；配置走环境变量或密钥管理。
- 所有外部输入视为不可信，必须校验与转义（XSS、路径穿越、越权）。
- 禁止绕过 CSRF、鉴权、会话保护机制。
- 敏感操作必须留审计日志，日志中不得包含敏感数据。

## 提交与 PR

- 提交信息使用 Conventional Commits：`feat/fix/refactor/test/docs/chore(scope): 说明`，正文写"为什么"。
- PR 描述包含：改了什么、为什么、如何验证、影响范围。
- PR 自检：测试通过、lint 无告警、无遗留调试代码（`console.log` / `var_dump` / `print`）、无新增依赖未说明。

## 不确定的时候

需求或上下文不明确时，先提问或先给出方案再动手，不要假设。
