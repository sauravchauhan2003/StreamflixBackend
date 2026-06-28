package com.example.VideoService;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Paths;

@Component
public class DbSyncScheduler {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private void performBackupAndSync() {
        try {
            String backupPath = "./data/videodb_backup.zip";
            // Create a safe H2 snapshot backup
            jdbcTemplate.execute("BACKUP TO '" + backupPath + "'");
            // Upload the zip
            DbBlobSyncManager.syncUp(backupPath, "videodb.zip");
            // Clean up the local backup zip
            Files.deleteIfExists(Paths.get(backupPath));
        } catch (Exception e) {
            System.err.println("Error creating/uploading DB backup: " + e.getMessage());
        }
    }

    // Sync up every 5 minutes (300000 ms)
    @Scheduled(fixedRate = 300000)
    public void periodicSync() {
        performBackupAndSync();
    }

    // Sync up on graceful shutdown
    @PreDestroy
    public void onShutdown() {
        System.out.println("Graceful shutdown detected. Syncing DB backup to Azure Blob Storage...");
        performBackupAndSync();
    }
}
