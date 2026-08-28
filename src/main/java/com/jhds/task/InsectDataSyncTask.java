package com.jhds.task;

import com.jhds.service.InsectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InsectDataSyncTask {

    @Autowired
    private InsectService insectService;

    @Scheduled(fixedRate = 300000)
    public void syncInsectData() {
        try {
            log.debug("Insect data sync task started");
            insectService.syncLatestPhotos();
        } catch (Exception e) {
            if (isRedisFailure(e)) {
                log.warn("Redis unavailable, skip insect data sync: {}", e.getMessage());
            } else {
                log.error("Insect data sync error", e);
            }
        }
    }

    private boolean isRedisFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String name = current.getClass().getName();
            if (name.contains("RedisConnection") || name.contains("RedisSystem")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
