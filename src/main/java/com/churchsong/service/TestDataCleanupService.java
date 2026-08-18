package com.churchsong.service;

import com.churchsong.dto.cleanup.CleanupCandidateFamily;
import com.churchsong.dto.cleanup.CleanupCandidateSong;
import com.churchsong.dto.cleanup.TestDataCleanupReport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TestDataCleanupService {

    private static final String TEST_TITLE_PREFIX = "Unit Test ";
    private static final Set<Integer> PROTECTED_MULTILINGUAL_FAMILY_IDS = Set.of(
            91235,
            91236,
            91237,
            91238,
            91239
    );

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public TestDataCleanupService(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public TestDataCleanupReport cleanupAutomatedTestData(boolean apply) {
        CleanupSnapshot snapshot = inspectCleanupCandidates();
        TestDataCleanupReport report = buildReport(snapshot, apply);

        if (!apply) {
            report.setCommitted(false);
            return report;
        }

        transactionTemplate.executeWithoutResult(status -> {
            deleteSafeTestData(snapshot.safeSongIds(), snapshot.safeFamilyIds());
        });
        report.setCommitted(true);
        return report;
    }

    private CleanupSnapshot inspectCleanupCandidates() {
        List<CleanupSongRow> candidateSongs = jdbcTemplate.query(
                """
                        select id, title, family_id, language, song_type
                        from song
                        where title like ?
                        order by id
                        """,
                (resultSet, rowNum) -> new CleanupSongRow(
                        resultSet.getInt("id"),
                        resultSet.getString("title"),
                        (Integer) resultSet.getObject("family_id"),
                        resultSet.getString("language"),
                        resultSet.getString("song_type")
                ),
                TEST_TITLE_PREFIX + "%"
        );

        Set<Integer> candidateSongIds = candidateSongs.stream()
                .map(CleanupSongRow::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Integer> candidateFamilyIds = candidateSongs.stream()
                .map(CleanupSongRow::familyId)
                .filter(familyId -> familyId != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<CleanupCandidateSong> detailedSongs = new ArrayList<>();
        Set<Integer> safeSongIds = new LinkedHashSet<>();

        for (CleanupSongRow candidateSong : candidateSongs) {
            List<String> playlistReferences = findPlaylistReferences(candidateSong.id());
            List<String> servicePlanReferences = findServicePlanReferences(candidateSong.id());
            boolean safeToDelete = playlistReferences.isEmpty()
                    && servicePlanReferences.isEmpty();

            if (safeToDelete) {
                safeSongIds.add(candidateSong.id());
            }

            detailedSongs.add(
                    new CleanupCandidateSong(
                            candidateSong.id(),
                            candidateSong.title(),
                            candidateSong.familyId(),
                            candidateSong.language(),
                            candidateSong.songType(),
                            playlistReferences,
                            servicePlanReferences,
                            safeToDelete
                    )
            );
        }

        List<CleanupCandidateFamily> detailedFamilies = new ArrayList<>();
        Set<Integer> safeFamilyIds = new LinkedHashSet<>();

        for (Integer familyId : candidateFamilyIds) {
            CleanupFamilyRow familyRow = jdbcTemplate.queryForObject(
                    """
                            select id, canonical_title, source_family_key
                            from song_family
                            where id = ?
                            """,
                    (resultSet, rowNum) -> new CleanupFamilyRow(
                            resultSet.getInt("id"),
                            resultSet.getString("canonical_title"),
                            resultSet.getString("source_family_key")
                    ),
                    familyId
            );

            List<Integer> memberSongIds = jdbcTemplate.query(
                    """
                            select id
                            from song
                            where family_id = ?
                            order by id
                            """,
                    (resultSet, rowNum) -> resultSet.getInt("id"),
                    familyId
            );

            boolean allMembersAreCandidateSongs = !memberSongIds.isEmpty()
                    && candidateSongIds.containsAll(memberSongIds);
            boolean allMembersAreSafeSongs = !memberSongIds.isEmpty()
                    && safeSongIds.containsAll(memberSongIds);
            boolean titleMarkedAsTest = familyRow.canonicalTitle() != null
                    && familyRow.canonicalTitle().contains(TEST_TITLE_PREFIX.trim());
            boolean protectedFamily = PROTECTED_MULTILINGUAL_FAMILY_IDS.contains(familyId);
            boolean safeToDelete = !protectedFamily
                    && allMembersAreCandidateSongs
                    && allMembersAreSafeSongs
                    && titleMarkedAsTest;

            if (safeToDelete) {
                safeFamilyIds.add(familyId);
            }

            detailedFamilies.add(
                    new CleanupCandidateFamily(
                            familyRow.id(),
                            familyRow.canonicalTitle(),
                            familyRow.sourceFamilyKey(),
                            memberSongIds,
                            safeToDelete
                    )
            );
        }

        List<String> protectedSongs = findProtectedSongs();
        List<String> protectedFamilies = findProtectedFamilies();

        return new CleanupSnapshot(
                detailedSongs,
                detailedFamilies,
                protectedSongs,
                protectedFamilies,
                safeSongIds,
                safeFamilyIds
        );
    }

    private TestDataCleanupReport buildReport(
            CleanupSnapshot snapshot,
            boolean apply) {
        TestDataCleanupReport report = new TestDataCleanupReport(
                apply ? "APPLY" : "DRY RUN"
        );

        for (CleanupCandidateSong candidateSong : snapshot.candidateSongs()) {
            report.addCandidateSong(candidateSong);
        }

        for (CleanupCandidateFamily candidateFamily : snapshot.candidateFamilies()) {
            report.addCandidateFamily(candidateFamily);
        }

        for (String protectedSong : snapshot.protectedSongs()) {
            report.addProtectedSong(protectedSong);
        }

        for (String protectedFamily : snapshot.protectedFamilies()) {
            report.addProtectedFamily(protectedFamily);
        }

        return report;
    }

    private List<String> findPlaylistReferences(int songId) {
        return jdbcTemplate.query(
                """
                        select p.id, p.name, ps.song_order
                        from playlist_songs ps
                        join playlist p on p.id = ps.playlist_id
                        where ps.songs_id = ?
                        order by p.id, ps.song_order
                        """,
                (resultSet, rowNum) -> "playlistId="
                        + resultSet.getLong("id")
                        + ", name="
                        + resultSet.getString("name")
                        + ", order="
                        + resultSet.getInt("song_order"),
                songId
        );
    }

    private List<String> findServicePlanReferences(int songId) {
        return jdbcTemplate.query(
                """
                        select sp.id, sp.service_name, sp.service_date, sp.service_time, sps.song_order
                        from service_plan_songs sps
                        join service_plans sp on sp.id = sps.service_plan_id
                        where sps.song_id = ?
                        order by sp.id, sps.song_order
                        """,
                (resultSet, rowNum) -> "servicePlanId="
                        + resultSet.getLong("id")
                        + ", serviceName="
                        + resultSet.getString("service_name")
                        + ", serviceDate="
                        + resultSet.getString("service_date")
                        + ", serviceTime="
                        + resultSet.getString("service_time")
                        + ", order="
                        + resultSet.getInt("song_order"),
                songId
        );
    }

    private List<String> findProtectedSongs() {
        return jdbcTemplate.query(
                """
                        select id, title, family_id, language, song_type
                        from song
                        where family_id in (91235, 91236, 91237, 91238, 91239)
                        order by id
                        """,
                (resultSet, rowNum) -> "ID "
                        + resultSet.getInt("id")
                        + " | "
                        + resultSet.getString("title")
                        + " | familyId="
                        + resultSet.getInt("family_id")
                        + " | language="
                        + resultSet.getString("language")
                        + " | songType="
                        + resultSet.getString("song_type")
        );
    }

    private List<String> findProtectedFamilies() {
        return jdbcTemplate.query(
                """
                        select id, canonical_title, source_family_key
                        from song_family
                        where id in (91235, 91236, 91237, 91238, 91239)
                        order by id
                        """,
                (resultSet, rowNum) -> "ID "
                        + resultSet.getInt("id")
                        + " | "
                        + resultSet.getString("canonical_title")
                        + " | sourceFamilyKey="
                        + resultSet.getString("source_family_key")
        );
    }

    private void deleteSafeTestData(
            Set<Integer> safeSongIds,
            Set<Integer> safeFamilyIds) {
        if (!safeSongIds.isEmpty()) {
            String songIdPlaceholders = safeSongIds.stream()
                    .map(ignored -> "?")
                    .collect(Collectors.joining(", "));
            Object[] songIds = safeSongIds.toArray();

            jdbcTemplate.update(
                    "delete from playlist_songs where songs_id in (" + songIdPlaceholders + ")",
                    songIds
            );
            jdbcTemplate.update(
                    "delete from service_plan_songs where song_id in (" + songIdPlaceholders + ")",
                    songIds
            );
            jdbcTemplate.update(
                    "delete from song where id in (" + songIdPlaceholders + ")",
                    songIds
            );
        }

        if (!safeFamilyIds.isEmpty()) {
            String familyIdPlaceholders = safeFamilyIds.stream()
                    .map(ignored -> "?")
                    .collect(Collectors.joining(", "));
            jdbcTemplate.update(
                    "delete from song_family where id in (" + familyIdPlaceholders + ")",
                    safeFamilyIds.toArray()
            );
        }
    }

    private record CleanupSongRow(
            int id,
            String title,
            Integer familyId,
            String language,
            String songType) {
    }

    private record CleanupFamilyRow(
            int id,
            String canonicalTitle,
            String sourceFamilyKey) {
    }

    private record CleanupSnapshot(
            List<CleanupCandidateSong> candidateSongs,
            List<CleanupCandidateFamily> candidateFamilies,
            List<String> protectedSongs,
            List<String> protectedFamilies,
            Set<Integer> safeSongIds,
            Set<Integer> safeFamilyIds) {
    }
}
