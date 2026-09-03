# JHDS 本地启动说明

## 快速启动

在项目根目录创建 `.env.local.bat`，填入真实凭据（不要提交到 Git）：

```bat
@echo off
set "INSECT_API_USERNAME=虫情灯账号"
set "INSECT_API_PASSWORD=虫情灯密码"
set "YS7_APP_KEY=萤石云AppKey"
set "YS7_APP_SECRET=萤石云AppSecret"
set "YS7_VERIFY_CODE=摄像头验证码"
set "YS7_DEVICE_SERIAL=BG9980884"
set "YS7_CHANNEL_NO=1"
set "DASHSCOPE_API_KEY=阿里云DashScope密钥"
set "MQTT_BROKER_URL=tcp://11046xnld7705.vicp.fun:1883"
rem 后端 Client ID 必须与有人云 DTU 的 jhdskouhong 不同
set "MQTT_CLIENT_ID=jhdss-web-control"
set "MQTT_USERNAME=mqttuser"
set "MQTT_PASSWORD=有人云MQTT密码"
set "MQTT_CLEAN_SESSION=false"
set "MQTT_TRANSPARENT_MODE=true"
set "MQTT_IGNORE_ECHO=true"
rem 按电机控制器说明书填写四个实际串口十六进制帧
set "MOTOR_DIRECTION_OPEN_HEX="
set "MOTOR_DIRECTION_CLOSE_HEX="
set "MOTOR_STATE_OPEN_HEX="
set "MOTOR_STATE_CLOSE_HEX="
```

然后双击 `start-jhds.bat`，或在命令行运行：

```bat
start-jhds.bat
```

脚本会自动切换到项目目录、加载本地变量、检查 Java、Maven、MySQL 和 Redis，并通过 `mvn spring-boot:run` 启动当前源码。若需要生成可部署 JAR，请单独执行 `mvn -DskipTests package`。

## 启动前准备

1. 安装并加入 PATH：JDK 8+、Maven 3.6+。
2. 启动 MySQL，创建数据库并初始化表：

   ```bat
   mysql -uroot -p < src\main\resources\sql\init.sql
   ```

   默认连接为 `127.0.0.1:3306`、数据库 `jhds`、用户 `root`、密码 `a123456`。密码不同可在 `.env.local.bat` 中设置 `SPRING_DATASOURCE_PASSWORD`。
3. 启动 Redis，默认监听 `127.0.0.1:6379`。
4. 如果本地没有物联网 MQTT Broker，可在 `.env.local.bat` 中关闭 MQTT：

   ```bat
   set "JAVA_OPTS=-Ddevice.mqtt.enabled=false"
   ```

## 访问地址

- Web 首页：`http://localhost:9117/jhds/`
- Swagger：`http://localhost:9117/jhds/swagger-ui.html`
- 默认端口：`9117`

可用 JVM 参数临时覆盖配置，例如：

```bat
set "JAVA_OPTS=-Dserver.port=9118 -Dspring.redis.host=192.168.1.20"
start-jhds.bat
```

## 启动失败排查

- `INSECT_API_USERNAME is not set`：检查根目录 `.env.local.bat` 文件名和变量名。
- `Could not resolve placeholder`：五个必填变量必须全部存在。
- `Communications link failure`：MySQL 未启动、端口或密码不匹配。
- Redis connection refused：Redis 未启动或地址不正确。
- MQTT 连接失败：现场设备不可用时设置 `-Ddevice.mqtt.enabled=false`。
- MQTT 已连接但电机不动作：确认 `equipment` 表或四个 `MOTOR_*_HEX` 环境变量已填写电机控制器的真实串口帧；纯透传模式无法自动猜测协议。
- Maven 不可用：安装 Maven 并将其加入 PATH；需要 JAR 部署时先执行 `mvn -DskipTests package`。

## 停止服务

启动窗口中按 `Ctrl+C`。服务停止后，`captures` 目录中的巡逻图片和数据库数据会保留。
