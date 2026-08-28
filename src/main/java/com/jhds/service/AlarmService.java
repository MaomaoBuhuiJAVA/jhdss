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
import java.util.Locale;
import java.util.Map;

@Service
public class AlarmService {

    /**
     * Keep value 1 as resolved for compatibility with the original
     * 0=unhandled / 1=handled schema. Processing is the newly added state.
     */
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_RESOLVED = 1;
    private static final int STATUS_PROCESSING = 2;
    private static final int MAX_PAGE_SIZE = 500;
    private static final int MAX_MEMO_LENGTH = 5000;

    @Autowired
    private AlarmRecordMapper alarmRecordMapper;

    public IPage<AlarmRecord> getList(String level, String sourceModule, String status, Integer page, Integer size) {
        int pageNo = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        Page<AlarmRecord> p = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<AlarmRecord> wrapper = new LambdaQueryWrapper<AlarmRecord>()
                .orderByDesc(AlarmRecord::getCreatedAt)
                .orderByDesc(AlarmRecord::getId);
        if (level != null && !"all".equals(level)) {
            wrapper.eq(AlarmRecord::getLevel, level);
        }
        if (sourceModule != null && !"all".equals(sourceModule)) {
            wrapper.eq(AlarmRecord::getSourceModule, sourceModule);
        }
        if (status != null && !"all".equals(status)) {
            wrapper.eq(AlarmRecord::getStatus, toStatusCode(status));
        }
        return alarmRecordMapper.selectPage(p, wrapper);
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        List<Map<String, Object>> levelStats = alarmRecordMapper.selectStatsByLevel();
        List<Map<String, Object>> sourceStats = alarmRecordMapper.selectStatsBySource();
        List<Map<String, Object>> statusStats = alarmRecordMapper.selectStatsByStatus();
        int total = alarmRecordMapper.selectCount(null);
        stats.put("total", total);
        stats.put("byLevel", levelStats);
        stats.put("bySource", sourceStats);
        stats.put("byStatus", statusStats);
        return stats;
    }

    public void createAlarm(String title, String description, String level, String sourceModule, String location) {
        AlarmRecord record = new AlarmRecord();
        record.setTitle(title);
        record.setDescription(description);
        record.setLevel(level);
        record.setSourceModule(sourceModule);
        record.setLocation(location);
        record.setStatus(STATUS_PENDING);
        record.setCreatedAt(new Date());
        alarmRecordMapper.insert(record);
    }

    public AlarmRecord updateAlarm(Long id, String status, String handlingMemo) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("告警记录编号无效");
        }
        if (status == null && handlingMemo == null) {
            throw new IllegalArgumentException("请至少提交处置状态或处置说明");
        }
        if (handlingMemo != null && handlingMemo.length() > MAX_MEMO_LENGTH) {
            throw new IllegalArgumentException("处置说明不能超过" + MAX_MEMO_LENGTH + "个字符");
        }

        AlarmRecord record = alarmRecordMapper.selectById(id);
        if (record == null) {
            throw new IllegalArgumentException("告警记录不存在");
        }

        if (status != null) {
            int newStatus = toStatusCode(status);
            record.setStatus(newStatus);
            if (newStatus == STATUS_RESOLVED) {
                if (record.getHandledAt() == null) {
                    record.setHandledAt(new Date());
                }
            } else {
                record.setHandledAt(null);
            }
        }
        if (handlingMemo != null) {
            record.setHandlingMemo(handlingMemo);
        }
        alarmRecordMapper.updateById(record);
        return record;
    }

    /** Kept for callers of the legacy endpoint. */
    public void handle(Long id) {
        updateAlarm(id, "resolved", null);
    }

    private int toStatusCode(String status) {
        String normalized = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "pending":
                return STATUS_PENDING;
            case "processing":
                return STATUS_PROCESSING;
            case "resolved":
                return STATUS_RESOLVED;
            default:
                throw new IllegalArgumentException("不支持的告警状态: " + status);
        }
    }
}
