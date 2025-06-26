package com.example.VideoService.Model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.lang.ScopedValue;
import java.util.Optional;

@Repository
public interface VideoRepository extends JpaRepository<VideoEntity,String> {
    Optional<VideoEntity> findById(String id);
    Optional<VideoEntity> findByUploader(String uploader);
    Optional<VideoEntity> findByTitle(String title);
}
