package com.example.AuthService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@EnableDiscoveryClient
@EnableFeignClients
@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        DbBlobSyncManager.syncDown();
        SpringApplication.run(AuthServiceApplication.class, args);
    }

}
