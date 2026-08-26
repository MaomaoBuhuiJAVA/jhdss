# JHDS 智慧樱桃种植平台

基于 Spring Boot、Thymeleaf、MySQL、Redis 和 MQTT 的智慧农业管理平台。系统覆盖数据大屏、AI 轨道巡检、物联设备、气象站、营养液配液、虫情灯、报警中心、AI 农业助手和 AI 学习模块。

当前提交为前端页面恢复后的基线版本。运行期图片、日志、构建产物与本机密钥均不会提交到 Git。

## 技术栈

| 层级 | 技术 |
|---|---|
| 后端 | Java 8+、Spring Boot 2.1.13、MyBatis-Plus、Druid |
| 前端 | Thymeleaf、原生 JavaScript、CSS、Remix Icon |
| 数据与消息 | MySQL 5.7/8.x、Redis 5+、MQTT (Paho) |
| 可选 AI/设备服务 | Ollama、DashScope、萤石云、虫情灯云平台 |

## 项目结构

```text
src/main/java/com/jhds/
  controller/       页面与 REST API 控制器
  service/          业务逻辑、AI、萤石云、虫情灯和 MQTT 服务
  task/             定时采集、同步、灌溉、巡检与清理任务
  entity/ mapper/   数据实体与 MyBatis 映射
  config/           Spring、Redis、MQTT、Swagger 等配置
src/main/resources/
  application.yml   应用主配置
  sql/init.sql      MySQL 初始化脚本
  templates/        Thymeleaf 页面
  static/           页面 CSS、JavaScript 和图片资源
CONFIGURATION.md    完整配置与环境变量说明
STARTUP.md          Windows 本地启动和排障说明
HANDOVER.md         交接清单、服务依赖和验证记录
start-jhds.bat      Windows 一键启动脚本
```

## 快速开始

1. 安装 JDK 8+、Maven 3.6+、MySQL 和 Redis，并确保 `java`、`mvn` 可在终端执行。
2. 执行数据库初始化。注意 `src/main/resources/sql/init.sql` 含有 `DROP TABLE IF EXISTS`，重复执行会清空业务表。

   ```bat
   mysql -uroot -p < src\main\resources\sql\init.sql
   ```

3. 将 `.env.local.bat.example` 复制为 `.env.local.bat`，填写外部服务凭据。
4. 确认 MySQL 在 `127.0.0.1:3306`、Redis 在 `127.0.0.1:6379` 可用。
5. 双击 `start-jhds.bat`，或在项目根目录执行：

   ```bat
   start-jhds.bat
   ```

6. 浏览器访问 [http://localhost:9117/jhds/](http://localhost:9117/jhds/)。Swagger 地址为 [http://localhost:9117/jhds/swagger-ui.html](http://localhost:9117/jhds/swagger-ui.html)。

更多环境变量、无现场 MQTT 的本地运行方式和常见故障见 [CONFIGURATION.md](CONFIGURATION.md) 与 [STARTUP.md](STARTUP.md)。

## 页面入口

| 页面 | 地址 |
|---|---|
| 数据大屏 | `/jhds/dashboard` |
| AI 轨道巡检 | `/jhds/patrol` |
| 物联设备 | `/jhds/iot` |
| 气象站 | `/jhds/weather` |
| 营养液配液 | `/jhds/nutrient` |
| 虫情灯 | `/jhds/insect` |
| 报警中心 | `/jhds/alarm` |
| AI 农业助手 | `/jhds/ai` |
| AI 学习 | `/jhds/ai-learn` |

## 构建验证

```bat
mvn -DskipTests package
```

构建产物为 `target/jhds-agri-cloud-1.0.0.jar`，该文件由构建生成，未纳入版本控制。`start-jhds.bat` 默认以 `mvn spring-boot:run` 运行源码；需要 JAR 部署时再执行打包命令。

## 安全说明

- `.env.local.bat` 已被 Git 忽略；只提交 `.env.local.bat.example` 模板。
- `application.yml` 中含有本地开发默认的数据库和 MQTT 连接信息。部署前应使用环境变量或 JVM 参数覆盖账号、密码和服务地址。
- 外部服务凭据不能写入 README、提交记录、Issue、截图或日志。

## 版本控制

远端仓库：<https://github.com/MaomaoBuhuiJAVA/jhdss>

建议开发分支使用 `codex/` 前缀，合并前至少执行 Maven 打包和关键页面/API 冒烟检查。
