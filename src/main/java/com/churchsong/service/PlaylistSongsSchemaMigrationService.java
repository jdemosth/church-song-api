package com.churchsong.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PlaylistSongsSchemaMigrationService {

    private static final String TABLE_NAME = "playlist_songs";
    private static final String TEMP_TABLE_NAME = "playlist_songs__migration_new";
    private static final String EXPECTED_TABLE_SQL = """
            CREATE TABLE playlist_songs (
                playlist_id bigint not null,
                songs_id integer not null,
                song_order integer not null,
                primary key (playlist_id, song_order)
            )
            """;

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public PlaylistSongsSchemaMigrationService(
            JdbcTemplate jdbcTemplate,
            DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    public void migrateIfNeeded() {
        String existingTableSql = loadTableSql();
        if (existingTableSql == null || existingTableSql.isBlank()) {
            return;
        }

        if (isExpectedSchema(existingTableSql)) {
            return;
        }

        applyMigration();
    }

    private String loadTableSql() {
        List<String> results = jdbcTemplate.query(
                "select sql from sqlite_master where type = 'table' and name = ?",
                (resultSet, rowNum) -> resultSet.getString("sql"),
                TABLE_NAME
        );

        if (results.isEmpty()) {
            return null;
        }

        return results.get(0);
    }

    private boolean isExpectedSchema(String sql) {
        String normalized = normalizeSql(sql);
        return normalized.contains("playlist_id bigint not null")
                && normalized.contains("songs_id integer not null")
                && normalized.contains("song_order integer not null")
                && normalized.contains("primary key (playlist_id, song_order)");
    }

    private String normalizeSql(String sql) {
        return sql.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void applyMigration() {
        List<PlaylistSongRow> rows = loadRowsForMigration();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                statement.execute("PRAGMA foreign_keys=OFF");
                statement.execute("drop table if exists " + TEMP_TABLE_NAME);
                statement.execute(EXPECTED_TABLE_SQL.replace(TABLE_NAME, TEMP_TABLE_NAME));

                for (PlaylistSongRow row : rows) {
                    statement.execute(
                            "insert into " + TEMP_TABLE_NAME
                                    + " (playlist_id, songs_id, song_order) values ("
                                    + row.playlistId() + ", "
                                    + row.songId() + ", "
                                    + row.songOrder() + ")"
                    );
                }

                statement.execute("drop table " + TABLE_NAME);
                statement.execute("alter table " + TEMP_TABLE_NAME + " rename to " + TABLE_NAME);

                validateConnectionState(connection);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw new IllegalStateException(
                        "Playlist songs schema migration failed.",
                        exception
                );
            } finally {
                statement.execute("PRAGMA foreign_keys=ON");
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to apply playlist songs schema migration.",
                    exception
            );
        }
    }

    private List<PlaylistSongRow> loadRowsForMigration() {
        List<LegacyPlaylistSongRow> legacyRows = jdbcTemplate.query(
                """
                        select playlist_id, songs_id, song_order, rowid
                        from playlist_songs
                        order by playlist_id,
                                 case when song_order is null then 1 else 0 end,
                                 song_order,
                                 rowid
                        """,
                (resultSet, rowNum) -> new LegacyPlaylistSongRow(
                        resultSet.getLong("playlist_id"),
                        resultSet.getInt("songs_id"),
                        resultSet.getObject("song_order") == null
                                ? null
                                : resultSet.getInt("song_order"),
                        resultSet.getLong("rowid")
                )
        );

        List<PlaylistSongRow> normalizedRows = new ArrayList<>();
        Long currentPlaylistId = null;
        int nextSongOrder = 0;

        for (LegacyPlaylistSongRow legacyRow : legacyRows) {
            if (!legacyRow.playlistId().equals(currentPlaylistId)) {
                currentPlaylistId = legacyRow.playlistId();
                nextSongOrder = 0;
            }

            normalizedRows.add(
                    new PlaylistSongRow(
                            legacyRow.playlistId(),
                            legacyRow.songId(),
                            nextSongOrder
                    )
            );
            nextSongOrder++;
        }

        return normalizedRows;
    }

    private void validateConnectionState(Connection connection)
            throws SQLException {
        try (Statement validation = connection.createStatement()) {
            try (ResultSet integrityResult = validation.executeQuery(
                    "PRAGMA integrity_check")) {
                if (!integrityResult.next()
                        || !"ok".equalsIgnoreCase(integrityResult.getString(1))) {
                    throw new IllegalStateException("Integrity check failed during migration.");
                }
            }

            try (ResultSet foreignKeyResult = validation.executeQuery(
                    "PRAGMA foreign_key_check")) {
                if (foreignKeyResult.next()) {
                    throw new IllegalStateException(
                            "Foreign key violations were detected during migration."
                    );
                }
            }
        }
    }

    private record LegacyPlaylistSongRow(
            Long playlistId,
            int songId,
            Integer songOrder,
            long rowId) {
    }

    private record PlaylistSongRow(
            long playlistId,
            int songId,
            int songOrder) {
    }
}
