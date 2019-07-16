package com.key.threadpool.controller;

import com.key.threadpool.service.AsyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Classname TestThreadPool
 * @Description TODO
 * @Date 2019-07-16 16:04
 * @Created by key
 */
@RestController
@RequestMapping("/v1")
public class TestThreadPool {

    private final static Logger logger = LoggerFactory.getLogger(TestThreadPool.class);

    @Autowired
    private AsyncService asyncService;


    /**
     * 2019-07-16 16:42:52.416  INFO 3769 --- [nio-8080-exec-2] c.k.t.controller.TestThreadPool          : start submit
     * 2019-07-16 16:42:52.416  INFO 3769 --- [nio-8080-exec-2] c.k.t.controller.TestThreadPool          : end submit
     * 2019-07-16 16:42:52.416  INFO 3769 --- [async-service-4] c.k.t.service.impl.AsyncServiceImpl      : start executeAsync
     * 2019-07-16 16:42:53.410  INFO 3769 --- [nio-8080-exec-4] c.k.t.controller.TestThreadPool          : start submit
     * 2019-07-16 16:42:53.410  INFO 3769 --- [nio-8080-exec-4] c.k.t.controller.TestThreadPool          : end submit
     * 2019-07-16 16:42:53.410  INFO 3769 --- [async-service-5] c.k.t.service.impl.AsyncServiceImpl      : start executeAsync
     * 2019-07-16 16:42:53.419  INFO 3769 --- [async-service-4] c.k.t.service.impl.AsyncServiceImpl      : end executeAsync
     * 2019-07-16 16:42:54.415  INFO 3769 --- [async-service-5] c.k.t.service.impl.AsyncServiceImpl      : end executeAsync
     * <p>
     * <p>
     * 如上日志所示，我们可以看到controller的执行线程是"nio-8080-exec-8"，这是tomcat的执行线程，
     * 而service层的日志显示线程名为“async-service-1”，显然已经在我们配置的线程池中执行了，
     * 并且每次请求中，controller的起始和结束日志都是连续打印的，表明每次请求都快速响应了，
     * 而耗时的操作都留给线程池中的线程去异步执行；
     *
     * @return
     */
    @RequestMapping("")
    public String test() {
        logger.info("start submit");

        //调用service层的任务
        asyncService.executeAysnc();

        logger.info("end submit");
        return "success";
    }

}
