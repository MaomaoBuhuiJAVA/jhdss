package com.jhds.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhds.entity.AlarmRecord;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface AlarmRecordMapper extends BaseMapper<AlarmRecord> {

    @Select("SELECT level, COUNT(*) as count FROM alarm_record GROUP BY level")
    List<Map<String, Object>> selectStatsByLevel();

    @Select("SELECT source_module AS sourceModule, COUNT(*) AS count FROM alarm_record GROUP BY source_module")
    List<Map<String, Object>> selectStatsBySource();

    @Select("SELECT status, COUNT(*) AS count FROM alarm_record GROUP BY status")
    List<Map<String, Object>> selectStatsByStatus();
}
