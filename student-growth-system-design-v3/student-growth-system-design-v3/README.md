# 学生个人教育成长档案系统 Backend Local V1

当前后端公开契约基线为 OpenAPI V3.15.0，Flyway 数据库基线为 V23。116 个公开 operationId 均已实现。

Local V1 是仅绑定 `127.0.0.1` 的个人单用户应用，不提供登录或应用层认证。Docker Compose 只提供带持久卷的本地 MySQL；数据库结构和初始化数据只由 Flyway V1-V23 管理。

1. 在 `docker/.env` 配置数据库密码，可从 `docker/.env.example` 开始。
2. 启动数据库：`docker compose --env-file docker/.env -f docker/docker-compose.yml up -d`
3. 将 `SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` 和（非默认端口时）
   `SPRING_DATASOURCE_URL` 设置为与 `docker/.env` 相同的连接信息。
4. 启动应用：`mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local`

持久文件根目录通过 `STDNTEDU_DATA_DIR` 配置。也可先执行 `mvnw.cmd clean package`，再以 `local` profile 运行生成的 JAR。
