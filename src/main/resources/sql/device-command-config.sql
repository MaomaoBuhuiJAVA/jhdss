-- 金华控制板 Modbus RTU 指令
-- 适用于已有数据库；仅填充空指令，不覆盖已经配置的现场指令。
-- 执行前请确认有人云 DTU 串口为 9600、8N1、纯透传模式。

UPDATE equipment
SET open_code = COALESCE(NULLIF(TRIM(open_code), ''), '01 06 00 02 00 01 E9 CA'),
    close_code = COALESCE(NULLIF(TRIM(close_code), ''), '01 06 00 02 00 00 28 0A')
WHERE alias = 'PUMP_CO2'
;

UPDATE equipment
SET open_code = COALESCE(NULLIF(TRIM(open_code), ''), '01 06 00 01 00 01 19 CA'),
    close_code = COALESCE(NULLIF(TRIM(close_code), ''), '01 06 00 01 00 00 D8 0A')
WHERE alias = 'PUMP_CIRCULATION'
;

-- 表格未提供 PUMP_IRRIGATE（灌溉泵）的独立指令，因此不猜测、不填充。

-- 网站巡检页约定 left=open_code、right=close_code。
UPDATE equipment
SET open_code = COALESCE(NULLIF(TRIM(open_code), ''), '03 05 00 01 00 FF DD A8'),
    close_code = COALESCE(NULLIF(TRIM(close_code), ''), '03 05 00 01 FF 00 DC 18')
WHERE alias = 'MOTOR_DIRECTION'
;

UPDATE equipment
SET close_code = '03 05 00 01 00 00 9D E8'
WHERE alias = 'MOTOR_STATE'
  AND COALESCE(TRIM(close_code), '') = '';
