package com.key.threadpool.config;

import com.key.threadpool.expand.VisiableThreadPoolTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @Classname ExecutorConfig
 * @Description 用来定义如何创建一个ThreadPoolTaskExecutor
 * @Date 2019-07-16 16:09
 * @Created by key
 */
@Configuration
@EnableAsync
public class ExecutorConfig {

    private static final Logger logger = LoggerFactory.getLogger(ExecutorConfig.class);

    /**
     * async-service-, 2. do submit,taskCount [20], completedTaskCount [19], activeCount [1], queueSize [0]
     * <p>
     * 这说明提交任务到线程池的时候，调用的是submit(Callable task)这个方法，其他字段也一目了然。 VisiableThreadPoolTaskExecutor
     *
     * @return
     */
    @Bean
    public Executor asyncServiceExecutor() {
        logger.info("start asyncServiceExecutor");

        /**
         *
         */
        //ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        /**
         * 扩展类， 监控线程池使用情况，比上一种方式多了监控日志。 可以分别测试。
         */
        ThreadPoolTaskExecutor executor = new VisiableThreadPoolTaskExecutor();
        //配置核心线程数
        executor.setCorePoolSize(5);
        //配置最大线程数
        executor.setMaxPoolSize(5);
        //配置队列大小
        executor.setQueueCapacity(99999);
        //配置线程池中的线程的名称前缀
        executor.setThreadNamePrefix("async-service-");
        // rejection-policy：当pool已经达到max size的时候，如何处理新任务
        // CALLER_RUNS：不在新线程中执行任务，而是有调用者所在的线程来执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

}
