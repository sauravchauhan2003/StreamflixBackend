package com.example.VideoService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableDiscoveryClient
@EnableFeignClients
@EnableAsync
@EnableScheduling
@SpringBootApplication
public class VideoServiceApplication {

	public static void main(String[] args) {
		DbBlobSyncManager.syncDown();
		SpringApplication.run(VideoServiceApplication.class, args);
	}

}
