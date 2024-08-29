package com.manniu.datasync.service;

import com.manniu.datasync.doMain.SnapshotDo;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 自定义一个 消息队列
 */
public class QueueService {
    public static BlockingQueue<SnapshotDo>  queue = new LinkedBlockingQueue<>(5000);
}
