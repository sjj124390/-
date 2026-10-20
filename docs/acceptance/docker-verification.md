# Docker 构建与运行验证记录

日期：2026-10-20（北京时间）。

- 源码提交：`ccce62f5ae201d00d2236b16a120fbb778964a2f`
- [GitHub Actions 成功运行记录](https://github.com/sjj124390/-/actions/runs/39376920770)
- 结论：三项镜像从源码构建成功，Compose 启动成功，17 项容器集成检查通过，镜像归档上传成功，GHCR 发布成功。
- 本报告依据已完成工作流的真实步骤和日志，不是预期输出。
- 平台：GitHub Actions Ubuntu/Linux amd64。

## 实际发布镜像

| 组件 | 镜像名（版本标签） | 发布日志中的 digest |
|---|---|---|
| 后端 | `ghcr.io/sjj124390/online-shopping/backend:1.0.0` | `sha256:23adaf4ead1cf9ce844af832f7f907130df16e883346f754aa8ea8644e1c7c3e` |
| 前端 | `ghcr.io/sjj124390/online-shopping/frontend:1.0.0` | `sha256:3e92c5fa1f1d7931c7086efc19a33e7ee9b28d86f29850a4daed4fd3e7847863` |
| 数据库 | `ghcr.io/sjj124390/online-shopping/database:1.0.0` | `sha256:f7b71e7d2361b5665778e644e11b3f67b05b8bee507c4f9fd6069a72d659879d` |

三项还各自发布了 `sha-ccce62f5ae201d00d2236b16a120fbb778964a2f` 标签，可用于固定验收版本。以上证明镜像推送成功；尚未独立验证匿名拉取权限，不能把“已发布”写成“任何人无需登录即可拉取”。

## 可离线加载的实际镜像包

[下载 OSS-docker-images-1](https://github.com/sjj124390/-/actions/runs/39376920770/artifacts/12243817293)，GitHub 下载可能要求登录。

- Artifact ZIP 约 349.5 MiB（366,446,026 bytes），包含镜像归档及证据。
- 有效期至 2026-11-19；请及时下载并长期保存，不把 Actions 临时附件当作永久地址。
- ZIP 解压后有 `OSS-docker-images.tar.gz`、`SHA256SUMS`、`docker-smoke-test.txt`、`compose-ps.txt`、`image-inspect.json`、`source-commit.txt`。
- Artifact ZIP 的 GitHub SHA256：`909be0266212ccf9c165bf0a7c7f5b4556d089043bfc8d2efcdd55a2613f53de`；内部 tar.gz 的校验值以 `SHA256SUMS` 为准，二者不能混用。

加载时执行 `docker load -i OSS-docker-images.tar.gz`，在根目录 `.env` 设置：

```dotenv
IMAGE_TAG=sha-ccce62f5ae201d00d2236b16a120fbb778964a2f
```

然后 `docker compose up -d --no-build --pull never --wait --wait-timeout 240`，访问 `http://127.0.0.1:5500`。

## 实际测试输出摘要

```text
PASS 01 Nginx -> backend -> database login
PASS 02 frontend HTML is served
PASS 03 frontend uses same-origin /api
PASS 04 incorrect password rejected
PASS 05 product published
PASS 06 buyer can view current product
PASS 07 second active product rejected
PASS 08 empty buyer name rejected
PASS 09 purchase returns 32-character query code
PASS 10 query code lookup works
PASS 11 wrong query code rejected
PASS 12 seller can list purchase intent
PASS 13 selected intent freezes product
PASS 14 frozen product rejects new intents
PASS 15 failed transaction restores product
PASS 16 completed transaction marks product sold
PASS 17 completed intent is visible in seller history
Docker integration smoke test passed: 17 checks
```

这17项证明当前实现的容器链路可运行，不代表自动冻结、自动补货、服务端鉴权等全部原需求已经完成；仍按需求对照跟踪。
