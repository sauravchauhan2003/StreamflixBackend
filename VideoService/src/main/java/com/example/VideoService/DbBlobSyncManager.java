package com.example.VideoService;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DbBlobSyncManager {

    private static final String DATA_DIR = "./data";
    private static final String BLOB_FILE_NAME = "videodb.zip";
    private static final String LOCAL_ZIP_NAME = "videodb_backup.zip";

    private static String connectionString;
    private static String containerName;

    static {
        try {
            Properties props = new Properties();
            ClassPathResource resource = new ClassPathResource("application.properties");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    props.load(is);
                }
            } else {
                File file = new File("src/main/resources/application.properties");
                if (file.exists()) {
                    try (FileInputStream fis = new FileInputStream(file)) {
                        props.load(fis);
                    }
                }
            }
            connectionString = props.getProperty("azure.storage.connection-string");
            containerName = props.getProperty("azure.storage.db-container-name");
        } catch (Exception e) {
            System.err.println("Failed to load azure storage properties: " + e.getMessage());
        }
    }

    public static void syncDown() {
        if (connectionString == null || connectionString.trim().isEmpty() ||
            containerName == null || containerName.trim().isEmpty()) {
            System.out.println("Azure Blob Storage credentials not configured. Skipping DB download.");
            return;
        }

        try {
            File dir = new File(DATA_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();

            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
            if (!containerClient.exists()) {
                containerClient.create();
                System.out.println("Created container: " + containerName);
            }

            BlobClient blobClient = containerClient.getBlobClient(BLOB_FILE_NAME);
            Path localFilePath = Paths.get(DATA_DIR, LOCAL_ZIP_NAME);

            if (blobClient.exists()) {
                System.out.println("Downloading database backup from Azure Blob Storage...");
                blobClient.downloadToFile(localFilePath.toString(), true);
                System.out.println("Database backup downloaded successfully. Extracting...");
                
                unzip(localFilePath.toString(), DATA_DIR);
                Files.deleteIfExists(localFilePath); // cleanup zip
                System.out.println("Extraction complete.");
            } else {
                System.out.println("Database backup not found in Azure Blob Storage. Will be created on startup.");
            }

        } catch (Exception e) {
            System.err.println("Error syncing down database from Azure Blob Storage: " + e.getMessage());
        }
    }

    public static void syncUp(String localFilePath, String blobName) {
        if (connectionString == null || connectionString.trim().isEmpty() ||
            containerName == null || containerName.trim().isEmpty()) {
            return;
        }

        try {
            if (!Files.exists(Paths.get(localFilePath))) {
                return;
            }

            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();

            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
            if (!containerClient.exists()) {
                containerClient.create();
            }

            BlobClient blobClient = containerClient.getBlobClient(blobName);
            System.out.println("Uploading database backup to Azure Blob Storage...");
            blobClient.uploadFromFile(localFilePath, true);
            System.out.println("Database backup uploaded successfully.");

        } catch (Exception e) {
            System.err.println("Error syncing up database to Azure Blob Storage: " + e.getMessage());
        }
    }
    
    private static void unzip(String zipFilePath, String destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory()) {
                    String fileName = new File(entry.getName()).getName();
                    File newFile = new File(destDir, fileName);
                    Files.copy(zis, newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
                entry = zis.getNextEntry();
            }
        }
    }
}
