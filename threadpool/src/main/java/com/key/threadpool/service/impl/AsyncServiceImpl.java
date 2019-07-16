package com.key.threadpool.service.impl;

import com.key.threadpool.service.AsyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * @Classname AsyncServiceImpl
 * @Description 将Service层的服务异步化
 * @Date 2019-07-16 16:03
 * @Created by key
 */
@Service
public class AsyncServiceImpl implements AsyncService {

    private static final Logger logger = LoggerFactory.getLogger(AsyncServiceImpl.class);

    @Override
    @Async("asyncServiceExecutor")
    public void executeAysnc() {
        logger.info("start executeAsync");
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            e.printStackTrace();
        }
        logger.info("end executeAsync");
    }
}
