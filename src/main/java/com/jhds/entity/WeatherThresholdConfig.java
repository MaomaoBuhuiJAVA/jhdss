package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("weather_threshold_config")
public class WeatherThresholdConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private BigDecimal tempMin;
    private BigDecimal tempMax;
    private BigDecimal humidityMin;
    private BigDecimal humidityMax;
    private BigDecimal windSpeedMax;
    private BigDecimal totalRainfallMax;
    private BigDecimal hourlyRainfallMax;
    private BigDecimal dailyRainfallMax;
    private BigDecimal lightMin;
    private BigDecimal lightMax;
    private BigDecimal uvIntensityMax;
    private BigDecimal uvIndexMax;
    private Integer batteryAlarm;
    private Integer enabled;
    private Date updatedAt;
}
