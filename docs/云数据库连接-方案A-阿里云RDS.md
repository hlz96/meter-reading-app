# 方案 A:后端连阿里云 RDS MySQL(操作清单)

> **目标**:后端仍在你本地跑(8081),数据库从本地 MySQL(3307)换成阿里云 RDS。
> **改动**:只改后端启动时的数据库连接串,**后端代码一行不动**(因为它用环境变量读配置)。
> **适用**:试水云数据库、让数据脱离本地。⚠️ 本方案开 RDS 公网访问,仅适合验证/开发,不是生产做法(生产见文末)。

---

## 前置说明(先读)

- **Redis 不用上云**:当前后端代码没有实际用到 Redis(短信验证码存在 DB,见 `SmsCode.java`)。本地保留 Redis 给后端连着即可,云上不用买。
- **本地已有数据不会自动过去**:现在本机 3307 里的 `13800138000` 账号留在本地。连上云 RDS 后是空库,Flyway 会自动建表,你重新注册一个即可。
- **RDS 版本选 MySQL 8.0**:与后端驱动 mysql-connector-j 8.3.0 匹配。

---

## 第一步:阿里云买 RDS 实例

1. 登录阿里云控制台 → 搜索 **"云数据库 RDS" → RDS MySQL**。
2. 点 **创建实例**,关键选项:
   - 计费方式:**按量付费**(试水用,随时释放不心疼;跑通了再转包月省钱)
   - 数据库类型:**MySQL 8.0**
   - 系列:**基础版**(单节点,最便宜;demo 足够)
   - 规格:最低配(如 1核1G / 通用型入门规格)
   - 存储:20GB 起步即可
   - 地域:选离你近的(如华东1-杭州)
3. 创建后等实例状态变 **"运行中"**(约几分钟)。

> 成本量级:基础版最低配按量付费约 ¥0.1-0.3/小时,不用时**记得释放实例**避免持续扣费。

---

## 第二步:开公网地址

RDS 默认只给**内网地址**(只有同 VPC 的阿里云服务器能连)。你后端在本地,必须开公网:

1. 进入实例详情 → 左侧 **数据库连接**。
2. 找到 **申请外网地址 / 开通外网地址**,点开通。
3. 记下生成的**外网连接地址**,形如:
   ```
   rm-xxxxxxxx.mysql.rds.aliyuncs.com   端口 3306
   ```

---

## 第三步:配白名单(不配连不上)

1. 实例详情 → **数据安全性 / 白名单设置**。
2. 需要把**你本地的公网出口 IP** 加进去。查你的公网 IP:
   ```bash
   curl -s https://myip.ipip.net || curl -s ifconfig.me
   ```
3. 把查到的 IP 加入白名单分组。
   - ⚠️ 家用宽带 IP 会变,变了就要重加。
   - ⚠️ **不要**图省事填 `0.0.0.0/0`(等于全公网可连,极危险)。

---

## 第四步:建库 + 建账号

在 RDS 控制台(不是本地):

1. 左侧 **账号管理** → 创建账号:
   - 账号名:`meter`
   - 密码:设一个强密码(记下来,下一步要用)
   - 类型:普通账号
2. 左侧 **数据库管理** → 创建数据库:
   - 库名:`meter_reading`
   - 字符集:**utf8mb4**
   - 授权账号:授权给刚建的 `meter`(读写权限)

---

## 第五步:改连接串启动后端

编辑项目根目录 `run-local.sh`,把 `start_backend()` 里的这三个环境变量改成 RDS 的值:

```bash
SPRING_DATASOURCE_URL="jdbc:mysql://rm-xxxxxxxx.mysql.rds.aliyuncs.com:3306/meter_reading?useSSL=true&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
SPRING_DATASOURCE_USERNAME=meter
SPRING_DATASOURCE_PASSWORD=你在第四步设的强密码
```

改动点说明:
- 地址换成 RDS 外网地址、端口 `3306`(不再是本地 3307)。
- `useSSL=true`:公网连接建议开 SSL(本地是 false)。
- 其余参数保留。

然后启动:
```bash
./run-local.sh start
```

Flyway 会在首次连上时自动建表(V1/V2/V3),和本地一样。

---

## 第六步:验证连通

```bash
# 1. 后端健康
curl -s http://localhost:8081/api/v1/ping
# 期望:{"code":0,...,"status":"up"}

# 2. 穿前端代理注册一个账号(验证写库到云 RDS)
curl -s -X POST http://localhost:5173/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"phone":"13800138000","password":"test1234","orgName":"演示公司"}'
# 期望:code:0 + 返回 accessToken
```

在 RDS 控制台的**数据库 → SQL 查询**里 `SELECT * FROM user;` 应能看到刚注册的账号,证明数据落到了云上。

---

## 常见问题排查

| 现象 | 原因 | 解决 |
|---|---|---|
| 后端启动卡住/`Communications link failure` | 白名单没加你的 IP | 重查公网 IP,加白名单 |
| `Access denied for user 'meter'` | 账号密码错 / 未授权到库 | 核对第四步账号密码与授权 |
| `Public Key Retrieval is not allowed` | 缺参数 | 连接串已带 `allowPublicKeyRetrieval=true`,确认没删 |
| 连接超时,IP 明明加了 | 家宽 IP 变了 | 重查 IP 重加白名单 |
| `Unknown database 'meter_reading'` | 库没建或库名不符 | 第四步在 RDS 建库 |

---

## ⚠️ 这不是生产做法(重要)

本方案为了让本地后端连上云 DB,**开了 RDS 公网 + 白名单**。这只适合验证/开发。真正上生产时:

- 数据库应走**内网**(后端也部署到阿里云 ECS,与 RDS 同 VPC,RDS **关闭公网**)。
- 见 `deploy/README.md` 和 `docs/上线前安全检查清单.md`——那才是完整生产部署(方案 B)。
- 别忘了 `上线前安全检查清单.md` 里的 P0 项(JWT_SECRET、短信验证码回传)。

---

## 用完记得

- **不再用就在 RDS 控制台释放实例**(按量付费会一直扣费)。
- 想切回本地:把 `run-local.sh` 的连接串改回 `127.0.0.1:3307` 即可。
