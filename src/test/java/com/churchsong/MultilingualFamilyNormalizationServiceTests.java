package com.churchsong;

import com.churchsong.dto.normalization.MultilingualFamilyNormalizationReport;
import com.churchsong.service.MultilingualFamilyNormalizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultilingualFamilyNormalizationServiceTests {

    @Test
    void dryRunReportsPlannedMigrationWithoutWriting(@TempDir Path tempDir) {
        withContext(
                tempDir.resolve("normalization-dry-run.db"),
                context -> {
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                    MultilingualFamilyNormalizationService normalizationService =
                            context.getBean(MultilingualFamilyNormalizationService.class);

                    seedNormalizationScenario(jdbcTemplate);

                    MultilingualFamilyNormalizationReport report =
                            normalizationService.normalizeLegacyDuplicateFamily(false);

                    assertFalse(report.isCommitted());
                    assertEquals(6, report.getCandidateSongs().size());
                    assertEquals(6, report.getSafeLegacySongs().size());
                    assertTrue(report.getReviewLegacySongs().isEmpty());
                    assertEquals(7, report.getRelationshipUpdates().size());
                    assertEquals(3, report.getDuplicateConflicts().size());
                    assertTrue(report.isFamilyRemovableAfterMigration());
                    assertEquals(6, countSongsInFamily(jdbcTemplate, 12));
                    assertEquals(3, countSongsInFamily(jdbcTemplate, 91235));
                    assertEquals(6, countRows(jdbcTemplate, "playlist_songs"));
                    assertEquals(2, countRows(jdbcTemplate, "service_plan_songs"));
                }
        );
    }

    @Test
    void applyMigratesReferencesPreservesOrderAndDeletesLegacyFamily(
            @TempDir Path tempDir) {
        withContext(
                tempDir.resolve("normalization-apply.db"),
                context -> {
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                    MultilingualFamilyNormalizationService normalizationService =
                            context.getBean(MultilingualFamilyNormalizationService.class);

                    seedNormalizationScenario(jdbcTemplate);

                    MultilingualFamilyNormalizationReport report =
                            normalizationService.normalizeLegacyDuplicateFamily(true);

                    assertTrue(report.isCommitted());
                    assertEquals(0, countSongsInFamily(jdbcTemplate, 12));
                    assertEquals(3, countSongsInFamily(jdbcTemplate, 91235));
                    assertEquals(0, countRowsMatching(jdbcTemplate,
                            "select count(*) from song where id in (27, 28, 29, 31, 32, 33)"));
                    assertEquals(0, countRowsMatching(jdbcTemplate,
                            "select count(*) from song_family where id = 12"));
                    assertEquals(List.of("67", "66"),
                            jdbcTemplate.queryForList(
                                    "select songs_id from playlist_songs where playlist_id = 1 order by song_order",
                                    String.class
                            ));
                    assertEquals(List.of("0", "1"),
                            jdbcTemplate.queryForList(
                                    "select song_order from playlist_songs where playlist_id = 1 order by song_order",
                                    String.class
                            ));
                    assertEquals(List.of("67", "65"),
                            jdbcTemplate.queryForList(
                                    "select songs_id from playlist_songs where playlist_id = 2 order by song_order",
                                    String.class
                            ));
                    assertEquals(List.of("65"),
                            jdbcTemplate.queryForList(
                                    "select song_id from service_plan_songs where service_plan_id = 100 order by song_order",
                                    String.class
                            ));
                    assertEquals(1, countRowsMatching(jdbcTemplate,
                            "select count(*) from song where id = 65"));
                    assertEquals(1, countRowsMatching(jdbcTemplate,
                            "select count(*) from song where id = 66"));
                    assertEquals(1, countRowsMatching(jdbcTemplate,
                            "select count(*) from song where id = 67"));
                }
        );
    }

    @Test
    void applyRollsBackWhenCanonicalSongIsMissing(@TempDir Path tempDir) {
        withContext(
                tempDir.resolve("normalization-rollback.db"),
                context -> {
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                    MultilingualFamilyNormalizationService normalizationService =
                            context.getBean(MultilingualFamilyNormalizationService.class);

                    seedNormalizationScenario(jdbcTemplate);
                    jdbcTemplate.update("delete from song where id = 66");

                    assertThrows(
                            IllegalStateException.class,
                            () -> normalizationService.normalizeLegacyDuplicateFamily(true)
                    );

                    assertEquals(6, countSongsInFamily(jdbcTemplate, 12));
                    assertEquals(2, countSongsInFamily(jdbcTemplate, 91235));
                    assertEquals(6, countRows(jdbcTemplate, "playlist_songs"));
                    assertEquals(2, countRows(jdbcTemplate, "service_plan_songs"));
                }
        );
    }

    private void seedNormalizationScenario(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
                "insert into song_family (id, canonical_title, source_family_key) values (?, ?, ?)",
                12,
                "Change Me O God",
                null
        );
        jdbcTemplate.update(
                "insert into song_family (id, canonical_title, source_family_key) values (?, ?, ?)",
                91235,
                "Change Me O God.",
                "familykey-e2b10c9271e91e05881f4fb277cce2017fe720ff"
        );

        insertSong(jdbcTemplate, 27, "Change Me O God.", "ENGLISH", 12, "SLOW");
        insertSong(jdbcTemplate, 28, "Chanje'm Senye", "HAITIAN_CREOLE", 12, "SLOW");
        insertSong(jdbcTemplate, 29, "Cámbiame O, Dios.", "SPANISH", 12, "SLOW");
        insertSong(jdbcTemplate, 31, "Change Me O God.", "ENGLISH", 12, "SLOW");
        insertSong(jdbcTemplate, 32, "Chanje'm Senye", "HAITIAN_CREOLE", 12, "SLOW");
        insertSong(jdbcTemplate, 33, "Cámbiame O, Dios.", "SPANISH", 12, "SLOW");
        insertSong(jdbcTemplate, 65, "Cámbiame O, Dios.", "SPANISH", 91235, null);
        insertSong(jdbcTemplate, 66, "Chanje'm Senye De Créature Mwen Ye A.", "HAITIAN_CREOLE", 91235, null);
        insertSong(jdbcTemplate, 67, "Change Me O God.", "ENGLISH", 91235, null);

        jdbcTemplate.update(
                "insert into playlist (id, name, reusable, service_date, source_playlist_id, theme) values (?, ?, ?, ?, ?, ?)",
                1L,
                "Wednesday Night",
                true,
                "2026-08-18",
                null,
                "Mercy"
        );
        jdbcTemplate.update(
                "insert into playlist (id, name, reusable, service_date, source_playlist_id, theme) values (?, ?, ?, ?, ?, ?)",
                2L,
                "Sunday Morning",
                true,
                "2026-08-19",
                null,
                "Grace"
        );

        jdbcTemplate.update(
                "insert into playlist_songs (playlist_id, songs_id, song_order) values (?, ?, ?)",
                1L,
                67,
                0
        );
        jdbcTemplate.update(
                "insert into playlist_songs (playlist_id, songs_id, song_order) values (?, ?, ?)",
                1L,
                27,
                1
        );
        jdbcTemplate.update(
                "insert into playlist_songs (playlist_id, songs_id, song_order) values (?, ?, ?)",
                1L,
                28,
                2
        );
        jdbcTemplate.update(
                "insert into playlist_songs (playlist_id, songs_id, song_order) values (?, ?, ?)",
                1L,
                31,
                3
        );

        jdbcTemplate.update(
                "insert into playlist_songs (playlist_id, songs_id, song_order) values (?, ?, ?)",
                2L,
                27,
                0
        );
        jdbcTemplate.update(
                "insert into playlist_songs (playlist_id, songs_id, song_order) values (?, ?, ?)",
                2L,
                29,
                1
        );

        jdbcTemplate.update(
                "insert into service_plans (id, service_name, service_date, service_time) values (?, ?, ?, ?)",
                100L,
                "Revival Night",
                "2026-08-20",
                "19:00:00"
        );

        jdbcTemplate.update(
                "insert into service_plan_songs (service_plan_id, song_id, song_order) values (?, ?, ?)",
                100L,
                29,
                0
        );
        jdbcTemplate.update(
                "insert into service_plan_songs (service_plan_id, song_id, song_order) values (?, ?, ?)",
                100L,
                33,
                1
        );
    }

    private void insertSong(
            JdbcTemplate jdbcTemplate,
            int id,
            String title,
            String language,
            int familyId,
            String songType) {
        jdbcTemplate.update(
                """
                        insert into song (id, title, author, lyrics, source_url, song_type, language, family_id)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                title,
                "Tester",
                "Lyrics",
                "https://example.com/" + id,
                songType,
                language,
                familyId
        );
    }

    private int countSongsInFamily(
            JdbcTemplate jdbcTemplate,
            int familyId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from song where family_id = ?",
                Integer.class,
                familyId
        );
        return count == null ? 0 : count;
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

    private int countRowsMatching(
            JdbcTemplate jdbcTemplate,
            String query) {
        Integer count = jdbcTemplate.queryForObject(
                query,
                Integer.class
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
