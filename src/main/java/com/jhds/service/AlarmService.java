package com.jhds.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jhds.entity.AlarmRecord;
import com.jhds.mapper.AlarmRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AlarmService {

    @Autowired
    private AlarmRecordMapper alarmRecordMapper;

    public IPage<AlarmRecord> getList(String level, String sourceModule, Integer page, Integer size) {
        Page<AlarmRecord> p = new Page<>(page, size);
        LambdaQueryWrapper<AlarmRecord> wrapper = new LambdaQueryWrapper<AlarmRecord>()
                .orderByDesc(AlarmRecord::getCreatedAt);
        if (level != null && !"all".equals(level)) {
            wrapper.eq(AlarmRecord::getLevel, level);
        }
        if (sourceModule != null && !"all".equals(sourceModule)) {
            wrapper.eq(AlarmRecord::getSourceModule, sourceModule);
        }
        return alarmRecordMapper.selectPage(p, wrapper);
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        List<Map<String, Object>> levelStats = alarmRecordMapper.selectStatsByLevel();
        List<Map<String, Object>> sourceStats = alarmRecordMapper.selectStatsBySource();
        int total = alarmRecordMapper.selectCount(null);
        stats.put("total", total);
        stats.put("byLevel", levelStats);
        stats.put("bySource", sourceStats);
        return stats;
    }

    public void createAlarm(String title, String description, String level, String sourceModule, String location) {
        AlarmRecord record = new AlarmRecord();
        record.setTitle(title);
        record.setDescription(description);
        record.setLevel(level);
        record.setSourceModule(sourceModule);
        record.setLocation(location);
        record.setStatus(0);
        record.setCreatedAt(new Date());
        alarmRecordMapper.insert(record);
    }

    public void handle(Long id) {
        AlarmRecord record = alarmRecordMapper.selectById(id);
        if (record != null) {
            record.setStatus(1);
            record.setHandledAt(new Date());
            alarmRecordMapper.updateById(record);
        }
    }
}
