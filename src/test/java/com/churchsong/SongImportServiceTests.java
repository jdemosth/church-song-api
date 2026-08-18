package com.churchsong;

import com.churchsong.dto.importing.ImportReport;
import com.churchsong.model.Song;
import com.churchsong.model.SongFamily;
import com.churchsong.model.SongLanguage;
import com.churchsong.model.SongType;
import com.churchsong.repository.SongFamilyRepository;
import com.churchsong.repository.SongRepository;
import com.churchsong.service.SongFamilyLibrary;
import com.churchsong.service.SongImportService;
import com.churchsong.service.SongLibrary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongImportServiceTests {

    @Test
    void dryRunDoesNotWriteRepositories(@TempDir Path tempDir)
            throws IOException {
        Path databaseFile = tempDir.resolve("dry-run.db");
        ImportFiles importFiles = writeImportFiles(
                tempDir,
                sampleSongs(),
                sampleFamilies()
        );

        withContext(
                databaseFile,
                context -> {
                    SongImportService songImportService =
                            context.getBean(SongImportService.class);
                    SongLibrary songLibrary = context.getBean(SongLibrary.class);
                    SongFamilyLibrary songFamilyLibrary =
                            context.getBean(SongFamilyLibrary.class);
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                    int initialSongCount = countRows(jdbcTemplate, "song");
                    int initialFamilyCount = countRows(jdbcTemplate, "song_family");

                    ImportReport report = songImportService.importReviewedSongs(
                            importFiles.songsFile(),
                            importFiles.familiesFile(),
                            true,
                            false
                    );

                    assertEquals(1, report.getFamiliesCreated());
                    assertEquals(2, report.getFamiliesSkipped());
                    assertEquals(2, report.getSongsCreated());
                    assertEquals(3, report.getSongsSkipped());
                    assertEquals(initialSongCount, countRows(jdbcTemplate, "song"));
                    assertEquals(initialFamilyCount, countRows(jdbcTemplate, "song_family"));
                    assertFalse(report.isCommitted());
                }
        );
    }

    @Test
    void applyImportsApprovedFamiliesIdempotentlyAndPreservesLyrics(
            @TempDir Path tempDir) throws IOException {
        Path databaseFile = tempDir.resolve("apply.db");
        ImportFiles importFiles = writeImportFiles(
                tempDir,
                sampleSongs(),
                sampleFamilies()
        );

        withContext(
                databaseFile,
                context -> {
                    SongImportService songImportService =
                            context.getBean(SongImportService.class);
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);

                    ImportReport firstReport = songImportService.importReviewedSongs(
                            importFiles.songsFile(),
                            importFiles.familiesFile(),
                            true,
                            true
                    );

                    Map<String, Object> family = findFamilyBySourceKey(
                            jdbcTemplate,
                            "familykey-approved"
                    );
                    Map<String, Object> englishSong = findSongBySourceUrl(
                            jdbcTemplate,
                            "https://example.com/change-me-en"
                    );
                    Map<String, Object> creoleSong = findSongBySourceUrl(
                            jdbcTemplate,
                            "https://example.com/change-me-ht"
                    );

                    assertEquals(1, firstReport.getFamiliesCreated());
                    assertEquals(2, firstReport.getSongsCreated());
                    assertTrue(firstReport.isCommitted());
                    assertEquals("Change Me O God.", family.get("canonical_title"));

                    assertEquals("ENGLISH", englishSong.get("language"));
                    assertEquals("HAITIAN_CREOLE", creoleSong.get("language"));
                    assertNull(englishSong.get("song_type"));
                    assertEquals("Line 1\nLine 2", englishSong.get("lyrics"));
                    assertNotNull(englishSong.get("family_id"));
                    assertEquals(
                            englishSong.get("family_id"),
                            creoleSong.get("family_id")
                    );

                    ImportReport secondReport = songImportService.importReviewedSongs(
                            importFiles.songsFile(),
                            importFiles.familiesFile(),
                            true,
                            true
                    );

                    assertEquals(1, secondReport.getFamiliesExisting());
                    assertEquals(2, secondReport.getSongsExisting());
                    assertEquals(2, countRows(jdbcTemplate, "song"));
                    assertEquals(1, countRows(jdbcTemplate, "song_family"));
                }
        );
    }

    @Test
    void importResolvesExistingApprovedFamilyBySourceKey(
            @TempDir Path tempDir) throws IOException {
        Path databaseFile = tempDir.resolve("existing-family.db");
        ImportFiles importFiles = writeImportFiles(
                tempDir,
                sampleSongs(),
                sampleFamilies()
        );

        withContext(
                databaseFile,
                context -> {
                    SongFamilyLibrary songFamilyLibrary =
                            context.getBean(SongFamilyLibrary.class);
                    SongImportService songImportService =
                            context.getBean(SongImportService.class);
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);

                    SongFamily existingFamily = songFamilyLibrary.addFamily(
                            new SongFamily(
                                    77,
                                    "Existing Change Me O God.",
                                    "familykey-approved"
                            )
                    );

                    ImportReport report = songImportService.importReviewedSongs(
                            importFiles.songsFile(),
                            importFiles.familiesFile(),
                            true,
                            true
                    );

                    assertEquals(1, report.getFamiliesExisting());
                    assertEquals(0, report.getFamiliesCreated());
                    assertEquals(2, report.getSongsCreated());
                    assertEquals(1, songFamilyLibrary.getFamilyList().size());
                    assertEquals(77, existingFamily.getId());
                    assertEquals(
                            77,
                            ((Number) findSongBySourceUrl(
                                    jdbcTemplate,
                                    "https://example.com/change-me-en"
                            ).get("family_id")).intValue()
                    );
                    assertEquals(
                            77,
                            ((Number) findSongBySourceUrl(
                                    jdbcTemplate,
                                    "https://example.com/change-me-ht"
                            ).get("family_id")).intValue()
                    );
                }
        );
    }

    private List<Map<String, Object>> sampleSongs() {
        return List.of(
                Map.of(
                        "title", "Change Me O God.",
                        "languageGuess", "ENGLISH",
                        "lyrics", "Line 1\nLine 2",
                        "sourceUrl", "https://example.com/change-me-en",
                        "reviewStatus", "PENDING",
                        "isLikelySong", true
                ),
                Map.of(
                        "title", "Chanje'm Senye",
                        "languageGuess", "HAITIAN_CREOLE",
                        "lyrics", "Liy 1\nLiy 2",
                        "sourceUrl", "https://example.com/change-me-ht",
                        "reviewStatus", "PENDING",
                        "isLikelySong", true
                ),
                Map.of(
                        "title", "Pending Family Song",
                        "languageGuess", "SPANISH",
                        "lyrics", "Uno\nDos",
                        "sourceUrl", "https://example.com/pending-family-song",
                        "reviewStatus", "PENDING",
                        "isLikelySong", true
                ),
                Map.of(
                        "title", "Rejected Song",
                        "languageGuess", "UNKNOWN",
                        "lyrics", "No import",
                        "sourceUrl", "https://example.com/rejected-song",
                        "reviewStatus", "REJECTED",
                        "isLikelySong", true
                ),
                Map.of(
                        "title", "Article Post",
                        "languageGuess", "ENGLISH",
                        "lyrics", "Paragraph text",
                        "sourceUrl", "https://example.com/article",
                        "reviewStatus", "PENDING",
                        "isLikelySong", false
                )
        );
    }

    private List<Map<String, Object>> sampleFamilies() {
        return List.of(
                Map.of(
                        "familyId", "family-0001",
                        "familyKey", "familykey-approved",
                        "reviewStatus", "APPROVED",
                        "members", List.of(
                                Map.of(
                                        "title", "Chanje'm Senye",
                                        "language", "HAITIAN_CREOLE",
                                        "sourceUrl", "https://example.com/change-me-ht"
                                ),
                                Map.of(
                                        "title", "Change Me O God.",
                                        "language", "ENGLISH",
                                        "sourceUrl", "https://example.com/change-me-en"
                                )
                        )
                ),
                Map.of(
                        "familyId", "family-0002",
                        "familyKey", "familykey-pending",
                        "reviewStatus", "PENDING",
                        "members", List.of(
                                Map.of(
                                        "title", "Pending Family Song",
                                        "language", "SPANISH",
                                        "sourceUrl", "https://example.com/pending-family-song"
                                )
                        )
                ),
                Map.of(
                        "familyId", "family-0003",
                        "familyKey", "familykey-rejected",
                        "reviewStatus", "REJECTED",
                        "members", List.of()
                )
        );
    }

    private ImportFiles writeImportFiles(
            Path tempDir,
            List<Map<String, Object>> songs,
            List<Map<String, Object>> families) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        Path songsFile = tempDir.resolve("songs-import.json");
        Path familiesFile = tempDir.resolve("song-families.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(songsFile.toFile(), songs);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(familiesFile.toFile(), families);
        return new ImportFiles(songsFile, familiesFile);
    }

    private void withContext(
            Path databaseFile,
            Consumer<ConfigurableApplicationContext> assertion) {
        try (ConfigurableApplicationContext context =
                     new SpringApplicationBuilder(
                             ChurchSongApiApplication.class)
                             .run(
                                     "--spring.profiles.active=test",
                                     "--spring.datasource.url=jdbc:sqlite:" + databaseFile,
                                     "--spring.jpa.hibernate.ddl-auto=create-drop",
                                     "--spring.sql.init.mode=never",
                                     "--spring.jpa.show-sql=false",
                                     "--spring.main.banner-mode=off",
                                     "--spring.main.web-application-type=none")) {

            assertion.accept(context);
        }
    }

    private int countRows(
            JdbcTemplate jdbcTemplate,
            String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + tableName,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private Map<String, Object> findSongBySourceUrl(
            JdbcTemplate jdbcTemplate,
            String sourceUrl) {
        return jdbcTemplate.queryForMap(
                "select * from song where source_url = ?",
                sourceUrl
        );
    }

    private Map<String, Object> findFamilyBySourceKey(
            JdbcTemplate jdbcTemplate,
            String sourceFamilyKey) {
        return jdbcTemplate.queryForMap(
                "select * from song_family where source_family_key = ?",
                sourceFamilyKey
        );
    }

    private record ImportFiles(Path songsFile, Path familiesFile) {
    }
}
