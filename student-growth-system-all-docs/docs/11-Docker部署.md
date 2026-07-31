# Docker部署
服务：mysql、backend、frontend；redis和minio可选。

启动：
```bash
cp docker/.env.example docker/.env
docker compose --env-file docker/.env -f docker/docker-compose.yml up -d
```

MySQL、上传文件和备份必须挂载持久卷。更新前必须先创建完整备份。
