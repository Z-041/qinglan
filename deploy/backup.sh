#!/usr/bin/env bash
# =============================================================================
# 青栏数据备份：MySQL 全量 dump + 上传目录打包，带日期命名与「保留最近 N 份」清理。
#
# 适用拓扑：docker compose（见仓库根 docker-compose.yml）。脚本通过在 mysql / app
# 容器内执行 mysqldump 与 tar 完成备份，因此**宿主机不需要安装 mysql 客户端**，
# 也不需要把 3306 发布到宿主机（保持网络最小暴露面）。
#
# 用法（在仓库根）：
#   bash deploy/backup.sh
#
# 定时（Linux cron，每天 UTC 03:30；时区按需改）：
#   30 3 * * *  cd /srv/qinglan && /usr/bin/bash deploy/backup.sh >> /var/log/qinglan-backup.log 2>&1
#
# 退出码：成功 0；任一步骤失败即非 0（set -e + 显式 die），供 cron 邮件 / 告警判断。
#
# 可覆盖的环境变量：
#   BACKUP_DIR                  备份落盘目录（默认 /var/backups/qinglan）
#   RETENTION                   保留最近多少份（默认 14）
#   COMPOSE_FILE                compose 文件名（默认 docker-compose.yml）
#   COMPOSE_CMD                 compose 命令（默认 `docker compose`；老版本可设 docker-compose）
#   DB_SERVICE / APP_SERVICE    服务名（默认 mysql / app）
#   UPLOAD_DIR_IN_CONTAINER     容器内上传目录（默认 /app/data/uploads）
#
# 未覆盖的已知缺口：
#   - 没有 binlog / 增量备份：本脚本是「全量 + 保留 N 份」，RPO = 备份周期，无法做到
#     按秒恢复（见 deploy/README.md「已知缺口」）。
#   - 备份落本地磁盘：不具备异地容灾，生产应把 BACKUP_DIR 指向异地/对象存储挂载点。
# =============================================================================
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/var/backups/qinglan}"
RETENTION="${RETENTION:-14}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
COMPOSE_CMD="${COMPOSE_CMD:-docker compose}"
DB_SERVICE="${DB_SERVICE:-mysql}"
APP_SERVICE="${APP_SERVICE:-app}"
UPLOAD_DIR_IN_CONTAINER="${UPLOAD_DIR_IN_CONTAINER:-/app/data/uploads}"

log() { printf '%s [backup] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }
die() { printf '%s [backup] ERROR: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >&2; exit 1; }

# 切到仓库根（脚本位于 deploy/ 下），保证 compose 文件可被找到
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

command -v docker >/dev/null 2>&1 || die "未找到 docker 命令，请确认在部署主机上运行"
[ -f "$COMPOSE_FILE" ] || die "找不到 compose 文件：$REPO_ROOT/$COMPOSE_FILE"

mkdir -p "$BACKUP_DIR"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DB_PARTIAL="$BACKUP_DIR/.qinglan-db-$STAMP.sql.partial"
DB_GZ="$BACKUP_DIR/qinglan-db-$STAMP.sql.gz"
UPLOADS_PARTIAL="$BACKUP_DIR/.qinglan-uploads-$STAMP.tar.gz.partial"
UPLOADS_TAR="$BACKUP_DIR/qinglan-uploads-$STAMP.tar.gz"

# 中途失败时清理半成品文件（避免出现看似完整、实则截断的备份）
trap 'rm -f "$DB_PARTIAL" "$UPLOADS_PARTIAL"' ERR

log "开始备份 stamp=$STAMP -> $BACKUP_DIR"

# ---------------------------------------------------------------------------
# 1) MySQL 全量导出
# --single-transaction：InnoDB 一致性快照，不锁表、不阻塞写入
# 变量 MYSQL_ROOT_PASSWORD / MYSQL_DATABASE 由 compose 注入 mysql 容器，容器内展开
# ---------------------------------------------------------------------------
log "导出数据库到 $DB_GZ"
if ! $COMPOSE_CMD -f "$COMPOSE_FILE" exec -T "$DB_SERVICE" sh -c \
        'exec mysqldump --single-transaction --quick --routines --triggers --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
        > "$DB_PARTIAL"; then
    rm -f "$DB_PARTIAL"
    die "mysqldump 失败（数据库不可用或容器未启动）"
fi
# 完整性粗校验：mysqldump 正常收尾会写 "-- Dump completed"；缺了说明连接中途断了
grep -q -- "-- Dump completed" "$DB_PARTIAL" || {
    rm -f "$DB_PARTIAL"
    die "dump 不完整（缺少 'Dump completed' 标记），已丢弃半成品"
}
gzip -c "$DB_PARTIAL" > "$DB_GZ"
rm -f "$DB_PARTIAL"
log "数据库备份完成：$(basename "$DB_GZ")"

# ---------------------------------------------------------------------------
# 2) 上传目录打包（在 app 容器内打包命名卷挂载点，宿主机无需知道卷真实路径）
# 切对象存储后本目录会变空，届时这一步应替换为对象存储的版本/快照能力
# ---------------------------------------------------------------------------
log "打包上传目录 $UPLOAD_DIR_IN_CONTAINER"
if ! $COMPOSE_CMD -f "$COMPOSE_FILE" exec -T "$APP_SERVICE" \
        tar czf - -C "$UPLOAD_DIR_IN_CONTAINER" . > "$UPLOADS_PARTIAL"; then
    rm -f "$UPLOADS_PARTIAL"
    die "上传目录打包失败（app 容器未启动？）"
fi
mv "$UPLOADS_PARTIAL" "$UPLOADS_TAR"
log "上传目录备份完成：$(basename "$UPLOADS_TAR")"

# ---------------------------------------------------------------------------
# 3) 校验和（可选：宿主无 sha256sum 时跳过）
# ---------------------------------------------------------------------------
if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$DB_GZ" "$UPLOADS_TAR" > "$BACKUP_DIR/qinglan-$STAMP.sha256"
    log "已写校验和：qinglan-$STAMP.sha256"
fi

# ---------------------------------------------------------------------------
# 4) 保留最近 N 份：以数据库备份文件为「每份」的代表，按时间从新到旧，删第 N+1 个起
# ---------------------------------------------------------------------------
log "清理旧备份，保留最近 $RETENTION 份"
mapfile -t STALE < <(ls -1t "$BACKUP_DIR"/qinglan-db-*.sql.gz 2>/dev/null | tail -n +"$((RETENTION + 1))")
for f in "${STALE[@]:-}"; do
    [ -n "$f" ] || continue
    rest="$(basename "$f")"; rest="${rest#qinglan-db-}"
    stamp="${rest%.sql.gz}"
    log "删除过期备份组 $stamp"
    rm -f "$BACKUP_DIR/qinglan-db-$stamp.sql.gz" \
          "$BACKUP_DIR/qinglan-uploads-$stamp.tar.gz" \
          "$BACKUP_DIR/qinglan-$stamp.sha256"
done

trap - ERR
log "备份成功完成"
exit 0
