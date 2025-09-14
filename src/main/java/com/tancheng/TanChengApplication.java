package com.tancheng;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

//@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.tancheng.mapper")
@SpringBootApplication
public class TanChengApplication {

    public static void main(String[] args) {
        SpringApplication.run(TanChengApplication.class, args);
    }

}
