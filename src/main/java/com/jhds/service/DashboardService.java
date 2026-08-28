package com.jhds.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jhds.entity.AlarmRecord;
import com.jhds.entity.DashboardFarmOperation;
import com.jhds.entity.DashboardGreenhouse;
import com.jhds.entity.DashboardMarketFeedback;
import com.jhds.entity.DashboardTodo;
import com.jhds.mapper.AlarmRecordMapper;
import com.jhds.mapper.DashboardFarmOperationMapper;
import com.jhds.mapper.DashboardGreenhouseMapper;
import com.jhds.mapper.DashboardMarketFeedbackMapper;
import com.jhds.mapper.DashboardTodoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads the dashboard's editable sidebar content from MySQL. */
@Service
public class DashboardService {
    private static final int DASHBOARD_ALARM_LIMIT = 2;

    @Autowired
    private DashboardGreenhouseMapper greenhouseMapper;
    @Autowired
    private DashboardFarmOperationMapper farmOperationMapper;
    @Autowired
    private DashboardTodoMapper todoMapper;
    @Autowired
    private DashboardMarketFeedbackMapper marketFeedbackMapper;
    @Autowired
    private AlarmRecordMapper alarmRecordMapper;

    public Map<String, Object> getOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("greenhouse", getPrimaryGreenhouse());
        overview.put("operations", getFarmOperations());
        overview.put("todos", getTodos());
        overview.put("marketFeedback", getMarketFeedback());
        overview.put("alarms", getCurrentAlarms());
        return overview;
    }

    private DashboardGreenhouse getPrimaryGreenhouse() {
        List<DashboardGreenhouse> primary = greenhouseMapper.selectList(
                new LambdaQueryWrapper<DashboardGreenhouse>()
                        .eq(DashboardGreenhouse::getIsPrimary, 1)
                        .orderByAsc(DashboardGreenhouse::getSortOrder)
                        .orderByAsc(DashboardGreenhouse::getId)
                        .last("LIMIT 1"));
        if (!primary.isEmpty()) {
            return primary.get(0);
        }

        List<DashboardGreenhouse> fallback = greenhouseMapper.selectList(
                new LambdaQueryWrapper<DashboardGreenhouse>()
                        .orderByAsc(DashboardGreenhouse::getSortOrder)
                        .orderByAsc(DashboardGreenhouse::getId)
                        .last("LIMIT 1"));
        return fallback.isEmpty() ? null : fallback.get(0);
    }

    private List<DashboardFarmOperation> getFarmOperations() {
        return farmOperationMapper.selectList(new LambdaQueryWrapper<DashboardFarmOperation>()
                .orderByAsc(DashboardFarmOperation::getSortOrder)
                .orderByDesc(DashboardFarmOperation::getOperationDate)
                .orderByDesc(DashboardFarmOperation::getId));
    }

    private List<DashboardTodo> getTodos() {
        return todoMapper.selectList(new LambdaQueryWrapper<DashboardTodo>()
                .orderByAsc(DashboardTodo::getSortOrder)
                .orderByAsc(DashboardTodo::getId));
    }

    private List<DashboardMarketFeedback> getMarketFeedback() {
        return marketFeedbackMapper.selectList(new LambdaQueryWrapper<DashboardMarketFeedback>()
                .eq(DashboardMarketFeedback::getEnabled, 1)
                .orderByAsc(DashboardMarketFeedback::getSortOrder)
                .orderByDesc(DashboardMarketFeedback::getId));
    }

    private List<AlarmRecord> getCurrentAlarms() {
        return alarmRecordMapper.selectList(new LambdaQueryWrapper<AlarmRecord>()
                .in(AlarmRecord::getStatus, 0, 2)
                .orderByDesc(AlarmRecord::getCreatedAt)
                .orderByDesc(AlarmRecord::getId)
                .last("LIMIT " + DASHBOARD_ALARM_LIMIT));
    }
}
