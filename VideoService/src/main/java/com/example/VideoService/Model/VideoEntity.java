package com.example.VideoService.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

@Entity
public class VideoEntity {

    @Id
    private String id;

    private String uploader;
    private String title;
    private String desc;
    private int views;
    private LocalDateTime uploadedAt;

    /** Internal filesystem path — not exposed in API responses */
    @JsonIgnore
    private String filepath;

    /** Internal thumbnail path — not exposed; served via /thumbnails/{id} */
    @JsonIgnore
    private String thumbnailPath;

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public String getId()                    { return id; }
    public void   setId(String id)           { this.id = id; }

    public String getUploader()              { return uploader; }
    public void   setUploader(String u)      { this.uploader = u; }

    public String getTitle()                 { return title; }
    public void   setTitle(String t)         { this.title = t; }

    public String getDesc()                  { return desc; }
    public void   setDesc(String d)          { this.desc = d; }

    public int    getViews()                 { return views; }
    public void   setViews(int v)            { this.views = v; }

    public LocalDateTime getUploadedAt()     { return uploadedAt; }
    public void setUploadedAt(LocalDateTime u) { this.uploadedAt = u; }

    public String getFilepath()              { return filepath; }
    public void   setFilepath(String f)      { this.filepath = f; }

    public String getThumbnailPath()         { return thumbnailPath; }
    public void   setThumbnailPath(String t) { this.thumbnailPath = t; }
}
