# 青栏 部署与发布手册

本目录（`deploy/`）与仓库根的几个文件构成青栏的生产部署产物。**本手册不重复**《修复记录.txt》
里已登记的编号，只在必要处引用衔接。

> ⚠️ 诚实声明：这些产物**未经运行验证**——本机没有 Docker、没有 nginx，无法真正构建镜像、
> 起服务或跑恢复演练。已在本机验证的只有：YAML/SQL/shell 语法解析、环境变量名与后端配置键
> 的逐项核对、以及所有被引用路径与仓库实际结构的一致性。逐项状态见文末「验证状态」。

---

## 1. 产物清单

| 文件 | 作用 |
| --- | --- |
| `Dockerfile`（仓库根） | 后端镜像：多阶段（Maven 构建 → 精简 JRE 运行），非 root 运行，`HEALTHCHECK` 指向 `/actuator/health` |
| `.dockerignore`（仓库根） | 缩小构建上下文，并**阻止本机 `application.yml` / `.env` 进入镜像层** |
| `docker-compose.yml`（仓库根） | 编排 mysql:8.0 + redis:7 + app + nginx；只有 nginx 对外发布端口 |
| `.env.example`（仓库根） | 全部必需环境变量模板；真实 `.env` 不入库 |
| `deploy/nginx.conf` | `/`→学生端、`/admin/`→管理端、`/api`+`/uploads`→后端；安全响应头；缓存 |
| `deploy/backup.sh` | MySQL 全量 dump + 上传目录打包，带日期命名与保留 N 份 |
| 本文件 | 部署、发布、回滚、演练手册 |

---

## 2. 部署拓扑

```
          Internet
             │  :80
        ┌────▼────┐
        │  nginx  │  /            → qinglan-web/dist   (挂载)
        │         │  /admin/      → qinglan-admin/dist (挂载)
        │         │  /api,/uploads→ app:8080 (反代)
        └────┬────┘
             │ 内部网络 qinglan（bridge）
     ┌───────┼───────────────┐
 ┌───▼───┐ ┌─▼───┐      ┌────▼────┐
 │  app  │ │mysql│      │  redis  │
 │ :8080 │ │ :3306│     │ :6379   │
 └───────┘ └─────┘      └─────────┘
   jar     mysql-data     redis-data
 uploads-data（命名卷，落在 app 的 /app/data/uploads）
```

同源部署（AGENTS.md 第七章）：`/`、`/admin/`、`/api`、`/uploads` 同源 → localStorage 共用一把
令牌键，维持「一个浏览器 = 一个账号 = 一份会话」。**不要**把前端拆到别的域/子域，那属会话模型变更。

---

## 3. 前置条件

- 部署主机：已装 Docker Engine + Docker Compose v2（`docker compose` 子命令）。
- Node 20+ / pnpm（仅用于在主机或 CI 构建前端产物；不需要在主机装 JDK）。
- 一个可发信的 SMTP 账号（验证码是注册/登录的唯一入口，`transport=logging` 不能上生产）。
- 一个域名与 TLS 证书（本配置只监听 80；TLS 建议放在前置负载均衡/证书终结层，**若加了一层，
  `QINGLAN_TRUSTED_PROXY_HOPS` 要相应 +1**，见第 5 节）。

---

## 4. 首次部署

```bash
# 1) 取代码
git clone <repo> /srv/qinglan && cd /srv/qinglan

# 2) 准备环境变量（所有密钥只来自 .env）
cp .env.example .env
#    按 .env.example 的注释逐项填值；生成随机密钥：
#      openssl rand -base64 32        # QINGLAN_CRYPTO_AES_KEY（恰好 32 字节）
#      openssl rand -base64 48        # QINGLAN_JWT_SECRET
#      openssl rand -base64 24        # MYSQL_ROOT_PASSWORD / QINGLAN_DB_PASSWORD / REDIS_PASSWORD
#    ⚠️ AES 密钥与 email-pepper 一旦上线不可轮换，请生成后单独离线备份。
#    ⚠️ 确认 .env 不会被提交（仓库根 .gitignore 已忽略 .env）。

# 3) 构建前端产物（两个 SPA）——详见第 6 节
pnpm install
pnpm -r build          # 产出 qinglan-web/dist 与 qinglan-admin/dist

# 4) 构建并启动全栈
docker compose build
docker compose up -d

# 5) 冒烟
curl -fsS http://<host>/actuator/health   # 经 nginx 不打到这里，用下面方式直查 app：
docker compose exec app curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsSI http://<host>/                 # 应有 X-Frame-Options: DENY 与 CSP frame-ancestors
curl -fsSI http://<host>/admin/           # 管理端同样要有
```

