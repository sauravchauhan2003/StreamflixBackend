package com.example.VideoService.Model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommentInteractionRepository extends JpaRepository<CommentInteraction, Long> {
    Optional<CommentInteraction> findByUsernameAndCommentId(String username, String commentId);
    long countByCommentIdAndType(String commentId, VideoInteraction.InteractionType type);
}
