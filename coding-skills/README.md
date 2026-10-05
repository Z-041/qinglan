# 多语言工程实践 Skills（Java / C# / Vue / Go / Python / PHP / C）

给 AI 编码 Agent 使用的工程规范技能包：每个目录一个标准 Agent Skill（`SKILL.md` + YAML frontmatter），
Agent 在匹配到对应语言文件时自动加载，用于约束代码生成与代码审查。

## 目录结构

```
coding-skills/
├── AGENTS.md                 # 项目根模板：复制到你的仓库根目录，声明各语言 skill 加载规则
├── java-springboot/SKILL.md
├── dotnet-best-practices/SKILL.md
├── vue-best-practices/
│   ├── SKILL.md
│   └── references/           # reactivity / sfc / component-data-flow / composables
├── go-rust-systems/SKILL.md
├── python-development/SKILL.md
├── php-general-engineer/SKILL.md
└── c-coding-standards/SKILL.md
```

## 内容清单

| 语言 | Skill 目录 | 覆盖范围 | 上游来源 | SKILL.md 行数 | 参考文件 |
| --- | --- | --- | --- | --- | --- |
| C | `c-coding-standards` | ISO C11/C17/C23 + CERT C + MISRA 风格，12 个域规则与 DO/DON'T 示例、工具链基线 | changfengpro/agent-skills | 750 | — |
| C# / .NET | `dotnet-best-practices` | XML 文档注释、主构造器 DI、async/await、MSTest + FluentAssertions、SOLID | github/awesome-copilot | 118 | — |
| Go | `go-rust-systems` | Go 系统编程模式（含 Rust 章节），错误处理、并发、工程结构 | d-padmanabhan/claude-engineering-skills | 208 | — |
| Java | `java-springboot` | 构造器注入、DTO 边界、@ConfigurationProperties、Spring Data JPA、JUnit 5 + Mockito | github/awesome-copilot | 86 | — |
| PHP | `php-general-engineer` | PSR-12、PHP 8.2+（readonly/enum/match）、Laravel/Symfony、phpstan level 8+、PHPUnit/Pest | notque/vexjoy-agent | 122 | — |
| Python | `python-development` | Python 3.14+ 标准、类型标注、async 模式、测试约定 | d-padmanabhan/claude-engineering-skills | 282 | — |
| Vue 3 | `vue-best-practices` | Composition API + <script setup>、TypeScript、响应式、SFC 结构、composables、Pinia/Router/测试 | vuejs-ai/skills | 156 | 4 个 |

## 安装

### Claude Code（全局生效）

```bash
mkdir -p ~/.claude/skills
cp -r coding-skills/* ~/.claude/skills/
```

### 仅当前项目生效

```bash
mkdir -p .claude/skills
cp -r coding-skills/* .claude/skills/
cp coding-skills/AGENTS.md ./AGENTS.md   # 再按项目实际命令修改
```

其他支持 `SKILL.md` 的 Agent（Cursor、Codex CLI、Copilot CLI 等）同理，放到各自约定目录即可。

## 配套权威规范（建议写入 AGENTS.md 作为校验依据）

| 语言 | 权威规范 |
| --- | --- |
| Java | 阿里巴巴《Java 开发手册》（嵩山版）、Google Java Style |
| C# | Microsoft .NET 编码约定、Framework Design Guidelines |
| Vue | Vue.js 官方风格指南（优先级 A/B/C/D） |
| Go | Uber Go Style Guide、Effective Go、Go Code Review Comments |
| Python | Google Python Style Guide、PEP 8 |
| PHP | PSR-1 / PSR-12、PHPStan 官方文档 |
| C | CERT C、MISRA C:2012/2025、ISO C 标准 |

## 已知限制

- `php-general-engineer/SKILL.md` 引用了上游的 `references/hooks-and-behaviors.md`、`references/php-conventions.md`，
  这两份文件未包含在本包内；核心要点已内联进 SKILL.md 正文。
- `c-coding-standards/SKILL.md` 超过 500 行的推荐上限（内容本身完整，建议按需裁剪）。
- 抓取过程中代码块围栏有丢失，已尽最大努力恢复内容与格式；如发现个别示例排版异常，以上游原文为准。

## 来源与许可

各 skill 版权归上游仓库所有（awesome-copilot、vuejs-ai/skills、claude-engineering-skills、
notque/vexjoy-agent、changfengpro/agent-skills）。本包仅做收集、清洗（重建 frontmatter、
恢复标题与表格、修正转义）与重新组织，便于离线安装。
