package com.uber;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 共乘叫車平台 - 主應用程式入口
 */
@SpringBootApplication
public class RideDispatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(RideDispatchApplication.class, args);
    }
}
