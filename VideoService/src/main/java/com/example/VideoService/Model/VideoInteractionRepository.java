package com.example.VideoService.Model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VideoInteractionRepository extends JpaRepository<VideoInteraction, Long> {

    Optional<VideoInteraction> findByUsernameAndVideoId(String username, String videoId);

    long countByVideoIdAndType(String videoId, VideoInteraction.InteractionType type);

    /** Total likes or dislikes across all videos (for summary analytics) */
    @Query("SELECT COUNT(i) FROM VideoInteraction i WHERE i.type = :type")
    long countByType(@Param("type") VideoInteraction.InteractionType type);
}
