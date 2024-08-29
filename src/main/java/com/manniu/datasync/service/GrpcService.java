package com.manniu.datasync.service;

import HoeData.BigData.Basedefine;
import HoeData.BigData.SnapshotOuterClass;
import Server.GrpcImp.Enum.MiddleDataType;
import Until.CreateDate;
import Until.ProtoUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.manniu.datasync.doMain.SnapshotDo;
import com.manniu.datasync.util.ThreadPoolUtil;
import lombok.Data;
import org.example.Base.Requst;
import org.example.Entity.*;
import org.example.Impl.IDataService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
@Component
public class GrpcService implements CommandLineRunner {
    @Value("${manniu.grpc.local.ip}")
    private String grpcIp;
    @Value("${manniu.grpc.local.port}")
    private String grpcPort;

    @Value("${manniu.http.remote.ip}")
    private String httpRemoteIp;
    @Value("${manniu.http.remote.port}")
    private String httpRemotePort;
    //访问中台的webClient
    private WebClient webClient;
    private IDataService dataService;

    //当前时区
    private ZoneOffset zoneOffset = ZoneId.systemDefault().getRules().getOffset(Instant.now());
    private DateTimeFormatter sf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private Integer batchSize = 2;

    public static HashMap<String,BidirectionalSnapshots> bidirectionalSnapshotsMap = new HashMap<>();

    public static HashMap<String,Boolean> subscribeFlagMap = new HashMap<>();

    public static HashMap<String,HashMap<String,Snapshot>> snapshotMap = new HashMap<>();

    public IDataService getDataService() {
        if(dataService == null){
            dataService = CreateDate.CreateDate(grpcIp, grpcPort, "admin", "admin", Requst.GRPC);
        }
        return dataService;
    }

