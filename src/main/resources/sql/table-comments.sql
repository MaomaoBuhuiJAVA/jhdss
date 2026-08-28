-- JHDS table comment migration
-- This script changes only MySQL table metadata. It does not delete or update rows.
USE jhds;

ALTER TABLE alarm_record COMMENT = '页面：报警中心；区域：报警列表、状态与处置说明、来源分布；数据：标题、描述、级别、来源、位置、处置状态、说明与时间。页面通过API读取和修改本表；气象阈值等后台服务也会写入本表。';
ALTER TABLE control_log COMMENT = '后台运维审计日志；用途：MQTT设备控制、气象和土壤Modbus采集的指令/响应记录；数据：设备、控制值、指令/响应、成功状态、手自动来源和时间。当前无前端页面直接展示本表。';
ALTER TABLE equipment COMMENT = '页面：物联设备、营养液配液；区域：大棚控制面板和手动模式泵控制卡片；数据：设备名称/别名、控制指令和开关状态。物联设备页仅显示别名以GH开头的设备，配液页显示PUMP设备。';
ALTER TABLE insect_record COMMENT = '页面：虫情灯；区域：本地记录图库、今日统计和AI识别巡检种类统计；数据：虫体图片、种类、数量、设备、AI引擎和采集时间。告警弹窗与外部照片标签页不读取本表。';
ALTER TABLE page_alert_content COMMENT = '页面：数据大屏、虫情灯、AI轨道巡检；区域：页面专用告警卡片和详情弹窗；数据：标题、说明、静态图片URL和展示开关。';
ALTER TABLE irrigation_record COMMENT = '页面：营养液配液；区域：灌溉统计卡与AI决策日志；数据：手动/自动/AI模式、泵别名、运行时长和执行时间。页面聚合显示本日次数、运行时长、估算用水量和最近记录。';
ALTER TABLE irrigation_schedule COMMENT = '页面：营养液配液；区域：自动模式的自动灌溉计划表单；数据：计划时间、运行时长、频率和启用状态。页面保存、回显和后台调度均读取本表。';
ALTER TABLE patrol_record COMMENT = '页面：AI轨道巡检；区域：视频下方巡检记录与AI识别结果；数据：轨道位置、抓拍图片、拍摄时间、AI状态和AI结果。';
ALTER TABLE patrol_task COMMENT = '页面：AI轨道巡检；区域：巡检任务列表与新建任务表单；数据：任务名称、执行时间、巡检范围和执行状态。';
ALTER TABLE plant_cultivation COMMENT = '页面：历年档案；区域：栽培管理；数据：月度灌溉、施肥、修剪、搭架、除草、换盆和备注。';
ALTER TABLE plant_growth_record COMMENT = '页面：历年档案；区域：生长观测；数据：观测日期、株高、冠幅、叶花果数量、照片和备注。';
ALTER TABLE plant_info COMMENT = '页面：历年档案；区域：植株档案侧栏和基础档案；数据：植物基本资料、种植条件、主图和备注。';
ALTER TABLE plant_pest_disease COMMENT = '页面：历年档案；区域：病虫害逆境；数据：发生名称/时间、症状、严重程度、处理措施、效果和图片。';
ALTER TABLE plant_phenology COMMENT = '页面：历年档案；区域：物候年历；数据：生育阶段、物候期、发生日期、观察描述和图片地址。';
ALTER TABLE plant_year_record COMMENT = '页面：历年档案；区域：年度选项卡和年终总结；数据：年度生长评级、年度总结、问题复盘和改进建议。';
ALTER TABLE soil_sensor_data COMMENT = '页面：营养液配液；区域：土壤传感器与48小时趋势图；数据：土壤温湿度、EC、pH、盐分、氮磷钾和采集时间。页面从本表读取全部指标与历史趋势。';
ALTER TABLE weather_sensor_data COMMENT = '页面：气象站、数据大屏；区域：气象指标卡、48小时趋势图和大屏气象缩略卡；数据：温湿度、风速风向、雨量、光照、紫外线、电量和采集时间。当前直接读取最新/历史记录，空表时页面保留初始值。';
ALTER TABLE weather_station_protocol COMMENT = '后台气象采集配置；用途：WeatherService按启用项读取Modbus指令、单位和排序后采集并写入weather_sensor_data；数据：传感器标识、显示名称、指令、单位、排序和启用状态。当前无页面直接编辑或展示本表。';

ALTER TABLE weather_threshold_config COMMENT = '页面：气象站；区域：报警阈值设置面板；数据：温湿度、风速、雨量、光照、紫外线和低电量报警阈值及开关。页面直接读取/保存，后台据此生成alarm_record。';
