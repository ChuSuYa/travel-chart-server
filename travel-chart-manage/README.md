# TravelChart 管理服务

管理端接口统一为 `/api/manage/**`，通过网关路由到本服务。首次启动前必须配置
`ADMIN_BOOTSTRAP_USERNAME` 与 `ADMIN_BOOTSTRAP_PASSWORD`；服务仅在该用户名不存在时创建一次管理员。

最小接口：管理员登录、仪表盘指标、用户查询与禁用/启用、POI 查询与上架/下线。

已有数据库需先执行 `../db/migrations/V2__manage_module.sql`。新数据库由 `db/init.sql` 自动创建表结构。

| 方法 | 端点 | 用途 |
| --- | --- | --- |
| POST | `/api/manage/auth/login` | 管理员登录 |
| GET | `/api/manage/dashboard` | 仪表盘汇总 |
| GET / PATCH | `/api/manage/users`、`/api/manage/users/{id}/status` | 用户列表与状态管理 |
| GET / PATCH | `/api/manage/content/pois`、`/api/manage/content/pois/{id}/status` | POI 列表与上下架 |
