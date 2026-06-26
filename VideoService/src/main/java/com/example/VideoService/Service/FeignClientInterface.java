package com.example.VideoService.Service;

import com.example.VideoService.Config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign client for calling AuthService's /extractuser endpoint.
 * FeignConfig adds X-Internal-Key to every call automatically.
 */
@FeignClient(name = "AUTHSERVICE", configuration = FeignConfig.class)
public interface FeignClientInterface {

    @PostMapping("/extractuser")
    String extractUsername(@RequestHeader("token") String token);
}
