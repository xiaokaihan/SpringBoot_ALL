package com.key.springboot_redis.config.cluster;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.*;

@SpringBootTest
@RunWith(SpringRunner.class)
public class RedisClusterTest {

    @After
    public void tearDown() throws Exception {
        System.out.println("test end.");
    }

    @Autowired
    private RedisTemplate redisTemplate;

    @Test
    public void test() {
        redisTemplate.opsForValue().set("redis-cluster-ooh", "helllooooooooooo");
        System.out.println(redisTemplate.opsForValue().get("redis-cluster-ooh"));
    }
}