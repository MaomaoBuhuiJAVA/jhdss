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
            log.error("Insect data sync error", e);
        }
    }
}
