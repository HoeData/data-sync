package com.manniu.datasync.service;

import java.io.File;
import java.time.LocalDateTime;

/**
 * 发送方
 */
public interface SyncDataServiceSend {
//
//    /**
//     * 同步某些点 在某些时刻
//     * @param startTime
//     * @param endTime
//     * @param pointName
//     * @return
//     */
//    String createSendFile(LocalDateTime startTime,LocalDateTime endTime,String ... pointName);
    /**
     * 第一次发送
     * 1.保存住文件
     * 2.保存子文件
     * 3.第一次先进行
     * @param file
     */
    void sendFile(File file,String fileId);

    /**
     * 重试、补偿发送
     */
    void compensateFile();
}