生产启动守卫（`ProdEnvironmentGuard`，已实现）：`SPRING_PROFILES_ACTIVE=prod` 时，**任一**下列条件
不满足即**拒绝启动**并打印中文原因：

| 配置键 | 生产必须 | 对应 `.env` 变量 |
| --- | --- | --- |
| `qinglan.auth.expose-code` | `false` | `QINGLAN_AUTH_EXPOSE_CODE=false` |
| `qinglan.auth.code-store` | `redis` | `QINGLAN_AUTH_CODE_STORE=redis` |
| `qinglan.store.type` | `redis` | `QINGLAN_STORE_TYPE=redis` |
| `qinglan.storage.type` | `s3` | `QINGLAN_STORAGE_TYPE=s3`（+ `QINGLAN_S3_ENDPOINT` / `QINGLAN_S3_BUCKET` / `QINGLAN_S3_ACCESS_KEY` / `QINGLAN_S3_SECRET_KEY`，缺失即启动失败） |
| `qinglan.storage.image-signing-secret` | 显式配置（不得依赖回落 `qinglan.jwt.secret`） | `QINGLAN_STORAGE_IMAGE_SIGNING_SECRET`（私有图片签名 URL 的 HMAC 密钥） |
| `spring.jpa.hibernate.ddl-auto` | `validate`（非 create/create-drop/update） | （由镜像内模板固定为 validate） |
| `spring.datasource.url` | 非 localhost/127.0.0.1/::1/0.0.0.0 | `QINGLAN_DB_URL` 指向 `mysql` 服务 |

因此 **`SPRING_PROFILES_ACTIVE` 必须为 `prod`**：不设它，守卫不触发，应用会以「回显验证码 +
内存存储」的本地态静默启动 —— 这是最危险的一种「看起来正常」。

**配置文件从哪来**：`src/main/resources/application.yml` 被 gitignore、不在仓库里，干净 checkout
打出的 jar **不含任何配置**。因此 `Dockerfile` 把可提交的 `application.example.yml` 拷到镜像内
`/app/config/application.yml`：它保存了全部「环境变量 → 配置键」映射（`${QINGLAN_...}` 占位符），
默认值 fail-closed，再由 `.env` 注入实际值。这就是「一套环境变量部署全栈」的实现方式，无需在
主机维护第二份配置。

### 4.1 私有图片签名 URL（受控图片下发）

`/uploads/**` 由后端受控端点按帖子可见性下发（T-27①）：**APPROVED 帖的图对所有人公开，待审 / 下架 /
未绑定上传仅作者可见**。但浏览器 `<img>` 请求**不带 Bearer 令牌**，等价于游客，于是作者自己也取不到
自己的私有图（发帖页预览、作者看自己待审/下架帖的图会 404 破图）。

解法：服务端在**下发 DTO 时按 viewer 决定 URL** —— 公开图维持原样（保持 URL 干净、可缓存）；私有图
追加**短时签名**：

```
/uploads/YYYY/MM/<hex>.<ext>?exp=<epochSeconds>&sig=<base64url(HMAC-SHA256(objectKey + ":" + exp, secret))>
```

- 校验：签名**常量时间比较**（`MessageDigest.isEqual`）、`exp` 未过期、且不超过「now + 有效期上限」
  （默认 10 分钟，硬上限 1 小时）。通过即放行，不依赖 viewer 身份；非法/过期/超上限一律与「不存在」
  同码同状态 **404**（不泄露存在性）。
- 密钥：`QINGLAN_STORAGE_IMAGE_SIGNING_SECRET`（生产必填，见上表）；留空时回落 `qinglan.jwt.secret`
  仅为本地/测试便利。
