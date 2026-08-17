# 部署说明 — 起步阶段(一台服务器全包)

面向:**起步阶段自己用 / 少数人用**。一台轻量云服务器,用 Docker Compose 同时跑
后端 + MySQL + Redis + Nginx,照片存本地磁盘。月成本约 ¥30-70。

> 用户变多后如何升级到云数据库,见 `../docs/TRD.md` 第 8.1 节的分阶段策略。

---

## 目录结构
```
deploy/
├── docker-compose.yml      # 一体化编排:后端+MySQL+Redis+Nginx
├── .env.example            # 环境变量样例(复制为 .env 改密码)
├── .gitignore              # 忽略 .env / data / 证书
├── mysql-init/
│   └── 01-init.sql         # MySQL 首次启动:编码+授权(不建表)
├── nginx/
│   ├── conf.d/meter.conf   # 反向代理 + HTTPS + 照片托管
│   └── certs/              # HTTPS 证书(自行放入,git 忽略)
├── scripts/
│   ├── backup.sh           # 每日备份(务必配 crontab)
│   └── restore.sh          # 从备份恢复
└── data/                   # 运行时数据(git 忽略,自动生成)
    ├── mysql/  redis/  uploads/  backups/
```

---

## 一、服务器准备
1. 买一台**轻量应用服务器**(2核2G / 3-4M 带宽 / 50G SSD,挑首年活动价)。
2. 系统选 Ubuntu 22.04 或 Debian 12。
3. 安装 Docker 与 Compose 插件:
   ```bash
   curl -fsSL https://get.docker.com | sh
   sudo systemctl enable --now docker
   ```
4. **防火墙/安全组只放行 80、443、SSH(22)**,数据库端口绝不对外开放。

## 二、配置
```bash
cd deploy
cp .env.example .env
# 编辑 .env,把每个「改成…」的占位改成强密码
# JWT_SECRET 可用:openssl rand -base64 48
```

## 三、准备后端镜像
- 若本地有后端代码:`docker build -t meter-backend:latest .`(在后端项目根目录)
- 或推到镜像仓库后,把 `.env` 里 `BACKEND_IMAGE` 改成仓库地址。
- 表结构由后端 **Flyway** 在首次启动时自动初始化,无需手动建表。

## 四、启动
```bash
docker compose up -d      # 拉起全部服务
docker compose ps         # 看状态,mysql/redis 应为 healthy
docker compose logs -f backend   # 看后端启动日志
```

## 五、配置 HTTPS(有域名时)
1. 域名解析到服务器公网 IP。
2. 先用 certbot 签证书(或用云厂商免费证书),把 `fullchain.pem` / `privkey.pem` 放进 `nginx/certs/`。
3. 改 `nginx/conf.d/meter.conf` 里的 `server_name` 为你的域名。
4. `docker compose restart nginx`。
> 没有域名时:先只用 80 段、以公网 IP 访问跑通,后续再补 HTTPS。

## 六、配置每日备份(重要!)
```bash
chmod +x scripts/backup.sh scripts/restore.sh
crontab -e
# 加一行:每天凌晨 3 点备份
0 3 * * * /root/meter-reading-app/deploy/scripts/backup.sh >> /var/log/meter-backup.log 2>&1
```
建议再把 `data/backups/` 定期同步到对象存储/网盘(脚本末尾有 rclone 示例),
**异地留一份**才能扛住整机故障。

---

## 常用运维命令
| 操作 | 命令 |
|------|------|
| 查看状态 | `docker compose ps` |
| 看日志 | `docker compose logs -f backend` |
| 更新后端 | 重新 build/pull 镜像后 `docker compose up -d backend` |
| 手动备份 | `bash scripts/backup.sh` |
| 恢复数据 | `bash scripts/restore.sh data/backups/xxx.sql.gz` |
| 停止全部 | `docker compose down`(加 `-v` 会删数据卷,慎用) |
| 看磁盘 | `df -h` 和 `du -sh data/*` |

## 安全红线(自建必守)
- ✅ MySQL/Redis 只监听 `127.0.0.1`(compose 已配),**绝不暴露公网**。
- ✅ `.env` 用强密码,且不提交到 git。
- ✅ SSH 用密钥登录,关闭密码登录。
- ✅ 每日备份 + 异地一份。
