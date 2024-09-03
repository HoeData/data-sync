package com.manniu.datasync.config;

import com.manniu.datasync.service.SyncDataServiceSend;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 船舶端定时任务
 */
@Component
@ConditionalOnProperty(name = "manniu.datasync.enable", havingValue = "true")
public class ShipTask {

    @Autowired
    private SyncDataServiceSend syncDataServiceSend;
    /**
     * 船舶段定时补偿发送文件（断点续传）
     */
    @Scheduled(fixedDelay = 60*60*1000)
    public void compensateFile(){
        syncDataServiceSend.compensateFile();
    }
}
