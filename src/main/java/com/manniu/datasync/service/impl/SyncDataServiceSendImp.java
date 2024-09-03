package com.manniu.datasync.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.manniu.datasync.entity.SyncFile;
import com.manniu.datasync.entity.SyncFileDetail;
import com.manniu.datasync.service.GrpcService;
import com.manniu.datasync.service.SyncDataServiceSend;
import com.manniu.datasync.service.SyncFileDetailService;
import com.manniu.datasync.service.SyncFileService;
import com.manniu.datasync.util.CommonUtil;
import com.manniu.datasync.util.ThreadPoolUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SyncDataServiceSendImp implements SyncDataServiceSend {
    @Autowired
    private SyncFileService syncFileService;
    @Autowired
    private SyncFileDetailService  syncFileDetailService;
    @Autowired
    private WebClient webClient;


    @Transactional
    @Override
    public void sendFile(File file,String id){
        SyncFile syncFile = new SyncFile();
        syncFile.setFileId(id);
        syncFile.setPlatform(1);
        syncFile.setFileName(file.getName());
        syncFile.setFilePath(file.getAbsolutePath());
        syncFile.setCreateTime(Instant.now().toEpochMilli());
        syncFile.setStatus(0);
        List<SyncFileDetail> fileDetailList = CommonUtil.splitFile(file).stream().map(v -> {
            SyncFileDetail detail = new SyncFileDetail();
            detail.setFileId(syncFile.getFileId());
            detail.setFileDetailId(IdWorker.get32UUID());
            detail.setFileName(v.getFileName());
            detail.setFilePath(v.getFilePath());
            detail.setStatus(0);
            detail.setCreateTime(v.getCreateTime());
            detail.setSort(Integer.parseInt(v.getFileName().split("\\.")[0]));
            return detail;
        }).collect(Collectors.toList());
        syncFileService.save(syncFile);
        syncFileDetailService.saveBatch(fileDetailList);
        //开始进行发送
        ThreadPoolUtil.submit( ()->{
           //开始处理分片请求
            for (SyncFileDetail syncFileDetail : fileDetailList) {
                executeSendFile(syncFileDetail, syncFile);
            }
        });
    }

    /**
     * 重试、补偿发送
     */
    @Override
    public void compensateFile() {
        //查找未发送的文件信息
        List<SyncFile> syncFileList = syncFileService.list(Wrappers.<SyncFile>lambdaQuery()
                .eq(SyncFile::getStatus, 0)
                .eq(SyncFile::getPlatform, 1)
                .orderByDesc(SyncFile::getCreateTime)
        );
        for (SyncFile syncFile : syncFileList) {
            handleFileDetailList(syncFile);
        }

    }

    /**
     * 处理某一个大文件
     * @param syncFile
     */
    private void handleFileDetailList(SyncFile syncFile){
        List<SyncFileDetail> list = syncFileDetailService.list(Wrappers.<SyncFileDetail>lambdaQuery()
                .eq(SyncFileDetail::getFileId, syncFile.getFileId())
                .eq(SyncFileDetail::getStatus,0)
        );
        if(list.isEmpty()){
            doneSyncFileUpload(syncFile.getFileId());
        }else{
            for (SyncFileDetail syncFileDetail : list) {
                executeSendFile(syncFileDetail, syncFile);
            }
            //判断当前集合中未完成的时候存在 如果都不存在标记完成 发起合并请求
            long count = list.stream().filter(v -> v.getStatus() == 0).count();
            if(count==0){
                doneSyncFileUpload(syncFile.getFileId());
            }
        }
    }

    /**
     * 发送小文件到 需要同步的服务器中
     * @param syncFileDetail
     * @param syncFile
     */
    private void executeSendFile(SyncFileDetail syncFileDetail,SyncFile syncFile){
        MultiValueMap<String,Object> param = new LinkedMultiValueMap<>();
        param.add("file", new FileSystemResource(syncFileDetail.getFilePath()));
        param.add("fileName", syncFile.getFileName());
        param.add("fileDetailName", syncFileDetail.getFileName());
        param.add("fileId", syncFile.getFileId());
        Mono<String> stringMono = webClient.post()
                .uri("/syncData/receiveFile")
                .body(BodyInserters.fromMultipartData(param))
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> Mono.just(e.getMessage()));
        String block = stringMono.block();
        if("success".equals(block)){
            syncFileDetail.setStatus(1);
            syncFileDetailService.updateById(syncFileDetail);
        }
    }

    /**
     * 文件发送完成
     */
    private void doneSyncFileUpload(String fileId){
        //先请求合并 如果完成 在修改 方便下次补偿
        Mono<String> mono = webClient.get()
                .uri("/syncData/noticeMerge?fileId=" + fileId)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> Mono.just(e.getMessage()));
        String block = mono.block();
        if("success".equals(block)){
            //合并完成 修改状态
            //如果没有子文件 判定完成 给一个合并事件
            syncFileService.update(Wrappers.<SyncFile>lambdaUpdate()
                    .set(SyncFile::getStatus,1)
                    .eq(SyncFile::getFileId,fileId).eq(SyncFile::getPlatform,1));
        }else {
            //合并失败
        }
    }

}
