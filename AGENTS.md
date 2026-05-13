# AGENTS.md

## 1. 项目概述
Inker（研墨）是一个股票分析与追踪工具。

- 后端：Java 17、Spring Boot 3.2、Spring Data JPA。
- 前端：Vue 3、Vite、TypeScript、Vue Router。
- 当前主要能力：市场总览、股票列表、个股详情、自选追踪、行情同步

## 2. 快速命令
```bash
# 启动后端，默认 http://localhost:8081
cd server && mvn spring-boot:run

# 后端测试
cd server && mvn test

# 启动前端，默认由 Vite 分配本地端口
cd web && npm run dev

# 前端类型检查与构建
cd web && npm run build
```

## 3. 后端架构
后端位于 `server/`，包名根路径为 `com.inker.backend`。

- `controller`：REST API，统一使用 `/api/v1` 前缀。
- `service`：业务逻辑，如股票查询、行情同步、自选分组。
- `repository`：Spring Data JPA 数据访问。
- `entity`：数据库实体。
- `dto`：接口入参与返回模型。
- `config` / `scheduler` / `provider`：分别承载配置、定时任务、外部数据源适配。

## 4. 前端架构
前端位于 `web/`，入口为 `src/main.ts`。

- `src/app`：应用壳与路由。
- `src/features`：按业务模块组织页面、组合式函数等。
- `src/shared`：通用 API、组件、样式、状态、类型与工具函数。
- 当前路由：市场总览、选股列表、个股详情、自选追踪。
- 后端请求统一从 `src/shared/api/http.ts` 的 Axios 实例发起。

## 5. 关键约定
- 优先沿用现有分层、命名和目录结构。
- 新增后端接口时保持 Controller → Service → Repository 的调用方向。
- 新增前端页面或能力时优先放入对应 `features` 模块，可复用内容放入 `shared`。
- UI 样式优先复用 `web/src/shared/styles` 与 `docs/design.md` 中的设计 token。
- 不提交 `target/`、`dist/`、`node_modules/`、本地日志、临时文件和本地数据库文件。

## 6. 本地开发及验证流程
1. 启动后端：`cd server && mvn spring-boot:run`。
2. 启动前端：`cd web && npm run dev`。
3. 前端通过 `http://localhost:8081/api/v1` 访问后端。
4. 涉及接口变更时，同时检查前端 API 封装、DTO、页面调用链。

## 7. 质量检查
- 后端改动后优先运行：`cd server && mvn test`。
- 前端改动后优先运行：`cd web && npm run build`。
- 文档改动可人工检查标题层级、路径、命令是否与实际目录一致。
- 避免在无关文件中做格式化或重构。

## 8. 参考项目约定
暂无参考项目。

## 9. 文档导航
| 文档 | 用途 |
| --- | --- |
| `docs/design.md` | 前端设计规范与设计 token |
| `server/pom.xml` | 后端依赖、Java/Spring Boot 版本与 Maven 配置 |
| `web/package.json` | 前端依赖与 npm scripts |
| `server/src/main/resources/application.yml` | 后端端口、数据源与外部服务配置 |