- 落点：帖子详情的图片、我的帖子列表、上传接口返回、以及 `POST /api/uploads/image-urls`（批量换取，
  草稿恢复用）。前端 `<img>` 无需改动。
- 失效即恢复：下架后旧的**干净** URL 立即 404（T-27① 不受影响）；签名过期后前端刷新页面/重进详情页
  即拿到新签名。

> 未验证：以上与真实对象存储（S3 兼容）联动的端到端表现未实测；本机以本地磁盘实现 + MockMvc 验证。

### 环境变量名与后端键的对应（核对用）

`.env` 变量名 ⇄ `application.example.yml` 里的 `${...}`：一一对应，改名必须两边同步。
以下变量**不出现在后端占位符里**，原因各异：

- 仅被 `docker-compose.yml` 使用：`MYSQL_ROOT_PASSWORD`、`QINGLAN_DB_NAME`、`REDIS_PASSWORD`、
  `HTTP_PORT`、`QINGLAN_IMAGE_TAG`。
- `SPRING_PROFILES_ACTIVE`：由 Spring Boot 直接读取（标准环境变量绑定，不是 `${}` 占位符）。

`QINGLAN_DB_USERNAME` / `QINGLAN_DB_PASSWORD` 同时被 compose（建库账号）与后端（连库）使用，
改一处即两端一致。

---

## 5. nginx 与 `trusted-proxy-hops` 的对应关系

`deploy/nginx.conf` 里 `/api/` 与 `/uploads/` 会用
`proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;` 把真实客户端 IP 追加进 XFF。

后端 `IpRateLimitFilter` 依 `qinglan.security.trusted-proxy-hops` 取 **XFF 右起第 hops 跳**：

- 本部署只有 **这一层** nginx → 生产**必须** `QINGLAN_TRUSTED_PROXY_HOPS=1`。
  - 设 `0`：后端只取 TCP 对端 = nginx 容器 IP，限流会把全体用户算成同一个人（维度错误）。
  - 设 `1` 且用 `$proxy_add_x_forwarded_for`：客户端自报的左段（可伪造）被丢弃，取到真实 IP。
  - 若将来在前置再加一层 LB/CDN，hops 要随之 `+1`，**改前必须先确认拓扑**。
- 这也是 T-26① 的前置条件：公告「已读」限流要从 deviceId 改为按 IP，前提就是先把 hops 定对。

---

## 6. 前端产物构建策略

**本方案不在镜像里构建前端**：两个 SPA 由主机（或 CI）`pnpm -r build` 生成，再以 bind mount
挂进 nginx 容器（见 `docker-compose.yml` 的 nginx.volumes）。理由：

- `shared` 是**源码形式**被两个前端引用（`@shared` 别名指向 `../shared/src`），前端构建天然需要
  「仓库根 + shared + 两个应用」的完整 workspace；把它塞进 nginx 镜像没有收益，反而让镜像构建
  更复杂、更慢。
- 后端镜像只装运行所需 jar，职责单一；前端产物更新时只需替换挂载目录 + `nginx -s reload`，不必
  重打后端镜像。

> 若将来确实要在镜像内构建前端：构建上下文**必须是仓库根**（这样 `../shared` 才取得到），
> 且镜像内要装 Node + pnpm，按 `pnpm install && pnpm -r build` 产出 dist 再拷进最终镜像。
> 本轮未提供该 Dockerfile（无 Docker 可验证），故不臆造。

---

## 7. 备份与恢复

### 7.1 备份

`deploy/backup.sh`：在 mysql 容器内 `mysqldump`（`--single-transaction` 一致性快照、不锁写），
在 app 容器内 `tar` 打包上传目录；产物按 UTC 时间戳命名，写 sha256，保留最近 N 份。

```bash
cd /srv/qinglan
bash deploy/backup.sh
# 产物示例：
#   /var/backups/qinglan/qinglan-db-20260101T033000Z.sql.gz
#   /var/backups/qinglan/qinglan-uploads-20260101T033000Z.tar.gz
#   /var/backups/qinglan/qinglan-20260101T033000Z.sha256
```

