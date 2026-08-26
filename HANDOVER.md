# 项目交接说明

## 1. 交接版本

- 项目：JHDS 智慧樱桃种植平台
- 工作目录：`D:\大赛源码\jhdss`
- 远端仓库：<https://github.com/MaomaoBuhuiJAVA/jhdss>
- 交接基线：已将本轮前端定制页面恢复为最初版本，保留配置说明和 Windows 启动脚本。
- 构建状态：已执行 `mvn -DskipTests package`，构建成功。

## 2. 已恢复的前端基线

以下页面已恢复为初始交互和展示逻辑，删除了本轮临时演示文案、固定传感器数据、键盘触发弹窗和图片占位类改动：

| 页面/资源 | 恢复内容 |
|---|---|
| `dashboard.html`、`dashboard.js` | 数据大屏、气象卡片、设备列表和 API 驱动数据 |
| `iot.html`、`iot.css` | 物联设备控制面板和设备网格 |
| `weather.html`、`weather.js` | 气象站默认显示和传感器 API 回填 |
| `nutrient.html`、`nutrient.js`、`nutrient.css` | 土壤/营养液默认展示和数据回填 |
| `insect.html`、`insect.css` | 虫情灯类型分布和原始列表 |
| `patrol.html` | AI 轨道巡检原始页面，移除花朵告警键盘弹窗 |
| `ai-learn.html`、`ai-learn.js`、`ai-learn.css` | 原始视频上传与分析界面 |
| `ai.js` | 原始 AI 流式调用路径 |
| `fragments/header.html` | 原始导航项“物联设备” |

## 3. 运行依赖与启动顺序

```text
MySQL (3306) ─┐
Redis (6379) ─┼─> Spring Boot 服务 (9117/jhds) ─> Thymeleaf 页面与 REST API
MQTT Broker ─┘             │
                              ├─> 虫情灯云平台（可选业务依赖）
                              ├─> 萤石云（摄像头功能依赖）
                              ├─> Ollama（本地视觉/AI 功能依赖）
                              └─> DashScope（云端 AI 功能依赖）
```

1. 先启动 MySQL 与 Redis。
2. 首次运行时执行 `src/main/resources/sql/init.sql` 初始化 `jhds` 数据库。
3. 从 `.env.local.bat.example` 创建 `.env.local.bat`，填入五项必需的外部服务变量。
4. 运行 `start-jhds.bat`。
5. 访问 `http://localhost:9117/jhds/`；服务停止可在启动终端按 `Ctrl+C`。

具体命令、变量和排障见 [STARTUP.md](STARTUP.md) 与 [CONFIGURATION.md](CONFIGURATION.md)。

## 4. 配置与密钥责任

| 配置类别 | 当前来源 | 交接要求 |
|---|---|---|
| MySQL | `application.yml` 默认指向本机 `jhds` 库 | 生产环境必须以环境变量/JVM 参数覆盖账号与地址 |
| Redis | `application.yml` 默认本机 `6379` | 确认服务可达，否则应用初始化会失败 |
| 虫情灯 | `INSECT_API_USERNAME`、`INSECT_API_PASSWORD` | 必填；用于虫情灯登录和同步 |
| 萤石云 | `YS7_APP_KEY`、`YS7_APP_SECRET` | 必填；摄像头能力需要真实凭据 |
| DashScope | `DASHSCOPE_API_KEY` | 必填配置占位符；未设置会阻止 Spring 配置解析 |
| MQTT | YAML 中 `device.mqtt.*` | 无现场设备时设置 `-Ddevice.mqtt.enabled=false` |
| Ollama | YAML 中 `ollama.*` | 仅本地 AI 调用时要求本机服务与模型可用 |

`.env.local.bat`、`logs/`、`captures/`、`target/`、IDE 配置和文档解析临时目录均已在 `.gitignore` 中排除。不得提交真实密钥、访问令牌、用户巡检图片或数据库备份。

## 5. 数据与文件状态

- `src/main/resources/sql/init.sql` 是初始化脚本，不可直接在有保留数据的数据库中重复执行；其中包含删表语句。
- `captures/` 是巡检图片运行目录，归属于运行数据，不随代码仓库同步。
- `target/` 是 Maven 生成目录，需通过 `mvn -DskipTests package` 重建。
- 页面静态资源位于 `src/main/resources/static/`，模板位于 `src/main/resources/templates/`。

## 6. 验证记录

已完成：

- Maven 打包：`mvn -DskipTests package` 成功。
- JavaScript 语法检查：`dashboard.js`、`weather.js`、`ai-learn.js`、`ai.js` 通过。
- 静态检索：未发现被回退的临时市场反馈、花朵告警、演示传感器数值等标记残留。

待交接环境验证：

- 使用实际 `.env.local.bat` 启动服务并确认 `9117` 监听。
- 在真实 MySQL/Redis 服务可用时完成首页、巡检、气象、物联设备和 AI 接口冒烟测试。
- 使用具备写入权限的 GitHub 凭据确认远端首次推送成功。

## 7. 常见故障

| 现象 | 优先检查 |
|---|---|
| `Could not resolve placeholder` | `.env.local.bat` 是否存在、变量名是否正确、是否由启动脚本加载 |
| MySQL 连接失败 | 3306 端口、`jhds` 数据库、用户名/密码、初始化脚本执行状态 |
| Redis 拒绝连接 | Redis 服务与 6379 监听状态 |
| MQTT 报连接错误 | 网络和 Broker；本地演示可关闭 `device.mqtt.enabled` |
| 摄像头/虫情/AI 请求失败 | 对应第三方凭据、网络连通性及服务端状态 |
| 页面与预期不一致 | 清理浏览器缓存，确认启动的是本次打包或 `spring-boot:run` 的最新资源 |

## 8. 建议的后续工作

1. 将 `application.yml` 内的本地默认密码迁移至环境变量或密钥管理服务。
2. 将数据库建表与升级脚本拆分为可重复执行的迁移脚本，避免初始化脚本误清空数据。
3. 添加 Spring Boot 集成测试与核心接口的自动化冒烟测试。
4. 将第三方服务开关做成明确的本地开发 Profile，降低无设备开发环境的启动成本。
