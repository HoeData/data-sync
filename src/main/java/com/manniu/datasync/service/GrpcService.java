package com.manniu.datasync.service;

import Until.CreateDate;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.Data;
import org.example.Base.Requst;
import org.example.Entity.BidirectionalSnapshots;
import org.example.Impl.IDataService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Iterator;

@Data
@Component
public class GrpcService implements CommandLineRunner {
    @Value("${manniu.grpc.ip}")
    private String grpcIp;
    @Value("${manniu.grpc.port}")
    private String grpcPort;
    private IDataService dataService;

    public IDataService getDataService() {
        if(dataService == null){
            dataService = CreateDate.CreateDate(grpcIp, grpcPort, "admin", "admin", Requst.GRPC);
        }
        return dataService;
    }

    @Override
    public void run(String... args) throws Exception {
        //获取GRPC连接
        getDataService();
        BidirectionalSnapshots snapshots = new BidirectionalSnapshots();
//        snapshots.setTagName(".+?");
        snapshots.setTagName("^T9998\\..+?$");
        snapshots.setClientId(IdWorker.get32UUID());
        dataService.getSnapshotImpl().AddSubscribe(snapshots);
        Iterator<Object>  subscribeResult = dataService.getSnapshotImpl().SubscribeSnapshots(snapshots);
        System.out.println(subscribeResult);
        while(subscribeResult.hasNext()){
            System.out.println(subscribeResult.next());
        }



    }
}
