package com.example.VideoService.Controller;

import com.example.VideoService.Model.VideoEntity;
import com.example.VideoService.Model.VideoInteraction;
import com.example.VideoService.Model.VideoInteractionRepository;
import com.example.VideoService.Model.VideoRepository;
import com.example.VideoService.Model.VideoComment;
import com.example.VideoService.Model.VideoCommentRepository;
import com.example.VideoService.Model.CommentInteraction;
import com.example.VideoService.Model.CommentInteractionRepository;
import com.example.VideoService.Service.AzureBlobService;
import com.example.VideoService.Service.FeignClientInterface;
import com.example.VideoService.Service.VideoProcessing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@RestController
public class VideoController {

    @Autowired private FeignClientInterface feignClientInterface;
    @Autowired private VideoProcessing videoProcessing;
    @Autowired private AzureBlobService azureBlobService;
    @Autowired private VideoRepository repository;
    @Autowired private VideoInteractionRepository interactionRepo;
    @Autowired private VideoCommentRepository commentRepo;
    @Autowired private CommentInteractionRepository commentInteractionRepo;

    @Value("${files.hls-videos}")  private String hlsPath;
    @Value("${files.thumbnails}")  private String thumbnailsPath;

    // ── Upload Video ──────────────────────────────────────────────────────────
    private static final long MAX_VIDEO_SIZE     = 100L * 1024 * 1024; // 100 MB
    private static final long MAX_THUMBNAIL_SIZE =  10L * 1024 * 1024; //  10 MB

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> upload(
            @RequestParam("file")                       MultipartFile file,
            @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail,
            @RequestHeader("title")                     String title,
            @RequestHeader("desc")                      String desc,
            @RequestHeader(value = "category", defaultValue = "all") String category,
            @RequestHeader("Authorization")             String token) {

        if (file.isEmpty() || title.isBlank() || desc.isBlank()) {
            return ResponseEntity.badRequest().body("Missing required fields");
        }

        // ── Strict file size validation ──────────────────────────────────────
        if (file.getSize() > MAX_VIDEO_SIZE) {
            return ResponseEntity.status(413)
                    .body("Video file too large. Maximum allowed size is 100 MB.");
        }
        if (thumbnail != null && !thumbnail.isEmpty() && thumbnail.getSize() > MAX_THUMBNAIL_SIZE) {
            return ResponseEntity.status(413)
                    .body("Thumbnail file too large. Maximum allowed size is 10 MB.");
        }

        String uploader = feignClientInterface.extractUsername(token);

        VideoEntity video = new VideoEntity();
        video.setId(UUID.randomUUID().toString());
        video.setUploader(uploader);
        video.setTitle(title);
        video.setDesc(desc);
        video.setCategory("all".equalsIgnoreCase(category) ? null : category);
        video.setViews(0);

        videoProcessing.save(video, file, thumbnail);
        return ResponseEntity.ok("Video uploaded successfully. HLS processing started in background.");
    }

    // ── Serve Thumbnail ───────────────────────────────────────────────────────
    @GetMapping("/thumbnails/{videoId}")
    public ResponseEntity<Resource> getThumbnail(@PathVariable String videoId) throws IOException {
        String blobName = "thumbnails/" + videoId + ".jpg";
        if (azureBlobService.isEnabled() && azureBlobService.exists(blobName)) {
            return serveBlob(blobName, "image/jpeg");
        }
        Path path = Paths.get(thumbnailsPath, videoId + ".jpg");
        if (!Files.exists(path)) return ResponseEntity.notFound().build();
        return serveFile(path, "image/jpeg");
    }

    // ── HLS: Master Playlist ──────────────────────────────────────────────────
    @GetMapping("/{videoId}/master.m3u8")
    public ResponseEntity<Resource> getMasterPlaylist(@PathVariable String videoId) throws IOException {
        repository.incrementViewCount(videoId);
        String blobName = "hls-videos/" + videoId + "/master.m3u8";
        if (azureBlobService.isEnabled() && azureBlobService.exists(blobName)) {
            return serveBlob(blobName, "application/vnd.apple.mpegurl");
        }
        Path path = Paths.get(hlsPath, videoId, "master.m3u8");
        return serveFile(path, "application/vnd.apple.mpegurl");
    }

