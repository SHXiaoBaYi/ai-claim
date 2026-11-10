# 环境说明

测试和生产都只使用这一台 ECS。

| 项 | 值 |
|----|----|
| 公网 | `47.99.95.38` |
| 入口 | http://47.99.95.38/shxby/ |
| 数据库 | 这台机器上的 MySQL 8，`127.0.0.1:3306`，库名 `base_admin` |
| 测试 profile | `test` |
| 生产 profile | `prod`（关闭 Swagger / SQL 日志） |
| 本机开发 | `local`，后端 `8099`，前端 `8088`；先开 `deploy/tunnel-test-rds.ps1`，再连 `127.0.0.1:13307` |

发布：

```bash
./deploy/publish-ecs.sh test
./deploy/publish-ecs.sh prod
```

两台命令都发到 `47.99.95.38`。生产发布仍会按 Git 里的 SchemaMigrator 补表结构。
