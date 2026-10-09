# syntax=docker/dockerfile:1

# =============================================================================
# 青栏后端镜像（qinglan-server）。
#
# 构建上下文 = **仓库根目录**（见 docker-compose.yml 的 build.context: .）：
#   docker build -t qinglan-server:local .
#
# 为什么上下文取仓库根而不是 qinglan-server/：
#   .dockerignore 只能在上下文根生效，取仓库根才能用一份 ignore 同时排掉前端的
#   node_modules/dist 与本机的 application.yml（含密钥）。将来若要连前端产物一起
#   打进镜像，也能在上下文里直接取到 ../shared。
#
# 本镜像是**纯后端**镜像：前端两个 SPA 的产物由 nginx 容器挂载（见 docker-compose.yml），
# 不在此镜像内构建，理由见 deploy/README.md「前端产物构建策略」。
# =============================================================================

# ---------------------------- 构建阶段 ----------------------------
# 官方 Maven 镜像自带 JDK 21，省去在镜像里下载 maven-wrapper 发行包（内网/断网也能构建）
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先只拷 pom 单独拉依赖：源码改动不会让依赖层缓存失效，二次构建快得多
COPY qinglan-server/pom.xml ./qinglan-server/pom.xml
RUN mvn -q -B -f qinglan-server/pom.xml dependency:go-offline

# 再拷源码打包。跳过测试：镜像构建连不上 MySQL/Redis，测试在 CI 的 backend job 里跑
COPY qinglan-server/src ./qinglan-server/src
RUN mvn -q -B -f qinglan-server/pom.xml clean package -DskipTests

# ---------------------------- 运行阶段 ----------------------------
FROM eclipse-temurin:21-jre AS runtime

# curl 仅服务于下面的 HEALTHCHECK。
# 用 apt 装而非换基础镜像：Debian/Ubuntu 系 glibc 与构建阶段一致，避免 musl 差异。
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# 非 root 运行：容器若被攻破，限制其对宿主与同网其它容器的操作面（AGENTS.md 第九章）
RUN groupadd --system --gid 1001 qinglan \
 && useradd --system --uid 1001 --gid qinglan --create-home qinglan

WORKDIR /app

# 只复制运行所需产物：可执行 jar。不拷源码、不拷 target 里的其它文件（*.original 等）
COPY --from=build /build/qinglan-server/target/qinglan-server-*.jar /app/app.jar

# 把可提交的配置模板落成运行期配置。
# 为什么必须这么做：src/main/resources/application.yml 被 .gitignore 忽略、不在仓库里，
# 干净 checkout 打出的 jar **不含 application.yml**，没有任何配置；而模板 application.example.yml
# 里保存着全部「环境变量 -> 配置键」的映射（${QINGLAN_...} 占位符），
# 放到 file:./config/ 后 Spring Boot 会加载它，再用环境变量把值灌进去。
# 模板内的默认值是 fail-closed 的（expose-code=false 等），生产另由 ProdEnvironmentGuard 兜底，
# 因此「一套环境变量部署全栈」成立，无需在宿主机维护第二份配置文件。
COPY qinglan-server/src/main/resources/application.example.yml /app/config/application.yml

# 上传目录：由命名卷挂载（见 docker-compose.yml），保证容器重建/升级后图片不丢。
# 与 qinglan.storage.local-root 一致（生产用 QINGLAN_STORAGE_ROOT=/app/data/uploads）。
RUN mkdir -p /app/data/uploads && chown -R qinglan:qinglan /app
USER qinglan

EXPOSE 8080

# 便于按需追加 JVM 参数（如 -Xmx、-XX:+UseZGC）；默认空
ENV JAVA_OPTS=""

# 指向 actuator：start-period 给足冷启动（Flyway 迁移 + JPA 校验）时间
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

# 用 sh -c 让 $JAVA_OPTS 参与展开；exec 让 java 成为 PID 1，正确接收 SIGTERM 优雅停机
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
