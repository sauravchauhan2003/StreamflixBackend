package com.example.VideoService.Model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Represents a user's like or dislike on a video.
 * Unique constraint ensures one interaction per (user, video) pair.
 * Toggling works by checking existing interaction type.
 */
@Entity
@Table(name = "video_interaction",
        uniqueConstraints = @UniqueConstraint(columnNames = {"username", "video_id"}))
public class VideoInteraction {

    public enum InteractionType { LIKE, DISLIKE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    @Column(name = "video_id")
    private String videoId;

    @Enumerated(EnumType.STRING)
    private InteractionType type;

    private LocalDateTime createdAt;

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public Long getId()                           { return id; }
    public void setId(Long id)                    { this.id = id; }

    public String getUsername()                   { return username; }
    public void   setUsername(String u)           { this.username = u; }

    public String getVideoId()                    { return videoId; }
    public void   setVideoId(String v)            { this.videoId = v; }

    public InteractionType getType()              { return type; }
    public void setType(InteractionType t)        { this.type = t; }

    public LocalDateTime getCreatedAt()           { return createdAt; }
    public void setCreatedAt(LocalDateTime c)     { this.createdAt = c; }
}
