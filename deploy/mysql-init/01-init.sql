-- ============================================================
-- MySQL 首次启动自动执行(仅当数据目录为空时)
-- 作用:设置库编码 + 授权。表结构交给后端 Flyway 管理,这里不建表。
-- ============================================================

ALTER DATABASE meter_reading
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- 业务账号已由 compose 的 MYSQL_USER 创建,这里补授权(限本地网络)
GRANT ALL PRIVILEGES ON meter_reading.* TO 'meter'@'%';
FLUSH PRIVILEGES;
