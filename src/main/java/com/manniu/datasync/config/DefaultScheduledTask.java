package com.manniu.datasync.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.manniu.datasync.entity.SyncFile;
import com.manniu.datasync.service.SyncDataServiceSend;
import com.manniu.datasync.service.SyncFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
public class DefaultScheduledTask {
    @Autowired
    private SyncFileService syncFileService;


    /**
     * 定期检查文件是否上传或者合并完毕 把分割的文件进行删除
     */
    @Scheduled(cron = "0 0 0 15 * ?")
    public void clearCache(){
        //只清理近期一个月的
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusMonths(1);
        long start = startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = endTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        List<SyncFile> syncFileList = syncFileService.list(Wrappers.<SyncFile>lambdaQuery()
                .between(SyncFile::getCreateTime, start, end)
                .and(x -> x.eq(SyncFile::getStatus, 1).or().eq(SyncFile::getStatus, 2)));
        for (SyncFile syncFile : syncFileList) {
            File file = new File(syncFile.getFilePath());
            File parentFile = file.getParentFile();
            //发送方都删掉
            if(syncFile.getPlatform().equals(1)){
                recursionDeleteFile(parentFile);
            }
            else if(syncFile.getPlatform().equals(2)){ //接收方吧分割文件删掉
                File splitFile = new File(parentFile.getParentFile(),"split");
                recursionDeleteFile(splitFile);
            }

        }
    }



    private void recursionDeleteFile(File directory){
        if(!directory.exists()){
            return;
        }
        File[] files = directory.listFiles();
        if(files != null){
            for (File file: files) {
                if(file.isDirectory()){
                    recursionDeleteFile(file);
                }else{
                    file.delete();
                }
            }
        }
        directory.delete();
    }
}