    @Override
    public void run(String... args) {
        webClient = WebClient.builder().baseUrl("http://" + httpRemoteIp + ":" + httpRemotePort).defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build();
        //获取GRPC连接
        IDataService dataService = getDataService();
        Tablestate<List<Table>> listTablestate = dataService.getTableImpl().GetAllTables();
        List<Table> result = listTablestate.getResult();
        result = result.subList(0,1);
        for (Table table : result) {
            BidirectionalSnapshots snapshots = new BidirectionalSnapshots();
            snapshots.setTagName("^"+table.getTableName()+"\\..+?$");
            snapshots.setClientId(IdWorker.get32UUID());
            bidirectionalSnapshotsMap.put(table.getTableName(), snapshots);
            //先进行初始化值
            getSnapshots(table.getTableName());
            //开始订阅
            subscribeDetail(snapshots,table.getTableName());
        }
        //单独开启一个子线程去消费
        new Thread(()->{
            while (true){
                List<SnapshotDo> snapshotDos = new ArrayList<>();
                //转移的数量
                int successNumber = QueueService.queue.drainTo(snapshotDos, batchSize);
                while(batchSize > successNumber){
                    SnapshotDo take = null;
                    try {
                        take = QueueService.queue.take();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    snapshotDos.add(take);
                    successNumber++;
                }
                //处理或者发送这批同步的数据
                doSendHeadquarters(snapshotDos);
            }
        }).start();
        System.out.println("初始化成功");
    }


    //开始订阅
    private void subscribeDetail(BidirectionalSnapshots snapshots,String tableName){
        try{
            ThreadPoolUtil.submit(() ->{
                try{
                    dataService.getSnapshotImpl().AddSubscribe(snapshots);
                    Iterator<Object> subscribeResult = dataService.getSnapshotImpl().SubscribeSnapshots(snapshots);
                    while(subscribeResult.hasNext()){
                        SnapshotOuterClass.SubscribeSnapshotsResponse next = (SnapshotOuterClass.SubscribeSnapshotsResponse)subscribeResult.next();
                        List<Basedefine.PointData> resultList = next.getResultList();
                        resultList.forEach(pointData ->{
                            produceMessage(tableName,pointData);
                        });
                    }
                }catch (Exception e){
                    subscribeFlagMap.put(tableName,false);
                }
            });
        }catch (Exception e){
            subscribeFlagMap.put(tableName,false);
        }
    }


    public void getSnapshots(String tableName){
        IDataService dataService = getDataService();
        //搜索点
        Point point = new Point();
        point.setTableName(tableName);
        point.setTagName("**");
        point.setTagDesc("");
        PointStatus pointStatus = dataService.getPointImpl().Search(point, Integer.MAX_VALUE);
        ArrayList<String> pointNameList = (ArrayList<String>)pointStatus.getResult();
        //获取快照初始化
        try {
            SnapshotStatus snapshotStatus = dataService.getSnapshotImpl().GetSnapshots(pointNameList);
            List<Snapshot> snapshotList = (List<Snapshot>) snapshotStatus.getResult();
            HashMap<String, Snapshot> res = new HashMap<>();
            if(Objects.nonNull(snapshotList)){
                res = snapshotList.stream().collect(Collectors.toMap(Snapshot::getTagFullName, Function.identity(), (a, b) -> a, HashMap::new));
            }
            snapshotMap.put(tableName,res);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     *
     * @param tableName 中台表名
     * @param pointData 中台收到的新值
     */
    private void produceMessage(String tableName,Basedefine.PointData pointData){
        //转换后的值
        Snapshot convertSnapshot = ProtoUtil.ByteValue(String.valueOf(pointData.getType()), pointData.getValue().toByteArray());
        HashMap<String, Snapshot> tableSnapshotMap = snapshotMap.get(tableName);
        //内存中的值
        Snapshot memorySnapshot = tableSnapshotMap.get(pointData.getTagFullName());
        //如果是空 直接发送
        if(Objects.isNull(memorySnapshot)){
            convertSnapshot.setTagFullName(pointData.getTagFullName());
            MiddleDataType middleDataType = MiddleDataType.forValue(pointData.getType());
            convertSnapshot.setType(middleDataType);
            convertSnapshot.setTime(String.valueOf(pointData.getTime().getSeconds()));
            tableSnapshotMap.put(convertSnapshot.getTagFullName(),convertSnapshot);
            buildMessage(convertSnapshot);
        }else{
            //判断如果不相同
            if(!Objects.equals(memorySnapshot.getValue().toString(),convertSnapshot.getValue().toString())){
                memorySnapshot.setValue(convertSnapshot.getValue());
                memorySnapshot.setTime(String.valueOf(pointData.getTime().getSeconds()));
                buildMessage(memorySnapshot);
            }
        }
    }
    //构建消息并且添加到队列
    private void buildMessage(Snapshot snapshot){
        SnapshotDo snapshotDo = new SnapshotDo();
        snapshotDo.setTagFullName(snapshot.getTagFullName());
        snapshotDo.setTime(LocalDateTime.ofEpochSecond(Long.parseLong(snapshot.getTime()), 0, zoneOffset).format(sf));
        snapshotDo.setValue(snapshot.getValue().toString());
        snapshotDo.setType(snapshot.getType().getValue());
        boolean overflow = QueueService.queue.offer(snapshotDo);
        if(!overflow){
            //TODO落到磁盘 进行SQLite持久化
            System.out.println("磁盘持久化"+snapshotDo.getTagFullName()+snapshotDo.getValue());
        }
    }


    /**
     * 发送HTTP请求到数据中心
     * @param snapshotDos
     */
    private void doSendHeadquarters(List<SnapshotDo> snapshotDos) {
        JSONObject json = new JSONObject();
        json.put("datas",snapshotDos);
        Mono<String> result = webClient.post()
                .uri("/DataAdapter/Snapshot.PutSnapshots")
                .bodyValue(json.toString())
                .retrieve()
                .bodyToMono(String.class)
//                .onErrorResume(e -> Mono.just(e.getMessage()));
                .onErrorResume(e -> Mono.just("error"));
        String block = result.block();
        //成功发送并且拿到请求了
        if(!"error".equals(block)){
            SnapshotStatus snapshotStatus = JSONObject.parseObject(block, SnapshotStatus.class);
            System.out.println(snapshotStatus);
        }

    }


}
