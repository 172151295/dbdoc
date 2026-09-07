# DBDoc — 数据库表结构文档生成平台

基于 [smallbun/screw](https://github.com/smallbun/screw) 深度增强的数据库文档工具，提供 **Web 平台化** 使用方式：连接业务数据库，一键生成 Word / Excel / Markdown / HTML 表结构文档。

## 架构总览

```
┌────────────────────┐      /prod-api/*       ┌────────────────────┐
│  jimuqu-admin-ui   │ ─────────────────────► │     screw-web      │
│  Vue3 · Vite 前端  │  RSA/AES 传输加密      │  Spring Boot 3 后端 │
└────────────────────┘ ◄───────────────────── └─────────┬──────────┘
                                                        │ 内嵌调用
                                              ┌─────────▼──────────┐
                                              │    screw-master    │
                                              │  screw-core 文档   │
                                              │  生成引擎（增强版） │
                                              └─────────┬──────────┘
                                                        │ JDBC
                                              ┌─────────▼──────────┐
                                              │  业务数据库          │
                                              │  MySQL/Oracle/达梦… │
                                              └────────────────────┘
```

## 模块说明

| 目录 | 说明 | 技术栈 |
|---|---|---|
| `screw-master/` | 文档生成核心库（增强版）：Excel 单/多 Sheet 双模式、Word 模板 9 列对齐、视图(VIEW)支持、达梦等多数据库方言 | Java · Maven 多模块 |
| `screw-web/` | 后端服务：认证（BCrypt + Token）、个人中心、请求体 RSA/AES 解密过滤器、内嵌前端打包发布 | Java 17 · Spring Boot 3.5 · H2 · JPA |
| `jimuqu-admin-ui/` | 前端控制台：基于 jimuqu-admin（vben 系）定制，离线图标、定时任务、主题偏好 | Vue3 · Vite · TS |

## 快速开始

### 1. 构建核心库（首次需要）

```bash
mvn -f screw-master/screw-master/pom.xml clean install -DskipTests
```

### 2. 启动后端

```bash
cd screw-web
mvn spring-boot:run
# 或打包运行： mvn package && java -jar target/screw-web-*.jar
```

- 服务地址：<http://localhost:8760>
- 数据库：内置 H2 文件库（`data/screwweb.mv.db`，自动创建，重启不丢数据）
- H2 控制台：<http://localhost:8760/h2-console>（用户 `sa`，密码空）

### 3. 前端开发模式（可选，生产可直接用内嵌前端）

```bash
cd jimuqu-admin-ui
pnpm install
pnpm dev
```

### 4. 生产构建（前端内嵌进后端 jar）

```bash
cd jimuqu-admin-ui && pnpm build     # 产物 dist/
cd ../screw-web && mvn package       # 打包时复制 dist 进 jar
java -jar target/screw-web-*.jar
```

### 默认账号

`admin / admin123`（首次登录后请立即在「个人中心 → 安全设置」修改密码）

## 目录结构

```
dbdoc/
├── screw-master/            # 文档生成核心库
│   ├── screw-master/        #   Maven 聚合父 POM（构建入口）
│   ├── screw-core/          #   生成引擎核心
│   ├── screw-extension/     #   扩展模块
│   ├── screw-maven-plugin/  #   Maven 插件
│   ├── codestyle/           #   代码风格配置
│   └── lib/                 #   本地依赖
├── screw-web/               # Spring Boot 后端
└── jimuqu-admin-ui/         # Vue3 前端
```

## 安全说明（部署必读）

- **传输加密**：前端生产构建默认开启 `VITE_GLOB_ENABLE_ENCRYPT=true`，对敏感接口（如修改密码）使用
  `RSA(encrypt-key 头) + AES/ECB` 加密请求体；后端 `EncryptRequestDecryptFilter` 自动解密。
- **演示密钥**：`jimuqu-admin-ui/.env.production` 与 `screw-web/application.yml` 中的 RSA/AES 密钥为
  **前后端配对的演示密钥**（前端本就内置私钥用于响应解密，不构成云凭据泄露），生产部署请务必：
  - 后端通过环境变量覆盖：`SCREW_AES_KEY`、`SCREW_RSA_PRIVATE_KEY`、`SCREW_RSA_PUBLIC_KEY`
  - 或重新生成密钥对并同步替换前后端两侧
- **数据目录**：`screw-web/data/`（H2 业务数据，含业务库连接信息）已在 `.gitignore` 中排除，请勿提交。
- **H2 控制台**：生产环境建议关闭（`spring.h2.console.enabled=false`）。

## 致谢 / 来源声明

- 后端文档生成核心基于 [smallbun/screw](https://github.com/smallbun/screw)（Apache License 2.0）增强，遵循其原许可证。
- 前端基于 [jimuqu-admin-ui](https://gitee.com/chengliang4810/jimuqu-admin-ui) 定制。
