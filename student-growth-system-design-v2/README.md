# 学生个人教育成长档案系统设计基线 V2

本版本修复了上一版 SQL 与 OpenAPI 仅为占位的问题。

## 关键文件
- database/schema-full.sql：完整可执行 DDL
- database/flyway/：Flyway 迁移
- database/init-basic.sql：学段、年级、学科
- database/init-dictionary.sql：动态字典
- database/init-algorithm-config.sql：掌握度和复习配置
- api/openapi.yaml：覆盖主要业务域的 OpenAPI 3.1 草案
- codex/00-V2阶段零复查指令.md：先执行此文件
