package com.churchsong;

import com.churchsong.dto.AddSongTranslationRequest;
import com.churchsong.dto.AddSongTranslationResponse;
import com.churchsong.dto.SongFamilyVersionsResponse;
import com.churchsong.dto.SongRequest;
import com.churchsong.controller.SongController;
import com.churchsong.model.Song;
import com.churchsong.model.SongFamily;
import com.churchsong.model.SongLanguage;
import com.churchsong.model.SongType;
import com.churchsong.service.SongFamilyLibrary;
import com.churchsong.service.SongLibrary;
import com.churchsong.service.SongTranslationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
                    Song frenchSong = new Song(
                            family.getId(),
                            "Unit Test A Dieu Soit La Gloire",
                            null,
                            "...",
                            SongType.SLOW,
                            SongLanguage.FRENCH
                    );

                    songLibrary.addSong(englishSong);
                    songLibrary.addSong(creoleSong);
                    songLibrary.addSong(spanishSong);
                    songLibrary.addSong(frenchSong);

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
                            "FRENCH",
                            findSongValue(
                                    jdbcTemplate,
                                    "Unit Test A Dieu Soit La Gloire",
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
                    assertEquals(
                            TEST_FAMILY_ID.intValue(),
                            ((Number) findSongValue(
                                    jdbcTemplate,
                                    "Unit Test A Dieu Soit La Gloire",
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

    @Test
    void persistsSectionStructureOnCreate(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve("song-section-structure-create.db");

        withContext(
                databaseFile,
                context -> {
                    SongController songController =
                            context.getBean(SongController.class);
                    JdbcTemplate jdbcTemplate =
                            context.getBean(JdbcTemplate.class);

                    Song createdSong = songController.addSong(
                            new SongRequest(
                                    null,
                                    "Unit Test Sectioned Song",
                                    "Writer",
                                    "Li touche m",
                                    """
                                            [{"type":"CHORUS","verseNumber":null,"customLabel":"","name":"Chorus"}]
                                            """,
                                    true,
                                    SongType.SLOW,
                                    SongLanguage.HAITIAN_CREOLE
                            )
                    );

                    assertEquals(
                            """
                                    [{"type":"CHORUS","verseNumber":null,"customLabel":"","name":"Chorus"}]
                                    """.trim(),
                            findSongValue(
                                    jdbcTemplate,
                                    createdSong.getTitle(),
                                    "section_structure"
                            )
                    );
                    assertEquals(
                            1,
                            ((Number) findSongValue(
                                    jdbcTemplate,
                                    createdSong.getTitle(),
                                    "sections_confirmed"
                            )).intValue()
                    );
                });
    }

    @Test
    void preservesUpdatedSectionStructureOnSongUpdate(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve("song-section-structure-update.db");

        withContext(
                databaseFile,
                context -> {
                    SongController songController =
                            context.getBean(SongController.class);
                    JdbcTemplate jdbcTemplate =
                            context.getBean(JdbcTemplate.class);

                    insertSongRow(
                            jdbcTemplate,
                            401,
                            "Unit Test Section Update",
                            "Writer",
                            "Block 1\n\nBlock 2",
                            SongType.SLOW,
                            SongLanguage.UNKNOWN,
                            null
                    );

                    Song updatedSong = songController.updateSong(
                            401,
                            new SongRequest(
                                    null,
                                    "Unit Test Section Update",
                                    "Writer",
                                    "Block 1\n\nBlock 2",
                                    """
                                            [{"type":"VERSE","verseNumber":1,"customLabel":"","name":"Verse 1"},{"type":"CHORUS","verseNumber":null,"customLabel":"","name":"Chorus"}]
                                            """,
                                    true,
                                    SongType.SLOW,
                                    SongLanguage.UNKNOWN
                            )
                    );

                    assertEquals(
                            """
                                    [{"type":"VERSE","verseNumber":1,"customLabel":"","name":"Verse 1"},{"type":"CHORUS","verseNumber":null,"customLabel":"","name":"Chorus"}]
                                    """.trim(),
                            updatedSong.getSectionStructure()
                    );
                    assertTrue(
                            updatedSong.isSectionsConfirmed()
                    );
                    assertEquals(
                            1,
                            ((Number) findSongValue(
                                    jdbcTemplate,
                                    "Unit Test Section Update",
                                    "sections_confirmed"
                            )).intValue()
                    );
                });
    }

    @Test
    void returnsFourLanguageSlotsWithMissingVersionsLeftNull(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve("song-family-version-slots.db");

        withContext(
                databaseFile,
                context -> {
                    SongLibrary songLibrary =
                            context.getBean(SongLibrary.class);
                    SongFamilyLibrary songFamilyLibrary =
                            context.getBean(SongFamilyLibrary.class);

                    SongFamily family =
                            songFamilyLibrary.addFamily(
                                    new SongFamily(
                                            TEST_FAMILY_ID,
                                            "Unit Test Family Slots"
                                    )
                            );

                    Song creoleSong = new Song(
                            family.getId(),
                            "Unit Test Kreyol Only",
                            null,
                            "...",
                            SongType.SLOW,
                            SongLanguage.HAITIAN_CREOLE
                    );

                    songLibrary.addSong(creoleSong);

                    SongFamilyVersionsResponse response =
                            songFamilyLibrary
                                    .getLanguageVersionsByFamilyId(
                                            family.getId());

                    assertEquals(
                            family.getId(),
                            response.getFamilyId());
                    assertEquals(
                            Set.of(
                                    SongLanguage.ENGLISH,
                                    SongLanguage.HAITIAN_CREOLE,
                                    SongLanguage.SPANISH,
                                    SongLanguage.FRENCH
                            ),
                            response.getVersions().keySet());
                    assertNull(
                            response.getVersions().get(
                                    SongLanguage.ENGLISH));
                    assertNotNull(
                            response.getVersions().get(
                                    SongLanguage.HAITIAN_CREOLE));
                    assertNull(
                            response.getVersions().get(
                                    SongLanguage.SPANISH));
                    assertNull(
                            response.getVersions().get(
                                    SongLanguage.FRENCH));
                });
    }

    @Test
    void returnsAllAvailableLanguageVersionsBySlot(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve("song-family-all-language-slots.db");

        withContext(
                databaseFile,
                context -> {
                    SongLibrary songLibrary =
                            context.getBean(SongLibrary.class);
                    SongFamilyLibrary songFamilyLibrary =
                            context.getBean(SongFamilyLibrary.class);

                    SongFamily family =
                            songFamilyLibrary.addFamily(
                                    new SongFamily(
                                            TEST_FAMILY_ID,
                                            "Unit Test Complete Family"
                                    )
                            );

                    Map<SongLanguage, String> titlesByLanguage =
                            Map.of(
                                    SongLanguage.ENGLISH,
                                    "Unit Test English",
                                    SongLanguage.HAITIAN_CREOLE,
                                    "Unit Test Kreyol",
                                    SongLanguage.SPANISH,
                                    "Unit Test Espanol",
                                    SongLanguage.FRENCH,
                                    "Unit Test Francais"
                            );

                    titlesByLanguage.forEach(
                            (language, title) ->
                                    songLibrary.addSong(
                                            new Song(
                                                    family.getId(),
                                                    title,
                                                    null,
                                                    "...",
                                                    SongType.SLOW,
                                                    language
                                            )
                                    )
                    );

                    SongFamilyVersionsResponse response =
                            songFamilyLibrary
                                    .getLanguageVersionsByFamilyId(
                                            family.getId());

                    titlesByLanguage.forEach(
                            (language, expectedTitle) ->
                                    assertEquals(
                                            expectedTitle,
                                            response.getVersions()
                                                    .get(language)
                                                    .getTitle()
                                    )
                    );
                });
    }

    @Test
    void addsTranslationToExistingFamily(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve("song-family-add-spanish.db");

        withContext(
                databaseFile,
                context -> {
                    SongFamilyLibrary songFamilyLibrary =
                            context.getBean(SongFamilyLibrary.class);
                    JdbcTemplate jdbcTemplate =
                            context.getBean(JdbcTemplate.class);

                    SongFamily family =
                            songFamilyLibrary.addFamily(
                                    new SongFamily(
                                            TEST_FAMILY_ID,
                                            "Unit Test Existing Family"
                                    )
                            );

                    Song englishSong = new Song(
                            family.getId(),
                            "Unit Test Existing English",
                            null,
                            "...",
                            SongType.SLOW,
                            SongLanguage.ENGLISH
                    );
                    Song creoleSong = new Song(
                            family.getId(),
                            "Unit Test Existing Kreyol",
                            null,
                            "...",
                            SongType.SLOW,
                            SongLanguage.HAITIAN_CREOLE
                    );

                    insertSongRow(
                            jdbcTemplate,
                            101,
                            englishSong.getTitle(),
                            englishSong.getAuthor(),
                            englishSong.getLyrics(),
                            englishSong.getSongType(),
                            englishSong.getLanguage(),
                            englishSong.getFamilyId()
                    );
                    insertSongRow(
                            jdbcTemplate,
                            102,
                            creoleSong.getTitle(),
                            creoleSong.getAuthor(),
                            creoleSong.getLyrics(),
                            creoleSong.getSongType(),
                            creoleSong.getLanguage(),
                            creoleSong.getFamilyId()
                    );

                    AddSongTranslationResponse response =
                            context.getBean(
                                            SongTranslationService.class
                                    )
                                    .addTranslation(
                                            101,
                                            translationRequest(
                                                    SongLanguage.SPANISH,
                                                    "Unit Test Existing Espanol",
                                                    "... traduccion ..."
                                            )
                                    );

                    assertEquals(
                            family.getId(),
                            response.getSourceSong()
                                    .getFamilyId()
                    );
                    assertEquals(
                            family.getId(),
                            response.getTranslationSong()
                                    .getFamilyId()
                    );
                    assertEquals(
                            SongLanguage.SPANISH,
                            response.getTranslationSong()
                                    .getLanguage()
                    );
                    assertEquals(
                            3,
                            countRows(
                                    jdbcTemplate,
                                    "song"
                            )
                    );
                    assertEquals(
                            "Unit Test Existing Espanol",
                            response.getVersions()
                                    .getVersions()
                                    .get(
                                            SongLanguage.SPANISH
                                    )
                                    .getTitle()
                    );
                });
    }

    @Test
    void addsTranslationToStandaloneSongByCreatingFamily(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve("song-standalone-add-french.db");

        withContext(
                databaseFile,
                context -> {
                    SongTranslationService translationService =
                            context.getBean(
                                    SongTranslationService.class
                            );
                    JdbcTemplate jdbcTemplate =
                            context.getBean(JdbcTemplate.class);

                    Song standaloneSong = new Song(
                            null,
                            "Unit Test Standalone Kreyol",
                            null,
                            "...",
                            SongType.SLOW,
                            SongLanguage.HAITIAN_CREOLE
                    );

                    insertSongRow(
                            jdbcTemplate,
                            201,
                            standaloneSong.getTitle(),
                            standaloneSong.getAuthor(),
                            standaloneSong.getLyrics(),
                            standaloneSong.getSongType(),
                            standaloneSong.getLanguage(),
                            standaloneSong.getFamilyId()
                    );

                    AddSongTranslationResponse response =
                            translationService.addTranslation(
                                    201,
                                    translationRequest(
                                            SongLanguage.FRENCH,
                                            "Unit Test Standalone Francais",
                                            "... francais ..."
                                    )
                            );

                    Integer familyId =
                            response.getSourceSong()
                                    .getFamilyId();

                    assertNotNull(familyId);
                    assertEquals(
                            familyId,
                            response.getTranslationSong()
                                    .getFamilyId()
                    );
                    assertEquals(
                            1,
                            countRows(
                                    jdbcTemplate,
                                    "song_family"
                            )
                    );
                    assertEquals(
                            2,
                            countRows(
                                    jdbcTemplate,
                                    "song"
                            )
                    );
                    assertEquals(
                            familyId.intValue(),
                            ((Number) findSongValue(
                                    jdbcTemplate,
                                    "Unit Test Standalone Kreyol",
                                    "family_id"
                            )).intValue()
                    );
                });
    }

    @Test
    void rejectsDuplicateLanguageTranslationWithoutChangingDatabase(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve("song-family-duplicate-language.db");

        withContext(
                databaseFile,
                context -> {
                    SongFamilyLibrary songFamilyLibrary =
                            context.getBean(SongFamilyLibrary.class);
                    SongTranslationService translationService =
                            context.getBean(
                                    SongTranslationService.class
                            );
                    JdbcTemplate jdbcTemplate =
                            context.getBean(JdbcTemplate.class);

                    SongFamily family =
                            songFamilyLibrary.addFamily(
                                    new SongFamily(
                                            TEST_FAMILY_ID,
                                            "Unit Test Duplicate Language"
                                    )
                            );

                    Song englishSong = new Song(
                            family.getId(),
                            "Unit Test Duplicate English",
                            null,
                            "...",
                            SongType.SLOW,
                            SongLanguage.ENGLISH
                    );
                    Song spanishSong = new Song(
                            family.getId(),
                            "Unit Test Duplicate Espanol",
                            null,
                            "...",
                            SongType.SLOW,
                            SongLanguage.SPANISH
                    );

                    insertSongRow(
                            jdbcTemplate,
                            301,
                            englishSong.getTitle(),
                            englishSong.getAuthor(),
                            englishSong.getLyrics(),
                            englishSong.getSongType(),
                            englishSong.getLanguage(),
                            englishSong.getFamilyId()
                    );
                    insertSongRow(
                            jdbcTemplate,
                            302,
                            spanishSong.getTitle(),
                            spanishSong.getAuthor(),
                            spanishSong.getLyrics(),
                            spanishSong.getSongType(),
                            spanishSong.getLanguage(),
                            spanishSong.getFamilyId()
                    );

                    IllegalArgumentException exception =
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            translationService
                                                    .addTranslation(
                                                            301,
                                                            translationRequest(
                                                                    SongLanguage.SPANISH,
                                                                    "Unit Test Duplicate Espanol 2",
                                                                    "... otro ..."
                                                            )
                                                    )
                            );

                    assertEquals(
                            "This song already has a Español version.",
                            exception.getMessage()
                    );
                    assertEquals(
                            2,
                            countRows(
                                    jdbcTemplate,
                                    "song"
                            )
                    );
                    assertFalse(
                            titleExists(
                                    jdbcTemplate,
                                    "Unit Test Duplicate Espanol 2"
                            )
                    );
                });
    }

    private AddSongTranslationRequest translationRequest(
            SongLanguage language,
            String title,
            String lyrics) {
        AddSongTranslationRequest request =
                new AddSongTranslationRequest();
        request.setLanguage(language);
        request.setTitle(title);
        request.setLyrics(lyrics);
        return request;
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

    private boolean titleExists(
            JdbcTemplate jdbcTemplate,
            String title) {
        return countSongsByTitle(
                jdbcTemplate,
                title
        ) > 0;
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

    private void insertSongRow(
            JdbcTemplate jdbcTemplate,
            int id,
            String title,
            String author,
            String lyrics,
            SongType songType,
            SongLanguage language,
            Integer familyId) {
        jdbcTemplate.update(
                """
                        insert into song (
                            id,
                            title,
                            author,
                            lyrics,
                            source_url,
                            song_type,
                            language,
                            family_id
                        ) values (?, ?, ?, ?, null, ?, ?, ?)
                        """,
                id,
                title,
                author,
                lyrics,
                songType == null ? null : songType.name(),
                language == null ? null : language.name(),
                familyId
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
