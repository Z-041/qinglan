<p align="center">
  <b>青栏 Qinglan</b> · 校园匿名社区<br />
  <sub>合规先于功能 · 约束先于便利 · 权力必须被记录与复审</sub>
</p>

---

<p align="center">
  <sub>开发署名 · 本项目由 AI 编码助手 <b>TraeCode</b> 协助完成（人机协作：需求与决策由人拍板，实现与核验由 AI 执行）</sub>
</p>

---

[![CI](https://github.com/Z-041/qinglan/actions/workflows/ci.yml/badge.svg)](https://github.com/Z-041/qinglan/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-2E8B7A)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-2E8B7A)
![Vue](https://img.shields.io/badge/Vue-3.5-2E8B7A)
![License](https://img.shields.io/badge/license-MIT-lightgrey)


**青栏**是一个面向高校的**校园匿名社区（校园墙）**：学生用**校园邮箱**完成认证后，在**匿名**前提下
发帖、评论、点赞、举报；治理侧由独立的管理后台承担审核队列、处置表决、申诉复审、公告强读、
用户与权限管理、审计追溯。

项目的核心取向不是「功能多」，而是**在匿名场景下把合规与治理做扎实**：匿名代号不可跨帖关联、
真实身份对管理员不可见、联系方式服务端脱敏、审核状态迁移受约束、强制性公告必须真实阅读、
审计日志只增不改删。这些不是口号，而是**写进代码、由测试钉住**的领域不变量（见
[AGENTS.md](AGENTS.md) 第四章与 [核心领域约束](#五核心领域约束)）。

- **学生端** —— 部署在 `/`，面向 PC 为主、兼容移动端
- **管理端** —— 部署在同源 `/admin/`，仅面向 PC，与前端物理隔离
- **后端** —— Spring Boot 单体，按业务模块分包，库结构由 Flyway 管理
- **共享层** —— `shared` 包承载双端共用的请求层、基础组件、类型与样式变量

> 📌 **本文件很长，按需跳读**：想跑起来看 [快速开始](#十一本地开发)；
> 想知道「怎么设计」看 [系统架构](#六系统架构)；想知道「有哪些接口」看 [接口一览](#八接口一览)；
> 想核对「库长什么样」看 [数据模型](#七数据模型)。

---

## 目录

| 章节 | 内容 |
| --- | --- |
| [一、项目简介](#一项目简介) | 背景、定位、设计目标与非目标 |
| [二、功能总览](#二功能总览) | 学生端 / 管理端的全部功能 |
| [三、治理机制](#三治理机制核心) | 内容审核、处置表决、申诉、公告强读、权限模型、审计 |
| [四、技术栈](#四技术栈) | 语言、框架、库版本 |
| [五、核心领域约束](#五核心领域约束) | 7 条不可违反的不变量 |
| [六、系统架构](#六系统架构) | 拓扑、仓库结构、分层与边界、认证、存储、通用底座 |
| [七、数据模型](#七数据模型) | 15 张表、约束、索引 |
| [八、接口一览](#八接口一览) | 按模块的全部端点与权限点 |
| [九、错误码](#九错误码) | `QL-<status>-<seq>` 全量列表 |
| [十、配置参考](#十配置参考) | `application.yml` 与 `.env` 全部键 |
| [十一、本地开发](#十一本地开发) | 从零跑通前后端 |
| [十二、测试与质量门禁](#十二测试与质量门禁) | 三层测试与四道前端门禁 |
| [十三、部署与运维](#十三部署与运维) | Docker Compose、nginx、备份恢复回滚 |
| [十四、持续集成](#十四持续集成) | CI 的四个 job |
| [十五、文档导航](#十五文档导航) | 各文档的分工 |
| [十六、贡献约定](#十六贡献约定) | 提交、PR、判断顺序 |
| [十七、术语表](#十七术语表) | 匿名代号、提案、裁决、强读… |

---

## 一、项目简介

### 背景

校园墙类产品长期面临三对矛盾：

1. **匿名表达** vs **可追责**：允许匿名就会有人造谣、泄露隐私、刷量引流；不做匿名则没人敢说话。
2. **规模化内容** vs **有限审核人力**：学生运营团队不可能 7×24 值守，必须把机器能做的做掉、
   把人的时间留给必须人判断的部分。
3. **治理权力** vs **权力滥用**：单人管理员一键下架会在小圈子里演变成打压工具；权力必须被拆分、
   被记录、被复审。

### 定位与设计目标

青栏用一套**约束先于功能**的设计回答上述矛盾：

| 目标 | 落地手段 |
| --- | --- |
| 匿名但可治理 | 匿名代号按 `hash(post_id + author_id + salt)` 派生，**跨帖无法关联**；治理只需「处置内容」，不需要知道「谁写的」 |
| 隐私默认安全 | 联系方式仅认证用户可见且**服务端脱敏**；学号**加密存储**，管理员接口 / 日志 / 导出均无明文 |
| 处置必须集体决策 | 对已发布内容的处置走**委员会提案 + 表决**，达阈值才生效；僵局上报超管裁决 |
| 申诉是程序而非后门 | 作者可在窗口期内申诉，**复审人 ≠ 原处置人**；撤销已作出的处置属超管专属 |
| 取证留痕 | 下架 / 封禁 / 发公告 / 导出等全部落**只增不改删**的审计日志 |
| 公告真读 | 时长 + 滚动 + 勾选三条件在服务端逐项校验；发帖入口 `428` 兜底（禁用 JS 也绕不过） |

### 非目标（当前阶段明确不做）

- 不做第三方内容安全 API 与图像识别：一期用**本地敏感词库 + 隐私正则**硬拦截，
  图片仅做类型 / 大小校验（`post_image.sensitive_checked` 字段保留给二期）。
- 不做多租户 / 多学校；不做移动 App（学生端 Web 兼容移动浏览器）。
- 不做多实例零中断发布（当前为单实例 `app`，重启有秒级中断；要零中断需多实例 + 前置负载均衡）。

---

## 二、功能总览

### 学生端（`/`）

| 功能 | 说明 |
| --- | --- |
| 校园邮箱认证 | 邮箱验证码 → 校验 → 绑定学号；白名单域名（`fail-closed`，留空即全部拒绝） |
| 首页信息流 | 游标分页；按话题筛选；仅展示已通过审核的帖子 |
| 发布 | 预设话题（白名单，不允许自定义）、正文、最多 9 张图、可选联系方式（部分话题**强制**留联系方式）、草稿本地保存 |
| 帖子详情 | 正文 / 图片九宫格 / 联系方式（认证用户可见，游客为 `null`）/ 二级评论树 |
| 点赞 | 帖子与评论均可点赞，联合唯一键天然幂等 |
| 评论 | 一级评论 + 对一级评论的回复（二级）；可删除自己的评论 |
| 举报 | 8 个预设理由 + 补充说明；未登录 / 自己的内容不显示入口；同一目标仅一次（`409`） |
| 申诉 | 仅作者、内容已被下架、下架后 7 日内、每帖限一次 |
| 公告强读 | 全局弹窗，倒计时未满 / 未滚到底 / 未勾选时**遮罩、ESC、返回键都不关闭** |
| 消息中心 | 评论 / 点赞 / 审核结果 / 举报反馈的通知聚合，未读计数 |
| 我的帖子 | 仅本人可见，含各审核状态 |
| 个人中心 | 档案、信用分、退出登录 |

### 管理端（`/admin/`）

| 页面 | 所需权限点 | 说明 |
| --- | --- | --- |
| 登录 | — | 管理员账号从任一登录页进入；非管理员账号会提示并清登录态 |
| 数据看板 | `STATS_VIEW` | 内容 / 治理 / 用户指标、处置趋势、举报理由分布、处置时效、提案通过率 |
| 待审帖子 | `MODERATION_VIEW` | 主帖**先审后发**；行内放行 / 驳回（`POST_REVIEW`），数字键快捷键，行焦点自动加载上下文抽屉 |
| 审核队列 | `MODERATION_VIEW` | 按举报聚合排序（举报数优先 / 等得最久优先）；多选举报理由、目标类型、排序、只看超时筛选 |
| 我的提案与投票 | `PROPOSAL_VOTE` | 对已发布内容发起处置提案（`PROPOSAL_CREATE`）与投票；「只看我发起的」 |
| 待裁决 | `PROPOSAL_ARBITRATE` | 僵局提案由超管裁决（准予 / 不予，必填理由） |
| 申诉复审 | `APPEAL_REVIEW` | 申诉队列；通过则恢复内容，维持原判则驳回 |
| 用户管理 | `USER_MANAGE` | 列表与筛选（状态 / 角色 / 信用分区间 / 注册时间）、封禁 / 解封 / 调信用分、导出 CSV |
| 公告管理 | `ANNOUNCEMENT_DRAFT` | 建草稿、列表；**发布 / 撤回仅超管**（`ANNOUNCEMENT_MANAGE`）；学生视角强读预览 |
| 权限管理 | `ROLE_GRANT` | 下放 / 收回管理权限（委员会合计上限 6 人） |
| 审计日志 | `AUDIT_READ` | 全量治理动作留痕，可按动作与时间筛选，导出 CSV |

---

## 三、治理机制（核心）

这一章是青栏区别于普通「校园墙 CRUD」的部分，建议设计师 / 运营方重点读。

### 3.1 三道内容审核防线

```
发帖 / 评论
   │
   ├─① 本地硬拦截（同步，毫秒级）
   │     敏感词库（classpath:moderation/sensitive-words.txt）
   │     + 隐私正则（身份证 / 银行卡）
   │     ├─ 命中敏感词 → 400 QL-400-008 CONTENT_REJECTED
   │     └─ 命中隐私信息 → 400 QL-400-009 PRIVACY_CONTENT_BLOCKED
   │
   ├─② 举报聚合（异步，来自用户）
   │     同一目标有效举报数 ≥ 3（qinglan.review.auto-takedown-threshold）
   │     └─ 自动下架（operator = SYSTEM_OPERATOR_ID）+ 转人工复核
   │
   └─③ 人工审核（管理端）
         主帖：Pending → Reviewing → Approved / Rejected
         评论：先发后审，唯一兜底手段是「移除评论」
```

- 新帖一律落 `Pending`，**放行不跳态**——在同一事务内原子走完 `Pending → Reviewing → Approved`
  （`ReviewState.assertTransition` 会拒绝任何跳态）。
- 评论**不进审核状态机**（`PUBLISHED` / `REMOVED`），只保留移除处置。

### 3.2 处置与委员会表决

对**已发布内容**的处置不是单人动作，而是提案：

```
                 ┌──────────── 超管裁决 ────────────┐
                 ▼                                  │
   Open ──表决达阈值──▶ Approved（执行处置）         │
     │  └──同意票已不可能过半──▶ Escalated ──裁决──▶ Approved / Rejected
     └──结案前目标状态已变──▶ Rejected（处置无法执行）

   Approved / Rejected 为终态，不可再投票或改判
```

| 规则 | 说明 |
| --- | --- |
| 提案动作 | `TAKE_DOWN`（下架）/ `REMOVE_COMMENT`（移除评论）/ `HIDE_CONTACT`（隐藏联系方式）/ `UPHOLD_TAKEDOWN`（维持下架） |
| 表决分母 | 提案创建时**快照**当时的在册委员会人数 `snapshot_size`（成员变动不影响进行中的表决） |
| 一人一票 | `review_vote` 上 `uk_vote_proposal_voter`；弃权 = 不投票；重复投 `409 QL-409-011` |
| 同一目标唯一 | `review_proposal.uk_proposal_open_target`（进行中提案占用目标键；结案置 NULL） |
| 撤销归超管 | 「恢复可见 / 撤销隐藏联系方式」需 `DISPOSITION_REVOKE`（仅超管）——若任一委员可一键撤销，多数决就不构成约束 |
| 终态不可逆 | 已结案提案不能被重新投票或再次裁决，堵死「先投票后被改判」 |

### 3.3 申诉（程序正义）

| 环节 | 约束 |
| --- | --- |
| 谁能申诉 | **仅内容作者**（`NOT_CONTENT_OWNER` 拦截） |
| 何时能申诉 | 已下架后 `qinglan.review.appeal-window`（默认 **7 天**）内，超期 `409 QL-409-007` |
| 能申诉几次 | 每个目标**仅一次**（`appeal.uk_appeal_target`），重复 `409 QL-409-006` |
| 谁来复审 | 需 `APPEAL_REVIEW` 权限，且**复审人 ≠ 原处置人**（`QL-403-005 SELF_REVIEW_FORBIDDEN`） |
| 结果 | 通过 → `TakenDown → Approved` 恢复可见（另落 `POST_RESTORE` 审计）；驳回 → 维持原判 |

### 3.4 公告强制阅读

服务端逐项校验，**任一不满足即 `400` 且不落库**：

| 条件 | 配置 / 字段 | 不满足时 |
| --- | --- | --- |
| 停留时长 ≥ 要求 | `read_duration_ms >= min_read_seconds * 1000` | `400 QL-400-011` |
| 需滚动时必须滚到底 | `announcement.require_scroll_to_end` | `400 QL-400-012` |
| 需确认时必须已勾选 | `announcement.require_confirm` | `400 QL-400-012` |

**边界要说清**：`read_duration_ms` 与滚动 / 勾选信号都由**客户端上报**，服务端只能拒绝「不合格的上报」，
无法证明用户真的读了。所以「真强制」落在 `POST /api/posts` 的 **`428 QL-428-001`**：存在未读紧急公告
时**发不了帖**——前端倒计时只是体验层，禁用 JS 绕过 UI 也没用。

配套机制：

- 游客也要能被强制阅读 → 已读记录支持 `device_id`（前端生成并持久化），登录用户记 `user_id`；
  两列皆可空，唯一性分别由 `uk_announcement_read_user` / `uk_announcement_read_device` 保证。
- 限流：公告发布 `max-publishes-per-day`（默认 5/日）；标记已读按设备 `max-reads-per-window`（默认 30/分钟）。
- 公告状态机：仅 `DRAFT` 可发布、仅 `PUBLISHED` 可撤回；撤回**不删已读记录**，更正值 `supersedes_id` 指向被更正公告。

### 3.5 权限模型（权限点驱动）

**角色回答「能不能进后台」，权限点回答「进去以后能做什么」。** 接口只写
`@PreAuthorize("hasAuthority('X')")`（**禁止** `hasRole` / `hasAnyRole` / 类级放行），
前端按 `GET /api/admin/me` 下发的权限点渲染菜单与按钮——前端隐藏只是体验层，真正的防线在端点注解上。

13 个权限点与角色映射（**唯一来源**：`UserRole#permissions()`）：

| 权限点 | 含义 | REVIEWER | ADMIN | SUPER_ADMIN |
| --- | --- | :-: | :-: | :-: |
| `MODERATION_VIEW` | 治理数据读（待审帖 / 队列 / 上下文） | ✅ | ✅ | ✅ |
| `POST_REVIEW` | 待审帖通过 / 驳回 | ✅ | ✅ | ✅ |
| `REPORT_DISMISS` | 驳回举报（内容不违规，结案） | ✅ | ✅ | ✅ |
| `PROPOSAL_CREATE` | 对已发布内容发起处置提案 | ✅ | ✅ | ✅ |
| `PROPOSAL_VOTE` | 提案投票（含查看进度） | ✅ | ✅ | ✅ |
| `STATS_VIEW` | 数据看板 | ✅ | ✅ | ✅ |
| `APPEAL_REVIEW` | 申诉复审 | ❌ | ✅ | ✅ |
| `USER_MANAGE` | 用户列表 / 封禁 / 解封 / 调分 | ❌ | ✅ | ✅ |
| `ANNOUNCEMENT_DRAFT` | 公告管理页与建草稿 | ❌ | ✅ | ✅ |
| `ANNOUNCEMENT_MANAGE` | 发布 / 撤回公告 | ❌ | ❌ | ✅ |
| `ROLE_GRANT` | 角色下放 / 收回 | ❌ | ❌ | ✅ |
| `PROPOSAL_ARBITRATE` | 僵局裁决 | ❌ | ❌ | ✅ |
| `DISPOSITION_REVOKE` | 撤销已作出的处置（恢复可见 / 撤销隐藏联系方式） | ❌ | ❌ | ✅ |
| `AUDIT_READ` | 审计日志查询 | ❌ | ❌ | ✅ |

另有两条硬约束：

- **委员会上限 6 人**（`UserRole.MAX_COMMITTEE_SIZE`，管理员 + 评议员合计），新增委员前**带锁计数**；
  超限 `409 QL-409-009`。
- **唯一超级管理员是初始账号**：不可被降级 / 封禁 / 删除（被锁死只能改库），任何接口都不允许授予
  `SUPER_ADMIN`（`QL-403-006`）。且**不能修改自己的角色**（`QL-403-007`）。
- **令牌是无状态的、角色在签发时固定**，所以账号级禁令与权限收回必须在**每个写入口重新判定**：
  业务写入口调 `UserService.requireUsableById`；`/api/admin/**` 的非 GET 由 `AdminWriteGuardFilter`
  统一兜住，并**比对「数据库角色 = 令牌角色」**——否则被收回权限的人在令牌到期前照样能写。
  只读入口放行，以保留被禁令账号的浏览能力。

### 3.6 审计日志（只增不改删）

`review_log` 表**没有 UPDATE / DELETE 接口**；所有治理动作写入 `AuditAction` 枚举之一：

| 类别 | 动作 |
| --- | --- |
| 用户 | `BAN` · `UNBAN` · `CREDIT_ADJUST` |
| 内容 | `POST_APPROVE` · `POST_REJECT` · `POST_TAKEDOWN` · `POST_AUTO_TAKEDOWN` · `POST_RESTORE` · `POST_HIDE_CONTACT` · `POST_SHOW_CONTACT` · `COMMENT_REMOVE` |
| 举报 | `REPORT_DISMISS` · `REPORT_UPHOLD` |
| 申诉 | `APPEAL_REVIEW` |
| 权限 | `ROLE_GRANT` · `ROLE_REVOKE` |
| 提案 | `PROPOSAL_CREATE` · `PROPOSAL_RESOLVE` |
| 公告 | `ANNOUNCEMENT_CREATE` · `ANNOUNCEMENT_PUBLISH` · `ANNOUNCEMENT_REVOKE` |
| 隐私 | `CONTACT_VIEW`（查看联系方式，用于追溯批量抓取） |
| 数据 | `DATA_EXPORT`（批量导出，记录导出资源与筛选摘要，不含身份明文） |

系统自动下架的操作人记 `SYSTEM_OPERATOR_ID`（0），管理端显示为「系统」。

---

## 四、技术栈

| 层 | 选型 |
| --- | --- |
| 语言 / 运行时 | Java **21**（启用虚拟线程 `spring.threads.virtual.enabled`） |
| 后端框架 | Spring Boot **3.5**（Web / Security / Validation / Data JPA / Data Redis / Mail / Actuator） |
| 库结构 | Flyway（`V1…V8`，**只增不改**）+ Hibernate `ddl-auto: validate`（Hibernate 不许改表） |
| 数据库 | MySQL **8.x**（`utf8mb4_0900_ai_ci`，时间列统一 `DATETIME(6)` UTC） |
| 缓存 / 短时效 | Redis **7**（可选；`memory` 与 `redis` 两种实现可切换） |
| 认证 | 自签 JWT（jjwt **0.12.6**），无状态 Bearer，无 Cookie / Session |
| 对象存储 | AWS SDK v2（**S3 兼容**：MinIO / 腾讯 COS / 阿里 OSS），另有本地磁盘实现 |
| 静态门禁 | ArchUnit **1.5.1** + javac `-Xlint:all,-processing -Werror` |
| 学生端 / 管理端 | Vue **3.5** · TypeScript **5.8** · Vite **6** · Pinia **3** · Vue Router **4** · UnoCSS **66** |
| 管理端图表 | ECharts **6**（仅看板） |
| 测试 | JUnit 5 + Mockito + GreenMail 2.1 + S3Mock 3.9（后端）· Vitest 3 + Vue Test Utils（前端） |
| 部署 | Docker 多阶段构建 · Docker Compose · nginx 1.27-alpine |
| 主色 | `#2E8B7A`（唯一色值来源：`shared/src/styles/main.css` CSS 变量） |

> **两侧前端都不引第三方组件库**：所有控件（按钮、输入、下拉、弹层、抽屉、开关、分页…）
> 都是自研基础组件，语义色与 `ql-*` 快捷类在 `shared/src/uno.base.ts` 统一维护。

---

## 五、核心领域约束

以下 **7 条领域不变量**是系统的正确性根基（详见 [AGENTS.md](AGENTS.md) 第四章）。
任何涉及这些字段或流程的改动，**必须先写测试证明不变量仍成立**：

| # | 不变量 | 实现要求 |
| --- | --- | --- |
| 1 | **匿名代号跨帖不可关联** | `anonymous_code = hash(post_id + author_id + salt)`，同一用户在不同帖子下代号必须不同；任何对外接口不得同时返回 `anonymous_code` 与 `author_id` |
| 2 | **联系方式只对认证用户可见** | 脱敏在**服务端**完成，未认证 viewer 的响应里 `contact` 必须为 `null`（不是前端 `v-if`）；查看联系方式写 `CONTACT_VIEW` 审计 |
| 3 | **真实身份管理员不可见** | `student_id` 加密存储（AES-256-GCM + 随机 IV）；除专用调查通道外，管理员接口 / 日志 / 导出不得返回明文 |
| 4 | **举报不可刷量** | `UNIQUE(target_type, target_id, reporter_id)`；重复举报返回 `409`，**不静默吞掉** |
| 5 | **审核状态迁移合法** | 允许 `Draft→Pending→Reviewing→Approved/Rejected`、`Approved→TakenDown`、`TakenDown→Approved`；禁跳态、禁 `TakenDown→Pending`；用 `sealed interface ReviewState` 表达，新增状态须改 `permits` 并补测试 |
| 6 | **强制阅读必须真实计时** | 时长 + 滚动 + 勾选三条件逐项校验，任一不满足即 `400` 且不落库；「真强制」落在发帖 `428`（见 [3.4](#34-公告强制阅读)） |
| 7 | **审计日志只增不改删** | `review_log` 无 UPDATE / DELETE；下架 / 封禁 / 发公告 / 导出必须落日志 |

此外还有两条工程边界（详见 [6.3](#63-后端分层与模块边界)）：

- **后端模块依赖白名单**由 `ModuleBoundaryTest`（ArchUnit）常驻校验，未登记模块按
  「只允许依赖 `common`」处理——**漏写白名单会让构建失败，而不是静默通过**。
- **管理端接口按权限点授权**，默认拒绝（见 [3.5](#35-权限模型权限点驱动)）。

---

## 六、系统架构

### 6.1 部署拓扑

```
                         ┌─────────────────────────────────────────────┐
   浏览器 ─── :80 ──────▶ │ nginx（唯一对外发布端口）                     │
   （同源）               │  /            → 学生端 SPA（静态托管）        │
                         │  /admin/      → 管理端 SPA（静态托管）        │
                         │  /api         → app:8080（反向代理）          │
                         │  /uploads/**  → app:8080（受控图片，非静态！） │
                         └───────────────┬─────────────────────────────┘
                                         │  Docker 内网 qinglan
                    ┌────────────────────┼────────────────────┐
                    ▼                    ▼                    ▼
              ┌──────────┐        ┌──────────┐        ┌──────────────┐
              │  app     │──────▶ │  mysql:8 │        │  redis:7     │
              │ 8080     │        │  3306    │        └──────────────┘
              │ 非 root  │        └──────────┘        验证码 / 计数 /
              │ 健康检查 │           持久卷             一次性令牌
              └──────────┘
                上传卷（storage.type=local 时）
```

关键点：

- **同源部署**：学生端 `/`、管理端 `/admin/`、后端 `/api` 与 `/uploads` 四者同源 →
  localStorage 共用一把令牌键，维持「**一个浏览器 = 一个账号 = 一份会话**」。
- `/uploads/**` **必须反代到后端**，不能做静态托管：图片可见性由后端按访问者身份判定
  （下架帖的图对游客立即 404），缓存头也由后端下发（短 TTL、随身份变化）。
- `nginx` 的 `try_files` 回退必须各指各的 `index.html`（`/admin/xxx` 刷新若回退到学生端
  `index.html` 会渲染出学生端页面）。

### 6.2 仓库结构

```
qinglan/
├── qinglan-server/                     后端：Java 21 + Spring Boot（Maven wrapper，端口 8080）
│   ├── src/main/java/com/qinglan/
│   │   ├── auth/                       校园邮箱认证、验证码、学号绑定、JWT 与安全过滤器
│   │   │   ├── controller/ dto/ mail/ security/ service/ store/
│   │   ├── post/                       帖子、图片（受控下发 / 上传）、话题
│   │   │   ├── config/ controller/ dto/ entity/ repository/ service/
│   │   ├── comment/                    二级评论
│   │   ├── review/                     举报、审核队列、处置、申诉、提案与表决、看板统计
│   │   │   ├── config/ controller/ dto/ entity/ repository/ service/
│   │   ├── announcement/               公告与强制阅读
│   │   ├── notification/               个人通知聚合
│   │   ├── user/                       身份加密、信用分、封禁、委员会、角色与权限点
│   │   └── common/                     跨模块底座（不依赖任何业务模块）
│   │       ├── anonymous/              匿名代号派生
│   │       ├── audit/                  审计日志（只增不改删）
│   │       ├── config/                 prod 守卫、过滤器注册
│   │       ├── crypto/                 AES 加解密与哈希盐
│   │       ├── error/                  BizException / ErrorCode / 428 专用异常
│   │       ├── interaction/            点赞（多态目标 + 联合唯一）
│   │       ├── moderation/             敏感词 + 隐私正则本地硬拦截
│   │       ├── notify/                 通知端口（模块间解耦）
│   │       ├── persistence/            BaseEntity（审计字段）
│   │       ├── security/               权限点、CurrentUser、限流、Viewer
│   │       ├── storage/                存储抽象（local / s3）+ 签名 URL
│   │       ├── store/                  短时效存储抽象（memory / redis）
│   │       └── web/                    响应封装、游标、CSV、异常处理、TraceId
│   │   ├── src/main/resources/
│   │   │   ├── db/migration/           Flyway 迁移 V1…V8（只增不改）
│   │   │   ├── moderation/             sensitive-words.txt
│   │   │   └── application.example.yml 配置模板（可提交；application.yml 被 .gitignore 忽略）
│   │   └── src/test/java/com/qinglan/  JUnit 5 + Mockito + ArchUnit
│   ├── mvnw / mvnw.cmd / pom.xml
│
├── qinglan-web/                        学生端 SPA（Vue 3 + TS + Vite + UnoCSS，端口 5173，部署在 /）
│   └── src/{api,components,composables,router,stores,styles,types,views}
│
├── qinglan-admin/                      管理端 SPA（同上 + ECharts，端口 5174，部署在 /admin/）
│   └── src/
│       ├── api/                        管理端接口封装（admin / review / proposals）
│       ├── components/admin/           管理端业务组件（队列表、上下文抽屉、处置栏…）
│       ├── components/base/            管理端自研基础控件（见下表）
│       ├── composables/                管理端业务逻辑（含纯逻辑：reviewActionFlow / rolePolicy…）
│       ├── router/                     路由 + 「菜单 → 权限点」映射（navItems.ts）
│       ├── types/                      admin / review / proposal / permission
│       └── views/                      看板 / 待审帖 / 队列 / 提案 / 裁决 / 申诉 / 用户 / 公告 / 权限 / 审计
│
├── shared/                             双端共享（源码形式经 @shared/* 引入，不产出构建产物）
│   └── src/
│       ├── api/                        http.ts（拦截器 / 令牌 / 401 / 428）、user、auth、report
│       ├── components/                 ToastHost、AnnouncementReader、ModalShell、ConfirmDialog、base/
│       ├── composables/                useToast、useUserLabels、useAuth、useCursorList、useSubmit、
│       │                              useFocusTrap、useReadCountdown、useRelativeTime…
│       ├── stores/                     user.ts（用户态，单会话的唯一落点）
│       ├── styles/main.css             CSS 变量（全站唯一色值来源）
│       ├── types/                      双端共用类型（api / auth / user / review-common / announcement）
│       ├── uno.base.ts                 语义色 / ql-* 快捷类 / z 轴分层
│       └── vite/cspPlugin.ts           构建期注入 CSP（两端同一份策略）
│
├── docs/                               设计方案、改造方案、备案材料
├── deploy/                             部署手册（README）、nginx 配置、备份脚本
├── Dockerfile                          后端镜像（多阶段、非 root）
├── docker-compose.yml                  全栈编排（mysql + redis + app + nginx）
├── .env.example                        部署环境变量模板
├── pnpm-workspace.yaml                 workspace 定义（lockfile 全局唯一）
└── AGENTS.md                           编码规范（**冲突时以它为准**）
```

**管理端自研基础控件**（`qinglan-admin/src/components/base/`）：

| 组件 | 替代的控件 | 说明 |
| --- | --- | --- |
| `BaseCheckbox` | 复选框 | 原生 `input[type=checkbox]` + `accent-color` 着色 |
| `BaseSwitch` | 开关 | `<button role="switch">` + `aria-checked` |
| `BaseSelect` | 下拉 | 原生 `<select>` + `ql-field`（键盘 / 读屏 / 点外关闭由浏览器保证） |
| `BaseMultiSelect` | 多选下拉 | 自研弹出层内放复选框列表，勾选不收起 |
| `BaseSegmented` | 分段单选 | `role="radiogroup"` |
| `BaseTabs` | 页签条 | `role="tablist"`；面板由调用方 `v-show` 切换 |
| `BaseDrawer` | 右侧抽屉 | 遮罩 + ESC + 焦点陷阱 |
| `BasePopover` | 点击弹出层 | 点外部 / ESC 关闭 |
| `BaseDateTimeInput` | 日期时间 | 原生 `datetime-local` |
| `MessageBoxHost` | 命令式确认框 | 配合 `useMessageBox`，替代第三方 MessageBox |

### 6.3 后端分层与模块边界

```
Controller  →  Service  →  Repository  →  DB
```

- Controller **不得**注入或调用 Repository，只依赖 Service；只做参数校验与 DTO 装配，不写业务判断。
- Repository 不得返回 Entity 给 Controller；Entity → DTO 转换在 Service 层完成。
- `@Transactional` 加在 **Service 方法**，查询方法加 `readOnly = true`。

**跨模块依赖白名单**（依赖图必须无环，由 `ModuleBoundaryTest` 校验）：

| 方向 | 为什么允许 |
| --- | --- |
| `auth → user` | 认证流程读写用户（学号绑定、匿名 salt、信用分）；加解密集中在 `user`，明文身份不外流 |
| `comment → user`、`comment → post` | 维护 `post.comment_count`（冗余计数，避免列表 N+1） |
| `post → user`、`post → announcement` | 发帖前检查未读紧急公告（`428` 的服务端兜底） |
| `review → user`、`review → post`、`review → comment` | `review` 是审核体系的**编排者**，单向调用业务模块 |
| 所有模块 → `common` | 审计 / 匿名 / 存储 / 游标等底座；`common` **不得**反向依赖任何业务模块 |

反向依赖（`user → auth`、`post → review`、`user → review`、`post → comment` 等）一律禁止。
**审计日志走 `common.audit`**：业务模块直接依赖 `common`，不再有 `post → review` / `user → review`，
否则依赖图必然成环。

### 6.4 前端三包

| 包 | 角色 | 硬约束 |
| --- | --- | --- |
| `shared` | 双端共享（请求层、基础组件、类型、样式变量、用户态） | `vue` / `pinia` 必须是 `peerDependencies`（写成 `dependencies` 会装出两份 vue、Pinia 实例分裂）；不得依赖任一应用 |
| `qinglan-web` | 学生端 | 只能依赖 `shared`；不得含 `/admin` 路由（否则会捕获该路径） |
| `qinglan-admin` | 管理端 | 只能依赖 `shared`；`base: '/admin/'` 与 `createWebHistory('/admin/')` 必须一致 |

分层规则：

- **只有 `src/api/` 与 `shared/src/api/` 能发 HTTP 请求**，组件内禁止直接 `fetch` / `axios`。
- `components/` 是展示组件（props down / events up），**不得**直接调 Pinia store 拿业务数据；
  `views/` 负责组合；`stores/` 只存用户态 / 未读计数 / 草稿。
- 可复用逻辑（含**无响应式的纯逻辑**）一律抽 `composables/`，命名 `useXxx`。
- **请求错误的唯一出口**是 `shared/src/api/http.ts` 拦截器的全局 toast —— 页面**不得**再内联渲染
  请求错误文案；页面只负责结束 loading 态、决定成功后的流程推进。
- 提交态统一走 `useSubmit`（返回 boolean，失败不在此提示）。

### 6.5 认证与会话

```
① POST /api/auth/email/code         提交校园邮箱 → 校验域名白名单 → 发 6 位验证码（TTL 5 分钟）
② POST /api/auth/verify-email       提交邮箱 + 验证码 → 换取「邮箱令牌」（10 分钟、含 jti 一次性消费）
                                    └─ 首次：needsStudentBinding = true
③ POST /api/auth/bind-student-id    提交学号 → 创建用户（学号加密存储、派生匿名 salt）→ 签发 JWT

JWT（HS256，`issuer=qinglan`，无 Cookie / Session）用 `typ` 声明区分两类令牌、互不通用：
  · access   —— sub=userId、role、`access-token-ttl`（默认 2h）
  · email_verify —— sub=emailHash、jti（一次性消费）、`email-token-ttl`（默认 10m）
```

| 主题 | 设计 |
| --- | --- |
| 验证码错误码统一 | 「未获取 / 已过期 / 填错」共用 `QL-400-003`，避免把「该邮箱是否请求过验证码」变成可探测信息 |
| 发送频率 | `send-interval`（默认 60s）→ `429 QL-429-001`；验证失败 `max-verify-attempts` 5 次 → `429 QL-429-002` |
| 邮箱白名单 | `allowed-email-domains` 逗号分隔；**留空 = 拒绝所有**（fail-closed） |
| 401 | 拦截器统一处理：清持久化 + 重置 Pinia 内存态 + 跳登录页（学生端 `/login`、管理端 `/admin/login`） |
| 428 | 存在未读**紧急**公告时，写操作被 `PreconditionRequiredException` 拦截，响应携带公告内容，前端弹强读 |
| 封禁 | `status = BANNED` + `banned_until`；写入口统一调 `UserService.requireUsableById` |

### 6.6 图片上传与受控下发

| 环节 | 说明 |
| --- | --- |
| 上传 | `POST /api/uploads/images`（multipart，仅 JPG / PNG / WebP，单张 ≤ 5MB，9 张/帖上限，`30 次/分钟` 限流） |
| 归属绑定 | 上传即登记 `uploaded_file`（`object_key` 唯一）；发帖时按 `object_key` 绑定，**只能引用本人上传且未被使用**的图片（`QL-403-008` / `QL-409-012`） |
| 下发 | `GET /uploads/**` 受控端点按帖子可见性判定：待审 / 下架 / 未绑定上传 → **私有**，改用**签名 URL**（HMAC，`image-signing-ttl` 默认 10 分钟，硬上限 1 小时）；不可见与不存在**共用 404**，不泄露对象是否存在 |
| 刷新 | `POST /api/uploads/image-urls` 批量刷新过期签名（前端在图片 403/404 时调用） |
| 孤儿回收 | 超过 `orphan-retention`（默认 24h）仍未被引用的对象 + 登记行被定时清理 |
| 生产要求 | `storage.type` 必须 `s3`（不落应用服务器本地磁盘），`image-signing-secret` 必须显式配置（不得回落到 JWT 密钥） |

### 6.7 通用底座

| 能力 | 实现 |
| --- | --- |
| 统一响应 | `ApiResponse<T>{ code, message, data }`；异常经 `GlobalExceptionHandler` 转成对应错误码 |
| 分页 | 一律**游标**（`cursor`），列表接口不得用 `page/size`；拼装 / 解析统一走 `common/web/CompositeCursor` + `CursorCodec`（微秒精度） |
| 限流 | `IpRateLimitFilter`：按 IP 限流（默认 20/分钟），受保护路径默认 `/api/auth/email/code`；真实 IP 依据 `trusted-proxy-hops` 从 `X-Forwarded-For` **右起**取 |
| 追踪 | `TraceIdFilter` 写入 MDC，日志格式 `%5p [%X{traceId:-}]`；生产可切 JSON 结构化日志 |
| 审计 | `common/audit`：`AuditLog` + `AuditAction` + `AuditTargetType` + `AuditLogService`，只增不改删 |
| 时间 | 统一 `Instant`（UTC）；展示层转本地时区；**禁止** `LocalDateTime.now()` 直接入库 |
| 安全响应头 | `SecurityConfig.headers` 统一下发 CSP `default-src 'none'`、`X-Frame-Options: DENY`、`nosniff`、`Referrer-Policy: no-referrer`、`Permissions-Policy`；SPA 的 CSP `meta` 由 `cspPlugin` **构建期**注入 |
| 前端可访问性 | 本地 `prefers-reduced-motion` 全局兜底、弹层焦点陷阱（`useFocusTrap`）、skip link、`aria-*` 与可访问名、`tabular-nums`、日期锁定 `Intl.DateTimeFormat('zh-CN')` |

---

## 七、数据模型

### 7.1 表总览（15 张）

| 表 | 用途 | 首次迁移 | 后续索引 |
| --- | --- | --- | --- |
| `user` | 认证用户（学号加密、匿名 salt、信用分、角色、封禁） | V1 | V5 |
| `review_log` | 管理动作审计日志（只增不改删） | V1 | V5 |
| `topic` | 预设话题（白名单，不允许自定义） | V2 | — |
| `post` | 主帖（含 `contact` / `anonymous_code` / 冗余计数） | V2 | V5、V7 |
| `post_image` | 帖子图片（正文与图片分离） | V2 | — |
| `comment` | 二级评论（先发后审） | V2 | V5 |
| `like_record` | 点赞（多态目标 + 联合唯一） | V2 | — |
| `report` | 举报 | V3 | V5、V7 |
| `appeal` | 申诉 | V3 | — |
| `announcement` | 公告 | V4 | — |
| `announcement_read` | 公告已读记录（强制阅读证据） | V4 | — |
| `notification` | 个人通知（内容为快照） | V4 | — |
| `review_proposal` | 处置提案（委员会表决） | V6 | — |
| `review_vote` | 表决投票（一人一票） | V6 | — |
| `uploaded_file` | 上传文件登记（归属绑定与孤儿回收） | V8 | — |

### 7.2 与领域不变量强相关的约束

| 不变量 / 主题 | 精确约束 |
| --- | --- |
| #1 匿名代号跨帖不可关联 | `post.anonymous_code` / `comment.anonymous_code`（`VARCHAR(32) NOT NULL`）；`user.anonymous_salt`（`VARCHAR(64) NOT NULL`，一人一值） |
| #2 联系方式脱敏 | `post.contact`（`VARCHAR(200) NULL`，独立存储以便统一脱敏与批量隐藏）+ `post.contact_visibility`（`VISIBLE` / `HIDDEN`） |
| #3 真实身份不可见 | `user.student_id_enc`（`VARCHAR(512)`，AES-256-GCM 随机 IV）+ `user.student_id_hash`（唯一约束） |
| #4 举报不可刷量 | `UNIQUE KEY uk_report_target_reporter (target_type, target_id, reporter_id)` |
| #5 审核状态迁移合法 | `post.status` 存 `ReviewState` 持久化名；`review_reason` / `review_by` / `review_at` / `taken_down_at` |
| #6 强制阅读真实计时 | `announcement.min_read_seconds` / `require_scroll_to_end` / `require_confirm`；`announcement_read.read_duration_ms`（`BIGINT NOT NULL`）/ `confirmed` |
| #7 审计只增不改删 | `review_log` 表无 UPDATE / DELETE 接口 |
| 每目标仅一次申诉 | `UNIQUE KEY uk_appeal_target (target_type, target_id)` |
| 同一目标仅一条进行中提案 | `UNIQUE KEY uk_proposal_open_target (open_target_key)`（`open_target_key` 为 `'POST:7'` 之类；结案置 `NULL`，MySQL 唯一索引对 NULL 不去重） |
| 一人一票 | `UNIQUE KEY uk_vote_proposal_voter (proposal_id, voter_id)` |
| 点赞幂等 | `UNIQUE KEY uk_like_target_user (target_type, target_id, user_id)` |
| 公告去重（登录 / 游客） | `uk_announcement_read_user (announcement_id, user_id)` + `uk_announcement_read_device (announcement_id, device_id)`（两列皆可空） |
| 上传对象唯一 / 归属 | `UNIQUE KEY uk_uploaded_file_object_key (object_key)` + `bound_post_id`（NULL = 未绑定） |

### 7.3 逐表列清单

<details>
<summary>展开查看 15 张表的完整列定义（列名 / 类型 / 可空 / 默认值）</summary>

> 约定：布尔列一律 `BIT(1)`；时间列一律 `DATETIME(6)`（微秒精度、UTC）；
> 除 `review_proposal` / `review_vote`（脚本里仅声明 `CHARSET=utf8mb4`、未写 `COLLATE`）外，
> 其余表均为 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci`；
> **全库没有任何外键（FOREIGN KEY）**，表间关系由列名与索引承载。

**`user`（V1）— 认证用户**

| 列 | 类型 | 可空 | 默认 |
| --- | --- | :-: | --- |
| `id` | BIGINT | 否 | AUTO_INCREMENT |
| `email_hash` | VARCHAR(64) | 否 | — |
| `student_id_enc` | VARCHAR(512) | 否 | — |
| `student_id_hash` | VARCHAR(64) | 否 | — |
| `anonymous_salt` | VARCHAR(64) | 否 | — |
| `role` | VARCHAR(20) | 否 | — |
| `status` | VARCHAR(20) | 否 | — |
| `credit_score` | INT | 否 | 100 |
| `banned_until` | DATETIME(6) | 是 | — |
| `created_at` / `updated_at` | DATETIME(6) | 否 | — |

唯一键：`uk_user_email_hash(email_hash)`、`uk_user_student_id_hash(student_id_hash)`。

**`review_log`（V1）— 管理动作审计日志**

| 列 | 类型 | 可空 | 默认 |
| --- | --- | :-: | --- |
| `id` | BIGINT | 否 | AUTO_INCREMENT |
| `target_type` | VARCHAR(20) | 否 | — |
| `target_id` | BIGINT | 否 | — |
| `action` | VARCHAR(32) | 否 | — |
| `operator_id` | BIGINT | 否 | — |
| `reason` | VARCHAR(500) | 是 | — |
| `created_at` | DATETIME(6) | 否 | — |

**`topic`（V2）— 预设话题**（`uk_topic_name(name)`；V2 灌入 7 条种子：
表白 / 失物招领 / 二手 / 拼车 / 问答 / 吐槽 / 兼职，其中失物招领 / 二手 / 拼车 / 兼职 `requires_contact = b'1'`）

| 列 | 类型 | 可空 | 默认 |
| --- | --- | :-: | --- |
| `id` | BIGINT | 否 | AUTO_INCREMENT |
| `name` | VARCHAR(20) | 否 | — |
| `rules` | VARCHAR(500) | 否 | — |
| `requires_contact` | BIT(1) | 否 | — |
| `enabled` | BIT(1) | 否 | — |
| `sort_order` | INT | 否 | — |
| `created_at` / `updated_at` | DATETIME(6) | 否 | — |

**`post`（V2）— 主帖**

| 列 | 类型 | 可空 | 默认 |
| --- | --- | :-: | --- |
| `id` | BIGINT | 否 | AUTO_INCREMENT |
| `author_id` | BIGINT | 否 | — |
| `topic_id` | BIGINT | 否 | — |
| `content` | VARCHAR(2000) | 否 | — |
| `contact` | VARCHAR(200) | 是 | — |
| `contact_visibility` | VARCHAR(20) | 否 | — |
| `status` | VARCHAR(20) | 否 | — |
| `anonymous_code` | VARCHAR(32) | 否 | — |
| `review_reason` | VARCHAR(500) | 是 | — |
| `review_by` | BIGINT | 是 | — |
| `review_at` | DATETIME(6) | 是 | — |
| `taken_down_at` | DATETIME(6) | 是 | — |
| `report_count` | INT | 否 | 0 |
| `comment_count` | INT | 否 | 0 |
| `risk_score` | INT | 否 | 0 |
| `created_at` / `updated_at` | DATETIME(6) | 否 | — |

索引：`idx_post_feed(status,id)`、`idx_post_topic_feed(topic_id,status,id)`、`idx_post_author(author_id,created_at)`、
`idx_post_pending(status,created_at,id)`（V5）、`idx_post_status_created(status,created_at)`（V7）。

**`post_image`（V2）— 帖子图片**（`idx_post_image_post(post_id, sort_order)`；
`sensitive_checked` 一期恒为 `false`，字段保留给二期图像识别）

| 列 | 类型 | 可空 | 默认 |
| --- | --- | :-: | --- |
| `id` | BIGINT | 否 | AUTO_INCREMENT |
| `post_id` | BIGINT | 否 | — |
| `url` | VARCHAR(500) | 否 | — |
| `thumbnail_url` | VARCHAR(500) | 是 | — |
| `sensitive_checked` | BIT(1) | 否 | — |
| `sort_order` | INT | 否 | — |
| `created_at` / `updated_at` | DATETIME(6) | 否 | — |

**`comment`（V2）— 二级评论**（`parent_id` 为 NULL 即一级评论）

| 列 | 类型 | 可空 | 默认 |
| --- | --- | :-: | --- |
| `id` | BIGINT | 否 | AUTO_INCREMENT |
| `post_id` | BIGINT | 否 | — |
| `author_id` | BIGINT | 否 | — |
| `parent_id` | BIGINT | 是 | — |
| `content` | VARCHAR(500) | 否 | — |
| `status` | VARCHAR(20) | 否 | — |
| `anonymous_code` | VARCHAR(32) | 否 | — |
| `created_at` / `updated_at` | DATETIME(6) | 否 | — |

索引：`idx_comment_post(post_id, created_at)`、`idx_comment_parent(parent_id, created_at)`（V5）。

**`like_record`（V2）— 点赞**（脚本命名说明：设计稿叫 `like`，但 `LIKE` 是 MySQL 8 保留字）

| 列 | 类型 | 可空 | 默认 |
| --- | --- | :-: | --- |
| `id` | BIGINT | 否 | AUTO_INCREMENT |
| `target_type` | VARCHAR(20) | 否 | — |
| `target_id` | BIGINT | 否 | — |
| `user_id` | BIGINT | 否 | — |
| `created_at` / `updated_at` | DATETIME(6) | 否 | — |

**`report`（V3）— 举报**

| 列 | 类型 | 可空 | 默认 |
| --- | --- | :-: | --- |
| `id` | BIGINT | 否 | AUTO_INCREMENT |
| `target_type` | VARCHAR(20) | 否 | — |
| `target_id` | BIGINT | 否 | — |
| `reporter_id` | BIGINT | 否 | — |
| `reason_code` | VARCHAR(32) | 否 | — |
| `detail` | VARCHAR(500) | 是 | — |
| `status` | VARCHAR(20) | 否 | — |
| `handled_by` | BIGINT | 是 | — |
| `handled_at` | DATETIME(6) | 是 | — |
| `created_at` / `updated_at` | DATETIME(6) | 否 | — |

**`appeal`（V3）— 申诉**

| 列 | 类型 | 可空 | 默认 |
| --- | --- | :-: | --- |
| `id` | BIGINT | 否 | AUTO_INCREMENT |
| `target_type` | VARCHAR(20) | 否 | — |
| `target_id` | BIGINT | 否 | — |
| `appellant_id` | BIGINT | 否 | — |
| `reason` | VARCHAR(500) | 否 | — |
| `status` | VARCHAR(20) | 否 | — |
| `reviewed_by` | BIGINT | 是 | — |
| `reviewed_at` | DATETIME(6) | 是 | — |
| `restored` | BIT(1) | 否 | b'0' |
| `created_at` / `updated_at` | DATETIME(6) | 否 | — |

**`announcement`（V4）— 公告**

| 列 | 类型 | 可空 | 默认 |
| --- | --- | :-: | --- |
| `id` | BIGINT | 否 | AUTO_INCREMENT |
| `title` | VARCHAR(120) | 否 | — |
| `content` | TEXT | 否 | — |
| `level` | VARCHAR(20) | 否 | — |
| `status` | VARCHAR(20) | 否 | — |
| `start_at` | DATETIME(6) | 否 | — |
| `end_at` | DATETIME(6) | 是 | — |
| `min_read_seconds` | INT | 否 | — |
| `require_scroll_to_end` | BIT(1) | 否 | — |
| `require_confirm` | BIT(1) | 否 | — |
| `supersedes_id` | BIGINT | 是 | — |
| `created_by` | BIGINT | 否 | — |
| `published_at` | DATETIME(6) | 是 | — |
| `revoked_at` | DATETIME(6) | 是 | — |
| `created_at` / `updated_at` | DATETIME(6) | 否 | — |

**`announcement_read`（V4）— 公告已读记录**

| 列 | 类型 | 可空 | 默认 |
| --- | --- | :-: | --- |
| `id` | BIGINT | 否 | AUTO_INCREMENT |
| `announcement_id` | BIGINT | 否 | — |
| `user_id` | BIGINT | 是 | — |
| `device_id` | VARCHAR(64) | 是 | — |
| `read_at` | DATETIME(6) | 否 | — |
| `read_duration_ms` | BIGINT | 否 | — |
| `confirmed` | BIT(1) | 否 | — |
| `created_at` / `updated_at` | DATETIME(6) | 否 | — |

**`notification`（V4）— 个人通知**

| 列 | 类型 | 可空 | 默认 |
| --- | --- | :-: | --- |
| `id` | BIGINT | 否 | AUTO_INCREMENT |
| `user_id` | BIGINT | 否 | — |
| `type` | VARCHAR(32) | 否 | — |
| `title` | VARCHAR(120) | 否 | — |
| `body` | VARCHAR(500) | 是 | — |
| `link` | VARCHAR(200) | 是 | — |
| `read_at` | DATETIME(6) | 是 | — |
| `created_at` / `updated_at` | DATETIME(6) | 否 | — |

**`review_proposal`（V6）— 处置提案**

| 列 | 类型 | 可空 | 默认 |
| --- | --- | :-: | --- |
| `id` | BIGINT | 否 | AUTO_INCREMENT |
| `target_type` | VARCHAR(20) | 否 | — |
| `target_id` | BIGINT | 否 | — |
| `action` | VARCHAR(32) | 否 | — |
| `reason_code` | VARCHAR(32) | 否 | — |
| `detail` | VARCHAR(500) | 是 | — |
| `proposer_id` | BIGINT | 否 | — |
| `snapshot_size` | INT | 否 | — |
| `status` | VARCHAR(20) | 否 | — |
| `resolution` | VARCHAR(200) | 是 | — |
| `resolved_by` | BIGINT | 是 | — |
| `resolved_at` | DATETIME(6) | 是 | — |
| `created_at` / `updated_at` | DATETIME(6) | 否 | — |
| `open_target_key` | VARCHAR(40) | 是 | — |

**`review_vote`（V6）— 表决投票**

| 列 | 类型 | 可空 | 默认 |
| --- | --- | :-: | --- |
| `id` | BIGINT | 否 | AUTO_INCREMENT |
| `proposal_id` | BIGINT | 否 | — |
| `voter_id` | BIGINT | 否 | — |
| `decision` | VARCHAR(10) | 否 | — |
| `created_at` | DATETIME(6) | 否 | — |

**`uploaded_file`（V8）— 上传文件登记**（V8 从 `post_image` 回填历史数据）

| 列 | 类型 | 可空 | 默认 |
| --- | --- | :-: | --- |
| `id` | BIGINT | 否 | AUTO_INCREMENT |
| `uploader_id` | BIGINT | 否 | — |
| `object_key` | VARCHAR(255) | 否 | — |
| `bound_post_id` | BIGINT | 是 | — |
| `created_at` / `updated_at` | DATETIME(6) | 否 | — |

</details>

> ⚠️ `post.report_count` 的语义在迁移脚本中有一处跨版本注释差异：`V2` 注释为「有效举报数」，
> `V3` 注释为「待处理举报数」。两处均为脚本原文，引用时请留意版本。

---

## 八、接口一览

统一约定：响应体 `ApiResponse<T>`；列表一律游标分页（`cursor`）；错误码见
[第九章](#九错误码)。「权限」列指管理端所需**权限点**（`@PreAuthorize` 的实际取值）。

### 8.1 认证（`/api/auth`，全部匿名可调）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/email/code` | 发送验证码（白名单域名 + 频率限制） |
| POST | `/api/auth/verify-email` | 校验验证码，返回邮箱令牌与是否需要绑定学号 |
| POST | `/api/auth/bind-student-id` | 绑定学号，创建用户并签发 JWT |

### 8.2 学生端

| 方法 | 路径 | 登录 | 说明 |
| --- | --- | :-: | --- |
| GET | `/api/users/me` | ✅ | 当前用户档案（信用分、角色、状态） |
| GET | `/api/topics` | ❌ | 预设话题列表 |
| GET | `/api/posts` | ❌ | 信息流（游标；可按话题筛选） |
| POST | `/api/posts` | ✅ | 发布（落 `Pending`；未读紧急公告时 `428`） |
| GET | `/api/posts/mine` | ✅ | 我的帖子（含各审核状态） |
| GET | `/api/posts/{postId}` | ❌ | 详情（按 viewer 脱敏联系方式） |
| POST / DELETE | `/api/posts/{postId}/like` | ✅ | 点赞 / 取消（幂等） |
| GET | `/api/posts/{postId}/comments` | ❌ | 评论列表（二级树） |
| POST | `/api/posts/{postId}/comments` | ✅ | 发表评论 / 回复 |
| DELETE | `/api/comments/{commentId}` | ✅ | 删除自己的评论 |
| POST / DELETE | `/api/comments/{commentId}/like` | ✅ | 评论点赞 / 取消 |
| POST | `/api/uploads/images` | ✅ | 上传图片（multipart；JPG/PNG/WebP，≤5MB） |
| POST | `/api/uploads/image-urls` | ✅ | 批量刷新私有图片的签名 URL |
| GET | `/uploads/**` | ❌ | 图片受控下发（按帖子可见性；不可见与不存在共用 404） |
| GET | `/api/announcements/pending` | ❌ | 待读公告（含强读要求） |
| GET | `/api/announcements` | ❌ | 公告历史 |
| POST | `/api/announcements/{id}/read` | ❌ | 标记已读（服务端逐项校验三条件） |
| GET | `/api/notifications` | ✅ | 通知列表 |
| GET | `/api/notifications/unread-count` | ✅ | 未读计数 |
| POST | `/api/notifications/{id}/read` | ✅ | 单条已读 |
| POST | `/api/notifications/read-all` | ✅ | 全部已读 |
| POST | `/api/reports` | ✅ | 举报（重复 `409`） |
| GET | `/api/reports/mine` | ✅ | 我对某目标的举报状态 |
| POST | `/api/appeals` | ✅ | 提交申诉（作者 / 窗口期内 / 每帖一次） |

### 8.3 管理端

| 方法 | 路径 | 权限点 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/admin/me` | （已认证） | 当前管理员身份与权限点集合 |
| GET | `/api/admin/stats/overview` | `STATS_VIEW` | 看板总览 |
| GET | `/api/admin/review-queue` | `MODERATION_VIEW` | 审核队列（理由 / 目标类型 / 排序 / 只看超时） |
| GET | `/api/admin/pending-posts` | `MODERATION_VIEW` | 待审帖子队列 |
| GET | `/api/admin/review-context` | `MODERATION_VIEW` | 审核上下文（聚合） |
| GET | `/api/admin/review-context/history` | `MODERATION_VIEW` | 作者动态 + 处置历史 |
| GET | `/api/admin/todo-counts` | `MODERATION_VIEW` | 待办角标计数 |
| POST | `/api/admin/posts/{postId}/approve` | `POST_REVIEW` | 放行待审帖 |
| POST | `/api/admin/posts/{postId}/reject` | `POST_REVIEW` | 驳回待审帖（必填理由） |
| POST | `/api/admin/posts/{postId}/take-down` | `PROPOSAL_CREATE` | 发起「下架」提案 |
| POST | `/api/admin/posts/{postId}/hide-contact` | `PROPOSAL_CREATE` | 发起「隐藏联系方式」提案 |
| POST | `/api/admin/posts/{postId}/uphold-reports` | `PROPOSAL_CREATE` | 发起「维持下架」提案 |
| POST | `/api/admin/comments/{commentId}/remove` | `PROPOSAL_CREATE` | 发起「移除评论」提案 |
| POST | `/api/admin/reports/dismiss` | `REPORT_DISMISS` | 驳回举报（内容不违规，结案） |
| POST | `/api/admin/posts/{postId}/restore` | `DISPOSITION_REVOKE` | 恢复可见（仅超管） |
| POST | `/api/admin/posts/{postId}/show-contact` | `DISPOSITION_REVOKE` | 撤销隐藏联系方式（仅超管） |
| GET | `/api/admin/appeals` | `APPEAL_REVIEW` | 申诉队列 |
| POST | `/api/admin/appeals/{appealId}/accept` | `APPEAL_REVIEW` | 通过申诉（恢复内容） |
| POST | `/api/admin/appeals/{appealId}/reject` | `APPEAL_REVIEW` | 维持原判 |
| POST | `/api/admin/proposals` | `PROPOSAL_CREATE` | 发起处置提案 |
| GET | `/api/admin/proposals` | `PROPOSAL_VOTE` | 提案列表 |
| GET | `/api/admin/proposals/{proposalId}` | `PROPOSAL_VOTE` | 提案详情与汇总进度 |
| POST | `/api/admin/proposals/{proposalId}/vote` | `PROPOSAL_VOTE` | 投票（一人一票） |
| GET | `/api/admin/proposals/escalated` | `PROPOSAL_ARBITRATE` | 僵局提案列表 |
| POST | `/api/admin/proposals/{proposalId}/arbitrate` | `PROPOSAL_ARBITRATE` | 超管裁决（必填理由） |
| GET | `/api/admin/users` | `USER_MANAGE` | 用户列表（状态 / 角色 / 信用分 / 注册时间） |
| GET | `/api/admin/users/export` | `USER_MANAGE` | 导出用户 CSV（超限 `QL-400-014`） |
| POST | `/api/admin/users/{userId}/ban` | `USER_MANAGE` | 封禁（1–3650 天 + 理由） |
| POST | `/api/admin/users/{userId}/unban` | `USER_MANAGE` | 解封（理由） |
| POST | `/api/admin/users/{userId}/credit` | `USER_MANAGE` | 调整信用分（−100…100，非 0） |
| GET | `/api/admin/users/committee` | `ROLE_GRANT` | 委员会成员名单 |
| POST | `/api/admin/users/{userId}/role` | `ROLE_GRANT` | 下放 / 收回角色（上限 6 人） |
| POST | `/api/admin/announcements` | `ANNOUNCEMENT_DRAFT` | 创建公告草稿 |
| GET | `/api/admin/announcements` | `ANNOUNCEMENT_DRAFT` | 公告列表 |
| POST | `/api/admin/announcements/{id}/publish` | `ANNOUNCEMENT_MANAGE` | 发布（仅超管） |
| POST | `/api/admin/announcements/{id}/revoke` | `ANNOUNCEMENT_MANAGE` | 撤回（仅超管） |
| GET | `/api/admin/audit-logs` | `AUDIT_READ` | 审计日志（动作 / 时间筛选） |
| GET | `/api/admin/audit-logs/export` | `AUDIT_READ` | 导出审计 CSV |

**匿名可读的边界**（`SecurityConfig` 显式放行，其余一律 `authenticated()`）：
`/api/auth/**`、`/error`、`/actuator/health(/**)`、`GET /api/topics`、`GET /api/posts`、
`GET /api/posts/*`、`GET /api/posts/*/comments`、`GET /uploads/**`、
`GET /api/announcements`、`GET /api/announcements/pending`、`POST /api/announcements/*/read`。
注意 `GET /api/posts/mine` 需登录（它匹配单段通配，必须在放行前拦截）。

---

## 九、错误码

格式 `QL-<HTTP 状态>-<序号>`，便于日志检索与前端分支处理。

| 状态 | 错误码 | 含义 |
| --- | --- | --- |
| 400 | `QL-400-001` | 请求参数不合法 |
| 400 | `QL-400-002` | 仅支持校园邮箱认证（域名白名单） |
| 400 | `QL-400-003` | 验证码不正确或已失效（「未获取/过期/填错」统一） |
| 400 | `QL-400-005` | 该话题要求留下联系方式 |
| 400 | `QL-400-006` | 图片格式 / 大小不合法 |
| 400 | `QL-400-007` | 分页游标不合法 |
| 400 | `QL-400-008` | 内容命中敏感词（社区规范） |
| 400 | `QL-400-009` | 命中隐私信息（身份证 / 银行卡） |
| 400 | `QL-400-010` | 不能举报自己发布的内容 |
| 400 | `QL-400-011` | 公告阅读时长不足 |
| 400 | `QL-400-012` | 公告未滚到底 / 未勾选已了解 |
| 400 | `QL-400-013` | 提案动作与目标当前状态不匹配 |
| 400 | `QL-400-014` | 导出数据量超过单次上限（不静默截断） |
| 400 | `QL-400-015` | 引用的图片不存在（未通过本站上传） |
| 401 | `QL-401-001` | 请先完成认证 |
| 401 | `QL-401-002` | 邮箱认证凭证无效或已过期 |
| 403 | `QL-403-001` | 无权执行该操作 |
| 403 | `QL-403-002` | 账号已被限制 |
| 403 | `QL-403-003` | 只能操作自己发布的内容 |
| 403 | `QL-403-004` | 信用分不足，无法发帖 |
| 403 | `QL-403-005` | 不能复审自己处置过的内容 |
| 403 | `QL-403-006` | 超级管理员受保护 |
| 403 | `QL-403-007` | 不能修改自己的角色 |
| 403 | `QL-403-008` | 只能引用自己上传的图片 |
| 404 | `QL-404-001` | 用户不存在 |
| 404 | `QL-404-002` | 帖子不存在或已被删除 |
| 404 | `QL-404-003` | 话题不存在 |
| 404 | `QL-404-004` | 评论不存在或已被删除 |
| 404 | `QL-404-005` | 没有待处理的举报 |
| 404 | `QL-404-006` | 申诉不存在 |
| 404 | `QL-404-007` | 公告不存在 |
| 404 | `QL-404-008` | 通知不存在 |
| 404 | `QL-404-009` | 处置提案不存在 |
| 404 | `QL-404-010` | 图片不存在（与「不可见」共用，不泄露存在性） |
| 409 | `QL-409-001` | 该邮箱已完成认证 |
| 409 | `QL-409-003` | 该邮箱或学号已完成认证 |
| 409 | `QL-409-004` | 当前审核状态不允许该操作（禁跳态） |
| 409 | `QL-409-005` | 已举报过该内容 |
| 409 | `QL-409-006` | 该内容已提交过申诉 |
| 409 | `QL-409-007` | 申诉期已过 |
| 409 | `QL-409-008` | 公告当前状态不允许该操作 |
| 409 | `QL-409-009` | 管理员名额已满（委员会 ≤ 6） |
| 409 | `QL-409-010` | 该内容已有进行中的处置提案 |
| 409 | `QL-409-011` | 你已经投过票 |
| 409 | `QL-409-012` | 该图片已被使用 |
| 428 | `QL-428-001` | 存在未读的紧急公告（响应携带公告内容） |
| 429 | `QL-429-001` | 验证码发送过于频繁 |
| 429 | `QL-429-002` | 验证失败次数过多 |
| 429 | `QL-429-003` | 上传过于频繁 |
| 429 | `QL-429-004` | 今日公告发布已达上限 |
| 429 | `QL-429-005` | 公告已读操作过于频繁 |
| 429 | `QL-429-006` | IP 请求过于频繁（全局限流） |
| 500 | `QL-500-001` | 服务暂时不可用 |
| 500 | `QL-500-002` | 文件存储暂时不可用 |

> 错误信息**不得**回显内部堆栈、SQL、手机号、学号（安全红线）。

---

## 十、配置参考

### 10.1 后端配置（`application.yml`）

模板为 `qinglan-server/src/main/resources/application.example.yml`（可提交）；
真实的 `application.yml` 含本地密钥、**已被 .gitignore 忽略、不得提交**。

| 配置键 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | `8080` | 服务端口 |
| `spring.threads.virtual.enabled` | `true` | 启用虚拟线程（仅用于 IO 密集） |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/qinglan?...` | `characterEncoding=utf8` + `serverTimezone=UTC` |
| `spring.jpa.hibernate.ddl-auto` | `validate` | 表结构只由 Flyway 管；**禁止** create / update |
| `spring.flyway.locations` | `classpath:db/migration` | 迁移脚本位置 |
| `spring.servlet.multipart.max-file-size` | `5MB` | 单图上限 |
| `spring.servlet.multipart.max-request-size` | `10MB` | 与 nginx `client_max_body_size` 对齐 |
| `qinglan.crypto.aes-key` | — | **必填**：32 字节 Base64（学号加密） |
| `qinglan.crypto.email-pepper` | — | **必填**：邮箱 / 学号哈希盐 |
| `qinglan.jwt.secret` | — | **必填**：≥32 字节 Base64 |
| `qinglan.jwt.access-token-ttl` | `2h` | 访问令牌有效期 |
| `qinglan.jwt.email-token-ttl` | `10m` | 邮箱令牌有效期（含 jti 一次性消费） |
| `qinglan.auth.code-store` | `memory` | `memory` / `redis`；生产必须 `redis` |
| `qinglan.auth.code-ttl` | `5m` | 验证码有效期 |
| `qinglan.auth.code-length` | `6` | 验证码长度 |
| `qinglan.auth.max-verify-attempts` | `5` | 单验证码最大校验次数 |
| `qinglan.auth.send-interval` | `60s` | 发送间隔 |
| `qinglan.auth.allowed-email-domains` | 空 | 校园邮箱白名单（逗号分隔）；**留空即全拒** |
| `qinglan.auth.expose-code` | `false` | 是否在响应中回显验证码（**仅本地**） |
| `qinglan.auth.mail.transport` | `logging` | `logging`（只打日志）/ `smtp` |
| `qinglan.auth.mail.host/port/username/password/from/tls` | — | SMTP 参数（587 一般开 STARTTLS） |
| `qinglan.post.min-credit-to-post` | `60` | 发帖所需最低信用分 |
| `qinglan.post.max-images-per-post` | `9` | 单帖图片上限 |
| `qinglan.moderation.sensitive-word-file` | `classpath:moderation/sensitive-words.txt` | 敏感词库 |
| `qinglan.moderation.privacy-rules` | 身份证 / 银行卡正则 | 隐私信息硬拦截规则 |
| `qinglan.review.auto-takedown-threshold` | `3` | 有效举报达阈值自动下架 |
| `qinglan.review.appeal-window` | `7d` | 申诉窗口 |
| `qinglan.announcement.min-read-seconds` | `5` | 公告默认最短阅读秒数 |
| `qinglan.announcement.max-publishes-per-day` | `5` | 每日发布上限 |
| `qinglan.announcement.max-reads-per-window` | `30` | 已读上报窗口内上限 |
| `qinglan.announcement.read-rate-window` | `1m` | 已读限流窗口 |
| `qinglan.upload.max-per-window` / `window` | `30` / `1m` | 上传限流 |
| `qinglan.upload.orphan-cleanup-enabled` | `true` | 是否回收未绑定对象 |
| `qinglan.upload.orphan-retention` | `24h` | 孤儿保留期 |
| `qinglan.store.type` | `memory` | 短时效存储实现；生产必须 `redis` |
| `qinglan.storage.type` | `local` | `local` / `s3`；生产必须 `s3` |
| `qinglan.storage.local-root` | `./data/uploads` | 本地存储根目录（仅 local） |
| `qinglan.storage.image-signing-secret` | 空 | 私有图片签名密钥；生产必填、不得回落 JWT |
| `qinglan.storage.image-signing-ttl` | `10m` | 签名有效期（硬上限 1 小时） |
| `qinglan.storage.s3.endpoint/region/bucket/access-key/secret-key` | — | 仅 s3；前四项 + 凭据必填 |
| `qinglan.security.trusted-proxy-hops` | `0` | 信任的代理跳数（一层 nginx 设 `1`） |
| `qinglan.security.rate-limit.enabled` | `true` | IP 限流开关 |
| `qinglan.security.rate-limit.max-requests` | `20` | 窗口内单 IP 上限 |
| `qinglan.security.rate-limit.paths` | `/api/auth/email/code` | 受保护路径（Ant 风格） |
| `management.endpoints.web.exposure.include` | `health` | 只暴露健康检查 |
| `management.health.redis.enabled` | `false` | 生产用 Redis 时置 `true` |
| `logging.pattern.level` | `%5p [%X{traceId:-}]` | 日志级别前缀带 traceId |

### 10.2 生产启动守卫（`ProdEnvironmentGuard`）

`SPRING_PROFILES_ACTIVE=prod` 时逐项校验，**任一不满足即拒绝启动**：

- `qinglan.auth.expose-code = false`（不得回显验证码）
- `qinglan.auth.code-store = redis` 且 `qinglan.store.type = redis`
- `qinglan.storage.type = s3`
- `qinglan.storage.image-signing-secret` **显式配置**（不得回落到 `qinglan.jwt.secret`）
- `spring.jpa.hibernate.ddl-auto` 不在 `{create, create-drop, update}`
- `spring.datasource.url` **不指向** localhost / 127.0.0.1 / ::1 / 0.0.0.0

### 10.3 部署环境变量（`.env`）

模板 [.env.example](.env.example) 是「一套环境变量部署全栈」的唯一真源，每项都带
`[必填]` / `[不可轮换]` / `[生产必须]` 标记：

| 分组 | 变量 |
| --- | --- |
| 运行时 | `SPRING_PROFILES_ACTIVE` |
| MySQL | `MYSQL_ROOT_PASSWORD` · `QINGLAN_DB_NAME` · `QINGLAN_DB_USERNAME` · `QINGLAN_DB_PASSWORD` · `QINGLAN_DB_URL` |
| Redis | `REDIS_PASSWORD` · `QINGLAN_REDIS_HOST` · `QINGLAN_REDIS_PORT` · `QINGLAN_REDIS_PASSWORD` · `QINGLAN_REDIS_HEALTH` |
| 密钥 | `QINGLAN_CRYPTO_AES_KEY`（不可轮换）· `QINGLAN_CRYPTO_EMAIL_PEPPER`（不可轮换）· `QINGLAN_JWT_SECRET` |
| 认证 / 邮件 | `QINGLAN_AUTH_CODE_STORE` · `QINGLAN_AUTH_EMAIL_DOMAINS` · `QINGLAN_AUTH_EXPOSE_CODE` · `QINGLAN_AUTH_MAIL_TRANSPORT` · `QINGLAN_AUTH_MAIL_HOST/PORT/USERNAME/PASSWORD/FROM/TLS` |
| 存储 / 安全 | `QINGLAN_STORE_TYPE` · `QINGLAN_STORAGE_TYPE` · `QINGLAN_STORAGE_IMAGE_SIGNING_SECRET` · `QINGLAN_S3_ENDPOINT/REGION/BUCKET/ACCESS_KEY/SECRET_KEY` · `QINGLAN_STORAGE_ROOT` · `QINGLAN_UPLOAD_ORPHAN_CLEANUP_ENABLED` · `QINGLAN_UPLOAD_ORPHAN_RETENTION` · `QINGLAN_TRUSTED_PROXY_HOPS` · `QINGLAN_RATE_LIMIT_ENABLED` · `QINGLAN_RATE_LIMIT_MAX` |
| 编排（后端不读） | `HTTP_PORT` · `QINGLAN_IMAGE_TAG` |

> ⚠️ **`QINGLAN_CRYPTO_AES_KEY` 与 `QINGLAN_CRYPTO_EMAIL_PEPPER` 上线后不可轮换**：
> AES 密钥变了历史学号无法解密；email pepper 变了同邮箱算出不同 hash、账号关联能力丢失。
> 生成后必须**单独离线备份**。

---

## 十一、本地开发

### 环境要求

| 依赖 | 版本 | 备注 |
| --- | --- | --- |
| JDK | 21 | 后端编译与运行 |
| Node.js | 20+（CI 用 24，矩阵包见 [ci.yml](.github/workflows/ci.yml)） | 前端 |
| pnpm | 12（CI 固定） | workspace 统一管理，**在仓库根**安装 |
| MySQL | 8.x | 后端运行与**后端测试**都需真实库 |
| Redis | 7（可选） | 仅当 `code-store` / `store.type` 取 `redis` 时需要；默认 `memory`，本地无需 |
| Docker | 可选 | 仅完整部署需要 |

### 步骤

```bash
# 0. 前端依赖（仓库根执行一次）
pnpm install

# 1. 准备数据库
#    本地 MySQL 建库（默认库名 qinglan）：
#    CREATE DATABASE qinglan DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

# 2. 后端配置：从模板复制后填本地值（application.yml 被 .gitignore 忽略，不要提交）
cd qinglan-server
cp src/main/resources/application.example.yml src/main/resources/application.yml
#    必填：qinglan.crypto.aes-key（32 字节 Base64）、qinglan.crypto.email-pepper、qinglan.jwt.secret
#    本地联调可设：qinglan.auth.allowed-email-domains=qq.com（或你的测试域名）
#                  qinglan.auth.expose-code=true  +  qinglan.auth.mail.transport=logging

# 3. 启动后端（Flyway 自动迁移）
./mvnw spring-boot:run           # Windows: .\mvnw.cmd spring-boot:run
#    健康检查：GET http://localhost:8080/actuator/health

# 4. 启动前端（两个 dev server 已把 /api 与 /uploads 代理到 8080）
pnpm dev:web                     # 学生端  http://localhost:5173
pnpm dev:admin                   # 管理端  http://localhost:5174/admin/
```

生成随机密钥的参考命令：

```bash
openssl rand -base64 32     # QINGLAN_CRYPTO_AES_KEY（恰好 32 字节）
openssl rand -base64 32     # QINGLAN_CRYPTO_EMAIL_PEPPER
openssl rand -base64 48     # QINGLAN_JWT_SECRET
```

### 常见问题

| 现象 | 原因与处理 |
| --- | --- |
| 启动即失败，日志提到 `aes-key` / `pepper` / `secret` | 必填密钥为空（`qinglan.crypto.*` / `qinglan.jwt.secret`） |
| 启动失败，提示 `ddl-auto` 或数据源指向本机 | 误开了 `SPRING_PROFILES_ACTIVE=prod`；本地不要激活 prod |
| 邮箱验证永远失败 | `qinglan.auth.allowed-email-domains` 留空 = **全拒**（fail-closed），填测试域名 |
| 看不到验证码 | 本地把 `expose-code=true`（接口回显）或 `mail.transport=logging`（打进日志） |
| `/admin/` 白屏或刷新 404 | 管理端 `base` 与 `createWebHistory('/admin/')` 必须一致；学生端不得有 `/admin` 路由 |
| 端口冲突 | 学生端 5173、管理端 5174、后端 8080 三处错开 |

---

## 十二、测试与质量门禁

**交付门槛是三包各自的 `lint + typecheck + vitest + build`，缺一不可**；在自己那一包跑绿不等于
交付通过——改 `shared` 时必须把另外两包也跑一遍。

| 层 | 命令 | 内容 |
| --- | --- | --- |
| 后端单测 / 集成 | `cd qinglan-server && ./mvnw test` | JUnit 5 + Mockito，连**真实** MySQL（不 mock 数据库行为）；`@WebMvcTest` 验参数与状态码；`@DataJpaTest` 验 UNIQUE 与联表 |
| 后端架构门禁 | `./mvnw -Dtest=ModuleBoundaryTest test` | ArchUnit 校验跨模块白名单与 Controller 分层 |
| 后端静态门禁 | （编译期） | `-Xlint:all,-processing -Werror`：**任何 javac 警告都会让构建失败** |
| 前端三包 | `pnpm -r lint && pnpm -r typecheck && pnpm -r test && pnpm -r build` | Vitest + Vue Test Utils |
| 端到端（规划中） | Playwright | 覆盖「发帖 → 审核 → 下架」主流程 |

约定：

- 修 bug **先写能复现的失败测试，再修**；禁止为通过而删测试 / 放宽断言 / 加 `@Disabled`。
- **每条不变量至少一个针对性测试**（如举报重复 `409`、状态跳态 `409`、公告时长不足 `400`、
  匿名代号跨帖不同、未认证 viewer 的 `contact` 为 `null`）。
- Redis 相关用例由 `QINGLAN_REDIS_TEST` 条件开关：本机无 Redis 自动跳过，CI 中真跑。
- 测试的 `vi.mock` 按**模块标识符**拦截：模块搬到 `shared` 后，`vi.mock('@/api/x')` 必须同步改成
  `vi.mock('@shared/api/x')`，否则 mock 不命中、测试会去发真请求。

---

## 十三、部署与运维

完整手册见 [deploy/README.md](deploy/README.md)，此处为要点。

### 首次部署

```bash
cp .env.example .env              # 逐项填值；.env 已被 .gitignore 忽略
pnpm install && pnpm -r build     # 构建两个前端产物 dist/
docker compose build
docker compose up -d
curl -fsS http://localhost/       # 冒烟
```

### 编排要点（[docker-compose.yml](docker-compose.yml)）

- 服务：`mysql:8`（utf8mb4 + `--default-time-zone=+00:00`）· `redis:7`（`--requirepass` + AOF）·
  `app`（`depends_on` 等 mysql/redis **健康**后再起，避免 Flyway 撞上「库还没起来」）· `nginx:1.27-alpine`
- 只有 nginx 发布端口（默认 80）；`app` / `mysql` / `redis` 仅在内部网络
- 三个命名卷：`mysql-data` · `redis-data` · `uploads-data`
- `app` 镜像：多阶段构建、**非 root（uid 1001）** 运行、`HEALTHCHECK` 打 `/actuator/health`、
  `QINGLAN_IMAGE_TAG` 控制 tag 便于回滚

### nginx（[deploy/nginx.conf](deploy/nginx.conf)）

| 位置 | 行为 |
| --- | --- |
| `/` | 学生端 SPA（`try_files … /index.html`） |
| `/admin/` | 管理端 SPA（回退到 **`/admin/index.html`**）；`/admin` 301 → `/admin/` |
| `/assets/`、`/admin/assets/` | 带内容哈希资源，长缓存 `max-age=31536000, immutable` |
| `/api/` | 反代 `app:8080`，追加 `X-Forwarded-For` |
| `/uploads/` | **反代** `app:8080`（受控下发，非静态托管） |
| 安全头 | `X-Frame-Options: DENY` + `CSP frame-ancestors 'none'` + `nosniff` + `Referrer-Policy`（子 location 需重新声明，nginx `add_header` 是覆盖而非追加） |

`client_max_body_size 10m` 与后端 `max-request-size` 对齐，nginx 先拦超限（`413`），不让请求打到后端。

### 备份 / 恢复 / 回滚

- 备份：`deploy/backup.sh` 产出 `qinglan-db-*.sql.gz` + `qinglan-uploads-*.tar.gz` + `sha256`
  （详见 [deploy/README.md](deploy/README.md) 第 7 节）。
- 恢复顺序：**先停写 → 再恢复 → 再校验**；应用能起来 = Flyway 校验 + JPA `validate` 都通过。
- 回滚：`QINGLAN_IMAGE_TAG` 指回旧 tag 后 `docker compose up -d app`（单实例，有秒级中断）。

### 已知缺口（诚实汇总）

以 [deploy/README.md](deploy/README.md) 第 12 节与「验证状态」一节为准，其中包括：
恢复演练与 Flyway 迁移演练**尚未实际演练**、版本号规则的历史问题、
单实例发布非零中断、无第三方内容安全 API 与图像识别。请以上述文档的最新内容为准，勿据本 README 推断。

---

## 十四、持续集成

[.github/workflows/ci.yml](.github/workflows/ci.yml)，触发于 `push: main` 与所有 PR：

| Job | 运行环境 | 内容 |
| --- | --- | --- |
| `backend-static-analysis` | ubuntu + JDK 21 | ArchUnit 跨模块白名单 + javac lint（不依赖数据库，最快失败、归因清晰） |
| `backend` | ubuntu + JDK 21 + MySQL 8 + Redis 7（service container） | `./mvnw test`；`QINGLAN_REDIS_TEST=true` 打开 Redis 真跑用例；S3 用例走内嵌 S3Mock（无需 Docker） |
| `frontend` | ubuntu + Node + pnpm 12 | 矩阵 `shared` / `qinglan-web` / `qinglan-admin`（`fail-fast: false`），各跑 lint / typecheck / test / build |
| `frontend-audit` | ubuntu + pnpm | 只审**生产依赖**（`pnpm audit --prod --audit-level=high`），出现 high / critical 阻断合并 |

---

## 十五、文档导航

| 文档 | 回答什么问题 | 冲突时 |
| --- | --- | --- |
| [AGENTS.md](AGENTS.md) | 怎么写代码、边界在哪、测试门槛、安全红线 | **以它为准** |
| [docs/青栏项目设计方案.md](docs/青栏项目设计方案.md) | 做什么、为什么这么做（合规底线 / 身份模型 / 审核体系 / 公告通知 / 架构 / 数据模型 / 风控 / 开发计划 / 风险清单） | — |
| [docs/青栏管理端改造方案（工作台化）.md](docs/青栏管理端改造方案（工作台化）.md) | 管理端工作台化的设计 | — |
| [docs/青栏前端生产化改造方案.md](docs/青栏前端生产化改造方案.md) | 前端生产化改造 | — |
| [docs/青栏架构与生产环境全量问题清单与建议.md](docs/青栏架构与生产环境全量问题清单与建议.md) | 架构与生产环境的全量问题盘点 | — |
| [deploy/README.md](deploy/README.md) | 部署、备份、恢复、回滚、运维待办 | — |
| [docs/备案材料/](docs/备案材料) | 运营主体说明 / 社区公约 / 审核规则 / 隐私政策 / 投诉举报入口 | — |
| `修复记录.txt` | 逐条问题的现象 / 根因 / 处理 / 验证（倒序，纯文本，非 Markdown） | — |

**AGENTS.md 的三层约束**：① 模块边界（代码放哪）② 领域不变量（数据不能错）
③ 交付自检清单（交付前别忘）。

---

## 十六、贡献约定

1. **先读再改**：改前读完目标文件及其测试与调用方；只改任务范围内的代码，范围外问题先报告。
2. **验证必须真跑**：后端 `./mvnw test`；前端三包各自 `lint / typecheck / test / build`（在
   PR 里贴实际输出）。
3. **提交**：遵循 [Conventional Commits](https://www.conventionalcommits.org/)：
   `feat|fix|refactor|test|docs|chore(server|web|admin|shared|post|review|announcement): 说明`，
   正文写「为什么」，不只是「改了什么」。
4. **PR**：说明改了什么 / 为什么 / 如何验证 / **是否影响第四章不变量** / 是否破坏性变更。
5. **不确定时**（[AGENTS.md](AGENTS.md) 第十一章）：
   ① 有明确规则 → 执行；② 涉及不变量 → 先写测试证明成立再改；
   ③ 触及安全红线 → 停下说明风险；④ 影响架构的多种方案 → 列选项交人决策；
   ⑤ 纯样式 / 命名等无风险选择 → 自行决定并保持与既有代码一致。

---

## 十七、术语表

| 术语 | 含义 |
| --- | --- |
| **匿名代号** | 帖子 / 评论对外展示的昵称，`hash(post_id + author_id + salt)` 派生；**同一用户在不同帖子下代号不同**，故不可跨帖关联 |
| **匿名 salt** | 每个用户一个（`user.anonymous_salt`），参与代号派生，使不同用户的代号不可互相推算 |
| **委员会** | 管理员 + 学生评议员（`REVIEWER`），参与处置表决；**合计上限 6 人** |
| **提案（Proposal）** | 对已发布内容发起处置（下架 / 移除评论 / 隐藏联系方式 / 维持下架），需委员会表决 |
| **表决快照** | 提案创建时的在册委员会人数（`snapshot_size`），作为表决分母；成员变动不影响进行中的表决 |
| **僵局（Escalated）** | 同意票已不可能过半，提案上报超管裁决 |
| **裁决（Arbitrate）** | 超管对僵局提案作出准予 / 不予的最终决定（必填理由） |
| **复审人 ≠ 原处置人** | 申诉由不同于原处置人的管理员复核（`QL-403-005`） |
| **强读（强制阅读）** | 公告弹窗在「时长 + 滚动 + 勾选」任一项未满足时不可关闭；发帖入口 `428` 兜底 |
| **撤销（DISPOSITION_REVOKE）** | 恢复可见 / 撤销隐藏联系方式，仅超管——防止少数委员静默推翻多数决 |
| **权限点** | 管理端授权的最小单位（13 个），角色 → 权限点只在 `UserRole#permissions()` 一处 |
| **428** | HTTP「Precondition Required」，用于「存在未读紧急公告」时拦截写操作并携带公告内容 |
| **先审后发** | 主帖落 `Pending`，放行后才出现在信息流 |
| **先发后审** | 评论发表即公开，治理手段是移除 |
| **受控下发** | 图片不静态托管，经后端按帖子可见性判定；不可见与不存在共用 404 |
| **签名 URL** | 私有图片的限时访问地址（HMAC 签名，默认 10 分钟） |
| **孤儿对象** | 上传后超过保留期仍未被任何帖子引用的文件，定时清理 |

---