    // ── HLS: Quality Playlist ─────────────────────────────────────────────────
    @GetMapping("/{videoId}/{quality}/index.m3u8")
    public ResponseEntity<Resource> getVariantPlaylist(
            @PathVariable String videoId, @PathVariable String quality) throws IOException {
        String blobName = "hls-videos/" + videoId + "/" + quality + "/index.m3u8";
        if (azureBlobService.isEnabled() && azureBlobService.exists(blobName)) {
            return serveBlob(blobName, "application/vnd.apple.mpegurl");
        }
        Path path = Paths.get(hlsPath, videoId, quality, "index.m3u8");
        return serveFile(path, "application/vnd.apple.mpegurl");
    }

    // ── HLS: Segments ─────────────────────────────────────────────────────────
    @GetMapping("/{videoId}/{quality}/{segment}")
    public ResponseEntity<Resource> getSegment(
            @PathVariable String videoId,
            @PathVariable String quality,
            @PathVariable String segment) throws IOException {
        String blobName = "hls-videos/" + videoId + "/" + quality + "/" + segment;
        if (azureBlobService.isEnabled() && azureBlobService.exists(blobName)) {
            return serveBlob(blobName, "video/MP2T");
        }
        Path path = Paths.get(hlsPath, videoId, quality, segment);
        return serveFile(path, "video/MP2T");
    }

