package com.jhds.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhds.entity.WeatherSensorData;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface WeatherSensorDataMapper extends BaseMapper<WeatherSensorData> {

    @Select("SELECT * FROM weather_sensor_data WHERE record_time >= #{startTime} ORDER BY record_time ASC")
    List<WeatherSensorData> selectHistory(String startTime);
}
