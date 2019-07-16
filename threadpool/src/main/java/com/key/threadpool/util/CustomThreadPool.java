package com.key.threadpool.util;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author key
 * @Classname CustomThreadPool
 * @Description 自定义线程池
 * @Date 2019-07-16 11:38
 * @Created by key
 */
public class CustomThreadPool {

    /**
     * "其中线程线程1-4先占满了核心线程和最大线程数量，然后4、5线程进入等待队列，7-10线程被直接忽略拒绝执行，等1-4线程中有线程执行完后通知4、5线程继续执行。"
     * 这一段用词不太明确，容易误导新手，事实上应该是：
     * 1，由于线程预启动，首先创建了1，2号线程，然后task1，task2被执行；
     * 2，但任务提交没有结束，此时任务task3，task6到达发现核心线程已经满了，进入等待队列；
     * 3，等待队列满后创建任务线程3，4执行任务task3，task6，同时task4，task5进入队列；
     * 4，此时创建线程数（4）等于最大线程数，且队列已满，所以7，8，9，10任务被拒绝；
     * 5，任务执行完毕后回头来执行task4，task5，队列清空。
     *
     * @param args
     * @throws InterruptedException
     * @throws IOException
     */
    public static void main(String[] args) throws InterruptedException, IOException {
        int corePoolSize = 2;
        int maximumPoolSize = 4;
        long keepAliveTime = 10;
        TimeUnit unit = TimeUnit.SECONDS;
        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<Runnable>(2);
        ThreadFactory threadFactory = new NameTreadFactory();
        RejectedExecutionHandler handler = new MyIgnorePolicy();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit,
                workQueue, threadFactory, handler);
        executor.prestartAllCoreThreads(); // 预启动所有核心线程
        final int taskNum = 10;
        for (int i = 1; i <= taskNum; i++) {
            MyTask task = new MyTask(String.valueOf(i));
            executor.execute(task);
        }

        System.in.read(); //阻塞主线程
    }

    static class NameTreadFactory implements ThreadFactory {

        private final AtomicInteger mThreadNum = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "my-thread-" + mThreadNum.getAndIncrement());
            System.out.println(t.getName() + " has been created");
            return t;
        }
    }

    public static class MyIgnorePolicy implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            doLog(r, e);
        }

        private void doLog(Runnable r, ThreadPoolExecutor e) {
            // 可做日志记录等
            System.err.println(r.toString() + " rejected");
//          System.out.println("completedTaskCount: " + e.getCompletedTaskCount());
        }
    }

    static class MyTask implements Runnable {
        private String name;

        public MyTask(String name) {
            this.name = name;
        }

        @Override
        public void run() {
            try {
                System.out.println(this.toString() + " is running!");
                //让任务执行慢点
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "MyTask [name=" + name + "]";
        }
    }


}
