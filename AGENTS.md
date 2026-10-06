# AGENTS.md — 青栏 Qinglan

> 本文件是**青栏项目**的 AI 编码规范。项目名 `qinglan` 已定稿，不得改动。
> 方案详见 `青栏项目设计方案.md`；本文件定义"怎么写代码"，两者冲突时以本文件的工程约束为准。
> 规则写得细，是为了减少来回确认——按此执行即可，遇到未覆盖的情况按第十一章处理。

---

## 一、项目结构

双工程，物理隔离，禁止互相 import：

```
qinglan-server/          后端 Java 21 + Spring Boot 3.x，Maven
  src/main/java/com/qinglan/
    ├── auth/            校园邮箱认证、学号绑定、匿名代号
    ├── post/            帖子、图片、话题
    ├── comment/         二级评论
    ├── review/          举报、审核队列、处置（下架/恢复/隐藏联系方式）、申诉复审
    ├── announcement/    公告与强制阅读
    ├── notification/    个人通知聚合
    ├── user/            信用分、封禁
    └── common/          异常、响应封装、审计日志底座（common/audit）、工具

包名一律 `com.qinglan.<module>`，如 `com.qinglan.post`、`com.qinglan.review`。
新文件必须落在上述模块之一；不属于任何模块的先讨论，不要新建包。

qinglan-web/             前端 Vue 3 + TS + Vite + UnoCSS
  src/
    ├── api/             接口封装（唯一发请求的地方）
    ├── composables/     业务逻辑复用
    ├── components/      展示组件
    │   └── base/        自研基础 UI 组件（Button/Input/FormField/Card…）
    ├── views/           路由页面（管理端页面放 views/admin/）
    ├── stores/          Pinia：仅用户态、未读、草稿
    ├── styles/          全局 CSS 变量与 reset 之外的少量全局样式
    └── types/           接口类型
```

---

## 二、命令

| 场景 | 命令 |
| --- | --- |
| 后端构建 | `cd qinglan-server && ./mvnw -q clean install` |
| 后端测试（全量） | `./mvnw test` |
| 后端单测 | `./mvnw -Dtest=PostServiceTest test` |
| 后端启动 | `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` |
| 前端安装 | `cd qinglan-web && pnpm install` |
| 前端开发 | `pnpm dev` |
| 前端测试 | `pnpm vitest run` |
| 前端类型检查 | `pnpm vue-tsc --noEmit` |
| 前端 Lint | `pnpm lint` |

环境要求：JDK 21、Node 20+、MySQL 8、Redis 7。本地配置走 `application-local.yml`，不得提交任何密钥。

---

## 三、模块边界（MUST，违反即打回）

### 后端分层

```
Controller  →  Service  →  Repository  →  DB
```

- Controller **不得**注入或调用 Repository，只依赖 Service
- Controller 只做参数校验与 DTO 装配，**不得**写业务判断
- Service 之间横向调用：**仅允许**下列方向，其余跨模块调用先提 issue
  - `post → user`：查信用分 / 封禁态
  - `comment → user`：取匿名 salt 派生评论代号（不变量 #1）。salt 属身份字段，只允许用于派生，不得外流
  - `auth → user`：认证所需的身份查询与创建。身份字段的加解密集中在 `user` 内，明文身份不外流
  - `comment → post`：发表/删除评论时维护 `post.comment_count`（与 `report_count` 同为冗余计数，避免列表 N+1）
  - `review → user`、`review → post`、`review → comment`：`review` 是审核体系（P2）的**编排者**——举报聚合、审核队列、下架/恢复/隐藏联系方式、申诉复审都需要读改帖子与评论、封禁用户。这些是审核动作本身，只能由 `review` 单向发起，业务模块不得反向依赖 `review`
