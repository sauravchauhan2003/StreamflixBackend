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

    Optional<VideoEntity> findByTitle(String title);

    /** All videos by a specific uploader */
    List<VideoEntity> findAllByUploader(String uploader);

    /** Search by title OR uploader (case-insensitive) */
    List<VideoEntity> findByTitleContainingIgnoreCaseOrUploaderContainingIgnoreCase(
            String title, String uploader);

    /** Paginated feed sorted by views (most popular first) */
    @Query(value = "SELECT * FROM video_entity ORDER BY views DESC LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<VideoEntity> getPaginatedVideos(@Param("limit") int limit, @Param("offset") int offset);

    /** Increment view count atomically */
    @Transactional
    @Modifying
    @Query("UPDATE VideoEntity v SET v.views = v.views + 1 WHERE v.id = :id")
    void incrementViewCount(@Param("id") String id);

    /** Sum all views across all videos */
    @Query("SELECT COALESCE(SUM(v.views), 0) FROM VideoEntity v")
    Long getTotalViews();

    /** Total number of videos */
    @Query("SELECT COUNT(v) FROM VideoEntity v")
    Long getTotalVideoCount();
}
