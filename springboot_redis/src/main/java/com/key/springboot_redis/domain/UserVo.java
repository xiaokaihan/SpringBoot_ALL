package com.key.springboot_redis.domain;

import lombok.Data;
import lombok.ToString;

/**
 * @Classname UserVo
 * @Description TODO
 * @Date 2019-07-16 18:33
 * @Created by key
 */
@Data
@ToString
public class UserVo {

    public  static final String Table = "t_user";

    private String name;
    private String address;
    private Integer age;

}
