package com.example.VideoService.Controller;

import com.example.VideoService.Model.VideoEntity;
import com.example.VideoService.Model.VideoRepository;
import com.example.VideoService.Service.FeignClientInterface;
import com.example.VideoService.Service.VideoProcessing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@RestController
public class VideoController {
    @Autowired
    private FeignClientInterface feignClientInterface;
    @Autowired
    private VideoProcessing videoProcessing;
    @Autowired
    private VideoRepository repository;
    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file,
                                         @RequestHeader("title") String title,
                                         @RequestHeader("desc") String desc,
                                         @RequestHeader("Authorization") String token) {
        if (file.isEmpty() || title.isEmpty() || desc.isEmpty()) {
            return ResponseEntity.badRequest().body("Missing 1 or more parameters");
        }

        String uploader = feignClientInterface.extractUsername(token);

        VideoEntity videoEntity = new VideoEntity();
        videoEntity.setUploader(uploader);
        videoEntity.setDesc(desc);
        videoEntity.setTitle(title);
        videoEntity.setViews(0);
        videoEntity.setId(UUID.randomUUID().toString());

        videoProcessing.save(videoEntity, file);
        return ResponseEntity.ok("File Successfully uploaded");
    }

    @Value("${files.hls-videos}")
    private String hlsPath;
    @GetMapping("/{videoId}/master.m3u8")
    public ResponseEntity<Resource> getMasterPlaylist(@PathVariable String videoId) throws IOException {
        Path path = Paths.get(hlsPath, videoId, "master.m3u8");
        System.out.println(path.toString());
        repository.incrementViewCount(videoId);
        return serveFile(path, "application/vnd.apple.mpegurl");
    }

    // Serve variant playlist (e.g., 720/index.m3u8)
    @GetMapping("/{videoId}/{quality}/index.m3u8")
    public ResponseEntity<Resource> getVariantPlaylist(
            @PathVariable String videoId,
            @PathVariable String quality) throws IOException {
        Path path = Paths.get(hlsPath, videoId, quality, "index.m3u8");
        return serveFile(path, "application/vnd.apple.mpegurl");
    }

    // Serve .ts segments (e.g., 720/file001.ts)
    @GetMapping("/{videoId}/{quality}/{segment}")
    public ResponseEntity<Resource> getSegment(
            @PathVariable String videoId,
            @PathVariable String quality,
            @PathVariable String segment) throws IOException {
        Path path = Paths.get(hlsPath, videoId, quality, segment);
        return serveFile(path, "video/MP2T");
    }

    private ResponseEntity<Resource> serveFile(Path path, String contentType) throws IOException {
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new UrlResource(path.toUri());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(resource);
    }
    @GetMapping("/videos")
    public ResponseEntity<List<VideoEntity>> getVideosWithSQL(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (size > 20) size = 20;
        int offset = page * size;

        List<VideoEntity> videos = repository.getPaginatedVideos(size, offset);
        return ResponseEntity.ok(videos);
    }
    
}
