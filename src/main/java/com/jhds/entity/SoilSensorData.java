package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("soil_sensor_data")
public class SoilSensorData {
    @TableId(type = IdType.AUTO)
    private Long id;
    private BigDecimal soilTemp;
    private BigDecimal soilHumidity;
    private BigDecimal soilEc;
    private BigDecimal soilPh;
    private BigDecimal soilSalt;
    private BigDecimal soilNitrogen;
    private BigDecimal soilPhosphorus;
    private BigDecimal soilPotassium;
    private Date recordTime;
}
