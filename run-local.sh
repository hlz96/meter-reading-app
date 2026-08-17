#!/usr/bin/env bash
# ============================================================
# 本地一键启动/停止:后端(8081)+ 前端(5173),数据库可切本地 / 云
# 用法:
#   ./run-local.sh start           启动全部(本地 MySQL 3307)
#   ./run-local.sh start --cloud    启动全部(连云 DB,读 cloud.env)
#   ./run-local.sh stop            停止全部(前后端进程 + 本地 brew 服务)
#   ./run-local.sh status          查看各服务状态
#   ./run-local.sh logs            跟踪后端日志
#
# 数据库两种模式:
# - 本地(默认):brew MySQL,端口 3307,与本机老 MySQL(3306)隔离。
# - 云(--cloud):读项目根 cloud.env 里的 RDS 连接信息;不起本地 MySQL。
#   两种模式都在本地起 Redis(后端连接池需要;当前代码未实际读写 Redis)。
#
# 首次用云模式前:cp cloud.env.example cloud.env,填入 RDS 地址/账号/密码。
# cloud.env 含密码,不要提交到版本库。
# ============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT/backend"
FRONTEND_DIR="$ROOT/frontend"
JAR="$BACKEND_DIR/target/meter-reading-app-1.0.0.jar"
LOG_DIR="$ROOT/.local-run"
MODE_FILE="$LOG_DIR/mode"
CLOUD_ENV="$ROOT/cloud.env"
mkdir -p "$LOG_DIR"

export JAVA_HOME="/opt/homebrew/opt/openjdk@17"

# 本地 DB 默认值(本地模式用)
LOCAL_DB_URL="jdbc:mysql://127.0.0.1:3307/meter_reading?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
LOCAL_DB_USERNAME="meter"
LOCAL_DB_PASSWORD="meter"

# start_backend <db_url> <db_user> <db_pass>
start_backend() {
  echo "→ 启动后端(8081)..."
  cd "$BACKEND_DIR"
  SERVER_PORT=8081 \
  SPRING_DATASOURCE_URL="$1" \
  SPRING_DATASOURCE_USERNAME="$2" \
  SPRING_DATASOURCE_PASSWORD="$3" \
  SPRING_DATA_REDIS_HOST=localhost \
  SPRING_DATA_REDIS_PORT=6379 \
  SPRING_JPA_HIBERNATE_DDL_AUTO=none \
  nohup "$JAVA_HOME/bin/java" -jar "$JAR" > "$LOG_DIR/backend.log" 2>&1 &
  echo "  后端日志:$LOG_DIR/backend.log"
}

start_frontend() {
  echo "→ 启动前端(5173)..."
  cd "$FRONTEND_DIR"
  nohup npm run dev > "$LOG_DIR/frontend.log" 2>&1 &
  echo "  前端日志:$LOG_DIR/frontend.log"
}

wait_backend() {
  echo -n "  等后端就绪"
  for _ in $(seq 1 40); do
    if curl -s -m 2 http://localhost:8081/api/v1/ping 2>/dev/null | grep -q '"status":"up"'; then
      echo " ✅"; return 0
    fi
    echo -n "."; sleep 1
  done
  echo " ⚠️ 超时,查看 $LOG_DIR/backend.log"
}

case "${1:-}" in
  start)
    if [ ! -f "$JAR" ]; then
      echo "❌ 找不到后端 jar:$JAR"
      echo "   先编译:cd backend && JAVA_HOME=$JAVA_HOME mvn -DskipTests clean package"
      exit 1
    fi

    if [ "${2:-}" = "--cloud" ]; then
      # ---- 云模式 ----
      if [ ! -f "$CLOUD_ENV" ]; then
        echo "❌ 云模式需要 cloud.env,但没找到:$CLOUD_ENV"
        echo "   请先:cp cloud.env.example cloud.env,填入 RDS 地址/账号/密码"
        exit 1
      fi
      # shellcheck disable=SC1090
      source "$CLOUD_ENV"
      : "${CLOUD_DB_URL:?cloud.env 缺 CLOUD_DB_URL}"
      : "${CLOUD_DB_USERNAME:?cloud.env 缺 CLOUD_DB_USERNAME}"
      : "${CLOUD_DB_PASSWORD:?cloud.env 缺 CLOUD_DB_PASSWORD}"
      echo "模式:☁️  云数据库(cloud.env)"
      echo "cloud" > "$MODE_FILE"
      echo "→ 启动 Redis(brew,本地)..."
      brew services start redis >/dev/null 2>&1 || true
      start_backend "$CLOUD_DB_URL" "$CLOUD_DB_USERNAME" "$CLOUD_DB_PASSWORD"
    else
      # ---- 本地模式 ----
      echo "模式:💻 本地数据库(MySQL 3307)"
      echo "local" > "$MODE_FILE"
      echo "→ 启动 MySQL + Redis(brew,本地)..."
      brew services start mysql >/dev/null 2>&1 || true
      brew services start redis >/dev/null 2>&1 || true
      start_backend "$LOCAL_DB_URL" "$LOCAL_DB_USERNAME" "$LOCAL_DB_PASSWORD"
    fi

    wait_backend
    start_frontend
    echo ""
    echo "✅ 全部启动完成"
    echo "   前端:http://localhost:5173/"
    ;;
  stop)
    echo "→ 停止前端 / 后端进程..."
    pkill -f "meter-reading-app/frontend/node_modules/.bin/vite" 2>/dev/null && echo "  前端已停" || echo "  前端未运行"
    pkill -f "meter-reading-app-1.0.0.jar" 2>/dev/null && echo "  后端已停" || echo "  后端未运行"
    echo "→ 停止 MySQL + Redis(brew,本地)..."
    brew services stop mysql >/dev/null 2>&1 || true
    brew services stop redis >/dev/null 2>&1 || true
    rm -f "$MODE_FILE"
    echo "✅ 全部已停止(本地 DB 数据保留;云 DB 不受影响)"
    ;;
  status)
    chk() { lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1 && echo "✅ 在跑" || echo "❌ 停止"; }
    mode="$(cat "$MODE_FILE" 2>/dev/null || echo '未知')"
    echo "当前模式:  $mode"
    echo "前端 5173:  $(chk 5173)"
    echo "后端 8081:  $(curl -s -m 2 http://localhost:8081/api/v1/ping 2>/dev/null | grep -q up && echo '✅ 在跑' || echo '❌ 停止')"
    if [ "$mode" = "cloud" ]; then
      echo "数据库:     ☁️  云 DB(见 cloud.env,不检测)"
    else
      echo "MySQL 3307: $(chk 3307)"
    fi
    echo "Redis 6379: $(redis-cli ping 2>/dev/null | grep -q PONG && echo '✅ 在跑' || echo '❌ 停止')"
    ;;
  logs)
    tail -f "$LOG_DIR/backend.log"
    ;;
  *)
    echo "用法:$0 {start [--cloud]|stop|status|logs}"
    exit 1
    ;;
esac
