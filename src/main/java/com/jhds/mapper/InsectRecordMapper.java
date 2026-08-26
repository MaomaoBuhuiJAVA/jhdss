package com.jhds.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhds.entity.InsectRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;
import java.util.Map;

public interface InsectRecordMapper extends BaseMapper<InsectRecord> {

    @Select("SELECT species, SUM(count) as total FROM insect_record WHERE record_date = #{date} GROUP BY species")
    List<Map<String, Object>> selectStatsByDate(String date);

    @Select("SELECT COALESCE(SUM(count), 0) FROM insect_record WHERE record_date = #{date}")
    Integer selectTotalCountByDate(String date);

    @Select("SELECT COALESCE(SUM(count), 0) FROM insect_record WHERE record_date = #{date} AND device_id = #{deviceId}")
    Integer selectTotalCountByDateAndDevice(@Param("date") String date, @Param("deviceId") String deviceId);

    @Select("SELECT * FROM insect_record WHERE record_date = #{date} AND device_id = #{deviceId} ORDER BY record_time DESC")
    List<InsectRecord> selectByDateAndDevice(@Param("date") String date, @Param("deviceId") String deviceId);
}
