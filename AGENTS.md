# AGENTS.md — 青栏 Qinglan

> 本文件是**青栏项目**的 AI 编码规范。项目名 `qinglan` 已定稿，不得改动。
> 方案详见 `docs/青栏项目设计方案.md`；本文件定义"怎么写代码"，两者冲突时以本文件的工程约束为准。
> 规则写得细，是为了减少来回确认——按此执行即可，遇到未覆盖的情况按第十一章处理。
> 阅读顺序：先看目录定位，再看第三章（边界）与第四章（不变量），其余按需查。

---

## 目录

| 章 | 内容 | 什么时候读 |
| --- | --- | --- |
| [一](#一项目结构) | 项目结构与文件归属 | 新建文件前 |
| [二](#二命令) | 命令与环境要求 | 跑构建 / 测试前 |
| [三](#三模块边界must违反即打回) | 分层、模块白名单、前端分层、接口边界 | 划包 / 定接口前 |
| [四](#四领域不变量不可违反违反即数据事故) | 7 条不可违反的不变量 | 改动涉及数据前 |
| [五](#五skill-路由) | Skill 路由与迁移脚本约定 | 写代码前 |
| [六](#六java-编码约束) | Java 编码约束 | 写后端时 |
| [七](#七前端编码约束) | 前端编码约束 | 写前端时 |
| [八](#八测试要求交付门槛) | 测试与交付门槛 | 交付前 |
| [九](#九安全红线must-not) | 安全红线 | 碰权限 / 密钥 / 输出时 |
| [十](#十工作流与提交) | 工作流、提交、分支 | 每次任务收尾 |
| [十一](#十一不确定时怎么办) | 不确定时的判断顺序 | 拿不准时 |
| [十二](#十二交付自检清单) | 交付自检清单 | 交付前逐条核对 |

---

## 一、项目结构

三工程 + 一个共享包，物理隔离：**`qinglan-web` 与 `qinglan-admin` 不得互相 import，两者都只允许依赖 `shared`**（`shared` 也不得反向依赖任何应用）。

```
qinglan-server/          后端 Java 21 + Spring Boot 3.5，Maven（端口 8080）
  src/main/java/com/qinglan/
    ├── auth/            校园邮箱认证、验证码、学号绑定、JWT 与安全过滤器（mail/ store/ security/ config/）
    ├── post/            帖子、图片（受控下发 / 上传 / 可见性）、话题（config/ controller/ dto/ entity/ repository/ service/）
    ├── comment/         二级评论
    ├── review/          举报、审核队列、处置、申诉复审、提案与表决、看板统计、审计查询
    ├── announcement/    公告与强制阅读
    ├── notification/    个人通知聚合（实现 common/notify 的 NotificationPort）
    ├── user/            身份加密、信用分、封禁、委员会与权限点
    └── common/          异常与响应封装、审计底座（audit）、匿名代号（anonymous）、内容审核（moderation）、
                        存储（storage）、短时效存储（store）、游标与分页（web）、通用安全与限流（security）、
                        加解密（crypto）、点赞（interaction）、通知端口（notify）、开发/生产守卫（config）

  src/main/resources/
    ├── db/migration/    Flyway 迁移（V1…V8，只增不改）
    ├── moderation/      敏感词库（sensitive-words.txt）
    └── application.example.yml  配置模板（可提交；application.yml 被 .gitignore 忽略）

  src/test/java/com/qinglan/     JUnit 5 + Mockito；架构/授权门禁在 com/qinglan/support/
                                 （ModuleBoundaryTest：模块白名单 + Controller 分层；PermissionAnnotationTest：授权注解）

包名一律 `com.qinglan.<module>`，如 `com.qinglan.post`、`com.qinglan.review`。
新文件必须落在上述模块之一；不属于任何模块的先讨论，不要新建包。
每个模块内部按 controller / service / repository / entity / dto（+ 需要的 config）分层，不得越层引用。

qinglan-web/             学生端 Vue 3 + TS + Vite + UnoCSS（端口 5173，部署在 /）
  src/
    ├── api/             接口封装（唯一发请求的地方）
    ├── composables/     业务逻辑复用
    ├── components/      展示组件（基础组件已在 shared）
    ├── views/           路由页面
    ├── router/          路由与守卫（index.ts）
    ├── stores/          Pinia：仅学生端状态（notification.ts，未读计数）
    ├── styles/          学生端专属样式（全局变量在 shared）
    └── types/           接口类型（仅学生端）

qinglan-admin/           管理端 Vue 3 + TS + Vite + UnoCSS + ECharts（端口 5174，部署在 /admin/；仅 PC）
  src/
    ├── api/             管理端接口封装（admin.ts、review.ts、proposals.ts 的管理端部分）
    ├── components/admin/ 管理端业务组件
    ├── components/base/  管理端自研基础控件（Checkbox/Switch/Select/MultiSelect/Segmented/Tabs/Drawer/Popover/DateTimeInput/MessageBoxHost）
    ├── composables/     管理端业务逻辑复用（含纯逻辑模块 reviewActionFlow / reviewActionSet / rolePolicy）
    ├── router/          路由与「菜单→权限点」映射（navItems.ts）
    ├── views/           后台页面（AdminLayout / 登录 / 看板 / 队列 / 申诉 / 用户 / 公告 / 权限 / 审计）
    ├── styles/          管理端专属样式（全局变量在 shared）
    └── types/           管理端类型（admin.ts、review.ts、proposal.ts、permission.ts）

shared/                  两端共享（以源码形式经 `@shared/*` 别名引入，不产出构建产物）
  src/
    ├── api/             http.ts（拦截器/令牌/401/428）、user.ts、auth.ts、report.ts
    ├── components/      ToastHost、AnnouncementReader、ModalShell、ConfirmDialog、LoadMoreButton、
    │                    RelativeTime、StatePanel + base/（Button/Card/Icon/Input/Textarea/Spinner/CountBadge/FormField）
    ├── composables/     useToast、useUserLabels、useAuth、useCodeCooldown、useCursorList、useSubmit、
    │                    useFocusTrap、useReadCountdown、useRelativeTime
    ├── stores/          user.ts（用户态，单会话的落点）
    ├── styles/          CSS 变量（全站唯一色值来源）
    ├── types/           双端共用类型（api / auth / user / review-common / announcement）
    ├── uno.base.ts      语义色 / ql-* 快捷类 / z 轴分层
    └── vite/cspPlugin.ts 构建期注入 CSP（两端同一份策略）
```

`shared` 由 pnpm workspace 管理（`pnpm-workspace.yaml` 在**仓库根**，lockfile 全局唯一）。
`vue` / `pinia` 在 `shared` 里必须声明为 `peerDependencies`（另加 `devDependencies` 供本包单测），
**不能写成 `dependencies`**——否则会装出两份 vue，Pinia 实例分裂。

**文件归属判定**：按「谁在用」而不是「原来在哪个文件」。双端都用的进 `shared`；只有单端用的留在该应用
（含基础控件：双端共用放 `shared/src/components/base/`，仅管理端用放 `qinglan-admin/src/components/base/`）。

---

## 二、命令

| 场景 | 命令 |
| --- | --- |
| 后端构建 | `cd qinglan-server && ./mvnw -q clean install` |
| 后端测试（全量） | `./mvnw test` |
| 后端单测 | `./mvnw -Dtest=PostServiceTest test` |
| 后端架构/权限门禁 | `./mvnw -Dtest=ModuleBoundaryTest test`（ArchUnit：模块白名单 + Controller 分层） |
| 后端启动 | `./mvnw spring-boot:run` |
| 前端安装（在**仓库根**执行一次） | `pnpm install` |
| 学生端开发（5173） | `pnpm dev:web` |
| 管理端开发（5174） | `pnpm dev:admin` |
| 三包一起跑（交付门槛） | `pnpm -r lint && pnpm -r typecheck && pnpm -r test && pnpm -r build` |
| 只跑某一包 | 进 `shared` / `qinglan-web` / `qinglan-admin` 后 `pnpm lint`、`pnpm typecheck`、`pnpm test`、`pnpm build` |
| 全栈部署 | `cp .env.example .env` → 填值 → `pnpm -r build` → `docker compose build && docker compose up -d` |

环境要求：JDK 21、Node 20+（CI 用 24）、pnpm 12、MySQL 8；Redis 7 仅在 `qinglan.auth.code-store`
或 `qinglan.store.type` 取 `redis` 时才需要（默认 `memory`，本地无需 Redis）。
后端本地配置走 `application.yml`——**它含本地密钥，已被 .gitignore 忽略，不得提交**；
可提交的模板是同目录的 `application.example.yml`（复制为 `application.yml` 后填本地值）。

---

## 三、模块边界（MUST，违反即打回）

### 后端分层

```
Controller  →  Service  →  Repository  →  DB
```

- Controller **不得**注入或调用 Repository，只依赖 Service
- Controller 只做参数校验与 DTO 装配，**不得**写业务判断
- Service 之间可以互相调用（受本节白名单约束），但不得被 Repository 反向调用

### 模块依赖白名单（依赖图必须无环）

以下方向**允许**，逐条与 `ModuleBoundaryTest`（ArchUnit）的 `ALLOWED` 对应：

- `auth → user`：认证流程要读写用户（学号绑定、匿名 salt、信用分）；加解密集中在 `user` 内，明文身份不外流
- `comment → user`、`comment → post`：发表/删除评论时维护 `post.comment_count`（与 `report_count` 同为冗余计数，避免列表 N+1）
- `post → user`、`post → announcement`：发帖前检查是否存在未读的紧急公告（P4 强制阅读的**服务端兜底**，设计方案 5.4）。前端倒计时只是体验层、禁用 JS 即可绕，真正拦得住用户的是发帖这一动作，故只能由 `post` 发起查询
- `review → user`、`review → post`、`review → comment`：`review` 是审核体系（P2）的**编排者**——举报聚合、审核队列、下架/恢复/隐藏联系方式、申诉复审、提案表决都需要读改帖子与评论、封禁用户。这些是审核动作本身，只能由 `review` 单向发起，业务模块不得反向依赖 `review`
- **审计日志走 `common`**：`AuditLog` / `AuditAction` / `AuditTargetType` / `AuditLogService` 位于 `common/audit`。需要留痕的业务模块直接依赖 `common.audit`，**不再有 `post → review` / `user → review`**；否则依赖图必然成环（本节要求无环）
- **通知走端口 `common/notify`**：`NotificationPort` / `NotificationMessage` / `NotificationType` 位于 `common/notify`，由 `notification` 模块的 `NotificationService` 实现。**业务模块只依赖端口**（发通知、不关心落库细节），因此**任何模块都不得 import `com.qinglan.notification`**——否则「发通知」会牵出对通知模块的编译期依赖，反向依赖 `review/post/comment` 而成环。`announcement` 同样不依赖任何业务模块（`created_by` 只存 id）
- 所有模块均可依赖 `common`；`common/` 不得反向依赖任何业务模块（已由测试钉住）
- 上述方向**不得反向**（禁止 `user → auth`、`post → review`、`user → review`、`post → comment` 等）；确需新增方向先改本节并同步 `ModuleBoundaryTest`
- 白名单由 `ModuleBoundaryTest` 自动校验：**测试对未登记模块按「只允许依赖 `common`」处理**，因此漏写白名单会失败，而不是静默通过
- Repository 不得返回 Entity 给 Controller；Entity → DTO 转换必须在 Service 层完成

### 前端分层

- **只有各应用的 `src/api/` 与 `shared/src/api/` 能发起 HTTP 请求**；组件内禁止直接 `fetch`/`axios`
- 应用之间**不得互相 import**：`qinglan-web` 与 `qinglan-admin` 只能依赖 `shared`，`shared` 不得依赖任一应用
- `components/` 为展示组件：接收 props、emit 事件，**不得**直接调 Pinia store 拿业务数据
- `views/` 负责组合：`components` + `composables` + `api`
- `stores/` 只存用户态、未读计数、草稿箱；**不得**写业务规则
- 可复用逻辑抽 `composables/`，命名 `useXxx`；**纯逻辑（无响应式）也放 `composables/`**（如 `reviewActionFlow.ts`、`rolePolicy.ts`、`reviewActionSet.ts`），超过 3 处重复的 UI 抽组件
- 基础 UI 组件：**双端共用**放 `shared/src/components/base/`（只管外观与交互，props down / events up，不含业务语义）；**仅单端使用**的放该应用的 `components/base/`（如管理端的 Select/Drawer/MessageBoxHost）
- 管理端页面（P3 起）放 `qinglan-admin/src/views/`，仅面向 PC，不做移动适配；控件一律取自管理端 `components/base/` 的自研基础件（或 `shared/src/components/base/`），不引第三方组件库
- **判断某个文件归谁，按「谁在用」而不是「原来在哪个文件」**：双端都用的进 `shared`（如 `useUserLabels` 依赖的 `ReportReason` / `ReviewTargetType`），只有单端用的留在该应用（如 `ReasonPayload` 只被管理端使用）

### 前后端接口边界

- 接口出入参一律 DTO，**禁止**直接序列化 Entity
- 前端 `src/types/` 与后端 DTO 字段一一对应；改 DTO 必须同步改 types
- 分页统一用游标（`cursor`），列表接口不得用 `page/size`
- 游标的拼装与解析统一走 `common/web/CompositeCursor`（微秒换算见 `CursorCodec`），各 Service 不得各写一套分隔符/段数/校验
- 存储地址（`/uploads/...`）的拼装与解析统一走 `common/storage/StorageUrl`，两种存储实现不得各写一套前缀判断或路径解析
- 响应统一 `ApiResponse<T>{ code, message, data }`；业务失败用 `BizException` + `ErrorCode`，由 `GlobalExceptionHandler` 转换
- **HTTP 状态语义要准**：重复举报 / 重复申诉 / 重复投票 / 名额已满 → `409`（**不静默吞掉**）；未读紧急公告拦截写操作 → `428`；不存在与「无权看到」共用 `404`（不泄露存在性）

---

## 四、领域不变量（不可违反，违反即数据事故）

以下七条是系统的正确性根基，任何改动都必须保持成立：

| # | 不变量 | 实现要求 | 落点 |
| --- | --- | --- | --- |
| 1 | **匿名代号跨帖不可关联** | `anonymous_code = hash(post_id + author_id + salt)`。同一用户在不同帖子下代号必须不同。任何对外接口**不得**同时返回 `anonymous_code` 与 `author_id` | `common/anonymous/AnonymousCodeService`；`user.anonymous_salt` |
| 2 | **联系方式只对认证用户可见** | 脱敏在**服务端**完成，未认证 viewer 的响应里 `contact` 字段必须为 `null`，不是前端 `v-if` 隐藏。查看联系方式写审计日志 | `PostService` 按 `Viewer` 脱敏；`post.contact` / `contact_visibility`；`CONTACT_VIEW` 审计 |
| 3 | **真实身份管理员不可见** | `student_id` 加密存储。除依法配合调查的专用通道外，任何管理员接口、日志、导出功能不得返回明文身份 | `common/crypto/CryptoService`；`user.student_id_enc`（AES-256-GCM） |
| 4 | **举报不可刷量** | `UNIQUE(target_type, target_id, reporter_id)`，同一用户对同一目标仅一次。重复举报返回 `409`，不静默吞掉 | DB 唯一键 `uk_report_target_reporter`；`review/ReportService` |
| 5 | **审核状态迁移合法** | 允许：`Draft→Pending`、`Pending→Reviewing`、`Reviewing→Approved/Rejected`、`Approved→TakenDown`、`TakenDown→Approved`（申诉通过）或维持。禁止跳态，禁止 `TakenDown` 直接回 `Pending`。状态用 `sealed interface ReviewState` 表达，新增状态须改 `permits` 并补测试 | `post/entity/ReviewState#assertTransition`（**唯一裁决者**，实体不得绕过它改 `PostStatus`） |
| 6 | **强制阅读必须真实计时** | 仅当 `read_duration_ms >= min_read_seconds`、且（需滚动时）已滚到底、需确认时已勾选，才允许标记已读；服务端逐项校验上报值，任一不满足即 `400` 且**不落库**。**禁止**在弹窗打开时立即标记。**边界要说清**：`read_duration_ms` 与滚动/勾选信号都由客户端上报，服务端只能拒绝「不合格的上报」，无法证明用户真的读了——所以「真强制」落在 `POST /api/posts` 的 `428` 拦截（存在未读紧急公告时发不了帖，禁用 JS 绕过 UI 也没用，见设计方案 5.4）。引用本条时不要把它读成「时长不可伪造」 | `AnnouncementService` 逐项校验；`common/error/PreconditionRequiredException`（428） |
| 7 | **审计日志只增不改删** | `review_log` 无 UPDATE、无 DELETE 接口；所有下架/封禁/发布公告操作必须落日志 | `common/audit/AuditLogService`；动作词汇表 `AuditAction` |

涉及上表任一字段或流程的改动，**必须先写测试证明不变量仍成立**。
关联的提案状态机 `ProposalState` 同样「终态不可再迁移」，新增状态须改 `permits` 并补测试。

---

## 五、Skill 路由

| 触发条件 | 加载 Skill | 本项目重点约束 |
| --- | --- | --- |
| `qinglan-server/**/*.java`、`pom.xml` | `java-springboot` | 构造器注入（禁 `@Autowired` 字段注入）、DTO 边界、`@Transactional` 加在 Service 方法、全局 `@ControllerAdvice` 异常处理 |
| `qinglan-web/**`、`qinglan-admin/**`、`shared/**` 下的 `*.vue`、`*.ts`、`uno.config.ts`、`uno.base.ts` | `vue-best-practices` | `<script setup lang="ts">`、`ref` 优先于 `reactive`、props down / events up、`computed` 派生不做副作用、样式一律走 UnoCSS 原子类 |
| SQL、Flyway 迁移脚本 | （本项目 MySQL 8，无对应 Skill） | 见下方「迁移脚本约定」 |

Skill 由**全局安装**提供（仓库内不再保留 `coding-skills/` 副本），本项目实际加载 `java-springboot` 与 `vue-best-practices` 两个。

**迁移脚本约定**（`src/main/resources/db/migration/`）：

- 命名 `V<序号>__<小写下划线描述>.sql`，序号**只增不复用**
- **不得改写已上线脚本**；改结构一律新增一版（`V9…`）
- 结构变更与索引变更**分开成版本**（便于回滚与定位），加索引前先 `EXPLAIN` 验证
- 大表 DDL 放低峰期执行；脚本要可重复执行（`IF NOT EXISTS` / 幂等写法）
- 布尔列统一 `BIT(1)`；时间列统一 `DATETIME(6)`（UTC）；表统一 `utf8mb4`；**不建外键**（关联由列名 + 索引承载，与既有 15 张表一致）

---

## 六、Java 编码约束

- **版本**：Java 21。DTO 用 `record`；状态机用 `sealed interface` + `record`；分支用 pattern matching；返回值不用 `null`，用 `Optional` 或空集合
- **注入**：构造器注入，`private final` 字段。可用 `@RequiredArgsConstructor`
- **事务**：`@Transactional` 加在 Service 方法，不加在 Controller；查询方法加 `readOnly = true`
- **虚拟线程**：已全局启用（`spring.threads.virtual.enabled`），仅用于 IO 密集（内容安全 API 调用、图片处理、对外存储）。**禁止**用于 CPU 密集计算，不要在虚拟线程里用 `synchronized` 长临界区
- **Entity 只做映射**：实体不含业务判断、不发请求、不转 DTO；状态迁移一律经 `ReviewState` / `ProposalState` 的 `assertTransition`
- **N+1**：列表查询必须显式 `JOIN FETCH` 或用 DTO 投影；Review 时重点查评论树、图片列表
- **数据库是 MySQL 8**：不得使用 PostgreSQL 专有语法（`RETURNING`、`SERIAL`、`::cast`）或 Postgres 方言配置
- **时间**：统一 `Instant`（UTC），展示层转本地时区。禁止 `LocalDateTime.now()` 直接入库；JDBC 与库时区都对齐 UTC（`serverTimezone=UTC`、`DATETIME(6)`）
- **异常**：业务异常用自定义 `BizException` + 错误码；禁止捕获后吞掉；禁止 `catch (Exception e) {}`
- **错误码**：新增一律登记进 `common/error/ErrorCode`，格式 `QL-<HTTP 状态>-<序号>`；**不得复用已发布码的语义**，也不得把「是否请求过」这类信息写进错误信息（防探测）
- **日志**：SLF4J 参数化 `log.info("...{}", x)`；禁止字符串拼接；**禁止**打印手机号、学号、Token

---

## 七、前端编码约束

- **组件**：`<script setup lang="ts">`；props 用 `defineProps<Props>()`，emits 用 `defineEmits<Emits>()`
- **响应式**：`ref` 用于基础值与需整体替换的对象，`reactive` 用于单状态对象，**不得**从 `reactive` 直接解构（用 `toRefs`）
- **派生用 `computed`**，副作用放 `watch`；`computed` getter 内禁止修改状态、发请求
- **列表**：`v-for` 必带稳定 `:key`（用 id，不用 index）；禁止 `v-if` 与 `v-for` 同元素
- **分页**：列表状态机统一走 `shared/composables/useCursorList`（带筛选的用 `useFilteredCursorList`），不得各写一套 loading / 游标 / 竞态处理
- **样式**：一律用 **UnoCSS** 原子类（`presetWind3`）；主题语义色（`primary`/`surface`/`line`/`ink`/`muted`/`danger`…）在 `uno.config.ts` 映射到 `styles/main.css` 的 CSS 变量，禁止硬编码色值。语义色是 CSS 变量，**不能用透明度修饰符**（`bg-primary/10` 无效），浅底用 `*-soft` / `*-tint`。`<style>` 仅用于原子类难以表达的少量逻辑（过渡动画、`@keyframes`），且必须 `scoped`、用 class 选择器
- **组件库**：学生端一律用 `shared/src/components/base/` 自研基础组件（PC 为主、兼容移动端）；管理端（P3 起）用 `qinglan-admin/src/components/base/` 自研基础件 + UnoCSS，**仅支持 PC**，两端都不引第三方组件库
- **可访问性（a11y，与功能等同）**：控件要有可访问名（可见 label 或 `aria-label`）；弹层必须焦点陷阱（`useFocusTrap`）、ESC / 遮罩语义明确；可点元素用 `<button>` 而非 `div@click`；装饰性图标 `aria-hidden`；动效提供 `prefers-reduced-motion` 兜底；页面有 skip link 与唯一 `h1`；数字用 `tabular-nums`
- **文案与展示**：枚举**不得直接展示**，中文映射集中在 `shared/composables/useUserLabels.ts`；时间统一 `RelativeTime`（绝对时间用 `Intl.DateTimeFormat('zh-CN')`）；界面不展示内部数字 ID（如 userId）
- **布局**：PC 为主、兼容移动端；**基线样式即 PC 版**（≥1024px），窄屏用 `max-sm:` / `max-lg:` 收窄，不用 `sm:` / `lg:` 逐级增强；首页内容宽 1200px、用 `ql-shell` 限宽居中，其余学生端页面沿用 `ql-container`（720px）；分界断点 `lg:`（1024px）；不写死像素宽度；列表/卡片流用 flex/grid 原子类
- **全局组件的容器宽度**（顶部导航等）：必须与所在页面的内容容器一致，且由**同一个判断**驱动（见 `App.vue` 的 `fullWidth`）。头部 1200px 而内容 720px 时，1440px 视口下两者左边缘会错开 232px；改这类组件前先确认它影响哪些页面，不能只按当前页面的需要写死
- **XSS**：禁止 `v-html` 渲染用户输入；必须渲染富文本时先 DOMPurify
- **请求**：统一走 `src/api/`；401 由拦截器统一处理（清 token + 跳认证）
- **会话模型：一个浏览器 = 一个账号 = 一份会话**。令牌只有一份（`api/http.ts` 的 `TOKEN_STORAGE_KEY` + 内存态），用户态只有一个 `useUserStore`，前端**不区分**「学生端会话」与「管理端会话」。依据：后端只有一份 JWT，同一账号在前台与后台是同一身份。由此派生三条硬约束：
  - **退出登录后若当前页 `meta.requiresAuth` 必须跳转**（学生端回首页、管理端回 `/admin/login`）。路由守卫只在导航时运行，只清登录态而不导航会停在已不可用的页面上（`/profile` 只剩标题、`/publish` 停在提交不了的表单）
  - **管理员账号可从任一登录页进入**（他同时是本站认证用户），但前台登录成功后必须提示「当前为管理员账号」，避免误以为在用学生身份
  - 角色准入只有一份口径：`shared/src/types/user.ts` 的 `ADMIN_ROLES` / `isAdminRole`，路由守卫与管理端登录页都引用它，不得各写一份
  - 若将来产品要求「同一浏览器分别以前台/后台身份登录两个账号」，属架构变更（前端需同时持两份令牌并按区域切换 `Authorization`），**先提 issue 并同步本节**，不要就地改
- **前后台是两个应用、同源部署**：学生端在 `/`、管理端在 `/admin/`（`qinglan-web` / `qinglan-admin`）。两条硬约束：
  - **两端必须用同一个 `TOKEN_STORAGE_KEY`**（唯一来源是 `shared/src/api/http.ts`）——同源下 localStorage 共用一把钥匙，才能维持上一条的「一个浏览器 = 一个账号 = 一份会话」
  - **vite `base` 与 router 的 `createWebHistory(base)` 必须一致**（管理端两处都是 `/admin/`）。不一致的表现是 `/admin` 下白屏或刷新 404；`qinglan-web` 里**不得**再有 `/admin` 路由，否则它会捕获该路径
  - 共享代码一律经 `@shared/*` 引入；但**配置文件**（`vite.config.ts` 由 Vite 加载、`uno.config.ts` 由 jiti 加载）**不走别名解析**，对 `shared` 必须用相对路径
  - 改 `shared` 里被两端共用的东西（`uno.base.ts` 的语义色与 `ql-*`、`styles/main.css` 的变量、`base/` 组件、`ModalShell`/`ConfirmDialog`）后，**两端产物都要核对**：CSS 里应有对应类（UnoCSS 默认只扫 vite root，扫描范围在各自的 `uno.config.ts` 的 `content.pipeline.include` 里显式包含 `shared/src`，漏配就是「单测绿、页面崩」）
- **请求错误统一走全局弹窗**：所有请求错误（提交失败、加载失败等）一律由 `src/api/http.ts` 的拦截器 toast 提示，**页面不得再内联渲染请求错误文案**，不要「弹窗 + 容器内」提示两遍；页面只负责结束 loading 态、决定成功后的流程推进。提示由 `useToast` 统一在数秒后自动消失。
  **注意区分**：`FormField` 的 `error` 是**字段级校验文案**（如「结束时间必须晚于开始时间」），属组件展示职责，允许内联——它约束的是**请求错误**，不是这类本地校验
- **提交态统一走 `shared/composables/useSubmit`**：`{ submitting, run(fn) }` 返回 boolean（成功 true / 失败 false），失败不在此提示。返回结果对象、返回联合枚举、或需要在 `catch` 里 rethrow 的场景不套它
- **全局弹窗**：`AnnouncementModal.vue` 单例挂 `App.vue`，遮罩/ESC/返回键均不关闭，倒计时未满禁用关闭按钮
- **自研基础控件**（替代第三方组件库）：弹层一律走 `shared/ModalShell`（遮罩 + ESC + 焦点陷阱）；管理端用 `components/base/` 的自研件，命令式弹窗用 `useMessageBox` + `MessageBoxHost`（挂在 `App.vue`），**不得**引入第三方组件库
- **权限点驱动 UI**：管理端的按钮/菜单/快捷键一律按**权限点**（`usePermissions().can(...)` + `reviewActionSet` 里的 `permission`）渲染与否决，**禁止**在页面里写 `role === 'SUPER_ADMIN'` 这类角色判断——角色口径集中在 `qinglan-admin/src/composables/rolePolicy.ts` 与 `shared/src/types/user.ts`

---

## 八、测试要求（交付门槛）

| 层 | 要求 |
| --- | --- |
| Service | JUnit 5 + Mockito，覆盖正常 / 边界 / 失败三类 |
| Controller | `@WebMvcTest` 验证参数校验与状态码 |
| 持久层 | `@DataJpaTest`，重点验证 UNIQUE 约束与联表查询 |
| 不变量 | 第四章七条每条至少一个针对性测试 |
| 架构/授权 | `ModuleBoundaryTest`（跨模块白名单 + Controller 分层）、`PermissionAnnotationTest`（禁 `hasRole` / 类级放行） |
| 前端 | Vitest 覆盖 composables 与关键组件；Playwright 覆盖发帖 → 审核 → 下架主流程（**规划中，尚未落地**） |

前端已拆为三个 workspace 包，**三包的 lint / typecheck / vitest / build 都是交付门槛，缺一不可**：
`shared`（http、useToast、useUserLabels、useAuth 与 base 组件）、`qinglan-web`、`qinglan-admin`。
在自己那一包跑绿不等于交付通过——改 `shared` 时必须把另外两包也跑一遍。

- 修 bug 先写能复现的失败测试，再修
- 测试用**真实** MySQL/Redis，不 mock 数据库行为：CI 里由 GitHub Actions 的 **service container** 起 `mysql:8.0` 与 `redis:7`（见 `.github/workflows/ci.yml`），本地则用 `application.yml` 指向本机 MySQL（Redis 相关用例由 `QINGLAN_REDIS_TEST` 条件开关，本机无 Redis 时自动跳过）。不用 Testcontainers
- 禁止为了通过而删测试、放宽断言、加 `@Disabled`
- **`vi.mock` 的路径按模块标识符拦截**：模块一旦搬到 `shared`，spec 里的 `vi.mock('@/api/xxx')` 必须同步改成 `vi.mock('@shared/api/xxx')`，否则 mock 不命中、测试会去发真请求
- **控件桩要保留 `modelValue` / `update:modelValue` 这条唯一通道**（否则会出现「单测全绿、浏览器里弹窗关不掉」的盲区）；桩里渲染真 `<option>` / `<input>` 才能用 `setValue` 驱动

---

## 九、安全红线（MUST NOT）

- SQL 一律参数化（JPA / MyBatis `#{}`）；禁止拼接
- 密钥、Token、密码走环境变量；禁止进代码与日志。`QINGLAN_CRYPTO_AES_KEY` 与 `QINGLAN_CRYPTO_EMAIL_PEPPER` **上线后不可轮换**（轮换即历史身份无法解密/匹配），必须离线备份
- 所有输入视为不可信：DTO 加 `@Valid`，长度与范围都要校验
- 上传文件校验类型与大小，存对象存储，不存应用服务器本地磁盘
- 接口必须有权限注解；管理员接口**按权限点**授权，且默认拒绝
  - 权限点唯一词汇表是 `common/security/AdminPermission`；**角色 → 权限点只在 `UserRole#permissions()` 一处**。端点写 `@PreAuthorize("hasAuthority('X')")`，**禁止 `hasRole` / `hasAnyRole`，禁止把注解写在类上**（类级放行 + 方法级遗漏是最常见的越权来源）——由 `PermissionAnnotationTest` 扫描强制，新增权限点必须登记进枚举
  - 前端按 `GET /api/admin/me` 下发的权限点渲染菜单与按钮；「哪个菜单要哪个权限点」放 `qinglan-admin/src/router/navItems.ts` 一处。前端隐藏/显示只是体验层，真正的防线在端点的注解上（与「脱敏在服务端完成」同一条原则）——**同一动作的「按钮」与「快捷键」必须用同一口径过滤权限**，不得一个过滤、一个不过滤
  - 令牌是无状态的、角色在签发时即固定，所以**账号级禁令与权限收回都必须在每个写入口重新判定**：业务写入口调 `UserService.requireUsableById`；管理端写入口由 `AdminWriteGuardFilter`（`/api/admin/**` 的非 GET）统一兜住，它同时比对「数据库角色 = 令牌角色」——否则被收回权限的人在令牌到期前照样能写，「收回」就只是心理安慰。**这道复查不得移除**。只读入口放行，以保留被禁令账号的浏览能力
  - 唯一超级管理员是初始账号：不可被降级、不可被封禁、不可被删除（被锁死就只能改库），任何接口都不允许授予 `SUPER_ADMIN`；委员会（管理员 + 评议员）合计上限 6 人（`UserRole.MAX_COMMITTEE_SIZE`），新增委员前必须带锁计数
- **撤销已作出的处置**（恢复可见 / 撤销隐藏联系方式）归超管（`DISPOSITION_REVOKE`）：处置经委员会表决才成立，若任一委员可静默撤销，多数决就不构成约束
- 导出、批量查询接口必须分页 + 权限校验，禁止无限制全表扫描；导出虽为 GET，仍属**批量读取**，其权限点必须与「能看到同等数据」的写入口一致，且必须落 `DATA_EXPORT` 审计（记录筛选摘要，不含身份明文）
- 限流与真实 IP：受保护路径（默认 `/api/auth/email/code`）按 IP 限流；真实客户端 IP 依据 `qinglan.security.trusted-proxy-hops` 从 `X-Forwarded-For` **右起**取，设错会让限流形同虚设
- 不得在错误信息中回显内部堆栈、SQL、手机号、学号
- **安全响应头不得回退**：后端 API 响应必须带 CSP（`default-src 'none'`）、`X-Frame-Options: DENY`、`X-Content-Type-Options: nosniff`、`Referrer-Policy: no-referrer`、`Permissions-Policy`，由 `SecurityConfig.headers` 统一下发，测试见 `SecuritySkeletonTest`；前端 SPA 由 `vite.config.ts` 插件在**构建期**注入 CSP `meta`（dev 不注入，否则与 Vite HMR 冲突）。**后端 CSP 取 `'none'` 的前提是「后端只返回 JSON 与图片、不托管 SPA 页面」——若将来改由后端直接托管静态页面，必须同步放宽该策略，否则页面自身的脚本与样式会被自身响应头拦死。** `frame-ancestors` 写在 `meta` 里会被浏览器忽略，SPA 的防嵌套响应头须由托管服务器补
- 安全头是纵深防御的第二道墙，**不得**因此放松输出编码：禁止 `v-html` 渲染用户输入、禁止拼接 `innerHTML`，必须渲染富文本时先 DOMPurify

---

## 十、工作流与提交

1. **先读再改**：改前读完目标文件及其测试、调用方
2. **不越界**：只改任务范围内的代码；范围外问题先报告，不擅自修
3. **验证必须真跑**：后端 `./mvnw test`；前端**三包各自**跑 `pnpm lint && pnpm typecheck && pnpm test && pnpm build`（或在仓库根 `pnpm -r lint && pnpm -r typecheck && pnpm -r test && pnpm -r build`），贴实际输出
   - 后端编译已开 `-Xlint:all,-processing -Werror`：**任何 javac 警告都会让构建失败**，请正面修掉而不是压制（`processing` 一档因 Lombok 噪音而关闭，其余全开）
4. **分支与 CI**：主干是 `main`；CI（`.github/workflows/ci.yml`）在 `push: main` 与所有 PR 上跑四个 job（后端静态门禁 / 后端全量测试 / 前端三包矩阵 / 生产依赖审计）。**提交前本地门禁必须全绿**
5. **不提交**：`.env`、`application.yml`、任何密钥或令牌、`node_modules/`、`target/`、`dist/`（均已由 `.gitignore` 覆盖）
6. **提交**：Conventional Commits，`feat|fix|refactor|test|docs|chore(server|web|admin|shared|post|review|announcement): 说明`，正文写"为什么"
7. **PR**：说明改了什么、为什么、如何验证、是否影响第四章不变量、是否破坏性变更

---

## 十一、不确定时怎么办

按顺序判断，不要卡住也不要猜：

1. **本文件有明确规则** → 按规则执行，不询问
2. **涉及第四章不变量** → 保守处理：先写测试证明不变量成立，再改
3. **触及安全红线** → 停下来说明风险，提出安全方案
4. **多种合理方案且影响架构** → 列出选项与取舍，交由人决策，不要自行选择
5. **纯样式/命名等无风险选择** → 自行决定并保持与既有代码一致

---

## 十二、交付自检清单

完成后逐条核对：

- [ ] 分层边界未被打破（Controller 未直连 Repository、组件未直接发请求）
- [ ] 模块依赖方向在第三章白名单内（含通知走 `common/notify` 端口、未 import `notification` 模块），`ModuleBoundaryTest` 通过
- [ ] 管理端端点用权限点授权（`hasAuthority`），未出现 `hasRole` / `hasAnyRole` / 类级放行；前端未写死角色判断
- [ ] 第四章七条不变量均未被破坏，且有测试覆盖；状态机新增状态已改 `permits` 并补测试
- [ ] 新增/修改的接口有 DTO，未暴露 Entity；错误码已登记进 `ErrorCode` 且未复用旧码语义
- [ ] 联系方式脱敏在服务端完成，未认证用户拿不到明文
- [ ] 下架、封禁、发公告、导出操作均已写审计日志
- [ ] 列表查询无 N+1，分页用游标且走 `CompositeCursor`；存储地址走 `StorageUrl`
- [ ] 前端表单 / 列表 / 弹层走了既有约定（`useSubmit` / `useCursorList` / `ModalShell`），a11y 未回退
- [ ] 改动的 `shared` 内容已在两端产物中核对
- [ ] 新增迁移脚本只增不改、可重复执行；改动 `shared` 或后端后**三包门禁**真跑全绿
- [ ] 日志与错误信息中无敏感数据
- [ ] 测试真实执行并全部通过，贴出了实际输出
- [ ] 未修改任务范围外的代码
