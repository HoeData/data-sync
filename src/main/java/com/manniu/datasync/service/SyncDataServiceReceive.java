package com.manniu.datasync.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 接收方
 */
public interface SyncDataServiceReceive {
    void receiveFile(MultipartFile file, String fileName,String fileDetailName, String fileId) throws Exception;

    /**
     * 通知合并
     * @param fileId
     */
    void noticeFileMerge(String fileId);
}
