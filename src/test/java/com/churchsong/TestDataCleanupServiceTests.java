package com.churchsong;

import com.churchsong.dto.cleanup.CleanupCandidateFamily;
import com.churchsong.dto.cleanup.CleanupCandidateSong;
import com.churchsong.dto.cleanup.TestDataCleanupReport;
import com.churchsong.service.TestDataCleanupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDataCleanupServiceTests {

    @Test
    void dryRunReportsCandidatesWithoutChangingData(@TempDir Path tempDir) {
        withContext(
                tempDir.resolve("cleanup-dry-run.db"),
                context -> {
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                    TestDataCleanupService cleanupService =
                            context.getBean(TestDataCleanupService.class);

                    seedCleanupScenario(jdbcTemplate);

                    TestDataCleanupReport report =
                            cleanupService.cleanupAutomatedTestData(false);

                    assertFalse(report.isCommitted());
                    assertEquals(3, report.getCandidateSongs().size());
                    assertEquals(2, report.getSafeSongDeletes().size());
                    assertEquals(1, report.getReviewSongs().size());
                    assertEquals(1, report.getCandidateFamilies().size());
                    assertEquals(0, report.getSafeFamilyDeletes().size());
                    assertEquals(1, report.getReviewFamilies().size());
                    assertEquals(2, report.getProtectedSongs().size());
                    assertEquals(5, report.getProtectedFamilies().size());
                    assertEquals(5, countRows(jdbcTemplate, "song"));
                    assertEquals(6, countRows(jdbcTemplate, "song_family"));

                    CleanupCandidateSong reviewSong = report.getReviewSongs().getFirst();
                    assertEquals(37, reviewSong.getId());
                    assertEquals(1, reviewSong.getPlaylistReferences().size());

                    CleanupCandidateFamily reviewFamily = report.getReviewFamilies().getFirst();
                    assertEquals(91234, reviewFamily.getId());
                }
        );
    }

    @Test
    void applyDeletesOnlySafeTestDataAndPreservesProtectedRows(
            @TempDir Path tempDir) {
        withContext(
                tempDir.resolve("cleanup-apply.db"),
                context -> {
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                    TestDataCleanupService cleanupService =
                            context.getBean(TestDataCleanupService.class);

                    seedCleanupScenario(jdbcTemplate);

                    TestDataCleanupReport report =
                            cleanupService.cleanupAutomatedTestData(true);

                    assertTrue(report.isCommitted());
                    assertEquals(3, countRows(jdbcTemplate, "song"));
                    assertEquals(6, countRows(jdbcTemplate, "song_family"));
                    assertEquals(1, countSongsByTitle(jdbcTemplate, "Unit Test Cámbiame O, Dios."));
                    assertEquals(0, countSongsByTitle(jdbcTemplate, "Unit Test Change Me O God."));
                    assertEquals(0, countSongsByTitle(jdbcTemplate, "Unit Test Chanje'm Senye"));
                    assertEquals(1, countSongsByTitle(jdbcTemplate, "Cámbiame O, Dios."));
                    assertEquals(1, countSongsByTitle(jdbcTemplate, "Chanje'm Senye De Créature Mwen Ye A."));
                    assertEquals(1, countRows(jdbcTemplate, "playlist_songs"));

                    CleanupCandidateSong reviewSong = report.getReviewSongs().getFirst();
                    assertEquals(37, reviewSong.getId());
                }
        );
    }

    private void seedCleanupScenario(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
                "insert into song_family (id, canonical_title, source_family_key) values (?, ?, ?)",
                91234,
                "Unit Test Change Me O God",
                null
        );
        jdbcTemplate.update(
                "insert into song_family (id, canonical_title, source_family_key) values (?, ?, ?)",
                91235,
                "Change Me O God.",
                "familykey-approved-1"
        );
        jdbcTemplate.update(
                "insert into song_family (id, canonical_title, source_family_key) values (?, ?, ?)",
                91236,
                "I'm Glad He Laid His Hands On Me",
                "familykey-approved-2"
        );
        jdbcTemplate.update(
                "insert into song_family (id, canonical_title, source_family_key) values (?, ?, ?)",
                91237,
                "He Loved Me So Much",
                "familykey-approved-3"
        );
        jdbcTemplate.update(
                "insert into song_family (id, canonical_title, source_family_key) values (?, ?, ?)",
                91238,
                "Jesus I'll Never Forget",
                "familykey-approved-4"
        );
        jdbcTemplate.update(
                "insert into song_family (id, canonical_title, source_family_key) values (?, ?, ?)",
                91239,
                "I trust In Jesus.",
                "familykey-approved-5"
        );

        jdbcTemplate.update(
                """
                        insert into song (id, title, author, lyrics, source_url, song_type, language, family_id)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                35,
                "Unit Test Change Me O God.",
                "Tester",
                "Lyrics",
                "https://example.com/unit-test-en",
                "SLOW",
                "ENGLISH",
                91234
        );
        jdbcTemplate.update(
                """
                        insert into song (id, title, author, lyrics, source_url, song_type, language, family_id)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                36,
                "Unit Test Chanje'm Senye",
                "Tester",
                "Lyrics",
                "https://example.com/unit-test-ht",
                "SLOW",
                "HAITIAN_CREOLE",
                91234
        );
        jdbcTemplate.update(
                """
                        insert into song (id, title, author, lyrics, source_url, song_type, language, family_id)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                37,
                "Unit Test Cámbiame O, Dios.",
                "Tester",
                "Lyrics",
                "https://example.com/unit-test-es",
                "SLOW",
                "SPANISH",
                91234
        );
        jdbcTemplate.update(
                """
                        insert into song (id, title, author, lyrics, source_url, song_type, language, family_id)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                65,
                "Cámbiame O, Dios.",
                "Importer",
                "Lyrics",
                "https://example.com/imported-es",
                null,
                "SPANISH",
                91235
        );
        jdbcTemplate.update(
                """
                        insert into song (id, title, author, lyrics, source_url, song_type, language, family_id)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                66,
                "Chanje'm Senye De Créature Mwen Ye A.",
                "Importer",
                "Lyrics",
                "https://example.com/imported-ht",
                null,
                "HAITIAN_CREOLE",
                91235
        );

        jdbcTemplate.update(
                "insert into playlist (id, name, reusable, service_date, source_playlist_id, theme) values (?, ?, ?, ?, ?, ?)",
                1L,
                "Real Playlist",
                true,
                "2026-08-18",
                null,
                "Grace"
        );
        jdbcTemplate.update(
                "insert into playlist_songs (playlist_id, songs_id, song_order) values (?, ?, ?)",
                1L,
                37,
                0
        );
    }

    private int countRows(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + tableName,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private int countSongsByTitle(JdbcTemplate jdbcTemplate, String title) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from song where title = ?",
                Integer.class,
                title
        );
        return count == null ? 0 : count;
    }

    private void withContext(
            Path databaseFile,
            Consumer<ConfigurableApplicationContext> testBody) {
        try (ConfigurableApplicationContext context =
                     new SpringApplicationBuilder(ChurchSongApiApplication.class)
                             .run(
                                     "--spring.profiles.active=test",
                                     "--spring.datasource.url=jdbc:sqlite:" + databaseFile,
                                     "--spring.jpa.hibernate.ddl-auto=create-drop",
                                     "--spring.sql.init.mode=never",
                                     "--spring.jpa.show-sql=false",
                                     "--spring.main.banner-mode=off",
                                     "--spring.main.web-application-type=none")) {
            testBody.accept(context);
        }
    }
}
