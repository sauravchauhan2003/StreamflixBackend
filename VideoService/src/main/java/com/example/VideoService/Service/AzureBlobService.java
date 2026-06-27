package com.example.VideoService.Service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Path;

@Service
public class AzureBlobService {

    @Value("${azure.storage.connection-string}")
    private String connectionString;

    @Value("${azure.storage.video-container-name}")
    private String containerName;

    private BlobContainerClient containerClient;
    private boolean isEnabled = false;

    @PostConstruct
    public void init() {
        if (connectionString != null && !connectionString.trim().isEmpty() &&
            containerName != null && !containerName.trim().isEmpty()) {
            
            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();
                    
            containerClient = blobServiceClient.getBlobContainerClient(containerName);
            if (!containerClient.exists()) {
                containerClient.create();
            }
            isEnabled = true;
        }
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void uploadFile(String blobName, Path localFilePath) {
        if (!isEnabled) return;
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        blobClient.uploadFromFile(localFilePath.toString(), true);
    }

    public InputStream downloadStream(String blobName) {
        if (!isEnabled) return null;
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        if (blobClient.exists()) {
            return blobClient.openInputStream();
        }
        return null;
    }

    public boolean exists(String blobName) {
        if (!isEnabled) return false;
        return containerClient.getBlobClient(blobName).exists();
    }
}
