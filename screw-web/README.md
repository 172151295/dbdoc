# screw-web

数据库表结构文档生成平台 —— 将 [screw-core](https://github.com/smallbun/screw)（简洁好用的数据库表结构文档生成工具）服务化，提供 RESTful API 与 Web 界面。

## 功能特性

- 支持 11 种数据库：MySQL、MariaDB、Oracle、SQL Server、PostgreSQL、瀚高 HighGo、达梦 DM、DB2、H2、HSQLDB、SQLite
- 连接管理：数据库连接信息的增删改查、连接测试、密码 AES 加密存储（H2 文件库持久化）
- 分组管理：数据库连接分组
- 元数据查询：获取库表结构元数据
- 文档生成：HTML、Word、Markdown、Excel 四种格式导出

## 技术栈

- Spring Boot 3.5.3 / Java 17
- Spring Data JPA + H2（文件型，数据存于 `data/`）
- screw-core 1.0.6-SNAPSHOT（文档生成核心）

## 快速开始

### 前置条件

1. JDK 17+
2. Maven 3.8+（需联网拉取依赖，或本地仓库已具备完整依赖）
3. 本地 Maven 仓库已安装 `screw-core`：

```bash
cd ../screw-master/screw-master/screw-core
mvn install -Dmaven.javadoc.skip=true -Dgpg.skip=true
```

> 注意：本机 Maven 的 `settings.xml` 中 `pentaho` profile 会强制走证书过期的 pentaho 私服导致依赖下载失败，构建时请使用 `-s settings-clean.xml`（仅阿里云镜像）。

### 构建

```bash
mvn -s ../settings-clean.xml clean package -DskipTests
```

### 启动

```bash
java -jar target/screw-web.jar
```

启动后访问：

- Web 服务：http://localhost:8760
- H2 控制台：http://localhost:8760/h2-console （JDBC URL `jdbc:h2:file:./data/screwweb`，用户 `sa`，空密码）

## API 一览

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/connection/db-types` | 支持的数据库类型 |
| GET/POST | `/api/connection` | 连接列表 / 新建连接 |
| PUT/DELETE | `/api/connection/{id}` | 更新 / 删除连接 |
| POST | `/api/connection/test` | 测试连接 |
| GET/POST | `/api/group` | 分组列表 / 新建分组 |
| PUT/DELETE | `/api/group/{id}` | 更新 / 删除分组 |
| GET | `/api/metadata/{connectionId}` | 库表元数据 |
| GET | `/api/document/formats` | 支持的文档格式 |
| POST | `/api/document/generate` | 生成并下载文档 |

### 生成文档示例

```bash
curl -X POST http://localhost:8760/api/document/generate \
  -H "Content-Type: application/json" \
  -d '{"connectionId":1,"format":"HTML","title":"测试库文档","version":"1.0.0","description":"说明","tableNames":[]}'
```

## 配置

见 `src/main/resources/application.yml`：

- 服务端口：`server.port`（默认 8760）
- AES 密钥：`screw.security.aes-key`（可用环境变量 `SCREW_AES_KEY` 覆盖）
- H2 数据文件位置：`spring.datasource.url`

## 目录结构

```
src/main/java/cn/smallbun/screw/web/
├── common/      # 统一响应、业务异常
├── controller/  # REST 控制器
├── dto/         # 请求/响应对象
├── entity/      # JPA 实体（连接、分组）
├── repository/  # Spring Data JPA 仓库
├── service/     # 业务逻辑（连接、文档、元数据、数据库类型支持）
└── util/        # AES 加解密工具
```
