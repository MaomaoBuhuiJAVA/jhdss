package com.jhds.common;

import com.jhds.entity.WeatherSensorData;
import java.math.BigDecimal;
import java.util.function.BiConsumer;

public enum WeatherStationProtocol {

    TEMPERATURE("温度", "℃",
            (data, val) -> data.setTemperature(val),
            resp -> BigDecimal.valueOf((ModbusUtil.parseRegister1(resp) - 400) / 10.0)),

    HUMIDITY("湿度", "%",
            (data, val) -> data.setHumidity(val),
            resp -> BigDecimal.valueOf(ModbusUtil.parseRegister1(resp))),

    WIND_SPEED("风速", "m/s",
            (data, val) -> data.setWindSpeed(val),
            resp -> BigDecimal.valueOf(ModbusUtil.parseRegister1(resp) / 8.0 * 1.12)),

    WIND_DIRECTION("风向", "°",
            (data, val) -> data.setWindDirection(val),
            resp -> BigDecimal.valueOf(ModbusUtil.parseRegister1(resp))),

    TOTAL_RAINFALL("总累计雨量", "mm",
            (data, val) -> data.setRainfall(val),
            resp -> BigDecimal.valueOf(ModbusUtil.parseRegister1(resp) * 0.3)),

    HOURLY_RAINFALL("每小时雨量", "mm",
            (data, val) -> data.setHourlyRainfall(val),
            resp -> BigDecimal.valueOf(ModbusUtil.parseRegister1(resp) * 0.3)),

    DAILY_RAINFALL("每天雨量", "mm",
            (data, val) -> data.setDailyRainfall(val),
            resp -> BigDecimal.valueOf(ModbusUtil.parseRegister1(resp) * 0.3)),

    LIGHT_INTENSITY("光照强度", "Lux",
            (data, val) -> data.setLightIntensity(val),
            resp -> BigDecimal.valueOf(ModbusUtil.parseRegister2(resp) / 10.0)),

    UV_INTENSITY("紫外线强度", "uW/cm²",
            (data, val) -> data.setUvIntensity(val),
            resp -> BigDecimal.valueOf(ModbusUtil.parseRegister1(resp))),

    UV_INDEX("紫外线指数", "UVI",
            (data, val) -> data.setUvIndex(val),
            resp -> BigDecimal.valueOf(ModbusUtil.parseRegister1(resp))),

    BATTERY("设备电量", "",
            (data, val) -> data.setBatteryStatus(val != null ? val.intValue() : null),
            resp -> BigDecimal.valueOf(ModbusUtil.parseRegister1(resp)));

    private final String displayName;
    private final String unit;
    private final BiConsumer<WeatherSensorData, BigDecimal> setter;
    private final java.util.function.Function<String, BigDecimal> parser;

    WeatherStationProtocol(String displayName, String unit,
                           BiConsumer<WeatherSensorData, BigDecimal> setter,
                           java.util.function.Function<String, BigDecimal> parser) {
        this.displayName = displayName;
        this.unit = unit;
        this.setter = setter;
        this.parser = parser;
    }

    public String getDisplayName() { return displayName; }
    public String getUnit() { return unit; }

    public void setValue(WeatherSensorData data, String hexResponse) {
        try {
            BigDecimal value = parser.apply(hexResponse);
            setter.accept(data, value);
        } catch (Exception e) {
            // parsing failed, skip this field
        }
    }
}
