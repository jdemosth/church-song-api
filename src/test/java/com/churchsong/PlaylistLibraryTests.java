package com.churchsong;

import com.churchsong.model.Playlist;
import com.churchsong.model.Song;
import com.churchsong.model.SongType;
import com.churchsong.service.PlaylistLibrary;
import com.churchsong.service.SongLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaylistLibraryTests {

    @Test
    void copiesPlaylistAndPreservesSongOrder(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve(
                        "playlist-copy-order.db");

        withContext(
                databaseFile,
                context -> {
                    SongLibrary songLibrary =
                            context.getBean(
                                    SongLibrary.class);
                    PlaylistLibrary playlistLibrary =
                            context.getBean(
                                    PlaylistLibrary.class);

                    Song firstSong = createSong(
                            songLibrary,
                            "First");
                    Song secondSong = createSong(
                            songLibrary,
                            "Second");

                    Playlist sourcePlaylist =
                            createReusablePlaylist(
                                    playlistLibrary,
                                    "Reusable Source",
                                    List.of(
                                            secondSong,
                                            firstSong));

                    Playlist workingPlaylist =
                            playlistLibrary
                                    .usePlaylistForTodayService(
                                            sourcePlaylist.getId(),
                                            null,
                                            "2026-08-14",
                                            false);

                    assertEquals(
                            2,
                            workingPlaylist.getSongs()
                                    .size());
                    assertEquals(
                            "Second",
                            workingPlaylist.getSongs()
                                    .get(0)
                                    .getTitle());
                    assertEquals(
                            "First",
                            workingPlaylist.getSongs()
                                    .get(1)
                                    .getTitle());
                    assertFalse(
                            workingPlaylist.isReusable());
                });
    }

    @Test
    void keepsSourcePlaylistUnchanged(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve(
                        "playlist-source-unchanged.db");

        withContext(
                databaseFile,
                context -> {
                    SongLibrary songLibrary =
                            context.getBean(
                                    SongLibrary.class);
                    PlaylistLibrary playlistLibrary =
                            context.getBean(
                                    PlaylistLibrary.class);

                    Song firstSong = createSong(
                            songLibrary,
                            "Alpha");
                    Song secondSong = createSong(
                            songLibrary,
                            "Beta");

                    Playlist sourcePlaylist =
                            createReusablePlaylist(
                                    playlistLibrary,
                                    "Source Playlist",
                                    List.of(
                                            firstSong,
                                            secondSong));

                    Playlist workingPlaylist =
                            playlistLibrary
                                    .usePlaylistForTodayService(
                                            sourcePlaylist.getId(),
                                            "Friday — 08/14/2026",
                                            "2026-08-14",
                                            false);

                    playlistLibrary.moveSong(
                            workingPlaylist,
                            0,
                            1);

                    Playlist reloadedSource =
                            playlistLibrary.findPlaylistById(
                                    sourcePlaylist.getId());

                    assertEquals(
                            "Alpha",
                            reloadedSource.getSongs()
                                    .get(0)
                                    .getTitle());
                    assertEquals(
                            "Beta",
                            reloadedSource.getSongs()
                                    .get(1)
                                    .getTitle());
                });
    }

    @Test
    void createsTodaysGeneratedName(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve(
                        "playlist-generated-name.db");

        withContext(
                databaseFile,
                context -> {
                    PlaylistLibrary playlistLibrary =
                            context.getBean(
                                    PlaylistLibrary.class);

                    assertEquals(
                            "Friday — 08/14/2026",
                            playlistLibrary
                                    .generateTodayServiceName(
                                            "2026-08-14"));
                });
    }

    @Test
    void handlesExistingWorkingPlaylistForToday(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve(
                        "playlist-existing-today.db");

        withContext(
                databaseFile,
                context -> {
                    SongLibrary songLibrary =
                            context.getBean(
                                    SongLibrary.class);
                    PlaylistLibrary playlistLibrary =
                            context.getBean(
                                    PlaylistLibrary.class);

                    Song firstSong = createSong(
                            songLibrary,
                            "Song One");
                    Song secondSong = createSong(
                            songLibrary,
                            "Song Two");

                    Playlist firstSource =
                            createReusablePlaylist(
                                    playlistLibrary,
                                    "First Source",
                                    List.of(firstSong));
                    Playlist secondSource =
                            createReusablePlaylist(
                                    playlistLibrary,
                                    "Second Source",
                                    List.of(secondSong));

                    Playlist firstWorkingPlaylist =
                            playlistLibrary
                                    .usePlaylistForTodayService(
                                            firstSource.getId(),
                                            null,
                                            "2026-08-14",
                                            false);

                    IllegalStateException conflict =
                            assertThrows(
                                    IllegalStateException.class,
                                    () ->
                                            playlistLibrary.usePlaylistForTodayService(
                                                    secondSource.getId(),
                                                    null,
                                                    "2026-08-14",
                                                    false));

                    assertTrue(
                            conflict.getMessage()
                                    .contains(
                                            "already exists"));

                    Playlist replacementPlaylist =
                            playlistLibrary
                                    .usePlaylistForTodayService(
                                            secondSource.getId(),
                                            null,
                                            "2026-08-14",
                                            true);

                    assertNotNull(
                            replacementPlaylist.getId());
                    assertNotNull(
                            firstWorkingPlaylist.getId());
                    assertEquals(
                            "Song Two",
                            replacementPlaylist.getSongs()
                                    .get(0)
                                    .getTitle());
                });
    }

    @Test
    void persistsWorkingPlaylistAfterRestart(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve(
                        "playlist-restart.db");

        withContext(
                databaseFile,
                context -> {
                    SongLibrary songLibrary =
                            context.getBean(
                                    SongLibrary.class);
                    PlaylistLibrary playlistLibrary =
                            context.getBean(
                                    PlaylistLibrary.class);

                    Song song = createSong(
                            songLibrary,
                            "Hope");

                    Playlist sourcePlaylist =
                            createReusablePlaylist(
                                    playlistLibrary,
                                    "Restart Source",
                                    List.of(song));

                    playlistLibrary
                            .usePlaylistForTodayService(
                                    sourcePlaylist.getId(),
                                    null,
                                    "2026-08-14",
                                    false);
                });

        withContext(
                databaseFile,
                context -> {
                    PlaylistLibrary playlistLibrary =
                            context.getBean(
                                    PlaylistLibrary.class);

                    Playlist workingPlaylist =
                            playlistLibrary
                                    .findWorkingPlaylistByDate(
                                            java.time.LocalDate.parse(
                                                    "2026-08-14"));

                    assertNotNull(
                            workingPlaylist);
                    assertEquals(
                            "Friday — 08/14/2026",
                            workingPlaylist.getName());
                    assertEquals(
                            1,
                            workingPlaylist.getSongs()
                                    .size());
                    assertEquals(
                            "Hope",
                            workingPlaylist.getSongs()
                                    .get(0)
                                    .getTitle());
                });
    }

    @Test
    void createsEmptySavedServicePlaylist(
            @TempDir Path tempDir) {
        withContext(
                tempDir.resolve("saved-service-empty.db"),
                context -> {
                    PlaylistLibrary playlistLibrary =
                            context.getBean(PlaylistLibrary.class);

                    Playlist playlist =
                            playlistLibrary.createSavedServicePlaylist(
                                    "Sunday Service",
                                    "2026-08-16",
                                    null);

                    assertFalse(playlist.isReusable());
                    assertEquals("Sunday Service", playlist.getName());
                    assertEquals(
                            java.time.LocalDate.parse("2026-08-16"),
                            playlist.getServiceDate());
                    assertNull(playlist.getTheme());
                    assertNull(playlist.getSourcePlaylistId());
                    assertTrue(playlist.getSongs().isEmpty());
                });
    }

    @Test
    void persistsAndNormalizesSavedServiceTheme(
            @TempDir Path tempDir) {
        withContext(
                tempDir.resolve("saved-service-theme.db"),
                context -> {
                    PlaylistLibrary playlistLibrary =
                            context.getBean(PlaylistLibrary.class);

                    Playlist themedPlaylist =
                            playlistLibrary.createSavedServicePlaylist(
                                    "Grace Service",
                                    "2026-08-17",
                                    "  Grace  ");
                    Playlist blankThemePlaylist =
                            playlistLibrary.createSavedServicePlaylist(
                                    "Blank Theme Service",
                                    "2026-08-18",
                                    "   ");

                    assertEquals(
                            "Grace",
                            themedPlaylist.getTheme());
                    assertNull(blankThemePlaylist.getTheme());
                });
    }

    @Test
    void copiesReusablePlaylistAsIndependentSavedService(
            @TempDir Path tempDir) {
        withContext(
                tempDir.resolve("copy-reusable-playlist.db"),
                context -> {
                    SongLibrary songLibrary =
                            context.getBean(SongLibrary.class);
                    PlaylistLibrary playlistLibrary =
                            context.getBean(PlaylistLibrary.class);
                    Song songA = createSong(songLibrary, "A");
                    Song songB = createSong(songLibrary, "B");
                    Song songC = createSong(songLibrary, "C");
                    Playlist source = createReusablePlaylist(
                            playlistLibrary,
                            "Reusable Copy Source",
                            List.of(songA, songB, songC));

                    Playlist copied =
                            playlistLibrary.copyPlaylistForService(
                                    source.getId(),
                                    "Copied Sunday",
                                    "2026-08-23",
                                    null);

                    assertNotEquals(source.getId(), copied.getId());
                    assertFalse(copied.isReusable());
                    assertEquals(
                            source.getId(),
                            copied.getSourcePlaylistId());
                    assertEquals(
                            List.of("A", "B", "C"),
                            copied.getSongs().stream()
                                    .map(Song::getTitle)
                                    .toList());
                    assertEquals(
                            List.of("A", "B", "C"),
                            source.getSongs().stream()
                                    .map(Song::getTitle)
                                    .toList());
                });
    }

    @Test
    void copiesSavedPlaylistWithoutReusingItsTheme(
            @TempDir Path tempDir) {
        withContext(
                tempDir.resolve("copy-saved-playlist.db"),
                context -> {
                    SongLibrary songLibrary =
                            context.getBean(SongLibrary.class);
                    PlaylistLibrary playlistLibrary =
                            context.getBean(PlaylistLibrary.class);
                    Song songA = createSong(songLibrary, "Saved A");
                    Song songB = createSong(songLibrary, "Saved B");
                    Playlist source =
                            playlistLibrary.createSavedServicePlaylist(
                                    "Mercy Service",
                                    "2026-08-16",
                                    "Mercy");
                    playlistLibrary.addSongToPlaylist(source, songA);
                    playlistLibrary.addSongToPlaylist(source, songB);

                    Playlist blankThemeCopy =
                            playlistLibrary.copyPlaylistForService(
                                    source.getId(),
                                    "Mercy Follow Up",
                                    "2026-08-23",
                                    null);
                    Playlist themedCopy =
                            playlistLibrary.copyPlaylistForService(
                                    source.getId(),
                                    "Grace Follow Up",
                                    "2026-08-30",
                                    "Grace");

                    assertFalse(blankThemeCopy.isReusable());
                    assertEquals(
                            source.getId(),
                            blankThemeCopy.getSourcePlaylistId());
                    assertEquals(
                            List.of("Saved A", "Saved B"),
                            blankThemeCopy.getSongs().stream()
                                    .map(Song::getTitle)
                                    .toList());
                    assertEquals("Mercy", source.getTheme());
                    assertNull(blankThemeCopy.getTheme());
                    assertEquals("Grace", themedCopy.getTheme());
                });
    }

    @Test
    void keepsSourceIndependentAfterCopyChanges(
            @TempDir Path tempDir) {
        withContext(
                tempDir.resolve("copy-independence.db"),
                context -> {
                    SongLibrary songLibrary =
                            context.getBean(SongLibrary.class);
                    PlaylistLibrary playlistLibrary =
                            context.getBean(PlaylistLibrary.class);
                    Song songA = createSong(songLibrary, "First");
                    Song songB = createSong(songLibrary, "Second");
                    Song songC = createSong(songLibrary, "Third");
                    Playlist source = createReusablePlaylist(
                            playlistLibrary,
                            "Independent Source",
                            List.of(songA, songB, songC));
                    Playlist copied =
                            playlistLibrary.copyPlaylistForService(
                                    source.getId(),
                                    "Independent Copy",
                                    "2026-08-24",
                                    null);

                    copied = playlistLibrary.moveSong(
                            copied,
                            2,
                            0);
                    copied = playlistLibrary
                            .removeSongFromPlaylist(
                                    copied,
                                    songB);

                    assertEquals(
                            List.of("Third", "First"),
                            copied.getSongs().stream()
                                    .map(Song::getTitle)
                                    .toList());
                    assertEquals(
                            List.of("First", "Second", "Third"),
                            playlistLibrary.findPlaylistById(
                                    source.getId())
                                    .getSongs().stream()
                                    .map(Song::getTitle)
                                    .toList());
                });
    }

    @Test
    void updatesSavedServiceMetadataWithoutChangingSongs(
            @TempDir Path tempDir) {
        withContext(
                tempDir.resolve("saved-service-metadata.db"),
                context -> {
                    SongLibrary songLibrary =
                            context.getBean(SongLibrary.class);
                    PlaylistLibrary playlistLibrary =
                            context.getBean(PlaylistLibrary.class);
                    Song songA = createSong(songLibrary, "Opening");
                    Song songB = createSong(songLibrary, "Closing");
                    Playlist playlist =
                            playlistLibrary.createSavedServicePlaylist(
                                    "Original Service",
                                    "2026-08-16",
                                    null);
                    playlistLibrary.addSongToPlaylist(playlist, songA);
                    playlistLibrary.addSongToPlaylist(playlist, songB);

                    Playlist updated =
                            playlistLibrary.updatePlaylistMetadata(
                                    playlist.getId(),
                                    "Updated Service",
                                    "2026-08-17",
                                    "  Revival ");

                    assertEquals("Updated Service", updated.getName());
                    assertEquals(
                            java.time.LocalDate.parse("2026-08-17"),
                            updated.getServiceDate());
                    assertEquals("Revival", updated.getTheme());
                    assertEquals(
                            List.of("Opening", "Closing"),
                            updated.getSongs().stream()
                                    .map(Song::getTitle)
                                    .toList());
                });
    }

    @Test
    void retrievesSavedPlaylistByIdWithMetadataAndOrder(
            @TempDir Path tempDir) {
        withContext(
                tempDir.resolve("saved-service-get-by-id.db"),
                context -> {
                    SongLibrary songLibrary =
                            context.getBean(SongLibrary.class);
                    PlaylistLibrary playlistLibrary =
                            context.getBean(PlaylistLibrary.class);
                    Song songA = createSong(songLibrary, "First Song");
                    Song songB = createSong(songLibrary, "Second Song");
                    Playlist source = createReusablePlaylist(
                            playlistLibrary,
                            "Get Source",
                            List.of(songA, songB));
                    Playlist created =
                            playlistLibrary.copyPlaylistForService(
                                    source.getId(),
                                    "Retrieved Service",
                                    "2026-08-21",
                                    "Faith");

                    Playlist retrieved =
                            playlistLibrary.findPlaylistById(
                                    created.getId());

                    assertNotNull(retrieved);
                    assertEquals("Retrieved Service", retrieved.getName());
                    assertEquals(
                            java.time.LocalDate.parse("2026-08-21"),
                            retrieved.getServiceDate());
                    assertEquals("Faith", retrieved.getTheme());
                    assertEquals(source.getId(), retrieved.getSourcePlaylistId());
                    assertEquals(
                            List.of("First Song", "Second Song"),
                            retrieved.getSongs().stream()
                                    .map(Song::getTitle)
                                    .toList());
                });
    }

    @Test
    void validatesSavedServiceCreationAndCopyInput(
            @TempDir Path tempDir) {
        withContext(
                tempDir.resolve("saved-service-validation.db"),
                context -> {
                    PlaylistLibrary playlistLibrary =
                            context.getBean(PlaylistLibrary.class);

                    assertThrows(
                            IllegalArgumentException.class,
                            () -> playlistLibrary
                                    .createSavedServicePlaylist(
                                            " ",
                                            "2026-08-16",
                                            null));
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> playlistLibrary
                                    .createSavedServicePlaylist(
                                            "Missing Date",
                                            " ",
                                            null));
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> playlistLibrary
                                    .copyPlaylistForService(
                                            999L,
                                            "Missing Source",
                                            "2026-08-16",
                                            null));
                });
    }

    private void withContext(
            Path databaseFile,
            Consumer<ConfigurableApplicationContext> testBody) {
        try (ConfigurableApplicationContext context =
                     new SpringApplicationBuilder(
                             ChurchSongApiApplication.class)
                             .run(
                                     "--spring.profiles.active=test",
                                     "--spring.datasource.url=jdbc:sqlite:" + databaseFile,
                                     "--spring.jpa.hibernate.ddl-auto=update",
                                     "--spring.sql.init.mode=always",
                                     "--spring.main.banner-mode=off",
                                     "--spring.main.web-application-type=none")) {
            testBody.accept(context);
        }
    }

    private Song createSong(
            SongLibrary songLibrary,
            String title) {
        Song song = new Song(
                title,
                "Test Author",
                "[Verse 1]\nLine 1",
                SongType.SLOW);
        songLibrary.addSong(song);
        return song;
    }

    private Playlist createReusablePlaylist(
            PlaylistLibrary playlistLibrary,
            String name,
            List<Song> songs) {
        Playlist playlist = new Playlist(name);
        playlist.replaceSongs(songs);
        playlistLibrary.addPlaylist(playlist);
        return playlist;
    }
}
