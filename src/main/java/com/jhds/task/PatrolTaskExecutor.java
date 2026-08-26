package com.jhds.task;

import com.jhds.service.PatrolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PatrolTaskExecutor {

    @Autowired
    private PatrolService patrolService;

    @Scheduled(cron = "0 * * * * ?")
    public void executePending() {
        patrolService.executePendingTasks();
    }
}
