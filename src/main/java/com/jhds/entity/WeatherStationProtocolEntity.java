package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@Data
@TableName("weather_station_protocol")
public class WeatherStationProtocolEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sensorKey;
    private String displayName;
    private String commandHex;
    private String unit;
    private Integer sortOrder;
    private Integer enabled;
    private Date createdAt;
    private Date updatedAt;
}
