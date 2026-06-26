package com.paperless.local;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@MapperScan("com.paperless.local.mapper")
@SpringBootApplication
public class PaperlessLocalApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaperlessLocalApplication.class, args);
    }
}
