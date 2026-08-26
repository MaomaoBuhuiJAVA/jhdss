package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("weather_sensor_data")
public class WeatherSensorData {
    @TableId(type = IdType.AUTO)
    private Long id;
    private BigDecimal temperature;
    private BigDecimal humidity;
    private BigDecimal windSpeed;
    private BigDecimal rainfall;
    private BigDecimal windDirection;
    private BigDecimal lightIntensity;
    private BigDecimal uvIntensity;
    private BigDecimal uvIndex;
    private Integer batteryStatus;
    private BigDecimal hourlyRainfall;
    private BigDecimal dailyRainfall;
    private Date recordTime;
}
