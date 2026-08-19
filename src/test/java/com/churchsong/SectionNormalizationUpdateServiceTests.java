package com.churchsong;

import com.churchsong.dto.normalization.SectionNormalizationUpdateReport;
import com.churchsong.service.SectionNormalizationUpdateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectionNormalizationUpdateServiceTests {

    @Test
    void dryRunPerformsZeroWritesAndPreservesReferences(@TempDir Path tempDir)
            throws IOException {
        Path fixesFile = writeFixesFile(
                tempDir,
                List.of(sectionFix(
                        "Gracias Por Tu Garcia",
                        "https://example.com/gracias",
                        List.of("Verse 1", "Chorus", "Verse 2"),
                        "[Verse 1]\nGracias\n\n[Chorus]\nGloria\n\n[Verse 2]\nAmen"
                ))
        );

        withContext(
                tempDir.resolve("section-normalization-dry-run.db"),
                context -> {
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                    SectionNormalizationUpdateService service =
                            context.getBean(SectionNormalizationUpdateService.class);
                    seedNormalizationUpdateScenario(jdbcTemplate);

                    int songCountBefore = countRows(jdbcTemplate, "song");
                    int familyCountBefore = countRows(jdbcTemplate, "song_family");
                    int playlistRowsBefore = countRows(jdbcTemplate, "playlist_songs");
                    int servicePlanRowsBefore = countRows(jdbcTemplate, "service_plan_songs");
                    String lyricsBefore = songLyrics(jdbcTemplate, 1);

                    SectionNormalizationUpdateReport report =
                            service.applySectionNormalization(fixesFile, false);

                    assertFalse(report.isCommitted());
                    assertEquals(1, report.getCandidateCount());
                    assertEquals(1, report.getSongsToUpdate());
                    assertEquals(0, report.getAlreadyNormalized());
                    assertEquals(songCountBefore, countRows(jdbcTemplate, "song"));
                    assertEquals(familyCountBefore, countRows(jdbcTemplate, "song_family"));
                    assertEquals(playlistRowsBefore, countRows(jdbcTemplate, "playlist_songs"));
                    assertEquals(servicePlanRowsBefore, countRows(jdbcTemplate, "service_plan_songs"));
                    assertEquals(lyricsBefore, songLyrics(jdbcTemplate, 1));
                }
        );
    }

    @Test
    void applyUpdatesOnlyLyricsAndLeavesReferencesUntouched(@TempDir Path tempDir)
            throws IOException {
        Path fixesFile = writeFixesFile(
                tempDir,
                List.of(sectionFix(
                        "Gracias Por Tu Garcia",
                        "https://example.com/gracias",
                        List.of("Verse 1", "Chorus", "Verse 2"),
                        "[Verse 1]\nGracias\n\n[Chorus]\nGloria\n\n[Verse 2]\nAmen"
                ))
        );

        withContext(
                tempDir.resolve("section-normalization-apply.db"),
                context -> {
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                    SectionNormalizationUpdateService service =
                            context.getBean(SectionNormalizationUpdateService.class);
                    seedNormalizationUpdateScenario(jdbcTemplate);

                    Map<String, Object> before = findSongById(jdbcTemplate, 1);
                    List<String> playlistSongIdsBefore = jdbcTemplate.queryForList(
                            "select songs_id from playlist_songs where playlist_id = 1 order by song_order",
                            String.class
                    );
                    List<String> servicePlanSongIdsBefore = jdbcTemplate.queryForList(
                            "select song_id from service_plan_songs where service_plan_id = 100 order by song_order",
                            String.class
                    );

                    SectionNormalizationUpdateReport report =
                            service.applySectionNormalization(fixesFile, true);

                    Map<String, Object> after = findSongById(jdbcTemplate, 1);
                    assertTrue(report.isCommitted());
                    assertEquals(1, report.getSongsToUpdate());
                    assertEquals("[Verse 1]\nGracias\n\n[Chorus]\nGloria\n\n[Verse 2]\nAmen", after.get("lyrics"));
                    assertEquals(before.get("title"), after.get("title"));
                    assertEquals(before.get("author"), after.get("author"));
                    assertEquals(before.get("language"), after.get("language"));
                    assertEquals(before.get("family_id"), after.get("family_id"));
                    assertEquals(before.get("song_type"), after.get("song_type"));
                    assertEquals(before.get("source_url"), after.get("source_url"));
                    assertEquals(playlistSongIdsBefore, jdbcTemplate.queryForList(
                            "select songs_id from playlist_songs where playlist_id = 1 order by song_order",
                            String.class
                    ));
                    assertEquals(servicePlanSongIdsBefore, jdbcTemplate.queryForList(
                            "select song_id from service_plan_songs where service_plan_id = 100 order by song_order",
                            String.class
                    ));
                }
        );
    }

    @Test
    void applyIsIdempotentAndReportsAlreadyNormalizedOnSecondRun(@TempDir Path tempDir)
            throws IOException {
        Path fixesFile = writeFixesFile(
                tempDir,
                List.of(sectionFix(
                        "Toujours joyeux",
                        "https://example.com/toujours",
                        List.of("Verse 1", "Verse 2", "Verse 3", "Verse 4"),
                        "[Verse 1]\nLine 1\n\n[Verse 2]\nLine 2"
                ))
        );

        withContext(
                tempDir.resolve("section-normalization-idempotent.db"),
                context -> {
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                    SectionNormalizationUpdateService service =
                            context.getBean(SectionNormalizationUpdateService.class);
                    seedSong(
                            jdbcTemplate,
                            10,
                            "Toujours joyeux",
                            "French Author",
                            "I\nLine 1\n\nII\nLine 2",
                            "https://example.com/toujours",
                            "UNKNOWN",
                            null,
                            91238
                    );

                    SectionNormalizationUpdateReport first =
                            service.applySectionNormalization(fixesFile, true);
                    SectionNormalizationUpdateReport second =
                            service.applySectionNormalization(fixesFile, true);

                    assertTrue(first.isCommitted());
                    assertEquals(1, first.getSongsToUpdate());
                    assertTrue(second.isCommitted());
                    assertEquals(0, second.getSongsToUpdate());
                    assertEquals(1, second.getAlreadyNormalized());
                }
        );
    }

    @Test
    void invalidMissingAndAmbiguousRecordsAreReportedWithoutWrites(@TempDir Path tempDir)
            throws IOException {
        List<Map<String, Object>> fixes = new ArrayList<>();
        fixes.add(sectionFix(
                "Missing Song",
                "https://example.com/not-found",
                List.of("Verse 1"),
                "[Verse 1]\nMissing"
        ));
        fixes.add(sectionFix(
                "Duplicate Song",
                "https://example.com/duplicate",
                List.of("Verse 1"),
                "[Verse 1]\nDuplicate"
        ));
        Map<String, Object> invalidRecord = new LinkedHashMap<>();
        invalidRecord.put("title", "Broken Record");
        invalidRecord.put("sourceUrl", "");
        invalidRecord.put("currentSectionStructure", List.of("Verse 1"));
        invalidRecord.put("proposedDetectedSections", List.of("Verse 1"));
        invalidRecord.put("proposedNormalizedLyrics", " ");
        fixes.add(invalidRecord);

        Path fixesFile = writeFixesFile(tempDir, fixes);

        withContext(
                tempDir.resolve("section-normalization-validation.db"),
                context -> {
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                    SectionNormalizationUpdateService service =
                            context.getBean(SectionNormalizationUpdateService.class);

                    seedSong(
                            jdbcTemplate,
                            20,
                            "Duplicate One",
                            null,
                            "I\nAlpha",
                            "https://example.com/duplicate",
                            "ENGLISH",
                            null,
                            null
                    );
                    seedSong(
                            jdbcTemplate,
                            21,
                            "Duplicate Two",
                            null,
                            "I\nBeta",
                            "https://example.com/duplicate",
                            "ENGLISH",
                            null,
                            null
                    );

                    int songCountBefore = countRows(jdbcTemplate, "song");
                    SectionNormalizationUpdateReport report =
                            service.applySectionNormalization(fixesFile, false);

                    assertEquals(3, report.getCandidateCount());
                    assertEquals(0, report.getSongsToUpdate());
                    assertEquals(1, report.getNotFound());
                    assertEquals(1, report.getAmbiguousSourceUrlMatches());
                    assertEquals(1, report.getInvalidRecords());
                    assertEquals(songCountBefore, countRows(jdbcTemplate, "song"));
                }
        );
    }

    @Test
    void identicalLyricsAreReportedAsAlreadyNormalized(@TempDir Path tempDir)
            throws IOException {
        Path fixesFile = writeFixesFile(
                tempDir,
                List.of(sectionFix(
                        "M Pasyone de ou",
                        "https://example.com/mpasyone",
                        List.of("Verse 1", "Chorus", "Verse 2"),
                        "[Verse 1]\nLine 1\n\n[Chorus]\nLine 2"
                ))
        );

        withContext(
                tempDir.resolve("section-normalization-already.db"),
                context -> {
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                    SectionNormalizationUpdateService service =
                            context.getBean(SectionNormalizationUpdateService.class);
                    seedSong(
                            jdbcTemplate,
                            30,
                            "M Pasyone de ou",
                            "Armstrong Beauzil",
                            "[Verse 1]\nLine 1\n\n[Chorus]\nLine 2",
                            "https://example.com/mpasyone",
                            "HAITIAN_CREOLE",
                            null,
                            91236
                    );

                    SectionNormalizationUpdateReport report =
                            service.applySectionNormalization(fixesFile, false);

                    assertEquals(0, report.getSongsToUpdate());
                    assertEquals(1, report.getAlreadyNormalized());
                }
        );
    }

    @Test
    void applyRollsBackWhenDatabaseUpdateFails(@TempDir Path tempDir)
            throws IOException {
        Path fixesFile = writeFixesFile(
                tempDir,
                List.of(
                        sectionFix(
                                "Gracias Por Tu Garcia",
                                "https://example.com/gracias",
                                List.of("Verse 1", "Chorus", "Verse 2"),
                                "[Verse 1]\nGracias\n\n[Chorus]\nGloria\n\n[Verse 2]\nAmen"
                        ),
                        sectionFix(
                                "M Pasyone de ou",
                                "https://example.com/mpasyone",
                                List.of("Verse 1", "Chorus", "Verse 2"),
                                "[Verse 1]\nLigne 1\n\n[Chorus]\nLigne 2"
                        )
                )
        );

        withContext(
                tempDir.resolve("section-normalization-rollback.db"),
                context -> {
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                    SectionNormalizationUpdateService service =
                            context.getBean(SectionNormalizationUpdateService.class);
                    seedNormalizationUpdateScenario(jdbcTemplate);
                    seedSong(
                            jdbcTemplate,
                            2,
                            "M Pasyone de ou",
                            "Creole Author",
                            "I\nLigne 1\n\nKè\nLigne 2",
                            "https://example.com/mpasyone",
                            "HAITIAN_CREOLE",
                            null,
                            91236
                    );

                    jdbcTemplate.execute(
                            """
                                    create trigger fail_section_normalization_update
                                    before update of lyrics on song
                                    when new.id = 2
                                    begin
                                        select raise(abort, 'forced rollback');
                                    end
                                    """
                    );

                    assertThrows(
                            IllegalStateException.class,
                            () -> service.applySectionNormalization(fixesFile, true)
                    );

                    assertEquals("I\nGracias\n\nCoro:\nGloria\n\nII\nAmen", songLyrics(jdbcTemplate, 1));
                    assertEquals("I\nLigne 1\n\nKè\nLigne 2", songLyrics(jdbcTemplate, 2));
                }
        );
    }

    private Map<String, Object> sectionFix(
            String title,
            String sourceUrl,
            List<String> proposedSections,
            String proposedNormalizedLyrics) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", title);
        values.put("sourceUrl", sourceUrl);
        values.put("currentSectionStructure", List.of());
        values.put("proposedDetectedSections", proposedSections);
        values.put("currentLyrics", "");
        values.put("proposedNormalizedLyrics", proposedNormalizedLyrics);
        return values;
    }

    private Path writeFixesFile(
            Path tempDir,
            List<Map<String, Object>> fixes) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        Path fixesFile = tempDir.resolve("already-imported-section-fixes.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(fixesFile.toFile(), fixes);
        return fixesFile;
    }

    private void seedNormalizationUpdateScenario(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
                "insert into song_family (id, canonical_title, source_family_key) values (?, ?, ?)",
                91235,
                "Gracias Family",
                "familykey-gracias"
        );
        seedSong(
                jdbcTemplate,
                1,
                "Gracias Por Tu Garcia",
                "Spanish Author",
                "I\nGracias\n\nCoro:\nGloria\n\nII\nAmen",
                "https://example.com/gracias",
                "SPANISH",
                null,
                91235
        );
        jdbcTemplate.update(
                "insert into playlist (id, name, reusable, service_date, source_playlist_id, theme) values (?, ?, ?, ?, ?, ?)",
                1L,
                "Wednesday Night",
                true,
                "2026-08-19",
                null,
                "Mercy"
        );
        jdbcTemplate.update(
                "insert into playlist_songs (playlist_id, songs_id, song_order) values (?, ?, ?)",
                1L,
                1,
                0
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
                1,
                0
        );
    }

    private void seedSong(
            JdbcTemplate jdbcTemplate,
            int id,
            String title,
            String author,
            String lyrics,
            String sourceUrl,
            String language,
            String songType,
            Integer familyId) {
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

    private Map<String, Object> findSongById(JdbcTemplate jdbcTemplate, int songId) {
        return jdbcTemplate.queryForMap("select * from song where id = ?", songId);
    }

    private String songLyrics(JdbcTemplate jdbcTemplate, int songId) {
        return jdbcTemplate.queryForObject(
                "select lyrics from song where id = ?",
                String.class,
                songId
        );
    }

    private int countRows(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + tableName,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private void withContext(
            Path databaseFile,
            Consumer<ConfigurableApplicationContext> assertion) {
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

            assertion.accept(context);
        }
    }
}
