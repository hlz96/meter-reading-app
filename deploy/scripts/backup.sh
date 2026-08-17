#!/usr/bin/env bash
# ============================================================
# MySQL 每日备份脚本(自建方案的数据安全红线)
# 用法:
#   1. 手动跑一次:bash backup.sh
#   2. 加到 crontab 每天凌晨 3 点:
#      crontab -e
#      0 3 * * * /path/to/deploy/scripts/backup.sh >> /var/log/meter-backup.log 2>&1
# ⚠️ 建议再配一步:把备份目录定期同步到异地(对象存储/网盘),防服务器整机故障
# ============================================================
set -euo pipefail

# ---- 配置 ----
DEPLOY_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BACKUP_DIR="${DEPLOY_DIR}/data/backups"
KEEP_DAYS=14                       # 本地保留天数
CONTAINER="meter-mysql"

# 从 .env 读数据库名与密码
set -a; source "${DEPLOY_DIR}/.env"; set +a

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="${BACKUP_DIR}/${MYSQL_DATABASE}-${STAMP}.sql.gz"

mkdir -p "${BACKUP_DIR}"

echo "[$(date '+%F %T')] 开始备份 ${MYSQL_DATABASE} ..."
docker exec "${CONTAINER}" \
  mysqldump -u root -p"${MYSQL_ROOT_PASSWORD}" \
    --single-transaction --quick --routines --triggers \
    "${MYSQL_DATABASE}" | gzip > "${OUT}"

echo "[$(date '+%F %T')] 备份完成:${OUT} ($(du -h "${OUT}" | cut -f1))"

# 清理过期备份
find "${BACKUP_DIR}" -name "${MYSQL_DATABASE}-*.sql.gz" -mtime +${KEEP_DAYS} -delete
echo "[$(date '+%F %T')] 已清理 ${KEEP_DAYS} 天前的旧备份"

# ---- 可选:异地同步(取消注释并配好工具)----
# rclone copy "${OUT}" remote:meter-backups/   # 需先配 rclone 到对象存储/网盘
