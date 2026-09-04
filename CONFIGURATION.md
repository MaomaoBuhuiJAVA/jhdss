# JHDS 智慧农业云平台配置说明

## 1. 运行环境

- JDK 8（`pom.xml` 声明的 Java 版本为 8；JDK 17+ 也包含 JAXB 兼容依赖）
- Maven 3.6+
- MySQL 5.7/8.x，默认连接 `127.0.0.1:3306/jhds`
- Redis 5+，默认连接 `127.0.0.1:6379`、数据库 0
- 可选外部服务：MQTT、虫情灯云平台、萤石云、Ollama、阿里云百炼 DashScope

## 2. 配置文件位置

主配置文件为 `src/main/resources/application.yml`，打包后位于 JAR 内的 `application.yml`。
Spring Boot 的环境变量和 JVM 参数会覆盖同名配置，例如：

```bat
set SPRING_DATASOURCE_PASSWORD=your-password
set JAVA_OPTS=-Dserver.port=9118
```

敏感信息建议放在项目根目录的 `.env.local.bat`（该文件不要提交到 Git），启动脚本会自动加载它。

## 3. 环境变量清单

| 变量 | 是否必填 | 默认值 | 作用 |
|---|---|---|---|
| `INSECT_API_USERNAME` | 是 | 无 | 虫情灯云平台登录账号 |
| `INSECT_API_PASSWORD` | 是 | 无 | 虫情灯云平台登录密码 |
| `INSECT_API_BASE_URL` | 否 | `http://app.wlwapp.cn` | 虫情灯 API 根地址 |
| `YS7_APP_KEY` | 是 | 无 | 萤石云应用 Key |
| `YS7_APP_SECRET` | 是 | 无 | 萤石云应用 Secret |
| `YS7_VERIFY_CODE` | 视频加密开启时必填 | 无 | 摄像头验证码，仅保存在本机环境变量 |
| `YS7_DEVICE_SERIAL` | 否 | `BG9980884` | 巡检页默认萤石摄像头序列号 |
| `YS7_CHANNEL_NO` | 否 | `1` | 摄像头通道号 |
| `DASHSCOPE_API_KEY` | 是 | 无 | DashScope Bearer Token；当前 YAML 无默认值，缺失会阻止 Spring 启动 |
| `DASHSCOPE_BASE_URL` | 否 | `https://dashscope.aliyuncs.com` | DashScope API 根地址 |

注意：`application.yml` 中的 `insect.api.username/password`、`ys7.app-key/app-secret`、`dashscope.api-key` 使用无默认值占位符，缺少任意一个都会导致 Spring 启动阶段因无法解析占位符而失败。

## 4. application.yml 关键配置

| 配置项 | 当前默认值 | 说明 |
|---|---|---|
| `server.port` | `9117` | HTTP 端口 |
| `server.servlet.context-path` | `/jhds` | 访问前缀 |
| `spring.datasource.url` | `jdbc:mysql://127.0.0.1:3306/jhds?...` | MySQL 地址及连接参数 |
| `spring.datasource.username` | `root` | MySQL 用户名 |
| `spring.datasource.password` | `a123456` | MySQL 密码；建议改为环境变量覆盖 |
| `spring.redis.host/port/database` | `127.0.0.1/6379/0` | Redis 连接 |
| `device.mqtt.enabled` | `true` | 是否连接 MQTT；设为 `false` 可在无物联网现场时启动 |
| `device.mqtt.broker-url` | `tcp://11046xnld7705.vicp.fun:1883` | MQTT Broker |
| `device.mqtt.username/password` | `mqttuser`/`a123456` | MQTT 认证 |
| `device.mqtt.topic.prefix` | `/iot/jhds/prod` | 命令和响应主题前缀 |
| `device.mqtt.clean-session` | `false` | 是否清除 MQTT 会话；有人云配置为“关”时保持 `false` |
| `device.mqtt.transparent-mode` | `true` | 有人云“纯透传”模式；设备命令必须填写空格分隔的十六进制串口帧 |
| `device.mqtt.ignore-echo` | `true` | 过滤查询等非写入帧的有人云回显；Modbus 写入帧（05/06/15/16）的同帧回显会作为确认 |
| `device.mqtt.command-qos/response-qos` | `1/1` | 命令和响应主题 QoS |
| `MQTT_CLIENT_ID` | `jhdss-web-control` | 后端 MQTT 客户端 ID；必须与 DTU 的 `jhdskouhong` 不同；与其他后端实例冲突时打开 `MQTT_APPEND_INSTANCE_ID` |
| `ys7.force-h264` | `true` | 获取播放地址前请求萤石云将主/子码流切换为 H.264 |
| `ys7.protocol` | `4` | 默认使用 FLV 低延迟播放；FLV 不可用时前端回退 HLS（2） |
| `SPRING_DATASOURCE_USERNAME` | `root` | MySQL 登录用户名 |
| `SPRING_DATASOURCE_PASSWORD` | `a123456` | MySQL 登录密码；必须与目标电脑实际账号密码一致 |
| `MOTOR_DIRECTION_OPEN_HEX` / `MOTOR_DIRECTION_CLOSE_HEX` | 空 | 巡检电机方向的正转/反转串口帧 |
| `MOTOR_STATE_OPEN_HEX` / `MOTOR_STATE_CLOSE_HEX` | 空 | 巡检电机启动/停止串口帧；若配置了启动帧，左右移动会在方向帧后自动发送 |

