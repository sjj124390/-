# Docker 镜像构建、部署与交付

第10组在线购物系统 1.0.0。Docker 镜像为本次必须提交的成果。

**已实际完成构建、17项集成测试、镜像包导出及 GHCR 发布。** 本次固定版本、镜像 digest、真实日志与镜像包下载地址见 [Docker 验证记录](docker-verification.md)。后续修改则以各次新工作流结果为准。

## 1. 交付结构

| 镜像 | 内容 | 作用 |
|---|---|---|
| backend | Maven 从源码构建的 Spring Boot JAR + Java 8 JRE | 业务接口 |
| frontend | Nginx + 前端页面 + 同源 /api 代理配置 | 网页和 API 入口 |
| database | MySQL 8.0 + database/schema.sql | 首次启动自动建表和创建存储过程 |

Dockerfile、Compose 文件和自动构建流程已提供，首轮运行成功。镜像拉取时仍需核对包权限；只有源文件或地址文字并不足以证明任意访问者能拉取。

镜像仓库为 `ghcr.io/sjj124390/online-shopping/{backend,frontend,database}`；已发布 `1.0.0` 及 `sha-ccce62f5ae201d00d2236b16a120fbb778964a2f` 两种标签，验收优先使用 SHA 标签固定版本。

## 2. 本机从源码构建（不需要 Docker Hub 账号）

安装并启动 Docker Desktop（Windows 使用 Linux 容器）或 Docker Engine，需 Docker Compose v2，建议提供至少 4GB 可用内存。首次构建需访问基础镜像仓库和 Maven 仓库。

在包含 `compose.yaml` 的项目根目录执行：

```sh
docker version
docker compose version
docker compose build
docker compose up -d --no-build --wait --wait-timeout 240
docker compose ps
```

打开 `http://127.0.0.1:5500/index.html`，卖家登录 `http://127.0.0.1:5500/login.html`，首次演示账号 `admin/admin123`。后端首次启动可能比静态页面慢数十秒；页面显示网络错误时先看下方日志。

数据库使用独立命名卷保留数据，端口不映射到宿主机；后端只供容器网络访问。浏览器通过 Nginx 的 `/api` 转发到后端，无需改 IP、安装本机 MySQL、JDK 或 Python。

可将 `deploy/docker/.env.example` 复制到根目录 `.env` 后调整端口、课程测试库密码与 HMAC 密钥。已有数据库必须保持相同密码及 HMAC 密钥，否则服务连接或旧口令查询会失败。

## 3. 从已发布镜像部署

仅在工作流发布成功后使用：

```sh
docker compose pull
docker compose up -d --no-build --wait --wait-timeout 240
```

Compose 默认使用 `1.0.0`。固定到某次构建时，在 `.env` 中设置 `IMAGE_TAG=sha-完整提交号`。

如果 GHCR 提示 denied，先确认发布步骤成功。GitHub 新建容器包可能默认私有；仓库公开不等于镜像包自动公开。仓库所有者需在对应 Package settings 核对访问权限，或登录后拉取。要让教师免登录拉取，需确认三项包可公开访问；不能仅把规划地址写成“公开可用”。

## 4. 离线镜像包

GitHub Actions 构建和冒烟测试成功后，在本次运行页面的 Artifacts 下载 `OSS-docker-images-运行序号`，解压可得到：

- `OSS-docker-images.tar.gz`：三项实际 Docker 镜像，使用 SHA 标签。
- `SHA256SUMS`：镜像包校验值。
- `docker-smoke-test.txt`：真实测试输出。
- `compose-ps.txt`、`image-inspect.json`、`source-commit.txt`：容器状态、镜像元信息与源码版本。

加载与启动：

```sh
docker load -i OSS-docker-images.tar.gz
# 在 .env 中将 IMAGE_TAG 设置为 source-commit.txt 对应的 sha-完整提交号
docker compose up -d --no-build --pull never --wait --wait-timeout 240
```

Actions 文件保留 30 天，请下载留存镜像包；长期拉取可使用已成功发布的 GHCR 镜像。镜像包不包含本次测试的数据库卷与个人数据。

本地也能导出默认标签镜像：

```sh
docker save -o OSS-1.0.0-images.tar ghcr.io/sjj124390/online-shopping/backend:1.0.0 ghcr.io/sjj124390/online-shopping/frontend:1.0.0 ghcr.io/sjj124390/online-shopping/database:1.0.0
docker load -i OSS-1.0.0-images.tar
```

## 5. 容器验证

仅对全新、可丢弃的课程测试环境运行：

```sh
python deploy/docker/smoke_test.py --allow-write
```

脚本不清库，发现已有商品时停止；测试会新增演示商品和意向、变更交易状态。共 17 项检查，包括网页与同源配置、登录、发布、唯一在售约束、口令查询、选中冻结、冻结禁止意向、失败恢复和成功成交。不把这些检查等同于原需求全部通过。

GitHub Actions 自动在独立临时 Compose 项目中执行，导出镜像后删除 CI 临时卷；该清理只作用于工作流自己的容器。

## 6. 日志、停止与数据

```sh
docker compose logs --tail 100 backend database frontend
docker compose stop
docker compose start
docker compose down
```

以上命令不会删除数据库卷。不要对需要保留的数据执行 `docker compose down --volumes`。SQL 初始化只在空卷首次运行；更换源码不会自动迁移旧数据库。

## 7. 验收边界

容器封装不改变业务规则。自动冻结、自动补货、后台服务端鉴权的缺口仍见 `requirements-status.md`。Docker 构建成功、容器冒烟测试通过、完整功能验收通过是三个不同的结论，需分别以实际证据记录。

配置依据：Docker Compose 官方健康依赖说明 https://docs.docker.com/compose/how-tos/startup-order/ ；GitHub 官方镜像发布说明 https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images 。