- 任一步失败即**非零退出**（`set -euo pipefail` + 显式 `die`），可直接给 cron/告警判断。
- 完整性粗校验：mysqldump 收尾若无 `-- Dump completed` 标记，视为截断、丢弃并不留半成品。
- 保留策略：`RETENTION`（默认 14），以数据库 dump 为「每份」的代表，按时间删旧组。
- cron 示例（每天 UTC 03:30）：
  ```
  30 3 * * *  cd /srv/qinglan && /usr/bin/bash deploy/backup.sh >> /var/log/qinglan-backup.log 2>&1
  ```

### 7.2 恢复（顺序：先停写 → 再恢复 → 再校验）

```bash
cd /srv/qinglan
STAMP=20260101T033000Z          # 选定的备份组
BK=/var/backups/qinglan

# 0) 校验完整性
( cd "$BK" && sha256sum -c "qinglan-$STAMP.sha256" )

# 1) 停写：停掉应用与对外入口，避免恢复期间有新写入（mysql/redis 保持运行）
docker compose stop nginx app

# 2) 恢复数据库：先清空目标库再导入（dump 未含 CREATE DATABASE，导入到已有库会撞表）
docker compose exec -T mysql sh -c \
  'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE IF EXISTS \`$MYSQL_DATABASE\`; CREATE DATABASE \`$MYSQL_DATABASE\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"'
gunzip -c "$BK/qinglan-db-$STAMP.sql.gz" | docker compose exec -T mysql sh -c \
  'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'

# 3) 恢复上传目录：用 app 镜像起一次性容器（覆盖 entrypoint），挂在同一个 uploads 卷上
docker compose run --rm --no-deps -T --entrypoint tar app xzf - -C /app/data/uploads \
  < "$BK/qinglan-uploads-$STAMP.tar.gz"

# 4) 校验：先起后端看健康，再放流量
docker compose up -d app
docker compose exec app curl -fsS http://127.0.0.1:8080/actuator/health
#   应用起得来 = Flyway 校验 + JPA ddl-auto=validate 均通过 → 库结构与旧版本匹配
docker compose up -d nginx

# 5) 人工抽查：登录、首页列表、帖子详情、图片能否打开、管理端 /admin/ 刷新
```

> 恢复会**回退数据库到备份时刻**，期间新产生的数据会丢失 —— 这就是「无 binlog/增量备份」的 RPO 代价。

### 7.3 恢复演练清单（可执行清单，**未演练**）

本机无 Docker，以下步骤**未实际执行**，仅作为演练用的可执行清单：

- [ ] 在一台与生产同版本 compose 的临时主机（或预发）上部署一套空栈
- [ ] 灌入少量测试数据（发帖、传图）
- [ ] 跑 `bash deploy/backup.sh`，确认生成 db / uploads / sha256 三份
- [ ] `sha256sum -c` 校验通过
- [ ] 按 7.2 节步骤恢复，确认应用能起、图片能开、数据一致
- [ ] 记录实测 **RTO**（恢复耗时）与 **RPO**（数据丢失窗口 = 距上次备份的时长）
- [ ] 演练后销毁临时环境
- [ ] （可选）在正式环境做一次同样演练，最好安排在低峰期

### 7.4 已知缺口

- **没有 binlog / 增量备份**：本方案只有「全量 + 保留 N 份」，RPO = 备份周期（默认一天），
  无法按秒恢复。要做到低 RPO，需在 mysql 服务开启 `--log-bin` 并配 `mysqlbinlog` 增量归档。
- **备份落本机磁盘**：不具备异地容灾。生产应把 `BACKUP_DIR` 指向异地挂载或对象存储。
- **上传目录与对象存储**：生产已切 S3 兼容对象存储（`QINGLAN_STORAGE_TYPE=s3`），图片**不在**
  本地卷里，`tar` 打包上传目录这一步对生产不再有意义——备份应改用对象存储侧的版本/快照能力
  （或 `mc mirror` 之类）。本地卷仅在 `QINGLAN_STORAGE_TYPE=local`（本地开发/测试）时使用。

---

## 8. 发布流程

### 8.1 后端

