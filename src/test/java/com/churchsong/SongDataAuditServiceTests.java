package com.churchsong;

import com.churchsong.dto.audit.SongDataAuditReport;
import com.churchsong.service.SongDataAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongDataAuditServiceTests {

    @Test
    void auditReportsIntegrityIssuesWithoutWriting(@TempDir Path tempDir) {
        withContext(
                tempDir.resolve("song-audit.db"),
                context -> {
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                    SongDataAuditService auditService = context.getBean(SongDataAuditService.class);

                    seedAuditScenario(jdbcTemplate);
                    int songCountBefore = countRows(jdbcTemplate, "song");
                    int familyCountBefore = countRows(jdbcTemplate, "song_family");

                    SongDataAuditReport report = auditService.auditSongData();

                    assertEquals(8, report.getTotalSongs());
                    assertEquals(4, report.getTotalFamilies());
                    assertEquals(1, report.getEmptyFamilyCount());
                    assertEquals(1, report.getDuplicateLanguageFamilyCount());
                    assertEquals(1, report.getExactDuplicateSongCount());
                    assertEquals(0, report.getCrossFamilyDuplicateCount());
                    assertEquals(1, report.getOrphanedFamilyReferenceCount());
                    assertTrue(report.getProtectedImportedFamilies().stream()
                            .anyMatch(entry -> entry.contains("familyId=91235")));
                    assertTrue(report.getSuspiciousFamilies().stream()
                            .anyMatch(entry -> entry.contains("familyId=500")));
                    assertTrue(report.getExactDuplicateSongs().stream()
                            .anyMatch(entry -> entry.contains("Amazing Grace")));
                    assertTrue(report.getOrphanedSongReferences().stream()
                            .anyMatch(entry -> entry.contains("songId=8")));
                    assertEquals(songCountBefore, countRows(jdbcTemplate, "song"));
                    assertEquals(familyCountBefore, countRows(jdbcTemplate, "song_family"));
                }
        );
    }

    private void seedAuditScenario(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
                "insert into song_family (id, canonical_title, source_family_key) values (9, 'He Loved Me So Much', null)"
        );
        jdbcTemplate.update(
                "insert into song_family (id, canonical_title, source_family_key) values (500, 'Legacy Grace Family', null)"
        );
        jdbcTemplate.update(
                "insert into song_family (id, canonical_title, source_family_key) values (91235, 'Change Me O God.', 'familykey-1')"
        );
        jdbcTemplate.update(
                "insert into song_family (id, canonical_title, source_family_key) values (91236, 'I''m Glad He Laid His Hands On Me', 'familykey-2')"
        );

        insertSong(jdbcTemplate, 1, "Amazing Grace", "Lyrics A", "UNKNOWN", null, null);
        insertSong(jdbcTemplate, 2, "Amazing Grace", "Lyrics A", "UNKNOWN", null, null);
        insertSong(jdbcTemplate, 3, "Grace Song", "Lyrics B", "ENGLISH", 500, null);
        insertSong(jdbcTemplate, 4, "Grace Song Alt", "Lyrics C", "ENGLISH", 500, null);
        insertSong(jdbcTemplate, 5, "Change Me O God.", "Lyrics D", "ENGLISH", 91235, "https://example.com/en");
        insertSong(jdbcTemplate, 6, "Chanje'm Senye", "Lyrics E", "HAITIAN_CREOLE", 91235, "https://example.com/ht");
        insertSong(jdbcTemplate, 7, "I'm Glad He Laid His Hands On Me", "Lyrics F", "ENGLISH", 91236, "https://example.com/hands");
        insertSong(jdbcTemplate, 8, "Orphan Song", "Lyrics G", "ENGLISH", 12345, null);

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
            String lyrics,
            String language,
            Integer familyId,
            String sourceUrl) {
        jdbcTemplate.update(
                """
                        insert into song (id, title, author, lyrics, source_url, song_type, language, family_id)
                        values (?, ?, 'Tester', ?, ?, null, ?, ?)
                        """,
                id,
                title,
                lyrics,
                sourceUrl,
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
