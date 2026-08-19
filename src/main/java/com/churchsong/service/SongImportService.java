package com.churchsong.service;

import com.churchsong.dto.importing.ImportReport;
import com.churchsong.dto.importing.ImportSongResult;
import com.churchsong.model.Song;
import com.churchsong.model.SongFamily;
import com.churchsong.model.SongLanguage;
import com.churchsong.model.SongType;
import com.churchsong.repository.SongFamilyRepository;
import com.churchsong.repository.SongRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class SongImportService {

    private static final TypeReference<List<ImporterSongRecord>> SONG_RECORDS =
            new TypeReference<>() {
            };
    private static final TypeReference<List<ImporterFamilyRecord>> FAMILY_RECORDS =
            new TypeReference<>() {
            };
    private static final Set<String> BLOCKED_REVIEW_STATUSES = Set.of(
            "REJECTED",
            "SKIP",
            "SKIPPED"
    );

    private final ObjectMapper objectMapper;
    private final SongRepository songRepository;
    private final SongFamilyRepository songFamilyRepository;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    public SongImportService(
            JdbcTemplate jdbcTemplate,
            SongRepository songRepository,
            SongFamilyRepository songFamilyRepository,
            PlatformTransactionManager transactionManager) {
        this.objectMapper = new ObjectMapper()
                .configure(
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        false
                );
        this.jdbcTemplate = jdbcTemplate;
        this.songRepository = songRepository;
        this.songFamilyRepository = songFamilyRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ImportReport importReviewedSongs(
            Path songsFile,
            Path familiesFile,
            boolean approvedFamiliesOnly,
            boolean apply) {
        validateFilePath(songsFile, "songsFile");
        validateFilePath(familiesFile, "familiesFile");

        ImportPlan plan = buildPlan(
                readSongs(songsFile),
                readFamilies(familiesFile),
                approvedFamiliesOnly
        );

        return executePlan(
                plan,
                apply,
                "MULTILINGUAL SONG IMPORT",
                approvedFamiliesOnly
                        ? "APPROVED FAMILIES ONLY"
                        : "ALL ELIGIBLE SONGS"
        );
    }

    public ImportReport importSafeSongsOnly(
            Path songsFile,
            boolean apply) {
        validateFilePath(songsFile, "songsFile");
        ImportPlan plan = buildPlan(
                readSongs(songsFile),
                List.of(),
                false
        );
        return executePlan(
                plan,
                apply,
                "STAGED SAFE SONG IMPORT",
                "SAFE SONGS ONLY"
        );
    }

    private ImportReport executePlan(
            ImportPlan plan,
            boolean apply,
            String reportTitle,
            String scope) {
        if (!apply) {
            return buildDryRunReport(plan, reportTitle, scope);
        }

        return applyPlan(plan, reportTitle, scope);
    }

    private ImportReport buildDryRunReport(
            ImportPlan plan,
            String reportTitle,
            String scope) {
        ImportReport report = baseReport(reportTitle, "DRY RUN", scope);
        copyMessages(plan, report);

        plan.existingFamilyIds.forEach((ignored, ignoredId) ->
                report.incrementFamiliesExisting());
        plan.familyCreatePlans.forEach(ignored ->
                report.incrementFamiliesCreated());
        plan.existingSongUrls.forEach(ignored ->
                report.incrementSongsExisting());
        plan.songCreatePlans.forEach(ignored ->
                report.incrementSongsCreated());

        report.setCommitted(false);
        return report;
    }

    private ImportReport applyPlan(
            ImportPlan plan,
            String reportTitle,
            String scope) {
        ImportReport report = baseReport(reportTitle, "APPLY", scope);
        copyMessages(plan, report);

        plan.existingFamilyIds.forEach((ignored, ignoredId) ->
                report.incrementFamiliesExisting());
        plan.existingSongUrls.forEach(ignored ->
                report.incrementSongsExisting());

        Map<String, Integer> resolvedFamilyIds =
                new HashMap<>(plan.existingFamilyIds);
        Map<String, List<SongCreatePlan>> songsByFamilyKey = groupSongsByFamily(plan.songCreatePlans);
        Map<String, FamilyCreatePlan> familyCreatePlanByKey = new HashMap<>();

        for (FamilyCreatePlan familyCreatePlan : plan.familyCreatePlans) {
            familyCreatePlanByKey.put(
                    familyCreatePlan.sourceFamilyKey(),
                    familyCreatePlan
            );
        }

        for (FamilyCreatePlan familyCreatePlan : plan.familyCreatePlans) {
            if (songsByFamilyKey.containsKey(
                    familyCreatePlan.sourceFamilyKey())) {
                continue;
            }

            try {
                transactionTemplate.executeWithoutResult(status -> {
                    SongFamily family = songFamilyRepository
                            .findBySourceFamilyKey(
                                    familyCreatePlan.sourceFamilyKey())
                            .orElseGet(() -> songFamilyRepository.save(
                                    new SongFamily(
                                            familyCreatePlan.databaseId(),
                                            familyCreatePlan.canonicalTitle(),
                                            familyCreatePlan.sourceFamilyKey()
                                    )
                            ));

                    resolvedFamilyIds.put(
                            familyCreatePlan.sourceFamilyKey(),
                            family.getId()
                    );

                    List<SongCreatePlan> songsForFamily = songsByFamilyKey
                            .getOrDefault(
                                    familyCreatePlan.sourceFamilyKey(),
                                    List.of()
                            );

                    for (SongCreatePlan songCreatePlan : songsForFamily) {
                        if (songExists(songCreatePlan.sourceUrl())) {
                            report.incrementSongsExisting();
                            continue;
                        }

                        Song song = createSong(
                                songCreatePlan,
                                resolvedFamilyIds
                        );
                        songRepository.save(song);
                        report.incrementSongsCreated();
                    }
                });

                if (resolvedFamilyIds.containsKey(
                        familyCreatePlan.sourceFamilyKey())) {
                    report.incrementFamiliesCreated();
                }
            } catch (RuntimeException exception) {
                report.addError(
                        "Failed to import family "
                                + familyCreatePlan.sourceFamilyKey()
                                + ": "
                                + exception.getMessage()
                );
            }
        }

        for (Map.Entry<String, List<SongCreatePlan>> entry
                : songsByFamilyKey.entrySet()) {
            String familySourceKey = entry.getKey();
            List<SongCreatePlan> songsForFamily = entry.getValue();

            if (familySourceKey == null) {
                applySongsWithoutFamily(report, resolvedFamilyIds, songsForFamily);
                continue;
            }

            FamilyCreatePlan familyCreatePlan =
                    familyCreatePlanByKey.get(familySourceKey);

            try {
                transactionTemplate.executeWithoutResult(status -> {
                    if (familyCreatePlan != null) {
                        SongFamily family = songFamilyRepository
                                .findBySourceFamilyKey(
                                        familyCreatePlan.sourceFamilyKey())
                                .orElseGet(() -> songFamilyRepository.save(
                                        new SongFamily(
                                                familyCreatePlan.databaseId(),
                                                familyCreatePlan.canonicalTitle(),
                                                familyCreatePlan.sourceFamilyKey()
                                        )
                                ));

                        resolvedFamilyIds.put(
                                familyCreatePlan.sourceFamilyKey(),
                                family.getId()
                        );
                    }

                    for (SongCreatePlan songCreatePlan : songsForFamily) {
                        if (songRepository.findBySourceUrl(songCreatePlan.sourceUrl())
                                .isPresent()) {
                            report.incrementSongsExisting();
                            continue;
                        }

                        Song song = createSong(songCreatePlan, resolvedFamilyIds);
                        songRepository.save(song);
                        report.incrementSongsCreated();
                    }
                });

                if (familyCreatePlan != null) {
                    report.incrementFamiliesCreated();
                }
            } catch (RuntimeException exception) {
                report.addError(
                        "Failed to import song family group "
                                + familySourceKey
                                + ": "
                                + exception.getMessage()
                );
            }
        }

        report.setCommitted(!report.hasErrors());
        return report;
    }

    private void applySongsWithoutFamily(
            ImportReport report,
            Map<String, Integer> resolvedFamilyIds,
            List<SongCreatePlan> songsWithoutFamily) {
        for (SongCreatePlan songCreatePlan : songsWithoutFamily) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    if (songExists(songCreatePlan.sourceUrl())) {
                        report.incrementSongsExisting();
                        return;
                    }

                    Song song = createSong(songCreatePlan, resolvedFamilyIds);
                    songRepository.save(song);
                    report.incrementSongsCreated();
                });
            } catch (RuntimeException exception) {
                report.addError(
                        "Failed to import song "
                                + songCreatePlan.title()
                                + ": "
                                + exception.getMessage()
                );
            }
        }
    }

    private Song createSong(
            SongCreatePlan songCreatePlan,
            Map<String, Integer> resolvedFamilyIds) {
        Integer familyId = null;

        if (songCreatePlan.familySourceKey() != null) {
            familyId = resolvedFamilyIds.get(songCreatePlan.familySourceKey());

            if (familyId == null) {
                throw new IllegalStateException(
                        "No database family id was resolved for "
                                + songCreatePlan.familySourceKey()
                );
            }
        }

        return new Song(
                familyId,
                songCreatePlan.title(),
                null,
                songCreatePlan.lyrics(),
                songCreatePlan.sourceUrl(),
                null,
                songCreatePlan.language()
        );
    }

    private Map<String, List<SongCreatePlan>> groupSongsByFamily(
            List<SongCreatePlan> songCreatePlans) {
        Map<String, List<SongCreatePlan>> grouped = new LinkedHashMap<>();

        for (SongCreatePlan songCreatePlan : songCreatePlans) {
            grouped.computeIfAbsent(
                            songCreatePlan.familySourceKey(),
                            ignored -> new ArrayList<>())
                    .add(songCreatePlan);
        }

        return grouped;
    }

    private ImportPlan buildPlan(
            List<ImporterSongRecord> songs,
            List<ImporterFamilyRecord> families,
            boolean approvedFamiliesOnly) {
        ImportPlan plan = new ImportPlan();
        Map<String, ImporterFamilyRecord> approvedFamilies =
                collectApprovedFamilies(families, plan);
        Map<String, String> familyKeyBySourceUrl =
                buildApprovedFamilyMembership(approvedFamilies.values(), plan);
        Map<String, Integer> familyIdPlan =
                planFamilies(approvedFamilies.values(), plan);

        boolean includedPendingSongsWarning = false;

        for (ImporterSongRecord songRecord : songs) {
            String title = trimToNull(songRecord.title());
            String sourceUrl = trimToNull(songRecord.sourceUrl());

            if (!songRecord.isLikelySong()) {
                plan.skippedSongs.add(
                        new ImportSongResult(
                                safeTitle(title),
                                safeUrl(sourceUrl),
                                "SKIPPED",
                                "Record is marked isLikelySong=false."
                        )
                );
                continue;
            }

            if (blocksImport(songRecord.reviewStatus())) {
                plan.skippedSongs.add(
                        new ImportSongResult(
                                safeTitle(title),
                                safeUrl(sourceUrl),
                                "SKIPPED",
                                "Review status blocks import: "
                                        + songRecord.reviewStatus()
                        )
                );
                continue;
            }

            if (title == null) {
                plan.skippedSongs.add(
                        new ImportSongResult(
                                "(missing title)",
                                safeUrl(sourceUrl),
                                "SKIPPED",
                                "Missing title."
                        )
                );
                continue;
            }

            if (sourceUrl == null || !isHttpUrl(sourceUrl)) {
                plan.skippedSongs.add(
                        new ImportSongResult(
                                title,
                                safeUrl(sourceUrl),
                                "SKIPPED",
                                "Missing or invalid sourceUrl."
                        )
                );
                continue;
            }

            String lyrics = trimToNull(songRecord.lyrics());

            if (lyrics == null) {
                plan.skippedSongs.add(
                        new ImportSongResult(
                                title,
                                sourceUrl,
                                "SKIPPED",
                                "Missing lyrics."
                        )
                );
                continue;
            }

            SongLanguage language;

            try {
                language = mapLanguage(songRecord.languageGuess());
            } catch (IllegalArgumentException exception) {
                plan.skippedSongs.add(
                        new ImportSongResult(
                                title,
                                sourceUrl,
                                "SKIPPED",
                                exception.getMessage()
                        )
                );
                continue;
            }

            String familySourceKey = familyKeyBySourceUrl.get(sourceUrl);

            if (approvedFamiliesOnly && familySourceKey == null) {
                plan.skippedSongs.add(
                        new ImportSongResult(
                                title,
                                sourceUrl,
                                "SKIPPED",
                                "Song is not part of an approved family."
                        )
                );
                continue;
            }

            if ("PENDING".equals(normalizeStatus(songRecord.reviewStatus()))
                    && approvedFamiliesOnly
                    && familySourceKey != null
                    && !includedPendingSongsWarning) {
                plan.warnings.add(
                        "Song records are still marked PENDING in songs-import.json; approved family membership was used as the Phase 3B import gate."
                );
                includedPendingSongsWarning = true;
            }

            if (songExists(sourceUrl)) {
                plan.existingSongUrls.add(sourceUrl);
                continue;
            }

            if (familySourceKey != null
                    && !familyIdPlan.containsKey(familySourceKey)) {
                plan.skippedSongs.add(
                        new ImportSongResult(
                                title,
                                sourceUrl,
                                "SKIPPED",
                                "Approved family reference could not be resolved."
                        )
                );
                continue;
            }

            plan.songCreatePlans.add(
                    new SongCreatePlan(
                            familySourceKey,
                            title,
                            lyrics,
                            sourceUrl,
                            language
                    )
            );
        }

        return plan;
    }

    private Map<String, Integer> planFamilies(
            Iterable<ImporterFamilyRecord> approvedFamilies,
        ImportPlan plan) {
        Map<String, Integer> familyIds = new HashMap<>();
        int nextFamilyId = findCurrentMaxFamilyId() + 1;

        for (ImporterFamilyRecord familyRecord : approvedFamilies) {
            String sourceFamilyKey = trimToNull(familyRecord.familyKey());

            if (sourceFamilyKey == null) {
                plan.skippedFamilies.add(
                        "- (missing family key): APPROVED family record is missing familyKey."
                );
                continue;
            }

            String canonicalTitle = chooseCanonicalTitle(familyRecord.members());

            if (canonicalTitle == null) {
                plan.skippedFamilies.add(
                        "- " + sourceFamilyKey
                                + ": APPROVED family record is missing a usable canonical title."
                );
                continue;
            }

            Integer existingFamilyId =
                    findExistingFamilyIdBySourceKey(sourceFamilyKey);

            if (existingFamilyId != null) {
                plan.existingFamilyIds.put(sourceFamilyKey, existingFamilyId);
                familyIds.put(sourceFamilyKey, existingFamilyId);
                continue;
            }

            int plannedFamilyId = nextFamilyId++;
            plan.familyCreatePlans.add(
                    new FamilyCreatePlan(
                            sourceFamilyKey,
                            canonicalTitle,
                            plannedFamilyId
                    )
            );
            familyIds.put(sourceFamilyKey, plannedFamilyId);
        }

        return familyIds;
    }

    private Map<String, ImporterFamilyRecord> collectApprovedFamilies(
            List<ImporterFamilyRecord> families,
            ImportPlan plan) {
        Map<String, ImporterFamilyRecord> approvedFamilies =
                new LinkedHashMap<>();

        for (ImporterFamilyRecord familyRecord : families) {
            String familyKey = trimToNull(familyRecord.familyKey());
            String reviewStatus = normalizeStatus(familyRecord.reviewStatus());

            if (!"APPROVED".equals(reviewStatus)) {
                plan.skippedFamilies.add(
                        "- " + safeFamilyKey(familyKey)
                                + ": reviewStatus="
                                + reviewStatus
                );
                continue;
            }

            if (familyKey == null) {
                plan.skippedFamilies.add(
                        "- (missing family key): approved family record has no familyKey."
                );
                continue;
            }

            approvedFamilies.put(familyKey, familyRecord);
        }

        return approvedFamilies;
    }

    private Map<String, String> buildApprovedFamilyMembership(
            Iterable<ImporterFamilyRecord> approvedFamilies,
            ImportPlan plan) {
        Map<String, String> familyKeyBySourceUrl = new HashMap<>();

        for (ImporterFamilyRecord familyRecord : approvedFamilies) {
            String familyKey = trimToNull(familyRecord.familyKey());

            for (ImporterFamilyMember member : familyRecord.members()) {
                String sourceUrl = trimToNull(member.sourceUrl());

                if (sourceUrl == null || !isHttpUrl(sourceUrl)) {
                    plan.errors.add(
                            "Approved family " + familyKey
                                    + " contains a member with an invalid sourceUrl."
                    );
                    continue;
                }

                String previousKey = familyKeyBySourceUrl.putIfAbsent(
                        sourceUrl,
                        familyKey
                );

                if (previousKey != null && !previousKey.equals(familyKey)) {
                    plan.errors.add(
                            "Source URL " + sourceUrl
                                    + " appears in multiple approved families."
                    );
                }
            }
        }

        return familyKeyBySourceUrl;
    }

    private String chooseCanonicalTitle(
            List<ImporterFamilyMember> members) {
        return members.stream()
                .filter(member -> member.language() != null)
                .sorted(Comparator.comparingInt(member ->
                        "ENGLISH".equals(normalizeStatus(member.language())) ? 0 : 1))
                .map(ImporterFamilyMember::title)
                .map(this::trimToNull)
                .filter(title -> title != null)
                .findFirst()
                .orElse(null);
    }

    private SongLanguage mapLanguage(String languageGuess) {
        String normalizedLanguage = normalizeStatus(languageGuess);

        if (normalizedLanguage == null) {
            throw new IllegalArgumentException(
                    "Missing language."
            );
        }

        try {
            return SongLanguage.valueOf(normalizedLanguage);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Invalid language: " + languageGuess
            );
        }
    }

    private boolean blocksImport(String reviewStatus) {
        String normalizedStatus = normalizeStatus(reviewStatus);
        return normalizedStatus != null
                && BLOCKED_REVIEW_STATUSES.contains(normalizedStatus);
    }

    private boolean isHttpUrl(String value) {
        return value.startsWith("http://")
                || value.startsWith("https://");
    }

    private String normalizeStatus(String value) {
        String trimmed = trimToNull(value);

        if (trimmed == null) {
            return null;
        }

        return trimmed.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeTitle(String title) {
        return title == null ? "(missing title)" : title;
    }

    private String safeUrl(String sourceUrl) {
        return sourceUrl == null ? "(missing sourceUrl)" : sourceUrl;
    }

    private String safeFamilyKey(String familyKey) {
        return familyKey == null ? "(missing family key)" : familyKey;
    }

    private boolean songExists(String sourceUrl) {
        return findExistingSongIdBySourceUrl(sourceUrl) != null;
    }

    private Integer findExistingSongIdBySourceUrl(String sourceUrl) {
        List<Integer> results = jdbcTemplate.query(
                "select id from song where source_url = ? limit 1",
                (resultSet, rowNum) -> resultSet.getInt("id"),
                sourceUrl
        );

        return results.isEmpty() ? null : results.getFirst();
    }

    private Integer findExistingFamilyIdBySourceKey(String sourceFamilyKey) {
        List<Integer> results = jdbcTemplate.query(
                "select id from song_family where source_family_key = ? limit 1",
                (resultSet, rowNum) -> resultSet.getInt("id"),
                sourceFamilyKey
        );

        return results.isEmpty() ? null : results.getFirst();
    }

    private int findCurrentMaxFamilyId() {
        Integer maxId = jdbcTemplate.queryForObject(
                "select coalesce(max(id), 0) from song_family",
                Integer.class
        );
        return maxId == null ? 0 : maxId;
    }

    private void copyMessages(ImportPlan plan, ImportReport report) {
        plan.skippedFamilies.forEach(report::addSkippedFamily);
        plan.skippedSongs.forEach(report::addSkippedSong);
        plan.warnings.forEach(report::addWarning);
        plan.errors.forEach(report::addError);
    }

    private ImportReport baseReport(
            String reportTitle,
            String mode,
            String scope) {
        return new ImportReport(
                reportTitle,
                mode,
                scope
        );
    }

    private List<ImporterSongRecord> readSongs(Path songsFile) {
        try {
            return objectMapper.readValue(
                    Files.readString(songsFile),
                    SONG_RECORDS
            );
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "Unable to read songs file: " + exception.getMessage(),
                    exception
            );
        }
    }

    private List<ImporterFamilyRecord> readFamilies(Path familiesFile) {
        try {
            return objectMapper.readValue(
                    Files.readString(familiesFile),
                    FAMILY_RECORDS
            );
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "Unable to read families file: " + exception.getMessage(),
                    exception
            );
        }
    }

    private void validateFilePath(Path filePath, String fieldName) {
        if (filePath == null) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be null."
            );
        }

        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException(
                    fieldName + " does not exist: " + filePath
            );
        }
    }

    private record ImportPlan(
            List<FamilyCreatePlan> familyCreatePlans,
            Map<String, Integer> existingFamilyIds,
            List<SongCreatePlan> songCreatePlans,
            Set<String> existingSongUrls,
            List<String> skippedFamilies,
            List<ImportSongResult> skippedSongs,
            List<String> warnings,
            List<String> errors) {

        private ImportPlan() {
            this(
                    new ArrayList<>(),
                    new LinkedHashMap<>(),
                    new ArrayList<>(),
                    new HashSet<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>()
            );
        }
    }

    private record FamilyCreatePlan(
            String sourceFamilyKey,
            String canonicalTitle,
            int databaseId) {
    }

    private record SongCreatePlan(
            String familySourceKey,
            String title,
            String lyrics,
            String sourceUrl,
            SongLanguage language) {
    }

    private record ImporterSongRecord(
            String title,
            String languageGuess,
            String lyrics,
            String sourceUrl,
            String reviewStatus,
            boolean isLikelySong) {
    }

    private record ImporterFamilyRecord(
            String familyId,
            String familyKey,
            String reviewStatus,
            List<ImporterFamilyMember> members) {
    }

    private record ImporterFamilyMember(
            String title,
            String language,
            String sourceUrl) {
    }
}
