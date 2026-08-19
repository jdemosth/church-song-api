package com.churchsong.service;

import com.churchsong.dto.normalization.MultilingualFamilyNormalizationReport;
import com.churchsong.dto.normalization.NormalizationCandidateSong;
import com.churchsong.dto.normalization.NormalizationDuplicateConflict;
import com.churchsong.dto.normalization.NormalizationReferenceUpdate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MultilingualFamilyNormalizationService {

    private static final int LEGACY_FAMILY_ID = 12;
    private static final int CANONICAL_FAMILY_ID = 91235;
    private static final Set<Integer> PROTECTED_CANONICAL_SONG_IDS = Set.of(65, 66, 67);
    private static final Map<Integer, Integer> LEGACY_TO_CANONICAL_SONG_IDS = Map.of(
            27, 67,
            31, 67,
            28, 66,
            32, 66,
            29, 65,
            33, 65
    );

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public MultilingualFamilyNormalizationService(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public MultilingualFamilyNormalizationReport normalizeLegacyDuplicateFamily(
            boolean apply) {
        NormalizationSnapshot snapshot = inspectNormalizationPlan();
        MultilingualFamilyNormalizationReport report = buildReport(snapshot, apply);

        if (!apply) {
            report.setCommitted(false);
            return report;
        }

        transactionTemplate.executeWithoutResult(status -> {
            validateApplyPreconditions();
            applyPlaylistPlan(snapshot.playlistPlans());
            applyServicePlanPlan(snapshot.servicePlanPlans());
            validateNoRemainingReferences(snapshot.legacySongIds());
            deleteLegacySongs(snapshot.legacySongIds());
            deleteLegacyFamilyIfEmpty();
        });

        report.setCommitted(true);
        return report;
    }

    private NormalizationSnapshot inspectNormalizationPlan() {
        validateInspectionPreconditions();

        List<SongRow> legacySongs = loadSongsByIds(
                new ArrayList<>(LEGACY_TO_CANONICAL_SONG_IDS.keySet())
        );
        List<SongRow> canonicalSongs = loadSongsByIds(
                new ArrayList<>(PROTECTED_CANONICAL_SONG_IDS)
        );

        List<RelationshipRow> playlistRows = loadPlaylistRows();
        List<RelationshipRow> servicePlanRows = loadServicePlanRows();

        List<ContextMigrationPlan> playlistPlans = buildContextPlans(
                playlistRows,
                "playlist",
                "playlistId",
                "playlist_songs",
                "songs_id"
        );
        List<ContextMigrationPlan> servicePlanPlans = buildContextPlans(
                servicePlanRows,
                "service plan",
                "servicePlanId",
                "service_plan_songs",
                "song_id"
        );

        Map<Integer, List<String>> playlistReferencesBySongId = collectReferences(
                playlistPlans,
                true
        );
        Map<Integer, List<String>> servicePlanReferencesBySongId = collectReferences(
                servicePlanPlans,
                false
        );
        Map<Integer, List<String>> otherReferencesBySongId = collectOtherReferences();

        List<NormalizationCandidateSong> candidateSongs = new ArrayList<>();
        for (SongRow legacySong : legacySongs) {
            List<String> playlistReferences = playlistReferencesBySongId.getOrDefault(
                    legacySong.id(),
                    List.of()
            );
            List<String> servicePlanReferences = servicePlanReferencesBySongId.getOrDefault(
                    legacySong.id(),
                    List.of()
            );
            List<String> otherReferences = otherReferencesBySongId.getOrDefault(
                    legacySong.id(),
                    List.of()
            );
            boolean safeToRemoveAfterMigration = otherReferences.isEmpty();

            candidateSongs.add(
                    new NormalizationCandidateSong(
                            legacySong.id(),
                            legacySong.title(),
                            legacySong.language(),
                            legacySong.familyId(),
                            LEGACY_TO_CANONICAL_SONG_IDS.get(legacySong.id()),
                            playlistReferences,
                            servicePlanReferences,
                            otherReferences,
                            safeToRemoveAfterMigration
                    )
            );
        }

        List<NormalizationReferenceUpdate> relationshipUpdates = new ArrayList<>();
        List<NormalizationDuplicateConflict> duplicateConflicts = new ArrayList<>();

        for (ContextMigrationPlan plan : playlistPlans) {
            relationshipUpdates.addAll(plan.updates());
            duplicateConflicts.addAll(plan.duplicateConflicts());
        }

        for (ContextMigrationPlan plan : servicePlanPlans) {
            relationshipUpdates.addAll(plan.updates());
            duplicateConflicts.addAll(plan.duplicateConflicts());
        }

        boolean familyRemovableAfterMigration =
                candidateSongs.stream().allMatch(NormalizationCandidateSong::isSafeToRemoveAfterMigration)
                        && countSongsInFamily(LEGACY_FAMILY_ID) == LEGACY_TO_CANONICAL_SONG_IDS.size();

        return new NormalizationSnapshot(
                legacySongs,
                canonicalSongs,
                candidateSongs,
                playlistPlans,
                servicePlanPlans,
                relationshipUpdates,
                duplicateConflicts,
                new LinkedHashSet<>(findOtherSongReferenceTables()),
                familyRemovableAfterMigration
        );
    }

    private MultilingualFamilyNormalizationReport buildReport(
            NormalizationSnapshot snapshot,
            boolean apply) {
        MultilingualFamilyNormalizationReport report =
                new MultilingualFamilyNormalizationReport(
                        apply ? "APPLY" : "DRY RUN",
                        LEGACY_FAMILY_ID,
                        CANONICAL_FAMILY_ID
                );

        addMappings(report);

        for (NormalizationCandidateSong candidateSong : snapshot.candidateSongs()) {
            report.addCandidateSong(candidateSong);
        }

        for (NormalizationReferenceUpdate update : snapshot.relationshipUpdates()) {
            report.addRelationshipUpdate(update);
        }

        for (NormalizationDuplicateConflict conflict : snapshot.duplicateConflicts()) {
            report.addDuplicateConflict(conflict);
        }

        for (SongRow canonicalSong : snapshot.canonicalSongs()) {
            report.addProtectedCanonicalSong(
                    "ID " + canonicalSong.id()
                            + " | " + canonicalSong.title()
                            + " | language=" + canonicalSong.language()
                            + " | familyId=" + canonicalSong.familyId()
            );
        }

        report.addProtectedCanonicalFamily(
                "ID " + CANONICAL_FAMILY_ID
                        + " | " + loadFamilyCanonicalTitle(CANONICAL_FAMILY_ID)
        );

        for (String otherTable : snapshot.otherReferenceTables()) {
            report.addOtherReferenceTable(otherTable);
        }

        report.setFamilyRemovableAfterMigration(
                snapshot.familyRemovableAfterMigration()
        );
        return report;
    }

    private void addMappings(MultilingualFamilyNormalizationReport report) {
        report.addMapping("27 -> 67 ENGLISH");
        report.addMapping("31 -> 67 ENGLISH");
        report.addMapping("28 -> 66 HAITIAN_CREOLE");
        report.addMapping("32 -> 66 HAITIAN_CREOLE");
        report.addMapping("29 -> 65 SPANISH");
        report.addMapping("33 -> 65 SPANISH");
    }

    private void validateInspectionPreconditions() {
        validateCanonicalFamilyExists();
        validateCanonicalSongsExist();
        validateLegacySongsExist();
    }

    private void validateApplyPreconditions() {
        validateInspectionPreconditions();
    }

    private void validateCanonicalFamilyExists() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from song_family where id = ?",
                Integer.class,
                CANONICAL_FAMILY_ID
        );

        if (count == null || count != 1) {
            throw new IllegalStateException(
                    "Canonical family " + CANONICAL_FAMILY_ID + " does not exist."
            );
        }
    }

    private void validateCanonicalSongsExist() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from song where id in (65, 66, 67)",
                Integer.class
        );

        if (count == null || count != PROTECTED_CANONICAL_SONG_IDS.size()) {
            throw new IllegalStateException(
                    "Canonical songs 65, 66, and 67 must all exist."
            );
        }
    }

    private void validateLegacySongsExist() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from song where id in (27, 28, 29, 31, 32, 33)",
                Integer.class
        );

        if (count == null || count != LEGACY_TO_CANONICAL_SONG_IDS.size()) {
            throw new IllegalStateException(
                    "Legacy songs 27, 28, 29, 31, 32, and 33 must all exist."
            );
        }
    }

    private List<SongRow> loadSongsByIds(List<Integer> songIds) {
        String placeholders = songIds.stream()
                .map(id -> "?")
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow();

        return jdbcTemplate.query(
                """
                        select id, title, language, family_id, song_type
                        from song
                        where id in (%s)
                        order by id
                        """.formatted(placeholders),
                (resultSet, rowNum) -> new SongRow(
                        resultSet.getInt("id"),
                        resultSet.getString("title"),
                        resultSet.getString("language"),
                        (Integer) resultSet.getObject("family_id"),
                        resultSet.getString("song_type")
                ),
                songIds.toArray()
        );
    }

    private List<RelationshipRow> loadPlaylistRows() {
        return jdbcTemplate.query(
                """
                        select ps.playlist_id as context_id,
                               p.name as context_label,
                               ps.song_order as song_order,
                               ps.songs_id as song_id
                        from playlist_songs ps
                        join playlist p on p.id = ps.playlist_id
                        where ps.songs_id in (27, 28, 29, 31, 32, 33, 65, 66, 67)
                        order by ps.playlist_id, ps.song_order
                        """,
                (resultSet, rowNum) -> new RelationshipRow(
                        resultSet.getLong("context_id"),
                        resultSet.getString("context_label"),
                        resultSet.getInt("song_order"),
                        resultSet.getInt("song_id")
                )
        );
    }

    private List<RelationshipRow> loadServicePlanRows() {
        return jdbcTemplate.query(
                """
                        select sps.service_plan_id as context_id,
                               sp.service_name as context_label,
                               sps.song_order as song_order,
                               sps.song_id as song_id
                        from service_plan_songs sps
                        join service_plans sp on sp.id = sps.service_plan_id
                        where sps.song_id in (27, 28, 29, 31, 32, 33, 65, 66, 67)
                        order by sps.service_plan_id, sps.song_order
                        """,
                (resultSet, rowNum) -> new RelationshipRow(
                        resultSet.getLong("context_id"),
                        resultSet.getString("context_label"),
                        resultSet.getInt("song_order"),
                        resultSet.getInt("song_id")
                )
        );
    }

    private List<ContextMigrationPlan> buildContextPlans(
            List<RelationshipRow> rows,
            String relationshipType,
            String contextIdLabel,
            String tableName,
            String songIdColumnName) {
        Map<Long, List<RelationshipRow>> rowsByContextId = new LinkedHashMap<>();

        for (RelationshipRow row : rows) {
            rowsByContextId.computeIfAbsent(
                    row.contextId(),
                    ignored -> new ArrayList<>()
            ).add(row);
        }

        List<ContextMigrationPlan> plans = new ArrayList<>();
        for (Map.Entry<Long, List<RelationshipRow>> entry : rowsByContextId.entrySet()) {
            plans.add(
                    buildSingleContextPlan(
                            entry.getValue(),
                            relationshipType,
                            contextIdLabel,
                            tableName,
                            songIdColumnName
                    )
            );
        }

        return plans;
    }

    private ContextMigrationPlan buildSingleContextPlan(
            List<RelationshipRow> rows,
            String relationshipType,
            String contextIdLabel,
            String tableName,
            String songIdColumnName) {
        rows.sort(Comparator.comparingInt(RelationshipRow::songOrder));

        long contextId = rows.getFirst().contextId();
        String contextLabel = rows.getFirst().contextLabel();
        Set<Integer> existingCanonicalSongs = new LinkedHashSet<>();

        for (RelationshipRow row : rows) {
            if (PROTECTED_CANONICAL_SONG_IDS.contains(row.songId())) {
                existingCanonicalSongs.add(row.songId());
            }
        }

        Set<Integer> migratedTargetsWithoutCanonical = new LinkedHashSet<>();
        List<RowAction> rowActions = new ArrayList<>();
        List<NormalizationReferenceUpdate> updates = new ArrayList<>();
        List<NormalizationDuplicateConflict> conflicts = new ArrayList<>();

        for (RelationshipRow row : rows) {
            if (!LEGACY_TO_CANONICAL_SONG_IDS.containsKey(row.songId())) {
                rowActions.add(
                        new RowAction(
                                row.songOrder(),
                                row.songId(),
                                false
                        )
                );
                continue;
            }

            int canonicalSongId = LEGACY_TO_CANONICAL_SONG_IDS.get(row.songId());
            boolean canonicalAlreadyPresent =
                    existingCanonicalSongs.contains(canonicalSongId);
            boolean migratedTargetAlreadyUsed =
                    migratedTargetsWithoutCanonical.contains(canonicalSongId);

            if (canonicalAlreadyPresent || migratedTargetAlreadyUsed) {
                conflicts.add(
                        new NormalizationDuplicateConflict(
                                relationshipType,
                                contextId,
                                contextIdLabel + "=" + contextLabel,
                                row.songId(),
                                canonicalSongId,
                                row.songOrder(),
                                canonicalAlreadyPresent
                                        ? "remove redundant legacy row and keep existing canonical row"
                                        : "remove redundant legacy row created by duplicate legacy mapping"
                        )
                );
                updates.add(
                        new NormalizationReferenceUpdate(
                                relationshipType,
                                contextId,
                                contextIdLabel + "=" + contextLabel,
                                row.songOrder(),
                                row.songId(),
                                canonicalSongId,
                                "DELETE_REDUNDANT_LEGACY_REFERENCE"
                        )
                );
                continue;
            }

            migratedTargetsWithoutCanonical.add(canonicalSongId);
            rowActions.add(
                    new RowAction(
                            row.songOrder(),
                            canonicalSongId,
                            row.songId() != canonicalSongId
                    )
            );
            updates.add(
                    new NormalizationReferenceUpdate(
                            relationshipType,
                            contextId,
                            contextIdLabel + "=" + contextLabel,
                            row.songOrder(),
                            row.songId(),
                            canonicalSongId,
                            "UPDATE_REFERENCE"
                    )
            );
        }

        return new ContextMigrationPlan(
                contextId,
                contextLabel,
                relationshipType,
                tableName,
                songIdColumnName,
                rows,
                rowActions,
                updates,
                conflicts
        );
    }

    private Map<Integer, List<String>> collectReferences(
            List<ContextMigrationPlan> plans,
            boolean playlist) {
        Map<Integer, List<String>> referencesBySongId = new LinkedHashMap<>();

        for (ContextMigrationPlan plan : plans) {
            for (RelationshipRow row : plan.originalRows()) {
                if (!LEGACY_TO_CANONICAL_SONG_IDS.containsKey(row.songId())) {
                    continue;
                }

                String reference = playlist
                        ? "playlistId=" + plan.contextId()
                        + ", name=" + plan.contextLabel()
                        + ", order=" + row.songOrder()
                        : "servicePlanId=" + plan.contextId()
                        + ", serviceName=" + plan.contextLabel()
                        + ", order=" + row.songOrder();

                referencesBySongId.computeIfAbsent(
                        row.songId(),
                        ignored -> new ArrayList<>()
                ).add(reference);
            }
        }

        return referencesBySongId;
    }

    private Map<Integer, List<String>> collectOtherReferences() {
        Map<Integer, List<String>> referencesBySongId = new LinkedHashMap<>();

        for (String tableName : findOtherSongReferenceTables()) {
            List<String> songIdColumns = findSongIdColumns(tableName);

            for (String songIdColumn : songIdColumns) {
                String query = "select " + songIdColumn + " from " + tableName
                        + " where " + songIdColumn + " in (27, 28, 29, 31, 32, 33)";

                jdbcTemplate.query(
                        query,
                        resultSet -> {
                            int songId = resultSet.getInt(songIdColumn);
                            referencesBySongId.computeIfAbsent(
                                    songId,
                                    ignored -> new ArrayList<>()
                            ).add(tableName + "." + songIdColumn);
                        }
                );
            }
        }

        return referencesBySongId;
    }

    private List<String> findOtherSongReferenceTables() {
        List<String> tables = jdbcTemplate.queryForList(
                """
                        select name
                        from sqlite_master
                        where type = 'table'
                        order by name
                        """,
                String.class
        );

        List<String> referenceTables = new ArrayList<>();
        for (String table : tables) {
            if (table == null
                    || table.startsWith("sqlite_")
                    || "song".equals(table)
                    || "song_family".equals(table)
                    || "playlist".equals(table)
                    || "service_plans".equals(table)
                    || "playlist_songs".equals(table)
                    || "service_plan_songs".equals(table)) {
                continue;
            }

            if (!findSongIdColumns(table).isEmpty()) {
                referenceTables.add(table);
            }
        }

        return referenceTables;
    }

    private List<String> findSongIdColumns(String tableName) {
        return jdbcTemplate.query(
                "pragma table_info(" + tableName + ")",
                (resultSet, rowNum) -> resultSet.getString("name")
        ).stream()
                .filter(columnName -> "song_id".equals(columnName)
                        || "songs_id".equals(columnName))
                .toList();
    }

    private int countSongsInFamily(int familyId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from song where family_id = ?",
                Integer.class,
                familyId
        );
        return count == null ? 0 : count;
    }

    private String loadFamilyCanonicalTitle(int familyId) {
        return jdbcTemplate.queryForObject(
                "select canonical_title from song_family where id = ?",
                String.class,
                familyId
        );
    }

    private void applyPlaylistPlan(List<ContextMigrationPlan> plans) {
        for (ContextMigrationPlan plan : plans) {
            if (!"playlist".equals(plan.relationshipType())) {
                continue;
            }

            jdbcTemplate.update(
                    "delete from playlist_songs where playlist_id = ?",
                    plan.contextId()
            );

            for (int index = 0; index < plan.resultRows().size(); index++) {
                RowAction rowAction = plan.resultRows().get(index);
                jdbcTemplate.update(
                        "insert into playlist_songs (playlist_id, songs_id, song_order) values (?, ?, ?)",
                        plan.contextId(),
                        rowAction.songId(),
                        index
                );
            }
        }
    }

    private void applyServicePlanPlan(List<ContextMigrationPlan> plans) {
        for (ContextMigrationPlan plan : plans) {
            if (!"service plan".equals(plan.relationshipType())) {
                continue;
            }

            jdbcTemplate.update(
                    "delete from service_plan_songs where service_plan_id = ?",
                    plan.contextId()
            );

            for (int index = 0; index < plan.resultRows().size(); index++) {
                RowAction rowAction = plan.resultRows().get(index);
                jdbcTemplate.update(
                        "insert into service_plan_songs (service_plan_id, song_id, song_order) values (?, ?, ?)",
                        plan.contextId(),
                        rowAction.songId(),
                        index
                );
            }
        }
    }

    private void validateNoRemainingReferences(Set<Integer> legacySongIds) {
        String placeholders = legacySongIds.stream()
                .map(id -> "?")
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow();

        Integer playlistCount = jdbcTemplate.queryForObject(
                "select count(*) from playlist_songs where songs_id in (" + placeholders + ")",
                Integer.class,
                legacySongIds.toArray()
        );
        Integer servicePlanCount = jdbcTemplate.queryForObject(
                "select count(*) from service_plan_songs where song_id in (" + placeholders + ")",
                Integer.class,
                legacySongIds.toArray()
        );

        if ((playlistCount != null && playlistCount > 0)
                || (servicePlanCount != null && servicePlanCount > 0)) {
            throw new IllegalStateException(
                    "Legacy song references remain after migration."
            );
        }
    }

    private void deleteLegacySongs(Set<Integer> legacySongIds) {
        String placeholders = legacySongIds.stream()
                .map(id -> "?")
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow();

        jdbcTemplate.update(
                "delete from song where id in (" + placeholders + ")",
                legacySongIds.toArray()
        );
    }

    private void deleteLegacyFamilyIfEmpty() {
        Integer remainingSongs = jdbcTemplate.queryForObject(
                "select count(*) from song where family_id = ?",
                Integer.class,
                LEGACY_FAMILY_ID
        );

        if (remainingSongs != null && remainingSongs > 0) {
            throw new IllegalStateException(
                    "Legacy family " + LEGACY_FAMILY_ID + " still has songs."
            );
        }

        jdbcTemplate.update(
                "delete from song_family where id = ?",
                LEGACY_FAMILY_ID
        );
    }

    private record SongRow(
            int id,
            String title,
            String language,
            Integer familyId,
            String songType) {
    }

    private record RelationshipRow(
            long contextId,
            String contextLabel,
            int songOrder,
            int songId) {
    }

    private record RowAction(
            int originalOrder,
            int songId,
            boolean updated) {
    }

    private record ContextMigrationPlan(
            long contextId,
            String contextLabel,
            String relationshipType,
            String tableName,
            String songIdColumnName,
            List<RelationshipRow> originalRows,
            List<RowAction> resultRows,
            List<NormalizationReferenceUpdate> updates,
            List<NormalizationDuplicateConflict> duplicateConflicts) {
    }

    private record NormalizationSnapshot(
            List<SongRow> legacySongs,
            List<SongRow> canonicalSongs,
            List<NormalizationCandidateSong> candidateSongs,
            List<ContextMigrationPlan> playlistPlans,
            List<ContextMigrationPlan> servicePlanPlans,
            List<NormalizationReferenceUpdate> relationshipUpdates,
            List<NormalizationDuplicateConflict> duplicateConflicts,
            Set<String> otherReferenceTables,
            boolean familyRemovableAfterMigration) {
        Set<Integer> legacySongIds() {
            return legacySongs.stream()
                    .map(SongRow::id)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
    }
}
