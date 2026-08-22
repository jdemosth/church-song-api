package com.churchsong.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class DatabaseBackupService {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    DatabaseBackupService.class);

    private static final DateTimeFormatter FILE_NAME_FORMAT =
            DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd-HHmm");

    private final JdbcTemplate jdbcTemplate;
    private final String datasourceUrl;

    public DatabaseBackupService(
            JdbcTemplate jdbcTemplate,
            @Value("${spring.datasource.url}")
            String datasourceUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.datasourceUrl = datasourceUrl;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DatabaseBackupResult createBackup() {
        validateSqliteDatasource();

        String fileName =
                "churchsongs-backup-"
                        + LocalDateTime.now()
                        .format(FILE_NAME_FORMAT)
                        + ".db";

        Path backupPath = null;

        try {
            backupPath = Files.createTempFile(
                    "churchsongs-backup-",
                    ".db");
            Files.deleteIfExists(backupPath);

            String escapedBackupPath =
                    backupPath.toAbsolutePath()
                            .toString()
                            .replace("'", "''");

            jdbcTemplate.execute(
                    "VACUUM INTO '"
                            + escapedBackupPath
                            + "'");

            if (!Files.exists(backupPath)) {
                throw new IllegalStateException(
                        "Backup file was not created."
                );
            }

            long backupSize =
                    Files.size(backupPath);

            if (backupSize <= 0) {
                throw new IllegalStateException(
                        "Backup file is empty."
                );
            }

            byte[] bytes =
                    Files.readAllBytes(backupPath);

            return new DatabaseBackupResult(
                    fileName,
                    bytes);
        } catch (IOException exception) {
            logger.error(
                    "Could not create database backup.",
                    exception);
            throw new IllegalStateException(
                    "Could not create database backup."
            );
        } catch (RuntimeException exception) {
            logger.error(
                    "Could not create database backup.",
                    exception);
            throw exception;
        } finally {
            deleteTempFileQuietly(backupPath);
        }
    }

    private void validateSqliteDatasource() {
        if (datasourceUrl == null
                || !datasourceUrl.startsWith(
                "jdbc:sqlite:")) {
            throw new IllegalStateException(
                    "Database backup is only supported for SQLite datasources."
            );
        }
    }

    private void deleteTempFileQuietly(
            Path backupPath) {
        if (backupPath == null) {
            return;
        }

        try {
            Files.deleteIfExists(backupPath);
        } catch (IOException exception) {
            logger.warn(
                    "Could not delete temporary backup file: {}",
                    backupPath,
                    exception);
        }
    }

    public record DatabaseBackupResult(
            String fileName,
            byte[] bytes) {
    }
}
