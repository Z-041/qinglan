# 青栏 Qinglan — 交付包

校园墙项目（Java 21 + Vue 3）的完整开发约定，开箱即用。

## 包内容

```
qinglan/
├── AGENTS.md              项目专属编码规范（204 行）：分层边界、领域不变量、
│                          语言约束、测试门槛、安全红线、交付自检清单
├── 青栏项目设计方案.md      产品与技术方案（12 章）：合规底线、身份模型、
│                          功能清单、审核体系、公告系统、架构、数据模型、风控
└── coding-skills/         7 门语言工程实践 Skill（Java / C# / Vue / Go /
    │                      Python / PHP / C），本项目实际加载前两个
    ├── AGENTS.md          Skill 路由用的通用模板
    ├── README.md          来源、安装、权威规范对照
    ├── java-springboot/
    ├── vue-best-practices/  （含 references/）
    ├── dotnet-best-practices/
    ├── go-rust-systems/
    ├── python-development/
    ├── php-general-engineer/
    └── c-coding-standards/
```

## 快速开始

```bash
# 1. 安装 Skill（全局）
mkdir -p ~/.claude/skills
cp -r coding-skills/*/ ~/.claude/skills/

# 2. 把 AGENTS.md 放进项目根目录（青栏仓库尚未创建时先留着）
cp AGENTS.md <你的项目根>/AGENTS.md

# 3. 开始让 Agent 写代码，它会按 AGENTS.md 的规则执行
```

## 两份文档的分工

| 文档 | 回答什么问题 | 冲突时 |
| --- | --- | --- |
| `青栏项目设计方案.md` | 做什么、为什么这么做 | — |
| `AGENTS.md` | 怎么写代码、边界在哪 | 以 AGENTS.md 的工程约束为准 |

## AGENTS.md 的三层约束

1. **模块边界** — Controller 不得直连 Repository；前端只有 `src/api/` 能发请求
2. **领域不变量（7 条）** — 匿名代号不可关联、联系方式服务端脱敏、真实身份管理员不可见、
   举报不可刷量、审核状态禁止跳态、强制阅读必须真实计时、审计日志只增不改删
3. **交付自检清单（9 项）** — 每次完成后逐条核对

这三层是刻意设计的：边界管"代码放哪"，不变量管"数据不能错"，自检清单管"交付前别忘了"。

## 技术栈速览

| 层 | 选型 |
| --- | --- |
| 后端 | Java 21 + Spring Boot 3.x + Spring Data JPA + MySQL 8 + Redis 7 |
| 前端 | Vue 3 + TypeScript + Vite + Pinia + Vue Router 4 |
| 测试 | JUnit 5 + Mockito + ArchUnit（后端）/ Vitest（前端） |
| 内容安全 | 本地敏感词库 + 隐私正则（**第三方内容安全 API 未接入**，图像识别未做，人工审核兜底） |
| 主色 | `#2E8B7A` |
