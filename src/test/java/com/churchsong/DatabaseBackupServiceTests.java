package com.churchsong;

import com.churchsong.service.DatabaseBackupService;
import com.churchsong.service.SongLibrary;
import com.churchsong.model.Song;
import com.churchsong.model.SongType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseBackupServiceTests {

    @Test
    void createsReadableSqliteBackup(
            @TempDir Path tempDir) throws Exception {
        Path databaseFile =
                tempDir.resolve(
                        "backup-source.db");
        Path restoredBackupFile =
                tempDir.resolve(
                        "backup-copy.db");
        final byte[][] backupBytesHolder =
                new byte[1][];

        withContext(
                databaseFile,
                context -> {
                    SongLibrary songLibrary =
                            context.getBean(
                                    SongLibrary.class);

                    Song song = new Song(
                            "Backup Song",
                            "Backup Author",
                            "[Verse 1]\nLine 1",
                            SongType.SLOW);
                    songLibrary.addSong(song);
                });

        withContext(
                databaseFile,
                context -> {
                    DatabaseBackupService backupService =
                            context.getBean(
                                    DatabaseBackupService.class);

                    DatabaseBackupService.DatabaseBackupResult
                            backupResult =
                            backupService.createBackup();

                    assertNotNull(
                            backupResult);
                    assertTrue(
                            backupResult.fileName()
                                    .startsWith(
                                            "churchsongs-backup-"));
                    assertTrue(
                            backupResult.fileName()
                                    .endsWith(
                                            ".db"));
                    assertTrue(
                            backupResult.bytes()
                                    .length
                                    > 0);

                    backupBytesHolder[0] =
                            backupResult.bytes();
                });

        Files.write(
                restoredBackupFile,
                backupBytesHolder[0]);

        try (Connection connection =
                     DriverManager.getConnection(
                             "jdbc:sqlite:"
                                     + restoredBackupFile);
             Statement statement =
                     connection.createStatement();
             ResultSet resultSet =
                     statement.executeQuery(
                             "select count(*) from song where title = 'Backup Song'")) {
            assertTrue(resultSet.next());
            assertEquals(
                    1,
                    resultSet.getInt(1));
        }
    }

    private void withContext(
            Path databaseFile,
            Consumer<ConfigurableApplicationContext> testBody) {
        try (ConfigurableApplicationContext context =
                     new SpringApplicationBuilder(
                             ChurchSongApiApplication.class)
                             .run(
                                     "--spring.profiles.active=test",
                                     "--spring.datasource.url=jdbc:sqlite:" + databaseFile,
                                     "--spring.jpa.hibernate.ddl-auto=update",
                                     "--spring.sql.init.mode=always",
                                     "--spring.main.banner-mode=off",
                                     "--spring.main.web-application-type=none")) {
            new TransactionTemplate(
                    context.getBean(
                            PlatformTransactionManager.class))
                    .executeWithoutResult(
                            status -> testBody.accept(context));
        }
    }

}
