package com.churchsong;

import com.churchsong.model.Song;
import com.churchsong.model.SongFamily;
import com.churchsong.model.SongLanguage;
import com.churchsong.model.SongType;
import com.churchsong.service.SongFamilyLibrary;
import com.churchsong.service.SongLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongMultilingualTests {

    private static final String LEGACY_TEST_TITLE =
            "Unit Test Amazing Grace Multilingual Legacy";
    private static final Integer TEST_FAMILY_ID = 91234;

    @Test
    void oldSongDefaultsToUnknownLanguageAndNullFamily(
            @TempDir Path tempDir) {
        Path databaseFile = tempDir.resolve("song-old-defaults.db");

        withContext(
                databaseFile,
                context -> {
                    SongLibrary songLibrary = context.getBean(SongLibrary.class);
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);

                    Song song = new Song(
                            LEGACY_TEST_TITLE,
                            "John Newton",
                            "...",
                            SongType.SLOW
                    );

                    songLibrary.addSong(song);

                    assertEquals(
                            1,
                            countSongsByTitle(jdbcTemplate, LEGACY_TEST_TITLE)
                    );
                    assertNull(
                            findSongValue(
                                    jdbcTemplate,
                                    LEGACY_TEST_TITLE,
                                    "family_id"
                            )
                    );
                    assertEquals(
                            "UNKNOWN",
                            findSongValue(
                                    jdbcTemplate,
                                    LEGACY_TEST_TITLE,
                                    "language"
                            )
                    );
                });
    }

    @Test
    void supportsSongsInMultipleLanguagesWithinOneFamily(
            @TempDir Path tempDir) {
        Path databaseFile = tempDir.resolve("song-multilingual-family.db");

        withContext(
                databaseFile,
                context -> {
                    SongLibrary songLibrary = context.getBean(SongLibrary.class);
                    SongFamilyLibrary songFamilyLibrary = context.getBean(SongFamilyLibrary.class);
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);

                    SongFamily family = songFamilyLibrary.addFamily(
                            new SongFamily(TEST_FAMILY_ID, "Unit Test Change Me O God")
                    );

                    Song englishSong = new Song(
                            family.getId(),
                            "Unit Test Change Me O God.",
                            null,
                            "...",
                            SongType.SLOW,
                            SongLanguage.ENGLISH
                    );
                    Song creoleSong = new Song(
                            family.getId(),
                            "Unit Test Chanje'm Senye",
                            null,
                            "...",
                            SongType.SLOW,
                            SongLanguage.HAITIAN_CREOLE
                    );
                    Song spanishSong = new Song(
                            family.getId(),
                            "Unit Test Cámbiame O, Dios.",
                            null,
                            "...",
                            SongType.SLOW,
                            SongLanguage.SPANISH
                    );

                    songLibrary.addSong(englishSong);
                    songLibrary.addSong(creoleSong);
                    songLibrary.addSong(spanishSong);

                    assertEquals(
                            "ENGLISH",
                            findSongValue(
                                    jdbcTemplate,
                                    "Unit Test Change Me O God.",
                                    "language"
                            )
                    );
                    assertEquals(
                            "HAITIAN_CREOLE",
                            findSongValue(
                                    jdbcTemplate,
                                    "Unit Test Chanje'm Senye",
                                    "language"
                            )
                    );
                    assertEquals(
                            "SPANISH",
                            findSongValue(
                                    jdbcTemplate,
                                    "Unit Test Cámbiame O, Dios.",
                                    "language"
                            )
                    );
                    assertEquals(
                            TEST_FAMILY_ID.intValue(),
                            ((Number) findSongValue(
                                    jdbcTemplate,
                                    "Unit Test Change Me O God.",
                                    "family_id"
                            )).intValue()
                    );
                    assertEquals(
                            TEST_FAMILY_ID.intValue(),
                            ((Number) findSongValue(
                                    jdbcTemplate,
                                    "Unit Test Chanje'm Senye",
                                    "family_id"
                            )).intValue()
                    );
                    assertEquals(
                            TEST_FAMILY_ID.intValue(),
                            ((Number) findSongValue(
                                    jdbcTemplate,
                                    "Unit Test Cámbiame O, Dios.",
                                    "family_id"
                            )).intValue()
                    );
                });
    }

    @Test
    void trimsSongFamilyCanonicalTitle(
            @TempDir Path tempDir) {
        Path databaseFile = tempDir.resolve("song-family-trim.db");

        withContext(
                databaseFile,
                context -> {
                    SongFamilyLibrary songFamilyLibrary = context.getBean(SongFamilyLibrary.class);

                    SongFamily family = songFamilyLibrary.addFamily(
                            new SongFamily(9, "  He Loved Me So Much  ")
                    );

                    assertNotNull(family);
                    assertEquals("He Loved Me So Much", family.getCanonicalTitle());
                });
    }

    @Test
    void fullConstructorAllowsExplicitUnknownFamilyDefaults() {
        Song song = new Song(
                null,
                "Amazing Grace",
                "John Newton",
                "...",
                SongType.SLOW,
                null
        );

        assertNull(song.getFamilyId());
        assertEquals(SongLanguage.UNKNOWN, song.getLanguage());
    }

    private int countSongsByTitle(
            JdbcTemplate jdbcTemplate,
            String title) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from song where lower(title) = lower(?)",
                Integer.class,
                title
        );
        return count == null ? 0 : count;
    }

    private Object findSongValue(
            JdbcTemplate jdbcTemplate,
            String title,
            String columnName) {
        return jdbcTemplate.queryForObject(
                "select " + columnName + " from song where lower(title) = lower(?)",
                Object.class,
                title
        );
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
}
