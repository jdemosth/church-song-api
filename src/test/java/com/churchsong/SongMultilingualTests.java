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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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

                    Song song = new Song(
                            LEGACY_TEST_TITLE,
                            "John Newton",
                            "...",
                            SongType.SLOW
                    );

                    songLibrary.addSong(song);

                    List<Song> matchingSongs = songLibrary.getSongList()
                            .stream()
                            .filter(existingSong -> LEGACY_TEST_TITLE.equals(existingSong.getTitle()))
                            .toList();

                    assertTrue(!matchingSongs.isEmpty());
                    assertTrue(
                            matchingSongs.stream()
                                    .allMatch(existingSong -> existingSong.getFamilyId() == null));
                    assertTrue(
                            matchingSongs.stream()
                                    .allMatch(existingSong -> existingSong.getLanguage() == SongLanguage.UNKNOWN));
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

                    List<Song> songs = songFamilyLibrary.getSongsByFamilyId(TEST_FAMILY_ID);
                    List<Song> matchingSongs = songs.stream()
                            .filter(song -> song.getTitle().startsWith("Unit Test "))
                            .toList();

                    Map<String, SongLanguage> titleLanguages = matchingSongs.stream()
                            .collect(
                                    java.util.stream.Collectors.toMap(
                                            Song::getTitle,
                                            Song::getLanguage,
                                            (left, right) -> left
                                    )
                            );

                    assertEquals(3, titleLanguages.size());
                    assertEquals(
                            SongLanguage.ENGLISH,
                            titleLanguages.get("Unit Test Change Me O God."));
                    assertEquals(
                            SongLanguage.HAITIAN_CREOLE,
                            titleLanguages.get("Unit Test Chanje'm Senye"));
                    assertEquals(
                            SongLanguage.SPANISH,
                            titleLanguages.get("Unit Test Cámbiame O, Dios."));
                    assertTrue(
                            matchingSongs.stream()
                                    .allMatch(song -> TEST_FAMILY_ID.equals(song.getFamilyId())));
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

    private void withContext(
            Path databaseFile,
            Consumer<ConfigurableApplicationContext> assertion) {
        try (ConfigurableApplicationContext context =
                     new SpringApplicationBuilder(
                             ChurchSongApiApplication.class)
                             .properties(
                                     "spring.datasource.url=jdbc:sqlite:" + databaseFile,
                                     "spring.jpa.hibernate.ddl-auto=create-drop",
                                     "spring.sql.init.mode=never",
                                     "spring.jpa.show-sql=false",
                                     "spring.main.web-application-type=none")
                             .run()) {

            assertion.accept(context);
        }
    }
}
