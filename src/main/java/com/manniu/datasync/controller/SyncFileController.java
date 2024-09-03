package com.manniu.datasync.controller;

import com.manniu.datasync.service.SyncDataServiceReceive;
import com.manniu.datasync.service.SyncDataServiceSend;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class SyncFileController {
    @Autowired
    private SyncDataServiceReceive syncDataServiceReceive;
    @Autowired
    private SyncDataServiceSend syncDataServiceSend;


    @GetMapping("/syncData/test")
    public String test(){
        return "连接成功";
    }
    /**
     * 接收文件
     * @param file
     * @param fileName
     * @param fileDetailName
     * @param fileId
     * @return
     */
    @PostMapping("/syncData/receiveFile")
    public String receiveFile(
            @RequestParam ("file") MultipartFile file,
            @RequestParam ("fileName") String fileName,
            @RequestParam ("fileDetailName") String fileDetailName,
            @RequestParam ("fileId") String fileId){
        //保存文件返回结果
        try {
            syncDataServiceReceive.receiveFile(file, fileName,fileDetailName, fileId);
        } catch (Exception e) {
            return "error";
        }
        return "success";
    }

    /**
     * 通知合并文件
     * @return
     */
    @GetMapping("/syncData/noticeMerge")
    public String noticeMerge(@RequestParam String fileId){
        try{
            syncDataServiceReceive.noticeFileMerge(fileId);
            return "success";
        }catch (Exception e){
            return "error";
        }
    }
}
