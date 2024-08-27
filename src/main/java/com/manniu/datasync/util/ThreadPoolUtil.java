package com.manniu.datasync.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.*;

@Slf4j
@Component
public class ThreadPoolUtil {
    private static ThreadPoolExecutor executor;
    private int corePoolSize = 5;
    private int maximumPoolSize = 10;

    public static <T> Future<T> submit(Callable<T> task) {
        try {
            printThreadPoolStatus();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return executor.submit(task);
    }

    public static void submit(Runnable runnable) {
        try {
            printThreadPoolStatus();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        executor.submit(runnable);
    }

    /**
     * 打印线程池的状态
     */
    public static void printThreadPoolStatus() throws InterruptedException {
        int queueSize = executor.getQueue().size();
        log.info("当前排队线程数：" + queueSize);
        int activeCount = executor.getActiveCount();
        log.info("当前活动线程数：" + activeCount);
        long completedTaskCount = executor.getCompletedTaskCount();
        log.info("执行完成线程数：" + completedTaskCount);
        long taskCount = executor.getTaskCount();
        log.info("总线程数：" + taskCount);
        Thread.sleep(3000);
    }

    @PostConstruct
    public void initProcessorThreadPool() {
        executor = new ThreadPoolExecutor(
                corePoolSize
                , maximumPoolSize
                , 60
                , TimeUnit.HOURS
                , new LinkedBlockingQueue<>(500)
                , new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }


}
