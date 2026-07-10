package com.example.tripexpensemanager;

import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import java.io.InputStream;
import java.util.Collections;
import com.google.api.services.drive.model.FileList;


public class GoogleDriveService {

    private final Drive driveService;

    public GoogleDriveService(Drive driveService) {
        this.driveService = driveService;
    }

    public String uploadDatabase(InputStream dbInputStream, String fileName) throws Exception {
        File fileMetadata = new File();
        fileMetadata.setName(fileName);
        fileMetadata.setParents(Collections.singletonList("root"));

        InputStreamContent mediaContent = new InputStreamContent("application/x-sqlite3", dbInputStream);

        File file = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute();

        return file.getId();
    }
    public String getLatestBackupFileId(String fileName) throws Exception {
        // Search Drive for files created by your app with this exact name
        FileList result = driveService.files().list()
                .setQ("name='" + fileName + "' and trashed=false")
                .setSpaces("drive")
                .setOrderBy("createdTime desc") // Get the newest one first
                .setFields("files(id, name)")
                .execute();

        if (result.getFiles() != null && !result.getFiles().isEmpty()) {
            return result.getFiles().get(0).getId();
        }
        return null; // No backup found
    }

    public void downloadFile(String fileId, java.io.OutputStream outputStream) throws Exception {
        // Download the file data directly into the provided output stream
        driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream);
    }

}