有人云 DTU 配置为“纯透传”时，网页只负责发布原始串口字节，不能根据设备编号自动推断电机协议。请将电机控制器说明书中的实际十六进制帧（例如 `01 05 00 00 FF 00 8C 3A`）填入上述环境变量，或直接写入 `equipment.open_code` / `equipment.close_code`。没有这些帧时，网页会显示 MQTT 已连接，但不会发送危险的猜测指令。
| `ollama.base-url` | `http://localhost:11434` | 本地 Ollama 地址 |
| `ollama.model` | `qwen2.5vl:7b` | 本地视觉模型 |
| `dashscope.model` | `kimi-k2.7-code` | 云端模型 |
| `patrol.capture-path` | `./captures` | 巡逻图片保存目录 |
| `logging.level.com.jhds` | `debug` | 应用日志级别，生产建议改为 `info` |

可通过 `JAVA_OPTS` 传入任意 Spring 覆盖项，例如 `-Dspring.redis.host=192.168.1.20`、`-Ddevice.mqtt.enabled=false`。

## 5. 数据库初始化

首次启动前执行 `src/main/resources/sql/init.sql`。脚本会创建 `jhds` 数据库及业务表；文件包含 `DROP TABLE IF EXISTS`，重复执行会清空已有业务表，请先备份数据。

示例：

```bat
mysql -uroot -p < src\main\resources\sql\init.sql
```

## 6. 本地启动

1. 启动 MySQL 和 Redis。
2. 初始化数据库。
3. 创建 `.env.local.bat`，写入上表中的账号和密钥，例如：

```bat
@echo off
set "INSECT_API_USERNAME=your-insect-user"
set "INSECT_API_PASSWORD=your-insect-password"
set "YS7_APP_KEY=your-ys7-key"
set "YS7_APP_SECRET=your-ys7-secret"
set "YS7_VERIFY_CODE=your-device-verification-code"
set "YS7_DEVICE_SERIAL=BG9980884"
set "YS7_CHANNEL_NO=1"
set "DASHSCOPE_API_KEY=your-dashscope-key"
```

4. 双击 `start-jhds.bat`，或在命令行执行它。
5. 页面地址：`http://localhost:9117/jhds/`；Swagger：`http://localhost:9117/jhds/swagger-ui.html`。

无 MQTT 设备时，可在 `.env.local.bat` 增加：

```bat
set "JAVA_OPTS=-Ddevice.mqtt.enabled=false"
```

## 7. 定时任务

应用启用 Spring Scheduling：传感器采集每 15 分钟、虫情灯同步每 5 分钟、自动灌溉和巡逻任务每分钟、告警清理每天 04:00 执行。相关外部服务不可用时，对应功能会报错或跳过，不影响静态页面访问。

## 8. 常见问题

- 启动提示 `Could not resolve placeholder`：检查 `.env.local.bat` 是否存在并包含必填变量。
- 数据库连接失败：确认 MySQL 已启动、数据库已初始化，并检查 `spring.datasource.*`。
- Redis 连接失败：确认 Redis 在 `127.0.0.1:6379` 监听，或用 `JAVA_OPTS` 覆盖 `spring.redis.host/port`。
- AI 本地调用失败：确认 Ollama 已运行并执行 `ollama pull qwen2.5vl:7b`；也可改用 DashScope。
- MQTT 连接失败：检查网络、Broker 地址和账号；仅做页面开发时可关闭 `device.mqtt.enabled`。