```bash
cd /srv/qinglan
git fetch && git checkout <tag/commit>
SHA=$(git rev-parse --short HEAD)

# 构建并给镜像打「可回滚」的 tag（当前 pom 恒为 0.1.0-SNAPSHOT，不能靠版本号区分，见第 10 节）
docker compose build app
docker tag "qinglan-server:local" "qinglan-server:$SHA"

# 指向新 tag 并重启 app（单实例，会有数秒中断；要零中断需多实例 + 前置负载均衡，本方案未做）
#   在 .env 里把 QINGLAN_IMAGE_TAG 改成 $SHA
docker compose up -d app

# 观察
docker compose exec app curl -fsS http://127.0.0.1:8080/actuator/health
docker compose logs --tail=100 app
```

Flyway 迁移在应用启动时自动执行（`spring.flyway.enabled=true`）。迁移耗时会让启动变慢，
`HEALTHCHECK` 的 `start-period` 已给到 60s。

### 8.2 前端

```bash
cd /srv/qinglan
mkdir -p deploy/releases
STAMP=$(date -u +%Y%m%dT%H%M%SZ)

# 归档当前产物，供回滚
[ -d qinglan-web/dist ]   && tar czf "deploy/releases/web-$STAMP.tar.gz"   -C qinglan-web   dist
[ -d qinglan-admin/dist ] && tar czf "deploy/releases/admin-$STAMP.tar.gz" -C qinglan-admin dist

# 用新代码重新构建（会覆盖 dist/，nginx 直接读到新文件）
pnpm install && pnpm -r build
docker compose exec nginx nginx -s reload   # 可选：让新文件与缓存头立即生效
```

> `deploy/releases/` 建议加入 `.gitignore`。

---

## 9. 回滚预案

| 层 | 回滚方式 | 备注 |
| --- | --- | --- |
| 前端 | 从 `deploy/releases/{web,admin}-<stamp>.tar.gz` 解回 `qinglan-web/dist` / `qinglan-admin/dist`，`nginx -s reload` | 静态产物无状态，最安全；建议 UI 出问题时优先 |
| 后端镜像 | 把 `.env` 的 `QINGLAN_IMAGE_TAG` 改回上一个 sha，`docker compose up -d app` | 需保留旧镜像 tag（别 `docker image prune -a` 清光） |
| 配置 | 回滚 `.env` 后 `docker compose up -d` | 密钥回滚注意第 10 节的不可轮换项 |
| 数据库 | 见下 | 最复杂，见 Flyway 约定 |

**Flyway 迁移的回滚约定**（AGENTS.md 第九章「迁移脚本必须可回滚、不得改写已上线脚本」）：

- Flyway 社区版**没有自动 undo**，所以「可回滚」靠两条纪律落实：
  1. 破坏性变更走 **expand-contract**：先加新列/新表（expand）→ 双写/回填 → 下一个版本才删旧列
     （contract）。任何单次发布都不做「删了就没法退」的改动。
  2. 不可逆的迁移，回滚路径 = **整库恢复**（第 7.2 节），因此发布前**必须**先跑 `backup.sh`。
- ⚠️ **待验证项**：回滚到旧镜像时，若库中已应用了比旧代码更新的迁移版本，Flyway 可能因
  「存在未在本地解析的迁移」而拒绝启动。当前仓库未配置 `spring.flyway.ignore-migration-patterns`。
  上线前必须实测一次「旧镜像 + 新库」能否启动；若不能，需显式配置该属性或走整库恢复。
  （此项**未验证**，见文末。）

---

## 10. 版本号规则（含已知问题）

- 当前**所有**包版本恒定：后端 pom `0.1.0-SNAPSHOT`、前端各包 `0.1.0`。属**已知问题**：同一版本号
  对应不同提交，无法用版本号区分发布。
- 过渡方案：**以 git 短 sha 作为镜像 tag 与前端归档名**（第 8 节），这也是回滚的锚点。
- 建议的正式规则（未实施）：发布时用 `git tag vX.Y.Z`，构建期把版本注入构件
  （Maven 用 `${revision}` + flatten 插件，前端用 `vite define`），镜像 tag 用版本号。
  本轮不改构建脚本，仅记录规则。

