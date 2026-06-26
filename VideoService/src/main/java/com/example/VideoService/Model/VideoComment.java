package com.example.VideoService.Model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;

import java.time.LocalDateTime;

@Entity
public class VideoComment {

    @Id
    private String id;

    private String videoId;
    private String username;
    private String text;
    private LocalDateTime createdAt;

    @Transient
    private long likes;

    @Transient
    private long dislikes;

    @Transient
    private String userInteraction = "NONE";

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public long getLikes() { return likes; }
    public void setLikes(long likes) { this.likes = likes; }

    public long getDislikes() { return dislikes; }
    public void setDislikes(long dislikes) { this.dislikes = dislikes; }

    public String getUserInteraction() { return userInteraction; }
    public void setUserInteraction(String userInteraction) { this.userInteraction = userInteraction; }
}
