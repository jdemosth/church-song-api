package com.churchsong.service;

import com.churchsong.dto.migration.SongLanguageSchemaMigrationReport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class SongLanguageSchemaMigrationService {

    private static final String SONG_TABLE_NAME = "song";
    private static final String TEMP_SONG_TABLE_NAME = "song__migration_new";
    private static final String OLD_LANGUAGE_CHECK =
            "language varchar(255) check ((language in ('ENGLISH','HAITIAN_CREOLE','SPANISH','UNKNOWN')))";
    private static final String NEW_LANGUAGE_CHECK =
            "language varchar(255) check ((language in ('ENGLISH','HAITIAN_CREOLE','SPANISH','FRENCH','UNKNOWN')))";
    private static final Pattern OLD_LANGUAGE_CHECK_PATTERN = Pattern.compile(
            "language\\s+varchar\\(255\\)\\s+check\\s*\\(\\(language\\s+in\\s*\\('ENGLISH','HAITIAN_CREOLE','SPANISH','UNKNOWN'\\)\\)\\)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SONG_TABLE_NAME_PATTERN = Pattern.compile(
            "^create\\s+table\\s+song\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public SongLanguageSchemaMigrationService(
            JdbcTemplate jdbcTemplate,
            DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    public SongLanguageSchemaMigrationReport migrateFrenchLanguageConstraint(
            boolean apply) {
        String originalSongTableSql = loadSongTableSql();
        String migratedSongTableSql = buildMigratedSongTableSql(originalSongTableSql);

        SongLanguageSchemaMigrationReport report =
                new SongLanguageSchemaMigrationReport(apply ? "APPLY" : "DRY RUN");
        report.setOriginalSongTableSql(originalSongTableSql);
        report.setNewSongTableSql(migratedSongTableSql);
        report.setPreCounts(loadCountsSnapshot());

        if (originalSongTableSql.contains("'FRENCH'")) {
            report.setAlreadyMigrated(true);
            report.setPostCounts(loadCountsSnapshot());
            report.setIntegrityCheckResult(loadIntegrityCheckResult());
            report.setForeignKeyViolationCount(loadForeignKeyViolationCount());
            return report;
        }

        if (!apply) {
            report.setPostCounts(loadCountsSnapshot());
            report.setIntegrityCheckResult(loadIntegrityCheckResult());
            report.setForeignKeyViolationCount(loadForeignKeyViolationCount());
            return report;
        }

        applyMigration(originalSongTableSql, migratedSongTableSql);

        report.setCommitted(true);
        report.setPostCounts(loadCountsSnapshot());
        report.setIntegrityCheckResult(loadIntegrityCheckResult());
        report.setForeignKeyViolationCount(loadForeignKeyViolationCount());
        return report;
    }

    private void applyMigration(
            String originalSongTableSql,
            String migratedSongTableSql) {
        String tempSongTableSql = SONG_TABLE_NAME_PATTERN.matcher(migratedSongTableSql)
                .replaceFirst("CREATE TABLE " + TEMP_SONG_TABLE_NAME);
        List<String> dependentSql = loadSongDependentSql();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                statement.execute("PRAGMA foreign_keys=OFF");
                statement.execute("drop table if exists " + TEMP_SONG_TABLE_NAME);
                statement.execute(tempSongTableSql);
                statement.execute(
                        """
                                insert into song__migration_new (
                                    id,
                                    author,
                                    lyrics,
                                    song_type,
                                    title,
                                    family_id,
                                    language,
                                    source_url
                                )
                                select
                                    id,
                                    author,
                                    lyrics,
                                    song_type,
                                    title,
                                    family_id,
                                    language,
                                    source_url
                                from song
                                """
                );
                statement.execute("drop table " + SONG_TABLE_NAME);
                statement.execute(
                        "alter table " + TEMP_SONG_TABLE_NAME + " rename to " + SONG_TABLE_NAME
                );
                for (String sql : dependentSql) {
                    statement.execute(sql);
                }

                validateConnectionState(connection);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw new IllegalStateException(
                        "Song language schema migration failed.",
                        exception
                );
            } finally {
                statement.execute("PRAGMA foreign_keys=ON");
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to apply song language schema migration.",
                    exception
            );
        }
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

    private String loadSongTableSql() {
        String sql = jdbcTemplate.queryForObject(
                "select sql from sqlite_master where type = 'table' and name = ?",
                String.class,
                SONG_TABLE_NAME
        );
        if (sql == null || sql.isBlank()) {
            throw new IllegalStateException("Could not load song table schema.");
        }
        return sql;
    }

    private String buildMigratedSongTableSql(String originalSongTableSql) {
        if (originalSongTableSql.contains("'FRENCH'")) {
            return originalSongTableSql;
        }

        String replaced = OLD_LANGUAGE_CHECK_PATTERN.matcher(originalSongTableSql)
                .replaceFirst(NEW_LANGUAGE_CHECK);
        if (replaced.equals(originalSongTableSql)) {
            throw new IllegalStateException(
                    "Could not find the existing song.language CHECK constraint to migrate."
            );
        }
        return replaced;
    }

    private List<String> loadSongDependentSql() {
        return jdbcTemplate.query(
                """
                        select sql
                        from sqlite_master
                        where tbl_name = ?
                          and type in ('index', 'trigger')
                          and sql is not null
                        order by type, name
                        """,
                (resultSet, rowNum) -> resultSet.getString("sql"),
                SONG_TABLE_NAME
        );
    }

    private SongLanguageSchemaMigrationReport.CountsSnapshot loadCountsSnapshot() {
        SongLanguageSchemaMigrationReport.CountsSnapshot snapshot =
                new SongLanguageSchemaMigrationReport.CountsSnapshot();
        snapshot.setSongCount(queryCount("select count(*) from song"));
        snapshot.setFamilyCount(queryCount("select count(*) from song_family"));
        snapshot.setPlaylistSongCount(queryCount("select count(*) from playlist_songs"));
        snapshot.setServicePlanSongCount(queryCount("select count(*) from service_plan_songs"));
        snapshot.setMinSongId(queryNullableInt("select min(id) from song"));
        snapshot.setMaxSongId(queryNullableInt("select max(id) from song"));
        snapshot.setLanguageCounts(loadLanguageCounts());
        return snapshot;
    }

    private Map<String, Integer> loadLanguageCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        jdbcTemplate.query(
                "select language, count(*) as song_count from song group by language order by language",
                resultSet -> {
                    String language = resultSet.getString("language");
                    if (language == null) {
                        language = "NULL";
                    }
                    counts.put(language, resultSet.getInt("song_count"));
                }
        );
        return counts;
    }

    private int loadForeignKeyViolationCount() {
        return jdbcTemplate.query(
                "PRAGMA foreign_key_check",
                (resultSet, rowNum) -> 1
        ).size();
    }

    private String loadIntegrityCheckResult() {
        String result = jdbcTemplate.queryForObject(
                "PRAGMA integrity_check",
                String.class
        );
        return result == null ? "(null)" : result;
    }

    private int queryCount(String sql) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count == null ? 0 : count;
    }

    private Integer queryNullableInt(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
