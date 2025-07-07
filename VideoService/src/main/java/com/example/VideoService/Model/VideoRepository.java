package com.example.VideoService.Model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface VideoRepository extends JpaRepository<VideoEntity, String> {
    Optional<VideoEntity> findById(String id);
    Optional<VideoEntity> findByUploader(String uploader);
    Optional<VideoEntity> findByTitle(String title);

    @Transactional
    @Modifying
    @Query("UPDATE VideoEntity v SET v.views = v.views + 1 WHERE v.id = :id")
    void incrementViewCount(String id);

    @Query(value = "SELECT * FROM video_entity ORDER BY views DESC LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<VideoEntity> getPaginatedVideos(@Param("limit") int limit, @Param("offset") int offset);

}
