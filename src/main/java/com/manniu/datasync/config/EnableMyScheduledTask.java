package com.manniu.datasync.config;

import com.manniu.datasync.service.SyncDataServiceSend;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "manniu.datasync.enable", havingValue = "true")
public class EnableMyScheduledTask {

    @Autowired
    private SyncDataServiceSend syncDataServiceSend;

    @Scheduled(fixedDelay = 60*60*1000)
    public void compensateFile(){
        syncDataServiceSend.compensateFile();
    }
}