---

## 11. Flyway 迁移演练清单（可执行清单，**未演练**）

- [ ] 发布前 `bash deploy/backup.sh`，留一份可回退的完整备份
- [ ] 在预发/临时库用**新 jar** 启动，观察 Flyway 自动迁移日志与 `flyway_schema_history` 新增行
- [ ] 校验：应用能起（即 `ddl-auto=validate` 通过）+ 关键查询/接口正常
- [ ] 记录该批迁移的**执行耗时**（大表 DDL 尤其要测，必要时放低峰期，见 AGENTS.md 第五章）
- [ ] 验证回滚路径：
  - expand 类（新增列/表）：确认旧代码忽略新列仍可跑
  - contract 类（删列/表）：先按第 7.2 节在临时库演练整库恢复
- [ ] 验证「旧镜像 + 新库」能否启动（第 9 节待验证项）

---

## 12. 已知缺口与运维待办（与《修复记录.txt》衔接）

| 编号 | 与本方案的关系 | 状态 |
| --- | --- | --- |
| **L1** 请求级追踪 / 结构化日志采集 | 后端已有 `TraceIdFilter` 写 MDC、日志 pattern 带 `traceId`；`application.example.yml` 里结构化日志（JSON）开关已备好（注释）。**缺口**：未接入采集/聚合（Loki/ELK）。 | 部分 |
| **L2** IP 级全局限流 | 已有 `/api/auth/email/code` 的 IP 限流（`qinglan.security`）。**缺口**：无全局维度限流；公告「已读」仍按 deviceId（T-26①），依赖第 5 节先定好 hops。 | 部分 |
| **L4** 审计日志归档 | `review_log` 只增不改删（不变量 #7）。**缺口**：无归档/清理策略。 | 未做 |
| **L5** 静态资源内容哈希 | nginx 已给缓存头（assets `immutable`）。**uploads 的缓存改由后端受控端点下发**（短 TTL，见第 12 节），nginx 不再为 `/uploads/` 设 `Cache-Control`。 | 部分 |
| **T-12'** 图片归属绑定 / 孤儿回收 | **已实现**：上传即登记（`uploaded_file`，V8 迁移），发帖校验「属本人 + 未被引用」并回填绑定，定时任务回收超期未绑定项。**未实机验证**（同本方案整体）。 | 已实现待验 |
| **T-27①** 下架帖图片仍可直取 | **已实现**：`/uploads/**` 由受控端点下发，按帖子可见性裁决，不可见/不存在一律 404。**未实机验证**。 | 已实现待验 |
| **T-14** SPA 防嵌套响应头 | `deploy/nginx.conf` 已为**学生端与管理端两处**补 `X-Frame-Options: DENY` + CSP `frame-ancestors 'none'`。**未实机验证**。 | 已实现待验 |
| **T-26** ①~⑦ | ⑥（非本地 profile + expose-code 守卫）**已由 `ProdEnvironmentGuard` 实现**；① 的部署前提（定 hops）在本手册第 5 节；其余为产品决策。 | 部分 |
| **T-29** ①~⑤ | ④（启动守卫）**已实现**（同上）；其余为产品/性能取舍，本方案不涉及。 | 部分 |

**本方案触及但未落地的架构项**（属既定计划，非遗漏）：

- **对象存储**：**已落地**——`StorageService` 有本地（`local`）与 S3 兼容（`s3`）两套实现，
  生产由 prod 守卫强制 `qinglan.storage.type=s3`（AGENTS.md 第九章「存对象存储」）。配置项见
  `.env.example` 的 `QINGLAN_S3_*`。**未验证**：真实 MinIO / 腾讯 COS / 阿里 OSS 未实测
  （仅用内嵌 S3Mock 做过协议级集成测试）。
- **镜像仓库**：本方案镜像仅在部署主机本地构建，无 registry；多主机部署需先补私有 registry。
- **灰度 / 回滚自动化**：回滚是手工步骤（第 9 节），无蓝绿/金丝雀自动化，也无 CI 自动发布。

---

## 13. 需部署方拍板 / 填值的项

