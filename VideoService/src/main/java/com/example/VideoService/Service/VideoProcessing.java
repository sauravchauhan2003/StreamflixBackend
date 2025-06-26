package com.example.VideoService.Service;

import com.example.VideoService.Model.VideoEntity;
import com.example.VideoService.Model.VideoRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
public class VideoProcessing {
    @Value("${files.videos}")
    private String folderPath;

    @Value("${files.hls-videos}")
    private String hlsPath;

    @Autowired
    private VideoRepository repository;

    @PostConstruct
    public void init() {
        File videoFolder = new File(folderPath);
        File hlsFolder = new File(hlsPath);

        if (!videoFolder.exists()) {
            videoFolder.mkdir();
            System.out.println("Video folder created");
        } else {
            System.out.println("Video folder already exists");
        }

        if (!hlsFolder.exists()) {
            hlsFolder.mkdir();
            System.out.println("HLS folder created");
        } else {
            System.out.println("HLS folder already exists");
        }
    }
    @Async
    public void processVideoToHLS(VideoEntity videoEntity, Path videoPath) {
        try {
            String videoId = videoEntity.getId();
            Path hlsOutputDir = Paths.get(hlsPath, videoId);
            Files.createDirectories(hlsOutputDir);

            String[][] renditions = {
                    {"426x240", "400k", "240"},
                    {"640x360", "800k", "360"},
                    {"1280x720", "2800k", "720"},
                    {"1920x1080", "5000k", "1080"}
            };

            for (String[] rendition : renditions) {
                String resolution = rendition[0];
                String bitrate = rendition[1];
                String name = rendition[2];

                Path renditionDir = hlsOutputDir.resolve(name);
                Files.createDirectories(renditionDir);

                ProcessBuilder builder = new ProcessBuilder(
                        "ffmpeg",
                        "-i", videoPath.toString(),
                        "-vf", "scale=" + resolution,
                        "-c:a", "aac",
                        "-ar", "48000",
                        "-c:v", "h264",
                        "-profile:v", "main",
                        "-crf", "20",
                        "-sc_threshold", "0",
                        "-g", "48",
                        "-keyint_min", "48",
                        "-b:v", bitrate,
                        "-maxrate", bitrate,
                        "-bufsize", "1000k",
                        "-hls_time", "10",
                        "-hls_playlist_type", "vod",
                        "-f", "hls",
                        "-hls_segment_filename", renditionDir.resolve("file%03d.ts").toString(),
                        renditionDir.resolve("index.m3u8").toString()
                );

                builder.inheritIO();
                Process process = builder.start();
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    System.err.println("Failed to generate HLS for " + name + "p");
                } else {
                    System.out.println("Generated HLS for " + name + "p");
                }
            }

            // Create master playlist
            Path masterPlaylist = hlsOutputDir.resolve("master.m3u8");
            StringBuilder masterContent = new StringBuilder("#EXTM3U\n");

            for (String[] rendition : renditions) {
                String bandwidth = rendition[1].replace("k", "000");
                String resolution = rendition[0];
                String name = rendition[2];
                masterContent.append("#EXT-X-STREAM-INF:BANDWIDTH=")
                        .append(bandwidth)
                        .append(",RESOLUTION=")
                        .append(resolution)
                        .append("\n")
                        .append(name)
                        .append("/index.m3u8\n");
            }

            Files.write(masterPlaylist, masterContent.toString().getBytes());
            System.out.println("Master playlist created for video ID: " + videoId);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public VideoEntity save(VideoEntity videoEntity, MultipartFile file) {
        try {
            String filename = StringUtils.cleanPath(file.getOriginalFilename());
            Path videoPath = Paths.get(folderPath, filename);
            Files.copy(file.getInputStream(), videoPath, StandardCopyOption.REPLACE_EXISTING);

            videoEntity.setFilepath(videoPath.toString());
            VideoEntity saved = repository.save(videoEntity);
            processVideoToHLS(saved,videoPath);
            return saved;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    public VideoEntity getById(String videoId) {
        return repository.findById(videoId).orElse(null);
    }

    public VideoEntity getByTitle(String title) {
        return repository.findByTitle(title).orElse(null);
    }

    public List<VideoEntity> getAll() {
        return repository.findAll();
    }
}
