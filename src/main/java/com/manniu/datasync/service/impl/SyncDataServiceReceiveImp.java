package com.manniu.datasync.service.impl;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.manniu.datasync.config.SyncProperties;
import com.manniu.datasync.dao.SyncFileDao;
import com.manniu.datasync.entity.SyncFile;
import com.manniu.datasync.service.SyncDataServiceReceive;
import com.manniu.datasync.service.SyncFileService;
import com.manniu.datasync.util.CommonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class SyncDataServiceReceiveImp implements SyncDataServiceReceive {
    @Autowired
    private SyncFileDao syncFileDao;

    @Autowired
    private SyncProperties syncProperties;

    @Override
    public void receiveFile(MultipartFile file, String fileName,String detailFileName,String fileId) {
        SyncFile syncFile = checkFile(fileId, fileName);
        String saveFile = syncProperties.getSaveFile();
        File splitFile = new File(saveFile+File.separator+fileId+File.separator+"split");
        if(!splitFile.exists()) {
            splitFile.mkdirs();
        }
        File detailFile = new File(splitFile,detailFileName);
        try {
            file.transferTo(detailFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void noticeFileMerge(String fileId) {
        //判断是否已经合并
        SyncFile one = getOne(fileId, 2);
        if(Objects.isNull(one)) {
            throw new RuntimeException("未有同步请求");
        }
        if(one.getStatus() == 2){
            //已经合并过了不需要再次合并了
        }else{
            //开始合并
            //合并路径
            File splitFile = new File(syncProperties.getSaveFile()+File.separator+fileId+File.separator+"split");
            File mergeFile = new File(syncProperties.getSaveFile()+File.separator+fileId+File.separator+"merge"+File.separator+one.getFileName());
            //判断合并目标的路径是否存在
            File parentFile = mergeFile.getParentFile();
            if(!parentFile.exists()){
                parentFile.mkdirs();
            }
            try {
                CommonUtil.mergeFile(splitFile,mergeFile);
                //合并成功
                SyncFile update = new SyncFile();
                update.setStatus(2);
                update.setFilePath(mergeFile.getAbsolutePath());
                syncFileDao.update(update,Wrappers.<SyncFile>lambdaQuery().eq(SyncFile::getFileId,fileId).eq(SyncFile::getPlatform,2));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private SyncFile checkFile(String fileId,String fileName) {
        SyncFile syncFile = getOne(fileId,2);
        if(Objects.isNull(syncFile)) {
            syncFile = new SyncFile();
            syncFile.setFileId(fileId);
            syncFile.setPlatform(2);//接收方
            syncFile.setFileName(fileName);
            syncFile.setStatus(0);
            syncFile.setCreateTime(Instant.now().toEpochMilli());
            syncFileDao.insert(syncFile);
        }
        return syncFile;
    }

    private SyncFile getOne(String fileId,Integer platform){
        return syncFileDao.selectOne(Wrappers.<SyncFile>lambdaQuery().eq(SyncFile::getFileId, fileId).eq(SyncFile::getPlatform, platform));
    }

}
