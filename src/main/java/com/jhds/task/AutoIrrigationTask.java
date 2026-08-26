package com.jhds.task;

import com.jhds.service.NutrientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AutoIrrigationTask {

    @Autowired
    private NutrientService nutrientService;

    @Scheduled(cron = "0 * * * * ?")
    public void autoIrrigate() {
        nutrientService.autoIrrigate();
    }
}
