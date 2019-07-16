package com.key.threadpool.util;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Classname ThreadPoolUtil
 * @Description 线程池工具类
 * @Date 2019-07-15 12:03
 * @Created by key
 */
public class ThreadPoolUtil {

    private static AtomicInteger ai = new AtomicInteger();

    public static void main(String[] args) {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(4, 8,
                500, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(8),
                new ThreadPoolExecutor.DiscardOldestPolicy());

        for (int i = 0; i < 50; i++) {
            threadPoolExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    System.out.println(ai.getAndIncrement());
                }
            });
        }
        // System.out.println("Main thread");
    }

    /**
     * Executors 的四种线程池实现. 基于ThreadPoolExecutor
     * FixedThreadPool
     * CachedThreadPool
     * SingleThreadExecutor
     * ScheduledThreadPool
     */

    /**
     * corePoolSize与maximumPoolSize相等，即其线程全为核心线程，是一个固定大小的线程池;
     * keepAliveTime = 0   该参数默认对核心线程无效，而FixedThreadPool全部为核心线程;
     * workQueue 为LinkedBlockingQueue（无界阻塞队列），队列最大值为Integer.MAX_VALUE
     * 如果任务提交速度持续大余任务处理速度，会造成队列大量阻塞,因为队列很大，很有可能在拒绝策略前，内存溢出;
     * FixedThreadPool的任务执行是无序的；
     * <p>
     * 适用场景：可用于Web服务瞬时削峰，但需注意长时间持续高峰情况造成的队列阻塞!!!
     * 使用方式： Executors.newFixedThreadPool(nThreads);
     *
     * @param nThreads
     * @return
     */
    public static ExecutorService newFixedThreadPool(int nThreads) {
        return new ThreadPoolExecutor(nThreads, nThreads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingDeque<Runnable>());
    }

    /**
     * orePoolSize = 0，maximumPoolSize = Integer.MAX_VALUE，即线程数量几乎无限制;
     * keepAliveTime = 60s，线程空闲60s后自动结束;
     * workQueue 为 SynchronousQueue 同步队列，这个队列类似于一个接力棒，入队出队必须同时传递，
     * 因为CachedThreadPool线程创建无限制，不会有队列等待，所以使用SynchronousQueue；
     * <p>
     * 适用场景：快速处理大量耗时较短的任务，如Netty的NIO接受请求时，可使用CachedThreadPool。
     * 使用方式： Executors.newCachedThreadPool();
     *
     * @return
     */
    public static ExecutorService newCachedThreadPool() {
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>());
    }

    /**
     * 不就是newFixedThreadPool(1)吗？定眼一看，
     * 在源码中多了一层FinalizableDelegatedExecutorService包装，此处没有。
     * SingleThreadExecutor被包装后，无法成功向下转型。 因此，SingleThreadExecutor被定以后，无法修改，做到了真正的Single
     * <p>
     * 使用方式：  Executors.newSingleThreadExecutor();
     * 使用场景： 适合顺序执行的任务
     *
     * @return
     */
    public static ExecutorService newSingleThreadExecutor() {
        return new ThreadPoolExecutor(1, 1,
                0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
    }


    /**
     * newScheduledThreadPool调用的是ScheduledThreadPoolExecutor的构造方法，
     * 而ScheduledThreadPoolExecutor继承了ThreadPoolExecutor，构造是还是调用了其父类的构造方法.
     *
     * 使用场景：周期性的执行任务
     *
     * @param corePoolSize
     * @return
     */
    public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize) {
        return new ScheduledThreadPoolExecutor(corePoolSize);
    }


    /**
     *  自定义线程池
     * @return
     */

}
