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
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class VideoProcessing {

    @Value("${files.videos}")
    private String folderPath;

    @Value("${files.hls-videos}")
    private String hlsPath;

    @Value("${files.thumbnails}")
    private String thumbnailsPath;

    @Autowired
    private VideoRepository repository;

    @Autowired
    private AzureBlobService azureBlobService;

    @PostConstruct
    public void init() {
        createDir(folderPath);
        createDir(hlsPath);
        createDir(thumbnailsPath);
    }

    private void createDir(String path) {
        File dir = new File(path);
        if (!dir.exists()) dir.mkdirs();
    }

    public VideoEntity save(VideoEntity videoEntity, MultipartFile file, MultipartFile thumbnail) {
        try {
            String filename = StringUtils.cleanPath(file.getOriginalFilename());
            Path videoPath = Paths.get(folderPath, filename);
            Files.copy(file.getInputStream(), videoPath, StandardCopyOption.REPLACE_EXISTING);

            videoEntity.setFilepath(videoPath.toString());
            videoEntity.setUploadedAt(LocalDateTime.now());

            VideoEntity saved = repository.save(videoEntity);

            String thumbPath = saveThumbnail(saved.getId(), thumbnail, videoPath);
            saved.setThumbnailPath(thumbPath);
            repository.save(saved);

            processVideoToHLS(saved, videoPath);
            return saved;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String saveThumbnail(String videoId, MultipartFile thumbnailFile, Path videoPath) {
        try {
            Path thumbPath = Paths.get(thumbnailsPath, videoId + ".jpg");

            if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
                Files.copy(thumbnailFile.getInputStream(), thumbPath, StandardCopyOption.REPLACE_EXISTING);
            } else {
                ProcessBuilder pb = new ProcessBuilder(
                        "ffmpeg",
                        "-y",
                        "-ss", "00:00:05",
                        "-i", videoPath.toString(),
                        "-frames:v", "1",
                        "-q:v", "2",
                        thumbPath.toString()
                );
                pb.inheritIO();
                pb.start().waitFor();
            }

            return thumbPath.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Async
    public void processVideoToHLS(VideoEntity videoEntity, Path videoPath) {
        try {
            String videoId = videoEntity.getId();
            Path hlsOutputDir = Paths.get(hlsPath, videoId);
            Files.createDirectories(hlsOutputDir);

            String[][] renditions = {
                    {"426", "240", "400k", "240"},
                    {"640", "360", "800k", "360"},
                    {"1280", "720", "2800k", "720"},
                    {"1920", "1080", "5000k", "1080"}
            };

            for (String[] r : renditions) {
                String w = r[0];
                String h = r[1];
                String bitrate = r[2];
                String name = r[3];

                Path dir = hlsOutputDir.resolve(name);
                Files.createDirectories(dir);

                boolean ok = transcodeGPU(videoPath, dir, w, h, bitrate, name, videoId);

                if (!ok) {
                    System.out.println("Falling back to CPU for " + name + "p");
                    transcodeCPU(videoPath, dir, w, h, bitrate, name, videoId);
                }
            }

            // Master playlist
            Path master = hlsOutputDir.resolve("master.m3u8");
            StringBuilder sb = new StringBuilder("#EXTM3U\n");

            for (String[] r : renditions) {
                sb.append("#EXT-X-STREAM-INF:BANDWIDTH=")
                        .append(r[2].replace("k", "000"))
                        .append(",RESOLUTION=")
                        .append(r[0]).append("x").append(r[1]).append("\n")
                        .append(r[3]).append("/index.m3u8\n");
            }

            Files.write(master, sb.toString().getBytes());

            // --- Upload to Azure Blob Storage and cleanup ---
            if (azureBlobService.isEnabled()) {
                System.out.println("Uploading processed video data to Azure Blob Storage...");
                
                // Upload original video
                azureBlobService.uploadFile("videos/" + videoPath.getFileName().toString(), videoPath);
                
                // Upload thumbnail
                Path thumbPath = Paths.get(thumbnailsPath, videoId + ".jpg");
                azureBlobService.uploadFile("thumbnails/" + videoId + ".jpg", thumbPath);

                // Upload master playlist
                azureBlobService.uploadFile("hls-videos/" + videoId + "/master.m3u8", master);

                // Upload segments and playlists
                for (String[] r : renditions) {
                    String name = r[3];
                    Path dir = hlsOutputDir.resolve(name);
                    try (java.util.stream.Stream<Path> paths = Files.list(dir)) {
                        paths.forEach(p -> {
                            String blobName = "hls-videos/" + videoId + "/" + name + "/" + p.getFileName().toString();
                            azureBlobService.uploadFile(blobName, p);
                        });
                    }
                }
                
                System.out.println("Upload complete. Deleting local files...");
                Files.deleteIfExists(videoPath);
                Files.deleteIfExists(thumbPath);
                
                // Delete HLS dir recursively
                try (java.util.stream.Stream<Path> walk = Files.walk(hlsOutputDir)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean transcodeGPU(Path input, Path outDir,
                                 String w, String h, String bitrate,
                                 String name, String videoId)
            throws IOException, InterruptedException {

        System.out.println("[GPU] " + name + "p start");

        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-hwaccel", "cuda",
                "-hwaccel_output_format", "cuda",
                "-i", input.toString(),

                "-map", "0:v:0",
                "-map", "0:a?",

                "-vf", "scale_cuda=w=" + w + ":h=" + h,

                "-c:v", "h264_nvenc",
                "-preset", "p4",
                "-rc", "vbr_hq",
                "-cq", "20",
                "-b:v", bitrate,
                "-maxrate", bitrate,
                "-bufsize", "1000k",

                "-g", "48",
                "-keyint_min", "48",

                "-c:a", "aac",
                "-b:a", "128k",

                "-f", "hls",
                "-hls_time", "10",
                "-hls_playlist_type", "vod",
                "-hls_segment_filename", outDir.resolve("file%03d.ts").toString(),

                outDir.resolve("index.m3u8").toString()
        );

        pb.inheritIO();
        int code = pb.start().waitFor();

        return code == 0;
    }

    private void transcodeCPU(Path input, Path outDir,
                              String w, String h, String bitrate,
                              String name, String videoId)
            throws IOException, InterruptedException {

        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-i", input.toString(),

                "-map", "0:v:0",
                "-map", "0:a?",

                "-vf", "scale=" + w + ":" + h,

                "-c:v", "libx264",
                "-preset", "veryfast",
                "-crf", "20",
                "-b:v", bitrate,

                "-c:a", "aac",
                "-b:a", "128k",

                "-f", "hls",
                "-hls_time", "10",
                "-hls_playlist_type", "vod",
                "-hls_segment_filename", outDir.resolve("file%03d.ts").toString(),

                outDir.resolve("index.m3u8").toString()
        );

        pb.inheritIO();
        pb.start().waitFor();
    }

    public VideoEntity getById(String id) {
        return repository.findById(id).orElse(null);
    }

    public List<VideoEntity> getAll() {
        return repository.findAll();
    }
}