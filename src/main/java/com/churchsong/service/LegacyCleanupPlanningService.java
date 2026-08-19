package com.churchsong.service;

import com.churchsong.dto.cleanup.LegacyCleanupPlanReport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class LegacyCleanupPlanningService {

    private static final Set<Integer> PROTECTED_FAMILY_IDS = Set.of(
            91235,
            91236,
            91237,
            91238,
            91239
    );
    private static final Set<Integer> INSPECT_FAMILY_IDS = Set.of(9, 77);
    private static final Set<Integer> AMAZING_GRACE_IDS = Set.of(30, 34);
    private static final Set<Integer> DEMO_SONG_IDS = Set.of(1, 2, 4, 5, 22, 23, 24, 25, 26);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public LegacyCleanupPlanningService(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public LegacyCleanupPlanReport planLegacyCleanup(boolean apply) {
        LegacyCleanupSnapshot snapshot = inspectSnapshot();
        LegacyCleanupPlanReport report = buildReport(snapshot, apply);

        if (!apply) {
            report.setCommitted(false);
            return report;
        }

        transactionTemplate.executeWithoutResult(status -> {
            if (!snapshot.safeFamilyDeletes().isEmpty()) {
                jdbcTemplate.update(
                        "delete from song_family where id in (%s)".formatted(joinIds(snapshot.safeFamilyDeletes()))
                );
            }
        });
        report.setCommitted(true);
        return report;
    }

    private LegacyCleanupSnapshot inspectSnapshot() {
        Map<Integer, FamilyInspection> families = loadFamilies();
        Map<Integer, SongInspection> amazingGraceSongs = loadSongs(AMAZING_GRACE_IDS);
        Map<Integer, SongInspection> demoSongs = loadSongs(DEMO_SONG_IDS);

        List<Integer> safeFamilyDeletes = new ArrayList<>();
        List<String> emptyFamilyEntries = new ArrayList<>();
        List<String> safeDeletionEntries = new ArrayList<>();
        List<String> reviewEntries = new ArrayList<>();

        for (Integer familyId : List.of(77, 9)) {
            FamilyInspection family = families.get(familyId);
            if (family == null) {
                continue;
            }

            boolean safeToDelete = familyId == 77
                    && family.memberSongIds().isEmpty()
                    && family.sourceFamilyKey() != null
                    && !family.sourceFamilyKey().isBlank();
            String status = safeToDelete ? "SAFE_TO_DELETE" : "REVIEW";
            String relationNote = familyId == 9
                    ? " | possibleLegacyPredecessorOf=91237"
                    : "";

            String entry = "familyId=" + family.id()
                    + " | canonicalTitle=" + family.canonicalTitle()
                    + " | sourceFamilyKey=" + family.sourceFamilyKey()
                    + " | memberSongIds=" + family.memberSongIds()
                    + " | directReferences=none"
                    + relationNote
                    + " | status=" + status;
            emptyFamilyEntries.add(entry);

            if (safeToDelete) {
                safeFamilyDeletes.add(family.id());
                safeDeletionEntries.add(
                        "familyId=" + family.id()
                                + " | canonicalTitle=" + family.canonicalTitle()
                                + " | reason=empty legacy family shell"
                );
            } else {
                reviewEntries.add(
                        "familyId=" + family.id()
                                + " | canonicalTitle=" + family.canonicalTitle()
                                + " | reason="
                                + (familyId == 9
                                ? "empty but title matches protected family 91237"
                                : "not proven safe")
                );
            }
        }

        boolean exactAmazingGraceMatch = amazingGraceSongs.size() == 2
                && amazingGraceSongs.values().stream()
                .map(song -> song.title() + "|" + song.author() + "|" + song.language() + "|"
                        + song.songType() + "|" + song.familyId() + "|" + song.sourceUrl() + "|" + song.lyrics())
                .distinct()
                .count() == 1;
        Integer canonicalAmazingGraceId = exactAmazingGraceMatch ? 30 : null;
        if (canonicalAmazingGraceId != null) {
            reviewEntries.add(
                    "Amazing Grace duplicates | keepSongId=" + canonicalAmazingGraceId
                            + " | reviewDuplicateSongId=34"
            );
        }

        List<String> protectedFamilyEntries = PROTECTED_FAMILY_IDS.stream()
                .sorted()
                .map(id -> "familyId=" + id)
                .toList();

        return new LegacyCleanupSnapshot(
                families,
                amazingGraceSongs,
                demoSongs,
                safeFamilyDeletes,
                emptyFamilyEntries,
                safeDeletionEntries,
                reviewEntries,
                protectedFamilyEntries,
                canonicalAmazingGraceId,
                exactAmazingGraceMatch
        );
    }

    private LegacyCleanupPlanReport buildReport(
            LegacyCleanupSnapshot snapshot,
            boolean apply) {
        LegacyCleanupPlanReport report = new LegacyCleanupPlanReport(apply ? "APPLY" : "DRY RUN");
        snapshot.emptyFamilyEntries().forEach(report::addEmptyFamily);

        for (Integer songId : List.of(30, 34)) {
            SongInspection song = snapshot.amazingGraceSongs().get(songId);
            if (song == null) {
                continue;
            }

            report.addAmazingGraceDuplicate(
                    "songId=" + song.id()
                            + " | title=" + song.title()
                            + " | author=" + song.author()
                            + " | language=" + song.language()
                            + " | songType=" + song.songType()
                            + " | familyId=" + song.familyId()
                            + " | sourceUrl=" + song.sourceUrl()
                            + " | lyrics=" + song.lyrics()
                            + " | playlistRefs=" + song.playlistReferences()
                            + " | servicePlanRefs=" + song.servicePlanReferences()
                            + " | exactDuplicatePair="
                            + snapshot.exactAmazingGraceMatch()
                            + " | proposedCanonical="
                            + snapshot.canonicalAmazingGraceId()
            );
        }

        for (SongInspection song : snapshot.demoSongs().values()) {
            report.addPlaylistReference(
                    "songId=" + song.id()
                            + " | title=" + song.title()
                            + " | playlistRefs=" + song.playlistReferences()
                            + " | servicePlanRefs=" + song.servicePlanReferences()
            );
        }
        for (SongInspection song : snapshot.amazingGraceSongs().values()) {
            report.addPlaylistReference(
                    "songId=" + song.id()
                            + " | title=" + song.title()
                            + " | playlistRefs=" + song.playlistReferences()
                            + " | servicePlanRefs=" + song.servicePlanReferences()
            );
        }

        snapshot.safeDeletionEntries().forEach(report::addSafeDeletion);
        snapshot.reviewEntries().forEach(report::addNeedsReview);
        snapshot.protectedFamilyEntries().forEach(report::addProtectedFamily);
        return report;
    }

    private Map<Integer, FamilyInspection> loadFamilies() {
        Map<Integer, FamilyInspection> families = new LinkedHashMap<>();
        jdbcTemplate.query(
                """
                        select id, canonical_title, source_family_key
                        from song_family
                        where id in (9, 77)
                        order by id
                        """,
                resultSet -> {
                    int familyId = resultSet.getInt("id");
                    List<Integer> memberSongIds = jdbcTemplate.query(
                            """
                                    select id
                                    from song
                                    where family_id = ?
                                    order by id
                                    """,
                            (memberResultSet, rowNum) -> memberResultSet.getInt("id"),
                            familyId
                    );
                    families.put(
                            familyId,
                            new FamilyInspection(
                                    familyId,
                                    resultSet.getString("canonical_title"),
                                    resultSet.getString("source_family_key"),
                                    memberSongIds
                            )
                    );
                }
        );
        return families;
    }

    private Map<Integer, SongInspection> loadSongs(Set<Integer> ids) {
        Map<Integer, SongInspection> songs = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return songs;
        }

        jdbcTemplate.query(
                """
                        select id, title, author, lyrics, language, song_type, family_id, source_url
                        from song
                        where id in (%s)
                        order by id
                        """.formatted(joinIds(ids)),
                resultSet -> {
                    int songId = resultSet.getInt("id");
                    songs.put(
                            songId,
                            new SongInspection(
                                    songId,
                                    resultSet.getString("title"),
                                    resultSet.getString("author"),
                                    resultSet.getString("lyrics"),
                                    resultSet.getString("language"),
                                    resultSet.getString("song_type"),
                                    (Integer) resultSet.getObject("family_id"),
                                    resultSet.getString("source_url"),
                                    findPlaylistReferences(songId),
                                    findServicePlanReferences(songId)
                            )
                    );
                }
        );
        return songs;
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

    private String joinIds(Iterable<Integer> ids) {
        List<String> values = new ArrayList<>();
        for (Integer id : ids) {
            values.add(String.valueOf(id));
        }
        return String.join(", ", values);
    }

    private record LegacyCleanupSnapshot(
            Map<Integer, FamilyInspection> families,
            Map<Integer, SongInspection> amazingGraceSongs,
            Map<Integer, SongInspection> demoSongs,
            List<Integer> safeFamilyDeletes,
            List<String> emptyFamilyEntries,
            List<String> safeDeletionEntries,
            List<String> reviewEntries,
            List<String> protectedFamilyEntries,
            Integer canonicalAmazingGraceId,
            boolean exactAmazingGraceMatch) {
    }

    private record FamilyInspection(
            int id,
            String canonicalTitle,
            String sourceFamilyKey,
            List<Integer> memberSongIds) {
    }

    private record SongInspection(
            int id,
            String title,
            String author,
            String lyrics,
            String language,
            String songType,
            Integer familyId,
            String sourceUrl,
            List<String> playlistReferences,
            List<String> servicePlanReferences) {
    }
}
