package com.churchsong;

import com.churchsong.dto.cleanup.LegacyCleanupPlanReport;
import com.churchsong.service.LegacyCleanupPlanningService;
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

class LegacyCleanupPlanningServiceTests {

    @Test
    void dryRunIsReadOnlyAndReportsSafeReviewAndProtectedCases(@TempDir Path tempDir) {
        withContext(
                tempDir.resolve("legacy-cleanup-dry-run.db"),
                context -> {
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                    LegacyCleanupPlanningService service = context.getBean(LegacyCleanupPlanningService.class);

                    seedScenario(jdbcTemplate);
                    int familyCountBefore = countRows(jdbcTemplate, "song_family");
                    int songCountBefore = countRows(jdbcTemplate, "song");

                    LegacyCleanupPlanReport report = service.planLegacyCleanup(false);

                    assertFalse(report.isCommitted());
                    assertEquals(familyCountBefore, countRows(jdbcTemplate, "song_family"));
                    assertEquals(songCountBefore, countRows(jdbcTemplate, "song"));
                    assertTrue(report.getSafeDeletions().stream()
                            .anyMatch(entry -> entry.contains("familyId=77")));
                    assertTrue(report.getNeedsReview().stream()
                            .anyMatch(entry -> entry.contains("familyId=9")));
                    assertTrue(report.getAmazingGraceDuplicates().stream()
                            .anyMatch(entry -> entry.contains("songId=30")));
                    assertTrue(report.getAmazingGraceDuplicates().stream()
                            .anyMatch(entry -> entry.contains("songId=34")));
                    assertTrue(report.getPlaylistReferences().stream()
                            .anyMatch(entry -> entry.contains("songId=1")));
                    assertTrue(report.getPlaylistReferences().stream()
                            .anyMatch(entry -> entry.contains("songId=30")));
                    assertTrue(report.getProtectedFamilies().stream()
                            .anyMatch(entry -> entry.contains("familyId=91235")));
                }
        );
    }

    @Test
    void applyDeletesOnlySafeEmptyFamilyAndLeavesProtectedAndReferencedRows(@TempDir Path tempDir) {
        withContext(
                tempDir.resolve("legacy-cleanup-apply.db"),
                context -> {
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                    LegacyCleanupPlanningService service = context.getBean(LegacyCleanupPlanningService.class);

                    seedScenario(jdbcTemplate);

                    LegacyCleanupPlanReport report = service.planLegacyCleanup(true);

                    assertTrue(report.isCommitted());
                    assertEquals(0, countMatchingFamilies(jdbcTemplate, 77));
                    assertEquals(1, countMatchingFamilies(jdbcTemplate, 9));
                    assertEquals(1, countMatchingFamilies(jdbcTemplate, 91235));
                    assertEquals(1, countMatchingSongs(jdbcTemplate, 1));
                    assertEquals(1, countMatchingSongs(jdbcTemplate, 30));
                    assertEquals(1, countRows(jdbcTemplate, "playlist_songs"));
                }
        );
    }

    private void seedScenario(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
                "insert into song_family (id, canonical_title, source_family_key) values (9, 'He Loved Me So Much', null)"
        );
        jdbcTemplate.update(
                "insert into song_family (id, canonical_title, source_family_key) values (77, 'Existing Change Me O God.', 'familykey-approved')"
        );
        jdbcTemplate.update(
                "insert into song_family (id, canonical_title, source_family_key) values (91235, 'Change Me O God.', 'familykey-approved')"
        );

        insertSong(jdbcTemplate, 1, "Song A", "Tester", "Sample lyrics", null, null, null, null);
        insertSong(jdbcTemplate, 30, "Amazing Grace", "John Newton", "...", "UNKNOWN", "SLOW", null, null);
        insertSong(jdbcTemplate, 34, "Amazing Grace", "John Newton", "...", "UNKNOWN", "SLOW", null, null);
        insertSong(jdbcTemplate, 65, "Cámbiame O, Dios.", "Imported", "Lyrics", "SPANISH", null, 91235, "https://example.com/65");

        jdbcTemplate.update(
                "insert into playlist (id, name, reusable, service_date, source_playlist_id, theme) values (1, 'Audit Playlist', 1, '2026-08-18', null, 'Grace')"
        );
        jdbcTemplate.update(
                "insert into playlist_songs (playlist_id, songs_id, song_order) values (1, 1, 0)"
        );
    }

    private void insertSong(
            JdbcTemplate jdbcTemplate,
            int id,
            String title,
            String author,
            String lyrics,
            String language,
            String songType,
            Integer familyId,
            String sourceUrl) {
        jdbcTemplate.update(
                """
                        insert into song (id, title, author, lyrics, source_url, song_type, language, family_id)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                title,
                author,
                lyrics,
                sourceUrl,
                songType,
                language,
                familyId
        );
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

    private int countMatchingFamilies(
            JdbcTemplate jdbcTemplate,
            int familyId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from song_family where id = ?",
                Integer.class,
                familyId
        );
        return count == null ? 0 : count;
    }

    private int countMatchingSongs(
            JdbcTemplate jdbcTemplate,
            int songId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from song where id = ?",
                Integer.class,
                songId
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
