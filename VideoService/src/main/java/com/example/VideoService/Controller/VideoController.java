package com.example.VideoService.Controller;

import com.example.VideoService.Model.VideoEntity;
import com.example.VideoService.Service.FeignClientInterface;
import com.example.VideoService.Service.VideoProcessing;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Random;
import java.util.UUID;

@RestController
public class VideoController {
    @Autowired
    private FeignClientInterface feignClientInterface;
    @Autowired
    private VideoProcessing videoProcessing;
    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file,
                         @RequestParam("title") String title,
                         @RequestParam("desc") String desc,
                         @RequestParam("Authorization") String token,
                         HttpServletResponse response){
        String uploader = feignClientInterface.extractUsername(token);
        if(file.isEmpty()||title.isEmpty()||desc.isEmpty()){
            response.setStatus(400);
            return "Missing 1 or more parameters";
        }
        else{
            VideoEntity videoEntity=new VideoEntity();
            videoEntity.setUploader(uploader);
            videoEntity.setDesc(desc);
            videoEntity.setTitle(title);
            videoEntity.setDislikecount(0);
            videoEntity.setLikecount(0);
            videoEntity.setViews(0);
            videoEntity.setId(UUID.randomUUID().toString());
            videoProcessing.save(videoEntity,file);
            response.setStatus(200);
            return "File Successfully uploaded";
        }
    }
}