- **审计日志改走 `common`**：`AuditLog` / `AuditAction` / `AuditTargetType` / `AuditLogService` 位于 `common/audit`（表仍是 `review_log`）。需要留痕的业务模块（`post` 查看联系方式、`user` 封禁/调信用分、`review` 各类处置）直接依赖 `common.audit`，**不再有 `post → review` / `user → review`**。这样做的原因：`review` 编排业务模块、业务模块又需要写审计，若审计留在 `review` 依赖图必然成环（本节要求无环）；下沉到 `common` 后依赖图为 `业务模块 → common`、`review → 业务模块 → common`，无环且不变量 #7 行为不变
- 上述方向**不得反向**（禁止 `user → auth`、`post → review`、`user → review`、`post → comment` 等），以保证依赖图无环；确需新增方向先提 issue 并同步本节
- 本节白名单由 `ModuleBoundaryTest`（ArchUnit）自动校验：新增模块或新增跨模块方向必须同步该测试，否则 CI 的 `backend-static-analysis` 必红。测试对未登记模块按「只允许依赖 `common`」处理，因此**漏写白名单会失败，而不是静默通过**
- Repository 不得返回 Entity 给 Controller；Entity → DTO 转换必须在 Service 层完成
- `common/` 不得反向依赖任何业务模块

### 前端分层

- **只有 `src/api/` 能发起 HTTP 请求**；组件内禁止直接 `fetch`/`axios`
- `components/` 为展示组件：接收 props、emit 事件，**不得**直接调 Pinia store 拿业务数据
- `views/` 负责组合：`components` + `composables` + `api`
- `stores/` 只存用户态、未读计数、草稿箱；**不得**写业务规则
- 可复用逻辑抽 `composables/`，命名 `useXxx`，超过 3 处重复的 UI 抽组件
- 基础 UI 组件放 `components/base/`：只管外观与交互（props down / events up），不含业务语义，学生端与管理端共用
- 管理端页面（P3 起）放 `views/admin/`，使用 Element Plus，仅面向 PC，不做移动适配

### 前后端接口边界

- 接口出入参一律 DTO，**禁止**直接序列化 Entity
- 前端 `src/types/` 与后端 DTO 字段一一对应；改 DTO 必须同步改 types
- 分页统一用游标（`cursor`），列表接口不得用 `page/size`

---

## 四、领域不变量（不可违反，违反即数据事故）

以下七条是系统的正确性根基，任何改动都必须保持成立：

| # | 不变量 | 实现要求 |
| --- | --- | --- |
| 1 | **匿名代号跨帖不可关联** | `anonymous_code = hash(post_id + author_id + salt)`。同一用户在不同帖子下代号必须不同。任何对外接口**不得**同时返回 `anonymous_code` 与 `author_id` |
| 2 | **联系方式只对认证用户可见** | 脱敏在**服务端**完成，未认证 viewer 的响应里 `contact` 字段必须为 `null`，不是前端 `v-if` 隐藏。查看联系方式写审计日志 |
| 3 | **真实身份管理员不可见** | `student_id` 加密存储。除依法配合调查的专用通道外，任何管理员接口、日志、导出功能不得返回明文身份 |
| 4 | **举报不可刷量** | `UNIQUE(target_type, target_id, reporter_id)`，同一用户对同一目标仅一次。重复举报返回 `409`，不静默吞掉 |
| 5 | **审核状态迁移合法** | 允许：`Draft→Pending`、`Pending→Reviewing`、`Reviewing→Approved/Rejected`、`Approved→TakenDown`、`TakenDown→Approved`（申诉通过）或维持。禁止跳态，禁止 `TakenDown` 直接回 `Pending`。状态用 `sealed interface ReviewState` 表达，新增状态须改 `permits` 并补测试 |
| 6 | **强制阅读必须真实计时** | 仅当 `read_duration_ms >= min_read_seconds` 且（需滚动时）已滚到底，才允许标记已读。**禁止**在弹窗打开时立即标记 |
| 7 | **审计日志只增不改删** | `review_log` 无 UPDATE、无 DELETE 接口；所有下架/封禁/发布公告操作必须落日志 |

涉及上表任一字段或流程的改动，**必须先写测试证明不变量仍成立**。

---

## 五、Skill 路由

