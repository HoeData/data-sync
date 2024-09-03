package com.manniu.datasync.service;

import HoeData.BigData.Basedefine;
import HoeData.BigData.SnapshotOuterClass;
import Server.GrpcImp.Enum.MiddleDataType;
import Until.CreateDate;
import Until.ProtoUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.manniu.datasync.doMain.SnapshotDo;
import com.manniu.datasync.sqlite.SqliteUtil;
import com.manniu.datasync.util.ThreadPoolUtil;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.example.Base.Requst;
import org.example.Entity.*;
import org.example.Impl.IDataService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
@Component
@Log4j2
//只有在传播端在进行初始化
@ConditionalOnProperty(name = "manniu.datasync.enable", havingValue = "true")
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
    @Value("${manniu.queue.batchSize:100}")
    private Integer batchSize;

    public static HashMap<String, BidirectionalSnapshots> bidirectionalSnapshotsMap = new HashMap<>();

    public static HashMap<String, Boolean> subscribeFlagMap = new HashMap<>();

    public static HashMap<String, HashMap<String, Snapshot>> snapshotMap = new HashMap<>();

    public IDataService getDataService() {
        if (dataService == null) {
            dataService = CreateDate.CreateDate(grpcIp, grpcPort, "admin", "admin", Requst.GRPC);
        }
        return dataService;
    }

    @Override
    public void run(String... args) {
        webClient = WebClient.builder().baseUrl("http://" + httpRemoteIp + ":" + httpRemotePort)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        //获取GRPC连接
        IDataService dataService = getDataService();
        Tablestate<List<Table>> listTablestate = dataService.getTableImpl().GetAllTables();
        List<Table> result = listTablestate.getResult();
        Table testTable = new Table();
        testTable.setTableName("T9998");
        result = Arrays.asList(testTable);
        for (Table table : result) {
            BidirectionalSnapshots snapshots = new BidirectionalSnapshots();
            snapshots.setTagName("^" + table.getTableName() + "\\..+?$");
            snapshots.setClientId(IdWorker.get32UUID());
            bidirectionalSnapshotsMap.put(table.getTableName(), snapshots);
            //先进行初始化值
            getSnapshots(table.getTableName());
            //开始订阅
            subscribeDetail(snapshots, table.getTableName());
        }
        //单独开启一个子线程去消费
        new Thread(() -> {
            log.info("开始消费队列");
            while (true) {
                List<SnapshotDo> snapshotDos = new ArrayList<>();
                //转移的数量
                int successNumber = QueueService.queue.drainTo(snapshotDos, batchSize);
                while (batchSize > successNumber) {
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
                log.info("批量消费数量满了，开始消费");
                doSendHeadquarters(snapshotDos);
            }
        }).start();
        log.info("初始化成功");
    }


    //开始订阅
    private void subscribeDetail(BidirectionalSnapshots snapshots, String tableName) {
            ThreadPoolUtil.submit(() -> {
                System.out.println(Thread.currentThread().getName() + "开始订阅");
                try {
                    dataService.getSnapshotImpl().AddSubscribe(snapshots);
                    Iterator<Object> subscribeResult = dataService.getSnapshotImpl().SubscribeSnapshots(snapshots);
                    subscribeFlagMap.put(tableName, true);
                    try{
                        while (subscribeResult.hasNext()) {
                            SnapshotOuterClass.SubscribeSnapshotsResponse next = (SnapshotOuterClass.SubscribeSnapshotsResponse) subscribeResult.next();
                            List<Basedefine.PointData> resultList = next.getResultList();
                            resultList.forEach(pointData -> {
                                produceMessage(tableName, pointData);
                            });
                        }
                    } catch (Exception e) {
                        subscribeFlagMap.put(tableName, false);
                        throw new RuntimeException(e);
                    }
                } catch (Exception e) {
                    subscribeFlagMap.put(tableName, false);
                    throw new RuntimeException(e);
                }
            });
        }


    public void getSnapshots(String tableName) {
        IDataService dataService = getDataService();
        //搜索点
        Point point = new Point();
        point.setTableName(tableName);
        point.setTagName("**");
        point.setTagDesc("");
        PointStatus pointStatus = dataService.getPointImpl().Search(point, Integer.MAX_VALUE);
        ArrayList<String> pointNameList = (ArrayList<String>) pointStatus.getResult();
        //获取快照初始化
        try {
            SnapshotStatus snapshotStatus = dataService.getSnapshotImpl().GetSnapshots(pointNameList);
            List<Snapshot> snapshotList = (List<Snapshot>) snapshotStatus.getResult();
            HashMap<String, Snapshot> res = new HashMap<>();
            if (Objects.nonNull(snapshotList)) {
                res = snapshotList.stream().collect(Collectors.toMap(Snapshot::getTagFullName, Function.identity(), (a, b) -> a, HashMap::new));
            }
            snapshotMap.put(tableName, res);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param tableName 中台表名
     * @param pointData 中台收到的新值
     */
    private void produceMessage(String tableName, Basedefine.PointData pointData) {
        //转换后的值
        Snapshot convertSnapshot = ProtoUtil.ByteValue(String.valueOf(pointData.getType()), pointData.getValue().toByteArray());
        HashMap<String, Snapshot> tableSnapshotMap = snapshotMap.get(tableName);
        //内存中的值
        Snapshot memorySnapshot = tableSnapshotMap.get(pointData.getTagFullName());
        //如果是空 直接发送
        if (Objects.isNull(memorySnapshot)) {
            convertSnapshot.setTagFullName(pointData.getTagFullName());
            MiddleDataType middleDataType = MiddleDataType.forValue(pointData.getType());
            convertSnapshot.setType(middleDataType);
            convertSnapshot.setTime(String.valueOf(pointData.getTime().getSeconds()));
            tableSnapshotMap.put(convertSnapshot.getTagFullName(), convertSnapshot);
            buildMessage(convertSnapshot);
        } else {
            //判断如果不相同
            if (!Objects.equals(memorySnapshot.getValue().toString(), convertSnapshot.getValue().toString())) {
                memorySnapshot.setValue(convertSnapshot.getValue());
                memorySnapshot.setTime(String.valueOf(pointData.getTime().getSeconds()));
                buildMessage(memorySnapshot);
            }
        }
    }

    //构建消息并且添加到队列
    private void buildMessage(Snapshot snapshot) {
        SnapshotDo snapshotDo = new SnapshotDo();
        snapshotDo.setId(IdWorker.get32UUID());
        snapshotDo.setTagFullName(snapshot.getTagFullName());
        snapshotDo.setTime(LocalDateTime.ofEpochSecond(Long.parseLong(snapshot.getTime()), 0, zoneOffset).format(sf));
        snapshotDo.setValue(snapshot.getValue().toString());
        snapshotDo.setType(snapshot.getType().getValue());
        boolean overflow = QueueService.queue.offer(snapshotDo);
        if (!overflow) {
            SqliteUtil.batchInsertSnapshotDoList(Collections.singletonList(snapshotDo));
        }
    }


    /**
     * 发送HTTP请求到数据中心
     *
     * @param snapshotDos
     */
    private void doSendHeadquarters(List<SnapshotDo> snapshotDos) {
        JSONObject json = new JSONObject();
        json.put("datas", snapshotDos);
        Mono<String> result = webClient.post()
                .uri("/DataAdapter/Snapshot.PutSnapshots")
                .bodyValue(json.toString())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(e -> Mono.just("error"));
        String block = result.block();
        //成功发送并且拿到请求了
        if (!"error".equals(block)) {
            SnapshotStatus snapshotStatus = JSONObject.parseObject(block, SnapshotStatus.class);
            //返回的成功下标
            JSONArray successList = (JSONArray) snapshotStatus.getResult();
            //找到没成功的下标集合
            List<Integer> falseIndexList = new ArrayList<>();
            for (int i = 0; i < successList.size(); i++) {
                JSONObject obj = successList.getJSONObject(i);
                Boolean success = obj.getBoolean("Success");
                if (!success) {
                    falseIndexList.add(i);
                }
            }
            //将没成功的存到SQLite
            if (!falseIndexList.isEmpty()) {
                List<SnapshotDo> saveDb = falseIndexList.stream().map(snapshotDos::get).collect(Collectors.toList());
                SqliteUtil.batchInsertSnapshotDoList(saveDb);
            }
            //HTTP是通的 从SQLite中倒出来一份放到队列中
            sqliteTOQueue();
        } else {
            //存到SQLite中并且 把队列中都取出来
            List<SnapshotDo> batchInstall = new ArrayList<>();
            QueueService.queue.drainTo(batchInstall);
            batchInstall.addAll(snapshotDos);
            batchInstall.forEach(v -> {
                if (!StringUtils.isNotBlank(v.getId())) {
                    v.setId(IdWorker.get32UUID());
                }
            });
            SqliteUtil.batchInsertSnapshotDoList(batchInstall);
        }

    }

    private void sqliteTOQueue() {
        //取出一段并且删除
        List<SnapshotDo> snapshotDos = SqliteUtil.getSnapshotDoList();
        if (!snapshotDos.isEmpty()) {
            List<String> ids = snapshotDos.stream().map(SnapshotDo::getId).collect(Collectors.toList());
            SqliteUtil.deleteById(ids);
        } else {
            return;
        }
        Iterator<SnapshotDo> iterator = snapshotDos.iterator();
        while (iterator.hasNext()) {
            SnapshotDo snapshotDo = iterator.next();
            boolean offer = QueueService.queue.offer(snapshotDo);
            //如果加入队列失败 就把剩下的重新入库
            if (!offer) {
                SqliteUtil.batchInsertSnapshotDoList(snapshotDos);
                break;
            } else {
                iterator.remove();
            }
        }
    }

    @Scheduled(cron = "* */5 * * * ?")
    public void heartbeatDetection(){
//        System.out.println("测试是否可以连接");
        try{
            dataService.getServerImpl().GetHostTime();
        }catch (Exception e){
            //网络异常
            log.info("网络异常");
            return;
        }
        //先看标志服是否为false
        subscribeFlagMap.forEach((tableName,status)->{
            if(!status){
                BidirectionalSnapshots bidirectionalSnapshots = bidirectionalSnapshotsMap.get(tableName);
                subscribeDetail(bidirectionalSnapshots,tableName);
            }
        });
    }
}
