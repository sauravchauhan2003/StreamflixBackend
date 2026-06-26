package com.example.VideoService.Model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoCommentRepository extends JpaRepository<VideoComment, String> {
    List<VideoComment> findByVideoIdOrderByCreatedAtDesc(String videoId);
}
