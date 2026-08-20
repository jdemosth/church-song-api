package com.churchsong;

import com.churchsong.model.Playlist;
import com.churchsong.service.PlaylistLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaylistSongsSchemaMigrationTests {

    @Test
    void migratesLegacyPlaylistSongsTableOnStartup(
            @TempDir Path tempDir) throws Exception {
        Path databaseFile = tempDir.resolve("legacy-playlist-songs.db");
        seedLegacyDatabase(databaseFile);

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
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            PlaylistLibrary playlistLibrary = context.getBean(PlaylistLibrary.class);

            String schemaSql = jdbcTemplate.queryForObject(
                    "select sql from sqlite_master where type = 'table' and name = 'playlist_songs'",
                    String.class
            );

            assertNotNull(schemaSql);
            assertTrue(schemaSql.contains("songs_id integer not null"));
            assertTrue(schemaSql.contains("song_order integer not null"));
            assertTrue(schemaSql.toLowerCase().contains("primary key (playlist_id, song_order)"));

            Playlist playlist = playlistLibrary.findPlaylistById(1L);
            assertNotNull(playlist);
            assertEquals(3, playlist.getSongs().size());
            assertEquals(12, playlist.getSongs().get(0).getId());
            assertEquals(13, playlist.getSongs().get(1).getId());
            assertEquals(11, playlist.getSongs().get(2).getId());

            assertEquals(
                    0,
                    jdbcTemplate.queryForObject(
                            "select count(*) from pragma_foreign_key_check",
                            Integer.class
                    )
            );
        }
    }

    private void seedLegacyDatabase(Path databaseFile)
            throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
             Statement statement = connection.createStatement()) {
            statement.execute("create table playlist (id integer primary key, name varchar(255), reusable boolean, service_date date, source_playlist_id bigint, theme varchar(255))");
            statement.execute("create table song (id integer primary key, author varchar(255), family_id integer, language varchar(255), lyrics varchar(255), song_type varchar(255), source_url varchar(255), title varchar(255))");
            statement.execute("create table playlist_songs (playlist_id bigint not null, songs_id integer not null, song_order integer)");

            statement.execute("insert into playlist (id, name, reusable, service_date, source_playlist_id, theme) values (1, 'Legacy Playlist', 1, null, null, null)");
            statement.execute("insert into song (id, author, family_id, language, lyrics, song_type, source_url, title) values (11, 'A', null, 'ENGLISH', 'Lyrics A', 'FAST', null, 'Song A')");
            statement.execute("insert into song (id, author, family_id, language, lyrics, song_type, source_url, title) values (12, 'B', null, 'ENGLISH', 'Lyrics B', 'SLOW', null, 'Song B')");
            statement.execute("insert into song (id, author, family_id, language, lyrics, song_type, source_url, title) values (13, 'C', null, 'ENGLISH', 'Lyrics C', 'FAST', null, 'Song C')");

            statement.execute("insert into playlist_songs (playlist_id, songs_id, song_order) values (1, 11, null)");
            statement.execute("insert into playlist_songs (playlist_id, songs_id, song_order) values (1, 12, 5)");
            statement.execute("insert into playlist_songs (playlist_id, songs_id, song_order) values (1, 13, 9)");
        }
    }
}
