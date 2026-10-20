# 构建过程手册

项目：第10组在线购物系统（OSS）；应用版本：1.0.0；整理日期：2026-10-20。

本次必须交付 Docker 镜像。Docker 多阶段构建会在 Maven 容器内编译本页描述的后端源码，详见 [Docker 构建与交付手册](docker-guide.md)。自动工作流只有在容器集成测试成功后才会导出和发布镜像。

## 1. 源码与版本

- 仓库：[sjj124390/-](https://github.com/sjj124390/-)。
- 在仓库 Code → Download ZIP 下载当前源码，保存为 `OSS-1.0.0.zip`；解压后进入含 `backend`、`frontend`、`database` 的目录。
- [原始联调交付包](https://github.com/sjj124390/-/blob/main/--main.zip)另含已构建 JAR：`--main/backend/target/online-shopping-backend-1.0.0.jar`。这份上传包不是 GitHub 自动生成的源码归档，不应混淆。
- 本次将上传包中的 `FacadeController.java` 和 JDK 8 兼容修复同步到展开源码。上传包保留为历史交付物，后续源码修改应重新构建。

## 2. 工具与环境

| 工具 | 版本或要求 | 用途 |
|---|---|---|
| JDK | 8；原测试报告记录 1.8.0_491 | 编译、运行后端 |
| Maven | 3.9.9；已配置 JAVA_HOME 与 PATH | 解析依赖、打包 |
| Spring Boot | 2.7.18，由 pom.xml 管理 | 后端框架 |
| Python | 3.8+ | 前端静态服务、API 测试 |
| MySQL | 建议 8.0.16+；原报告为 8.0.46 | 运行与测试依赖，单独编译不需要启动 |

JDK 和 Maven 分别从其官方发行渠道安装，检查：

```sh
java -version
javac -version
mvn -version
python --version
```

首次构建需要网络下载 Maven 依赖。前端使用原生 HTML/CSS/JavaScript，没有 npm 安装和打包步骤。

## 3. 构建步骤

从项目根目录执行：

```sh
cd backend
mvn clean package
```

成功标志：控制台显示 `BUILD SUCCESS`，生成 `backend/target/online-shopping-backend-1.0.0.jar`。`pom.xml` 中的 Spring Boot Maven 插件将依赖打入可执行包。

构建成功不代表功能验收成功。当前 Python API 测试不接入 Maven 生命周期，必须按运行手册另行执行；没有 Java 测试时也不能把 Maven 打包成功写成全部用例通过。

## 4. 成果物

| 成果物 | 位置 |
|---|---|
| 构建配置 | [backend/pom.xml](https://github.com/sjj124390/-/blob/main/backend/pom.xml) |
| 可执行 JAR | 本地构建生成的 `backend/target/online-shopping-backend-1.0.0.jar` |
| 已有交付 JAR | [原始联调交付包](https://github.com/sjj124390/-/blob/main/--main.zip)内同名文件 |
| 前端资源 | [frontend](https://github.com/sjj124390/-/tree/main/frontend)，原样部署 |

## 5. 常见问题与验证范围

- `mvn` 找不到：检查 Maven 的 `bin` 目录和 JAVA_HOME。
- 依赖下载失败：检查 Maven 仓库访问条件，解决后重新构建。
- JDK 8 提示找不到 `isBlank()`：请使用同步后的源码；旧展开版本含 Java 11 API，已恢复上传包中的兼容写法。
- JAR 运行报数据库连接错误：按[安装与部署手册](deployment-guide.md)配置数据库，这不是编译错误。

本地整理环境无 Maven/MySQL。新增 GitHub Actions Docker 工作流已实际在 Maven 容器内重新编译，并启动三项容器完成 17 项链路测试。对应运行结果与镜像交付见 Docker 手册；原组员历史测试结果见仓库 `test-report.md`，两者分别记录。
