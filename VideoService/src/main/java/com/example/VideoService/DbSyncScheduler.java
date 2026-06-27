package com.example.VideoService;

import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DbSyncScheduler {

    // Sync up every 5 minutes (300000 ms)
    @Scheduled(fixedRate = 300000)
    public void periodicSync() {
        DbBlobSyncManager.syncUp();
    }

    // Sync up on graceful shutdown
    @PreDestroy
    public void onShutdown() {
        System.out.println("Graceful shutdown detected. Syncing DB to Azure Blob Storage...");
        DbBlobSyncManager.syncUp();
    }
}