| 触发条件 | 加载 Skill | 本项目重点约束 |
| --- | --- | --- |
| `qinglan-server/**/*.java`、`pom.xml` | `java-springboot` | 构造器注入（禁 `@Autowired` 字段注入）、DTO 边界、`@Transactional` 加在 Service 方法、全局 `@ControllerAdvice` 异常处理 |
| `qinglan-web/**/*.vue`、`**/*.ts`、`uno.config.ts` | `vue-best-practices` | `<script setup lang="ts">`、`ref` 优先于 `reactive`、props down / events up、`computed` 派生不做副作用、样式一律走 UnoCSS 原子类 |
| SQL、Flyway 迁移脚本 | （本项目 MySQL 8，无对应 Skill） | 迁移脚本必须可回滚、不得改写已上线脚本；加索引前先 `EXPLAIN` 验证；大表 DDL 放低峰期执行 |

Skill 位于 `coding-skills/`，本项目实际加载 `java-springboot` 与 `vue-best-practices` 两个。

---

## 六、Java 编码约束

- **版本**：Java 21。DTO 用 `record`；状态机用 `sealed interface` + `record`；分支用 pattern matching；返回值不用 `null`，用 `Optional` 或空集合
- **注入**：构造器注入，`private final` 字段。可用 `@RequiredArgsConstructor`
- **事务**：`@Transactional` 加在 Service 方法，不加在 Controller；查询方法加 `readOnly = true`
- **虚拟线程**：仅用于 IO 密集（内容安全 API 调用、图片处理、WebSocket 推送）。**禁止**用于 CPU 密集计算，不要在虚拟线程里用 `synchronized` 长临界区
- **N+1**：列表查询必须显式 `JOIN FETCH` 或用 DTO 投影；Review 时重点查评论树、图片列表
- **数据库是 MySQL 8**：不得使用 PostgreSQL 专有语法（`RETURNING`、`SERIAL`、`::cast`）或 Postgres 方言配置
- **时间**：统一 `Instant`（UTC），展示层转本地时区。禁止 `LocalDateTime.now()` 直接入库
- **异常**：业务异常用自定义 `BizException` + 错误码；禁止捕获后吞掉；禁止 `catch (Exception e) {}`
- **日志**：SLF4J 参数化 `log.info("...{}", x)`；禁止字符串拼接；**禁止**打印手机号、学号、Token

---

## 七、前端编码约束

- **组件**：`<script setup lang="ts">`；props 用 `defineProps<Props>()`，emits 用 `defineEmits<Emits>()`
- **响应式**：`ref` 用于基础值与需整体替换的对象，`reactive` 用于单状态对象，**不得**从 `reactive` 直接解构（用 `toRefs`）
- **派生用 `computed`**，副作用放 `watch`；`computed` getter 内禁止修改状态、发请求
- **列表**：`v-for` 必带稳定 `:key`（用 id，不用 index）；禁止 `v-if` 与 `v-for` 同元素
- **样式**：一律用 **UnoCSS** 原子类（`presetWind3`）；主题语义色（`primary`/`surface`/`line`/`ink`/`muted`/`danger`…）在 `uno.config.ts` 映射到 `styles/main.css` 的 CSS 变量，禁止硬编码色值。`<style>` 仅用于原子类难以表达的少量逻辑（过渡动画、`@keyframes`），且必须 `scoped`、用 class 选择器
- **组件库**：学生端一律用 `components/base/` 自研基础组件（PC 为主、兼容移动端）；管理端（P3 起）用 Element Plus + UnoCSS，**仅支持 PC**
- **布局**：PC 为主、兼容移动端；**基线样式即 PC 版**（≥1024px），窄屏用 `max-sm:` / `max-lg:` 收窄，不用 `sm:` / `lg:` 逐级增强；首页内容宽 1200px、用 `ql-shell` 限宽居中，其余学生端页面沿用 `ql-container`（720px）；分界断点 `lg:`（1024px）；不写死像素宽度；列表/卡片流用 flex/grid 原子类
- **XSS**：禁止 `v-html` 渲染用户输入；必须渲染富文本时先 DOMPurify
- **请求**：统一走 `src/api/`；401 由拦截器统一处理（清 token + 跳认证）
- **请求错误统一走全局弹窗**：所有请求错误（提交失败、加载失败等）一律由 `src/api/http.ts` 的拦截器 toast 提示，**页面不得再内联渲染请求错误文案**，不要「弹窗 + 容器内」提示两遍；页面只负责结束 loading 态、决定成功后的流程推进。提示由 `useToast` 统一在数秒后自动消失
- **全局弹窗**：`AnnouncementModal.vue` 单例挂 `App.vue`，遮罩/ESC/返回键均不关闭，倒计时未满禁用关闭按钮