    // ── Get Video List (paginated, sorted by views) ───────────────────────────
    @GetMapping("/videos")
    public ResponseEntity<List<VideoEntity>> getVideos(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "all") String category) {
        if (size > 20) size = 20;
        if ("all".equalsIgnoreCase(category)) {
            return ResponseEntity.ok(repository.getPaginatedVideos(size, page * size));
        } else {
            return ResponseEntity.ok(repository.getPaginatedVideosByCategory(category, size, page * size));
        }
    }

    // ── Get Single Video by ID ────────────────────────────────────────────────
    @GetMapping("/videos/{id}")
    public ResponseEntity<VideoEntity> getVideo(@PathVariable String id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Search Videos ─────────────────────────────────────────────────────────
    @GetMapping("/videos/search")
    public ResponseEntity<List<VideoEntity>> search(@RequestParam String q) {
        return ResponseEntity.ok(
                repository.findByTitleContainingIgnoreCaseOrUploaderContainingIgnoreCase(q, q));
    }

    // ── My Uploaded Videos (requires auth) ────────────────────────────────────
    @GetMapping("/my-videos")
    public ResponseEntity<List<VideoEntity>> getMyVideos(
            @RequestHeader("Authorization") String token) {
        String username = feignClientInterface.extractUsername(token);
        return ResponseEntity.ok(repository.findAllByUploader(username));
    }

    // ── Toggle Like ───────────────────────────────────────────────────────────
    @PostMapping("/videos/{id}/like")
    public ResponseEntity<Map<String, Object>> toggleLike(
            @PathVariable String id,
            @RequestHeader("Authorization") String token) {
        return toggleInteraction(id, token, VideoInteraction.InteractionType.LIKE);
    }

    // ── Toggle Dislike ────────────────────────────────────────────────────────
    @PostMapping("/videos/{id}/dislike")
    public ResponseEntity<Map<String, Object>> toggleDislike(
            @PathVariable String id,
            @RequestHeader("Authorization") String token) {
        return toggleInteraction(id, token, VideoInteraction.InteractionType.DISLIKE);
    }

    private ResponseEntity<Map<String, Object>> toggleInteraction(
            String videoId, String token, VideoInteraction.InteractionType newType) {

        String username = feignClientInterface.extractUsername(token);
        Optional<VideoInteraction> existing = interactionRepo.findByUsernameAndVideoId(username, videoId);

        String action;
        if (existing.isPresent()) {
            VideoInteraction interaction = existing.get();
            if (interaction.getType() == newType) {
                // Same type → remove (toggle off)
                interactionRepo.delete(interaction);
                action = "removed";
            } else {
                // Different type → switch
                interaction.setType(newType);
                interactionRepo.save(interaction);
                action = "changed_to_" + newType.name().toLowerCase();
            }
        } else {
            VideoInteraction interaction = new VideoInteraction();
            interaction.setUsername(username);
            interaction.setVideoId(videoId);
            interaction.setType(newType);
            interaction.setCreatedAt(LocalDateTime.now());
            interactionRepo.save(interaction);
            action = newType.name().toLowerCase() + "d";
        }

        Map<String, Object> response = new HashMap<>();
        response.put("action", action);
        response.put("likes",    interactionRepo.countByVideoIdAndType(videoId, VideoInteraction.InteractionType.LIKE));
        response.put("dislikes", interactionRepo.countByVideoIdAndType(videoId, VideoInteraction.InteractionType.DISLIKE));
        return ResponseEntity.ok(response);
    }

    // ── Get User's Current Interaction on a Video ─────────────────────────────
    @GetMapping("/videos/{id}/interaction")
    public ResponseEntity<Map<String, String>> getMyInteraction(
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String token) {

        if (token == null) {
            return ResponseEntity.ok(Map.of("type", "NONE"));
        }
        String username = feignClientInterface.extractUsername(token);
        String type = interactionRepo.findByUsernameAndVideoId(username, id)
                .map(i -> i.getType().name())
                .orElse("NONE");
        return ResponseEntity.ok(Map.of("type", type));
    }

    // ── Analytics: Single Video ───────────────────────────────────────────────
    @GetMapping("/videos/{id}/analytics")
    public ResponseEntity<Map<String, Object>> getVideoAnalytics(@PathVariable String id) {
        return repository.findById(id).map(video -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("videoId",    video.getId());
            data.put("title",      video.getTitle());
            data.put("uploader",   video.getUploader());
            data.put("views",      video.getViews());
            data.put("likes",      interactionRepo.countByVideoIdAndType(id, VideoInteraction.InteractionType.LIKE));
            data.put("dislikes",   interactionRepo.countByVideoIdAndType(id, VideoInteraction.InteractionType.DISLIKE));
            data.put("uploadedAt", video.getUploadedAt() != null ? video.getUploadedAt().toString() : "N/A");
            return ResponseEntity.ok(data);
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Analytics: Top Videos ─────────────────────────────────────────────────
    @GetMapping("/analytics/top")
    public ResponseEntity<List<Map<String, Object>>> getTopAnalytics(
            @RequestParam(defaultValue = "10") int limit) {

        List<VideoEntity> videos = repository.getPaginatedVideos(Math.min(limit, 50), 0);
        List<Map<String, Object>> result = new ArrayList<>();

        for (VideoEntity v : videos) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("videoId",    v.getId());
            item.put("title",      v.getTitle());
            item.put("uploader",   v.getUploader());
            item.put("views",      v.getViews());
            item.put("likes",      interactionRepo.countByVideoIdAndType(v.getId(), VideoInteraction.InteractionType.LIKE));
            item.put("dislikes",   interactionRepo.countByVideoIdAndType(v.getId(), VideoInteraction.InteractionType.DISLIKE));
            item.put("uploadedAt", v.getUploadedAt() != null ? v.getUploadedAt().toString() : "N/A");
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    // ── Analytics: Platform Summary ───────────────────────────────────────────
    @GetMapping("/analytics/summary")
    public ResponseEntity<Map<String, Object>> getAnalyticsSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalVideos",   repository.getTotalVideoCount());
        summary.put("totalViews",    repository.getTotalViews());
        summary.put("totalLikes",    interactionRepo.countByType(VideoInteraction.InteractionType.LIKE));
        summary.put("totalDislikes", interactionRepo.countByType(VideoInteraction.InteractionType.DISLIKE));
        return ResponseEntity.ok(summary);
    }

    // ── Comments ──────────────────────────────────────────────────────────────
    @GetMapping("/videos/{id}/comments")
    public ResponseEntity<List<VideoComment>> getComments(
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String token) {

        String currentUsername = null;
        if (token != null && !token.isBlank()) {
            try {
                currentUsername = feignClientInterface.extractUsername(token);
            } catch (Exception e) {
                // Invalid token
            }
        }

        List<VideoComment> comments = commentRepo.findByVideoIdOrderByCreatedAtDesc(id);
        for (VideoComment c : comments) {
            c.setLikes(commentInteractionRepo.countByCommentIdAndType(c.getId(), VideoInteraction.InteractionType.LIKE));
            c.setDislikes(commentInteractionRepo.countByCommentIdAndType(c.getId(), VideoInteraction.InteractionType.DISLIKE));
            
            if (currentUsername != null) {
                String type = commentInteractionRepo.findByUsernameAndCommentId(currentUsername, c.getId())
                        .map(i -> i.getType().name())
                        .orElse("NONE");
                c.setUserInteraction(type);
            }
        }
        return ResponseEntity.ok(comments);
    }

    @PostMapping("/videos/{id}/comments")
    public ResponseEntity<VideoComment> addComment(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String token) {

        String text = body.get("text");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String username = feignClientInterface.extractUsername(token);

        VideoComment comment = new VideoComment();
        comment.setId(UUID.randomUUID().toString());
        comment.setVideoId(id);
        comment.setUsername(username);
        comment.setText(text);
        comment.setCreatedAt(LocalDateTime.now());

        commentRepo.save(comment);

        comment.setLikes(0);
        comment.setDislikes(0);
        comment.setUserInteraction("NONE");

        return ResponseEntity.ok(comment);
    }

    @PostMapping("/comments/{id}/like")
    public ResponseEntity<Map<String, Object>> toggleCommentLike(
            @PathVariable String id,
            @RequestHeader("Authorization") String token) {
        return toggleCommentInteraction(id, token, VideoInteraction.InteractionType.LIKE);
    }

    @PostMapping("/comments/{id}/dislike")
    public ResponseEntity<Map<String, Object>> toggleCommentDislike(
            @PathVariable String id,
            @RequestHeader("Authorization") String token) {
        return toggleCommentInteraction(id, token, VideoInteraction.InteractionType.DISLIKE);
    }

    private ResponseEntity<Map<String, Object>> toggleCommentInteraction(
            String commentId, String token, VideoInteraction.InteractionType newType) {

        String username = feignClientInterface.extractUsername(token);
        Optional<CommentInteraction> existing = commentInteractionRepo.findByUsernameAndCommentId(username, commentId);

        String action;
        if (existing.isPresent()) {
            CommentInteraction interaction = existing.get();
            if (interaction.getType() == newType) {
                commentInteractionRepo.delete(interaction);
                action = "removed";
            } else {
                interaction.setType(newType);
                commentInteractionRepo.save(interaction);
                action = "changed_to_" + newType.name().toLowerCase();
            }
        } else {
            CommentInteraction interaction = new CommentInteraction();
            interaction.setUsername(username);
            interaction.setCommentId(commentId);
            interaction.setType(newType);
            interaction.setCreatedAt(LocalDateTime.now());
            commentInteractionRepo.save(interaction);
            action = newType.name().toLowerCase() + "d";
        }

        Map<String, Object> response = new HashMap<>();
        response.put("action", action);
        response.put("likes",    commentInteractionRepo.countByCommentIdAndType(commentId, VideoInteraction.InteractionType.LIKE));
        response.put("dislikes", commentInteractionRepo.countByCommentIdAndType(commentId, VideoInteraction.InteractionType.DISLIKE));
        return ResponseEntity.ok(response);
    }

    // ── Private helper ────────────────────────────────────────────────────────
    private ResponseEntity<Resource> serveFile(Path path, String contentType) throws IOException {
        if (!Files.exists(path)) return ResponseEntity.notFound().build();
        Resource resource = new UrlResource(path.toUri());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(resource);
    }

    private ResponseEntity<Resource> serveBlob(String blobName, String contentType) {
        InputStream stream = azureBlobService.downloadStream(blobName);
        if (stream == null) return ResponseEntity.notFound().build();
        Resource resource = new InputStreamResource(stream);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(resource);
    }
}
