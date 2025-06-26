package com.example.VideoService.Service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "AUTHSERVICE")
public interface FeignClientInterface {
    @PostMapping("/extractuser")
    String extractUsername(@RequestHeader("token") String token);

}
