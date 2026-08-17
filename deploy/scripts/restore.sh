#!/usr/bin/env bash
# ============================================================
# 从备份恢复 MySQL
# 用法:bash restore.sh <备份文件.sql.gz>
# ⚠️ 会覆盖现有库数据,执行前请确认!建议先跑一次 backup.sh 留底
# ============================================================
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "用法:bash restore.sh <备份文件.sql.gz>"
  echo "可用备份:"
  ls -1t "$(cd "$(dirname "$0")/.." && pwd)/data/backups/" 2>/dev/null || echo "  (无)"
  exit 1
fi

BACKUP_FILE="$1"
DEPLOY_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CONTAINER="meter-mysql"
set -a; source "${DEPLOY_DIR}/.env"; set +a

[ -f "${BACKUP_FILE}" ] || { echo "文件不存在:${BACKUP_FILE}"; exit 1; }

read -r -p "确认要恢复到库 ${MYSQL_DATABASE}?现有数据将被覆盖 [y/N] " ans
[ "${ans}" = "y" ] || { echo "已取消"; exit 0; }

echo "[$(date '+%F %T')] 开始恢复 ..."
gunzip -c "${BACKUP_FILE}" | docker exec -i "${CONTAINER}" \
  mysql -u root -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}"
echo "[$(date '+%F %T')] 恢复完成"
