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
                    assertNotEquals(
                            firstWorkingPlaylist.getId(),
                            replacementPlaylist.getId());
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

    private void withContext(
            Path databaseFile,
            Consumer<ConfigurableApplicationContext> testBody) {
        try (ConfigurableApplicationContext context =
                     new SpringApplicationBuilder(
                             ChurchSongApiApplication.class)
                             .properties(
                                     "spring.datasource.url=jdbc:sqlite:" + databaseFile,
                                     "spring.jpa.hibernate.ddl-auto=update",
                                     "spring.sql.init.mode=always",
                                     "spring.main.banner-mode=off")
                             .run()) {
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