1. **`QINGLAN_TRUSTED_PROXY_HOPS`**：默认为 1；若前置再加 LB/CDN 需调整（第 5 节）。
2. **备份保留份数与周期**：`RETENTION`（默认 14）与 cron 频率，取决于 RPO 目标与磁盘容量。
3. **SMTP 服务商与参数**：`QINGLAN_AUTH_MAIL_*` 全组，以及发件域名/SPF/DKIM。
4. **`QINGLAN_AUTH_EMAIL_DOMAINS`**：真实校园邮箱域名白名单（留空 = 拒绝所有邮箱）。
5. **密钥托管方式**：本方案用 `.env` 文件；是否改用 Docker secrets / 云 KMS 由部署方定。
   **注意** AES 密钥与 email-pepper **不可轮换**，须离线备份。
6. **是否启用结构化日志**：`application.example.yml` 的 `logging.structured` 开关（默认注释掉）。
7. **对象存储参数**：`QINGLAN_S3_ENDPOINT` / `QINGLAN_S3_REGION` / `QINGLAN_S3_BUCKET` /
   `QINGLAN_S3_ACCESS_KEY` / `QINGLAN_S3_SECRET_KEY`（生产必填，缺一即拒绝启动）。
   自建 MinIO 可直接复用；用云厂商（COS / OSS）时按厂商文档填 endpoint 与 region。
   桶需预先创建（应用不自动建桶）。
8. **私有图片签名密钥**：`QINGLAN_STORAGE_IMAGE_SIGNING_SECRET`（生产必填，见第 4 节守卫表与 4.1）。
   轮换会使**已签发的图片链接立即失效**（用户刷新页面即可拿到新链接），影响面小于 JWT 轮换；
   有效期为 `qinglan.storage.image-signing-ttl`（默认 10 分钟，硬上限 1 小时）。

---

## 验证状态（诚实汇总）

**已在本机真验证**：

- `docker-compose.yml`、`.github/workflows/ci.yml` YAML 语法可解析（`python -c "import yaml; yaml.safe_load(...)"`）。
- `deploy/backup.sh` shell 语法（`bash -n`）。
- `.env.example` 里每个后端变量名与 `application.example.yml` 的 `${...}` 占位符逐项核对一致。
- `nginx.conf` / `Dockerfile` / `docker-compose.yml` 引用的每个仓库路径（`qinglan-web/dist`、
  `qinglan-admin/dist`、`deploy/nginx.conf`、`qinglan-server/src/main/resources/application.example.yml`
  …）与仓库实际结构一致。
- 后端 `./mvnw test` 全量真跑通过：**572 用例、0 失败、跳过 6**（无失败、无 `@Disabled`；跳过的 6 条
  是本机无 Redis 时按既有条件自动跳过的 Redis 用例，CI 里会真跑）。
- S3 兼容实现（本轮新增）用**内嵌 S3Mock**（纯 Java、无 Docker）真跑：put/get/delete、path-style、
  删除不存在对象幂等。**真实 MinIO / COS / OSS 未实测**（见下）。
- V8 迁移在本机 MySQL 8 上真跑：Flyway 校验并应用 8 个迁移，`ddl-auto=validate` 通过。
- `uploaded_file` 回收查询的索引用**本机 MySQL 造数 + EXPLAIN** 验证（结论见 V8 迁移文件头）。

**未验证（本机无 Docker / nginx，无法运行）**：

- `docker compose build` / `up`、镜像构建、`HEALTHCHECK` 生效。
- nginx 起服务、静态托管、反代、`try_files` 回退、安全响应头与缓存头实际下发
  （含 `/uploads/` 反代到后端、图片短 TTL 缓存头与下架即时 404 的端到端表现）。
- 真实 S3 兼容对象存储（自建 MinIO / 腾讯 COS / 阿里 OSS）的连通性与桶权限；仅以内嵌 S3Mock
  做过协议级验证。
- `deploy/backup.sh` 的真实备份与第 7.2 节恢复流程、恢复演练。
- 生产启动守卫在容器内的实际触发行为。
- 第 9 节「旧镜像 + 新库」Flyway 启动行为。

> 部署方首次上线前，请在预发环境把上面「未验证」项逐条跑一遍再上生产。
