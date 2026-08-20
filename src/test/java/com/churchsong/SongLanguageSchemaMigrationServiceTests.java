package com.churchsong;

import com.churchsong.dto.migration.SongLanguageSchemaMigrationReport;
import com.churchsong.service.SongLanguageSchemaMigrationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongLanguageSchemaMigrationServiceTests {

    @Test
    void dryRunReportsMigrationWithoutWriting(@TempDir Path tempDir) {
        withContext(
                tempDir.resolve("language-schema-dry-run.db"),
                context -> {
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                    SongLanguageSchemaMigrationService migrationService =
                            context.getBean(SongLanguageSchemaMigrationService.class);

                    seedOldSchema(jdbcTemplate);
                    int songCountBefore = countRows(jdbcTemplate, "song");

                    SongLanguageSchemaMigrationReport report =
                            migrationService.migrateFrenchLanguageConstraint(false);

                    assertFalse(report.isCommitted());
                    assertFalse(report.isAlreadyMigrated());
                    assertTrue(report.getOriginalSongTableSql().contains("'UNKNOWN'"));
                    assertTrue(report.getNewSongTableSql().contains("'FRENCH'"));
                    assertEquals(songCountBefore, countRows(jdbcTemplate, "song"));
                    assertEquals("ok", report.getIntegrityCheckResult());
                    assertEquals(0, report.getForeignKeyViolationCount());
                }
        );
    }

    @Test
    void applyPreservesRowsIdsAndReferencesAndAllowsFrench(@TempDir Path tempDir) {
        withContext(
                tempDir.resolve("language-schema-apply.db"),
                context -> {
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                    SongLanguageSchemaMigrationService migrationService =
                            context.getBean(SongLanguageSchemaMigrationService.class);

                    seedOldSchema(jdbcTemplate);

                    SongLanguageSchemaMigrationReport report =
                            migrationService.migrateFrenchLanguageConstraint(true);

                    assertTrue(report.isCommitted());
                    assertEquals(4, countRows(jdbcTemplate, "song"));
                    assertEquals(2, countRows(jdbcTemplate, "playlist_songs"));
                    assertEquals(1, countRows(jdbcTemplate, "service_plan_songs"));
                    assertEquals(List.of("10", "11"),
                            jdbcTemplate.queryForList(
                                    "select songs_id from playlist_songs where playlist_id = 1 order by song_order",
                                    String.class
                            ));
                    assertEquals(List.of("12"),
                            jdbcTemplate.queryForList(
                                    "select song_id from service_plan_songs where service_plan_id = 100 order by song_order",
                                    String.class
                            ));

                    jdbcTemplate.update(
                            """
                                    insert into song (id, title, author, lyrics, source_url, song_type, language, family_id)
                                    values (?, ?, ?, ?, ?, ?, ?, ?)
                                    """,
                            15,
                            "French Song",
                            null,
                            "Paroles",
                            "https://example.com/french",
                            null,
                            "FRENCH",
                            77
                    );

                    Map<String, Object> frenchSong = jdbcTemplate.queryForMap(
                            "select id, language, family_id, source_url from song where id = 15"
                    );
                    assertEquals(15, ((Number) frenchSong.get("id")).intValue());
                    assertEquals("FRENCH", frenchSong.get("language"));
                    assertEquals(77, ((Number) frenchSong.get("family_id")).intValue());
                    assertEquals("https://example.com/french", frenchSong.get("source_url"));

                    assertThrows(
                            DataAccessException.class,
                            () -> jdbcTemplate.update(
                                    """
                                            insert into song (id, title, author, lyrics, source_url, song_type, language, family_id)
                                            values (?, ?, ?, ?, ?, ?, ?, ?)
                                            """,
                                    16,
                                    "Bad Song",
                                    null,
                                    "Lyrics",
                                    "https://example.com/bad",
                                    null,
                                    "GERMAN",
                                    77
                            )
                    );
                }
        );
    }

    @Test
    void secondApplyIsSafeNoOpWhenSchemaAlreadyMigrated(@TempDir Path tempDir) {
        withContext(
                tempDir.resolve("language-schema-idempotent.db"),
                context -> {
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                    SongLanguageSchemaMigrationService migrationService =
                            context.getBean(SongLanguageSchemaMigrationService.class);

                    seedOldSchema(jdbcTemplate);

                    SongLanguageSchemaMigrationReport first =
                            migrationService.migrateFrenchLanguageConstraint(true);
                    SongLanguageSchemaMigrationReport second =
                            migrationService.migrateFrenchLanguageConstraint(true);

                    assertTrue(first.isCommitted());
                    assertFalse(first.isAlreadyMigrated());
                    assertFalse(second.isCommitted());
                    assertTrue(second.isAlreadyMigrated());
                    assertEquals(4, countRows(jdbcTemplate, "song"));
                    assertEquals("ok", second.getIntegrityCheckResult());
                    assertEquals(0, second.getForeignKeyViolationCount());
                }
        );
    }

    private void seedOldSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("drop table if exists playlist_songs");
        jdbcTemplate.execute("drop table if exists service_plan_songs");
        jdbcTemplate.execute("drop table if exists playlist");
        jdbcTemplate.execute("drop table if exists service_plans");
        jdbcTemplate.execute("drop table if exists song");
        jdbcTemplate.execute("drop table if exists song_family");

        jdbcTemplate.execute(
                "create table song_family (id integer not null, canonical_title varchar(255), source_family_key varchar(255), primary key (id))"
        );
        jdbcTemplate.execute(
                "create table playlist (id integer, name varchar(255), reusable boolean, service_date date, source_playlist_id bigint, theme varchar(255), primary key (id))"
        );
        jdbcTemplate.execute(
                "create table service_plans (id integer not null, service_name varchar(255), service_date date, service_time time, primary key (id))"
        );
        jdbcTemplate.execute(
                "create table song (id integer not null, author varchar(255), lyrics varchar(255), song_type varchar(255) check ((song_type in ('FAST','SLOW'))), title varchar(255), family_id integer, language varchar(255) check ((language in ('ENGLISH','HAITIAN_CREOLE','SPANISH','UNKNOWN'))), source_url varchar(255), primary key (id))"
        );
        jdbcTemplate.execute(
                "create table playlist_songs (playlist_id bigint not null, songs_id integer not null, song_order integer not null, primary key (playlist_id, song_order))"
        );
        jdbcTemplate.execute(
                "create table service_plan_songs (service_plan_id integer not null, song_order integer not null, song_id integer not null, primary key (service_plan_id, song_order), foreign key (service_plan_id) references service_plans(id), foreign key (song_id) references song(id))"
        );
        jdbcTemplate.execute(
                "create index idx_service_plan_songs_song on service_plan_songs(song_id)"
        );

        jdbcTemplate.update(
                "insert into song_family (id, canonical_title, source_family_key) values (?, ?, ?)",
                77,
                "Family Title",
                "familykey-77"
        );
        insertSong(jdbcTemplate, 10, "English Song", "ENGLISH", 77, "FAST", "https://example.com/en");
        insertSong(jdbcTemplate, 11, "Creole Song", "HAITIAN_CREOLE", 77, "SLOW", "https://example.com/ht");
        insertSong(jdbcTemplate, 12, "Spanish Song", "SPANISH", 77, null, "https://example.com/es");
        insertSong(jdbcTemplate, 13, "Unknown Song", "UNKNOWN", 77, null, "https://example.com/unknown");

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
                "insert into service_plans (id, service_name, service_date, service_time) values (?, ?, ?, ?)",
                100L,
                "Sunday Morning",
                "2026-08-19",
                "10:00:00"
        );
        jdbcTemplate.update(
                "insert into playlist_songs (playlist_id, songs_id, song_order) values (?, ?, ?)",
                1L,
                10,
                0
        );
        jdbcTemplate.update(
                "insert into playlist_songs (playlist_id, songs_id, song_order) values (?, ?, ?)",
                1L,
                11,
                1
        );
        jdbcTemplate.update(
                "insert into service_plan_songs (service_plan_id, song_id, song_order) values (?, ?, ?)",
                100L,
                12,
                0
        );
    }

    private void insertSong(
            JdbcTemplate jdbcTemplate,
            int id,
            String title,
            String language,
            int familyId,
            String songType,
            String sourceUrl) {
        jdbcTemplate.update(
                """
                        insert into song (id, title, author, lyrics, source_url, song_type, language, family_id)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                title,
                "Tester",
                "Lyrics",
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