---

## 八、测试要求（交付门槛）

| 层 | 要求 |
| --- | --- |
| Service | JUnit 5 + Mockito，覆盖正常 / 边界 / 失败三类 |
| Controller | `@WebMvcTest` 验证参数校验与状态码 |
| 持久层 | `@DataJpaTest`，重点验证 UNIQUE 约束与联表查询 |
| 不变量 | 第四章七条每条至少一个针对性测试 |
| 前端 | Vitest 覆盖 composables 与关键组件；Playwright 覆盖发帖 → 审核 → 下架主流程 |

- 修 bug 先写能复现的失败测试，再修
- 测试用 Testcontainers 起真实 MySQL/Redis，不 mock 数据库行为
- 禁止为了通过而删测试、放宽断言、加 `@Disabled`

---

## 九、安全红线（MUST NOT）

- SQL 一律参数化（JPA / MyBatis `#{}`）；禁止拼接
- 密钥、Token、密码走环境变量；禁止进代码与日志
- 所有输入视为不可信：DTO 加 `@Valid`，长度与范围都要校验
- 上传文件校验类型与大小，存对象存储，不存应用服务器本地磁盘
- 接口必须有权限注解；管理员接口单独角色，且默认拒绝
- 导出、批量查询接口必须分页 + 权限校验，禁止无限制全表扫描
- 不得在错误信息中回显内部堆栈、SQL、手机号、学号
- **安全响应头不得回退**：后端 API 响应必须带 CSP（`default-src 'none'`）、`X-Frame-Options: DENY`、`X-Content-Type-Options: nosniff`、`Referrer-Policy: no-referrer`、`Permissions-Policy`，由 `SecurityConfig.headers` 统一下发，测试见 `SecuritySkeletonTest`；前端 SPA 由 `vite.config.ts` 插件在**构建期**注入 CSP `meta`（dev 不注入，否则与 Vite HMR 冲突）。**后端 CSP 取 `'none'` 的前提是「后端只返回 JSON 与图片、不托管 SPA 页面」——若将来改由后端直接托管静态页面，必须同步放宽该策略，否则页面自身的脚本与样式会被自身响应头拦死。** `frame-ancestors` 写在 `meta` 里会被浏览器忽略，SPA 的防嵌套响应头须由托管服务器补（见 `修复记录.txt` T-14）。
- 安全头是纵深防御的第二道墙，**不得**因此放松输出编码：禁止 `v-html` 渲染用户输入、禁止拼接 `innerHTML`，必须渲染富文本时先 DOMPurify

---

## 十、工作流与提交

1. **先读再改**：改前读完目标文件及其测试、调用方
2. **不越界**：只改任务范围内的代码；范围外问题先报告，不擅自修
3. **验证必须真跑**：`./mvnw test` 与 `pnpm vitest run && pnpm vue-tsc --noEmit`，贴实际输出
   - 后端编译已开 `-Xlint:all,-processing -Werror`：**任何 javac 警告都会让构建失败**，请正面修掉而不是压制（`processing` 一档因 Lombok 噪音而关闭，其余全开）
4. **提交**：Conventional Commits，`feat|fix|refactor|test|docs|chore(post|review|announcement|web): 说明`，正文写"为什么"
5. **PR**：说明改了什么、为什么、如何验证、是否影响第四章不变量、是否破坏性变更

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
- [ ] 第四章七条不变量均未被破坏，且有测试覆盖
- [ ] 新增/修改的接口有 DTO，未暴露 Entity
- [ ] 联系方式脱敏在服务端完成，未认证用户拿不到明文
- [ ] 下架、封禁、发公告操作均已写审计日志
- [ ] 列表查询无 N+1，分页用游标
- [ ] 日志与错误信息中无敏感数据
- [ ] 测试真实执行并全部通过，贴出了实际输出
- [ ] 未修改任务范围外的代码
