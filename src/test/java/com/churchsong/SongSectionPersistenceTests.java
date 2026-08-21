package com.churchsong;

import com.churchsong.controller.SongController;
import com.churchsong.dto.SongSectionDescriptorRequest;
import com.churchsong.dto.SongSectionsUpdateRequest;
import com.churchsong.model.Song;
import com.churchsong.model.SongLanguage;
import com.churchsong.model.SongType;
import com.churchsong.service.SongLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongSectionPersistenceTests {

    @Test
    void startupAddsSectionPersistenceColumnsToLegacySongTable(
            @TempDir Path tempDir) throws Exception {
        Path databaseFile =
                tempDir.resolve("song-legacy-section-columns.db");

        seedLegacySongTable(databaseFile);

        withUpdateContext(
                databaseFile,
                context -> {
                    JdbcTemplate jdbcTemplate =
                            context.getBean(JdbcTemplate.class);
                    SongLibrary songLibrary =
                            context.getBean(SongLibrary.class);

                    String schemaSql = jdbcTemplate.queryForObject(
                            """
                                    select sql
                                    from sqlite_master
                                    where type = 'table'
                                      and name = 'song'
                                    """,
                            String.class
                    );

                    assertNotNull(schemaSql);
                    assertTrue(
                            schemaSql.contains("section_structure")
                    );
                    assertTrue(
                            schemaSql.contains("sections_confirmed")
                    );

                    Song legacySong =
                            songLibrary.findSongById(1);

                    assertNotNull(legacySong);
                    assertEquals(
                            "Legacy Song",
                            legacySong.getTitle()
                    );
                    assertEquals(
                            "Legacy line 1\nLegacy line 2",
                            legacySong.getLyrics()
                    );
                    assertEquals(
                            null,
                            legacySong.getSectionStructure()
                    );
                    assertFalse(
                            legacySong.isSectionsConfirmed()
                    );
                }
        );
    }

    @Test
    void manualSectionSavePersistsAcrossRestart(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve("song-sections-persist-across-restart.db");

        final Integer[] songIdHolder = new Integer[1];

        withUpdateContext(
                databaseFile,
                context -> {
                    SongLibrary songLibrary =
                            context.getBean(SongLibrary.class);
                    SongController songController =
                            context.getBean(SongController.class);

                    Song song = new Song(
                            "Li touche m",
                            "Armstrong Beauzil",
                            """
                                    [Verse 1]
                                    Ligne 1
                                    [Verse 2]
                                    Ligne 2
                                    [Verse 3]
                                    Ligne 3
                                    """,
                            SongType.SLOW
                    );
                    song.setLanguage(
                            SongLanguage.HAITIAN_CREOLE
                    );

                    songLibrary.addSong(song);
                    songIdHolder[0] = song.getId();

                    Song updatedSong =
                            songController.updateSongSections(
                                    song.getId(),
                                    requestWith(
                                            descriptor(
                                                    "VERSE",
                                                    1,
                                                    null
                                            ),
                                            descriptor(
                                                    "CHORUS",
                                                    null,
                                                    null
                                            ),
                                            descriptor(
                                                    "VERSE",
                                                    2,
                                                    null
                                            )
                                    )
                            );

                    assertTrue(
                            updatedSong.isSectionsConfirmed()
                    );
                    assertTrue(
                            updatedSong.getSectionStructure()
                                    .contains("\"name\":\"Chorus\"")
                    );
                    assertEquals(
                            """
                                    [Verse 1]
                                    Ligne 1
                                    [Verse 2]
                                    Ligne 2
                                    [Verse 3]
                                    Ligne 3
                                    """.trim(),
                            updatedSong.getLyrics()
                    );
                }
        );

        withUpdateContext(
                databaseFile,
                context -> {
                    SongLibrary songLibrary =
                            context.getBean(SongLibrary.class);

                    Song persistedSong =
                            songLibrary.findSongById(
                                    songIdHolder[0]
                            );

                    assertNotNull(persistedSong);
                    assertTrue(
                            persistedSong.isSectionsConfirmed()
                    );
                    assertEquals(
                            """
                                    [Verse 1]
                                    Ligne 1
                                    [Verse 2]
                                    Ligne 2
                                    [Verse 3]
                                    Ligne 3
                                    """.trim(),
                            persistedSong.getLyrics()
                    );
                    assertEquals(
                            "[{\"type\":\"VERSE\",\"verseNumber\":1,\"customLabel\":\"\",\"name\":\"Verse 1\"},{\"type\":\"CHORUS\",\"verseNumber\":null,\"customLabel\":\"\",\"name\":\"Chorus\"},{\"type\":\"VERSE\",\"verseNumber\":2,\"customLabel\":\"\",\"name\":\"Verse 2\"}]",
                            persistedSong.getSectionStructure()
                    );
                }
        );
    }

    @Test
    void manualSectionSavePreservesEditedIndexOrder(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve("song-sections-preserve-order.db");

        withUpdateContext(
                databaseFile,
                context -> {
                    SongLibrary songLibrary =
                            context.getBean(SongLibrary.class);
                    SongController songController =
                            context.getBean(SongController.class);

                    Song song = new Song(
                            "Li touche m",
                            "Armstrong Beauzil",
                            """
                                    Intro line
                                    [Verse 1]
                                    Ligne 1
                                    [Verse 2]
                                    Ligne 2
                                    """,
                            SongType.SLOW
                    );
                    song.setLanguage(
                            SongLanguage.HAITIAN_CREOLE
                    );

                    songLibrary.addSong(song);

                    Song updatedSong =
                            songController.updateSongSections(
                                    song.getId(),
                                    requestWith(
                                            descriptor(
                                                    "CHORUS",
                                                    null,
                                                    null
                                            ),
                                            descriptor(
                                                    "VERSE",
                                                    1,
                                                    null
                                            ),
                                            descriptor(
                                                    "VERSE",
                                                    2,
                                                    null
                                            )
                                    )
                            );

                    assertEquals(
                            "[{\"type\":\"CHORUS\",\"verseNumber\":null,\"customLabel\":\"\",\"name\":\"Chorus\"},{\"type\":\"VERSE\",\"verseNumber\":1,\"customLabel\":\"\",\"name\":\"Verse 1\"},{\"type\":\"VERSE\",\"verseNumber\":2,\"customLabel\":\"\",\"name\":\"Verse 2\"}]",
                            updatedSong.getSectionStructure()
                    );

                    Song reloadedSong =
                            songLibrary.findSongById(
                                    song.getId()
                            );

                    assertNotNull(reloadedSong);
                    assertEquals(
                            "[{\"type\":\"CHORUS\",\"verseNumber\":null,\"customLabel\":\"\",\"name\":\"Chorus\"},{\"type\":\"VERSE\",\"verseNumber\":1,\"customLabel\":\"\",\"name\":\"Verse 1\"},{\"type\":\"VERSE\",\"verseNumber\":2,\"customLabel\":\"\",\"name\":\"Verse 2\"}]",
                            reloadedSong.getSectionStructure()
                    );
                    assertTrue(
                            reloadedSong.isSectionsConfirmed()
                    );
                }
        );
    }

    @Test
    void rejectsInvalidManualSectionMetadata(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve("song-sections-invalid-metadata.db");

        withUpdateContext(
                databaseFile,
                context -> {
                    SongLibrary songLibrary =
                            context.getBean(SongLibrary.class);
                    SongController songController =
                            context.getBean(SongController.class);

                    Song song = new Song(
                            "Section Validation Song",
                            "Writer",
                            """
                                    [Verse 1]
                                    Line 1
                                    [Verse 2]
                                    Line 2
                                    """,
                            SongType.SLOW
                    );
                    songLibrary.addSong(song);

                    ResponseStatusException mismatchedCount =
                            assertThrows(
                                    ResponseStatusException.class,
                                    () ->
                                            songController
                                                    .updateSongSections(
                                                            song.getId(),
                                                            requestWith(
                                                                    descriptor(
                                                                            "VERSE",
                                                                            1,
                                                                            null
                                                                    )
                                                            )
                                                    )
                            );

                    assertEquals(
                            "Section count does not match the song's existing section structure.",
                            mismatchedCount.getReason()
                    );

                    ResponseStatusException blockLabel =
                            assertThrows(
                                    ResponseStatusException.class,
                                    () ->
                                            songController
                                                    .updateSongSections(
                                                            song.getId(),
                                                            requestWith(
                                                                    descriptor(
                                                                            "OTHER",
                                                                            null,
                                                                            "Block 1"
                                                                    ),
                                                                    descriptor(
                                                                            "CHORUS",
                                                                            null,
                                                                            null
                                                                    )
                                                            )
                                                    )
                            );

                    assertEquals(
                            "Artificial Block labels are not valid saved section metadata.",
                            blockLabel.getReason()
                    );
                }
        );
    }

    private SongSectionsUpdateRequest requestWith(
            SongSectionDescriptorRequest... sections) {
        SongSectionsUpdateRequest request =
                new SongSectionsUpdateRequest();
        request.setSectionsConfirmed(true);
        request.setSections(List.of(sections));
        return request;
    }

    private SongSectionDescriptorRequest descriptor(
            String type,
            Integer verseNumber,
            String customLabel) {
        SongSectionDescriptorRequest descriptor =
                new SongSectionDescriptorRequest();
        descriptor.setType(type);
        descriptor.setVerseNumber(verseNumber);
        descriptor.setCustomLabel(customLabel);
        return descriptor;
    }

    private void seedLegacySongTable(Path databaseFile)
            throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:sqlite:" + databaseFile
        );
             var statement = connection.createStatement()) {
            statement.execute(
                    """
                            create table song (
                                id integer not null,
                                author varchar(255),
                                lyrics varchar(255),
                                source_url varchar(255),
                                song_type varchar(255),
                                title varchar(255),
                                family_id integer,
                                language varchar(255),
                                primary key (id)
                            )
                            """
            );
            statement.execute(
                    """
                            insert into song (
                                id,
                                author,
                                lyrics,
                                source_url,
                                song_type,
                                title,
                                family_id,
                                language
                            ) values (
                                1,
                                'Legacy Writer',
                                'Legacy line 1\nLegacy line 2',
                                null,
                                'SLOW',
                                'Legacy Song',
                                null,
                                'HAITIAN_CREOLE'
                            )
                            """
            );
        }
    }

    private void withUpdateContext(
            Path databaseFile,
            Consumer<ConfigurableApplicationContext> assertion) {
        try (ConfigurableApplicationContext context =
                     new SpringApplicationBuilder(
                             ChurchSongApiApplication.class)
                             .run(
                                     "--spring.profiles.active=test",
                                     "--spring.datasource.url=jdbc:sqlite:" + databaseFile,
                                     "--spring.jpa.hibernate.ddl-auto=update",
                                     "--spring.sql.init.mode=never",
                                     "--spring.jpa.show-sql=false",
                                     "--spring.main.banner-mode=off",
                                     "--spring.main.web-application-type=none")) {

            assertion.accept(context);
        }
    }
}
