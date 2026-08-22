package com.churchsong.controller;

import com.churchsong.service.DatabaseBackupService;
import com.churchsong.service.DatabaseBackupService.DatabaseBackupResult;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
public class AdminBackupController {

    private final DatabaseBackupService
            databaseBackupService;

    public AdminBackupController(
            DatabaseBackupService databaseBackupService) {
        this.databaseBackupService =
                databaseBackupService;
    }

    @GetMapping("/admin/backup/database")
    public ResponseEntity<ByteArrayResource>
    downloadDatabaseBackup() {
        DatabaseBackupResult backupResult =
                databaseBackupService.createBackup();

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\""
                                + backupResult.fileName()
                                + "\"")
                .contentType(
                        MediaType.parseMediaType(
                                "application/x-sqlite3"))
                .contentLength(
                        backupResult.bytes().length)
                .body(
                        new ByteArrayResource(
                                backupResult.bytes()));
    }
}
