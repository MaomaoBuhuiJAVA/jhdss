package com.jhds.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jhds.entity.AlarmRecord;
import com.jhds.mapper.AlarmRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;

@Slf4j
@Component
public class AlarmCleanTask {

    @Autowired
    private AlarmRecordMapper alarmRecordMapper;

    @Scheduled(cron = "0 0 4 * * ?")
    public void cleanOldAlarms() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -30);
        int deleted = alarmRecordMapper.delete(
                new LambdaQueryWrapper<AlarmRecord>()
                        .lt(AlarmRecord::getCreatedAt, cal.getTime()));
        log.info("Cleaned {} old alarm records", deleted);
    }
}
