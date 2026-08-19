package com.churchsong.service;

import com.churchsong.dto.audit.SongDataAuditReport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SongDataAuditService {

    private static final Set<Integer> PROTECTED_IMPORTED_FAMILY_IDS = Set.of(
            91235,
            91236,
            91237,
            91238,
            91239
    );
    private static final Pattern PLACEHOLDER_TITLE_PATTERN = Pattern.compile(
            "^song\\s+(?:[a-z]|one|two)$",
            Pattern.CASE_INSENSITIVE
    );

    private final JdbcTemplate jdbcTemplate;

    public SongDataAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SongDataAuditReport auditSongData() {
        SongDataAuditReport report = new SongDataAuditReport();

        List<SongRow> songs = loadSongs();
        List<FamilyRow> families = loadFamilies();
        Map<Integer, FamilyRow> familyById = families.stream()
                .collect(Collectors.toMap(FamilyRow::id, family -> family));
        Map<Integer, List<SongRow>> songsByFamilyId = songs.stream()
                .filter(song -> song.familyId() != null)
                .collect(Collectors.groupingBy(
                        SongRow::familyId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<Integer, List<String>> playlistRefsBySongId = loadPlaylistReferencesBySongId();
        Map<Integer, List<String>> servicePlanRefsBySongId = loadServicePlanReferencesBySongId();

        report.setTotalSongs(songs.size());
        report.setTotalFamilies(families.size());

        List<FamilyRow> emptyFamilies = families.stream()
                .filter(family -> songsByFamilyId.getOrDefault(family.id(), List.of()).isEmpty())
                .toList();
        report.setEmptyFamilyCount(emptyFamilies.size());
        for (FamilyRow family : emptyFamilies) {
            String recommendation = looksLikeTestOrDevelopmentFamily(family)
                    ? "ORPHAN CLEANUP"
                    : "REVIEW";
            report.addEmptyFamily(
                    "familyId=" + family.id()
                            + " | canonicalTitle=" + family.canonicalTitle()
                            + " | memberCount=0 | recommendation=" + recommendation
            );
        }

        int duplicateLanguageFamilies = 0;
        Set<Integer> suspiciousFamilyIds = new LinkedHashSet<>();
        for (FamilyRow family : families) {
            List<SongRow> members = songsByFamilyId.getOrDefault(family.id(), List.of());
            if (members.isEmpty()) {
                continue;
            }

            Map<String, Long> countsByLanguage = members.stream()
                    .collect(Collectors.groupingBy(
                            song -> normalizeNullable(song.language()),
                            LinkedHashMap::new,
                            Collectors.counting()
                    ));
            List<String> duplicateLanguages = countsByLanguage.entrySet().stream()
                    .filter(entry -> entry.getValue() > 1)
                    .map(Map.Entry::getKey)
                    .toList();

            String memberSummary = members.stream()
                    .sorted(Comparator.comparingInt(SongRow::id))
                    .map(song -> song.id() + ":" + song.title() + "[" + normalizeNullable(song.language()) + "]")
                    .collect(Collectors.joining(", "));

            if (!duplicateLanguages.isEmpty()) {
                duplicateLanguageFamilies++;
                suspiciousFamilyIds.add(family.id());
                report.addSuspiciousFamily(
                        "familyId=" + family.id()
                                + " | canonicalTitle=" + family.canonicalTitle()
                                + " | duplicateLanguages=" + duplicateLanguages
                                + " | members=" + memberSummary
                                + " | recommendation=REVIEW"
                );
            } else if (!PROTECTED_IMPORTED_FAMILY_IDS.contains(family.id())
                    && familySharesCanonicalTitle(family, families)) {
                suspiciousFamilyIds.add(family.id());
                report.addSuspiciousFamily(
                        "familyId=" + family.id()
                                + " | canonicalTitle=" + family.canonicalTitle()
                                + " | similarFamilyTitleDetected"
                                + " | members=" + memberSummary
                                + " | recommendation=POSSIBLE MERGE"
                );
            } else if (!PROTECTED_IMPORTED_FAMILY_IDS.contains(family.id())) {
                report.addCleanFamily(
                        "familyId=" + family.id()
                                + " | canonicalTitle=" + family.canonicalTitle()
                                + " | members=" + memberSummary
                                + " | recommendation=KEEP"
                );
            }
        }
        report.setDuplicateLanguageFamilyCount(duplicateLanguageFamilies);

        List<SongRow> orphanedSongs = songs.stream()
                .filter(song -> song.familyId() != null && !familyById.containsKey(song.familyId()))
                .toList();
        report.setOrphanedFamilyReferenceCount(orphanedSongs.size());
        for (SongRow song : orphanedSongs) {
            report.addOrphanedSongReference(
                    "songId=" + song.id()
                            + " | title=" + song.title()
                            + " | familyId=" + song.familyId()
                            + " | recommendation=ORPHAN CLEANUP"
            );
        }

        Map<String, List<SongRow>> songsByExactKey = songs.stream()
                .collect(Collectors.groupingBy(
                        song -> normalizeTitle(song.title()) + "|"
                                + normalizeLyrics(song.lyrics()) + "|"
                                + normalizeNullable(song.language()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<List<SongRow>> exactDuplicates = songsByExactKey.values().stream()
                .filter(group -> group.size() > 1)
                .toList();
        report.setExactDuplicateSongCount(exactDuplicates.size());
        Set<Integer> suspiciousSongIds = new LinkedHashSet<>();
        for (List<SongRow> group : exactDuplicates) {
            suspiciousSongIds.addAll(group.stream().map(SongRow::id).toList());
            report.addExactDuplicateSong(
                    formatSongGroup(group)
                            + " | exactMatchOn=normalizedTitle+normalizedLyrics+language"
                            + " | recommendation=POSSIBLE DELETE"
            );
        }

        Map<String, List<SongRow>> songsByTitleLanguage = songs.stream()
                .collect(Collectors.groupingBy(
                        song -> normalizeTitle(song.title()) + "|"
                                + normalizeNullable(song.language()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<List<SongRow>> nearDuplicates = songsByTitleLanguage.values().stream()
                .filter(group -> group.size() > 1)
                .filter(group -> !allSameNormalizedLyrics(group))
                .toList();
        report.setNearDuplicateSongCount(nearDuplicates.size());
        for (List<SongRow> group : nearDuplicates) {
            suspiciousSongIds.addAll(group.stream().map(SongRow::id).toList());
            report.addNearDuplicateSong(
                    formatSongGroup(group)
                            + " | sameNormalizedTitleAndLanguageDifferentLyrics"
                            + " | recommendation=REVIEW"
            );
        }

        Map<String, List<SongRow>> songsByNormalizedTitle = songs.stream()
                .collect(Collectors.groupingBy(
                        song -> normalizeTitle(song.title()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<List<SongRow>> crossFamilyGroups = songsByNormalizedTitle.values().stream()
                .filter(group -> group.size() > 1)
                .filter(group -> {
                    Set<String> familyBuckets = group.stream()
                            .map(song -> song.familyId() == null ? "NONE" : String.valueOf(song.familyId()))
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    return familyBuckets.size() > 1;
                })
                .toList();
        report.setCrossFamilyDuplicateCount(crossFamilyGroups.size());
        for (List<SongRow> group : crossFamilyGroups) {
            suspiciousSongIds.addAll(group.stream().map(SongRow::id).toList());
            Set<Integer> involvedFamilyIds = group.stream()
                    .map(SongRow::familyId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            suspiciousFamilyIds.addAll(involvedFamilyIds);
            report.addCrossFamilyDuplicateCandidate(
                    formatSongGroup(group)
                            + " | familyBuckets="
                            + group.stream()
                                    .map(song -> song.familyId() == null ? "NONE" : String.valueOf(song.familyId()))
                                    .collect(Collectors.toCollection(LinkedHashSet::new))
                            + " | recommendation=POSSIBLE MERGE"
            );
        }

        List<String> artifactEntries = new ArrayList<>();
        for (SongRow song : songs) {
            if (looksLikeTestSong(song) || looksLikeDevelopmentSong(song)) {
                suspiciousSongIds.add(song.id());
                artifactEntries.add(
                        "songId=" + song.id()
                                + " | title=" + song.title()
                                + " | language=" + normalizeNullable(song.language())
                                + " | familyId=" + song.familyId()
                                + " | playlistRefs=" + playlistRefsBySongId.getOrDefault(song.id(), List.of())
                                + " | servicePlanRefs=" + servicePlanRefsBySongId.getOrDefault(song.id(), List.of())
                                + " | recommendation=REVIEW"
                );
            }
        }

        for (FamilyRow family : families) {
            if (looksLikeTestOrDevelopmentFamily(family)) {
                suspiciousFamilyIds.add(family.id());
                artifactEntries.add(
                        "familyId=" + family.id()
                                + " | canonicalTitle=" + family.canonicalTitle()
                                + " | memberCount=" + songsByFamilyId.getOrDefault(family.id(), List.of()).size()
                                + " | recommendation="
                                + (songsByFamilyId.getOrDefault(family.id(), List.of()).isEmpty()
                                ? "ORPHAN CLEANUP"
                                : "REVIEW")
                );
            }
        }
        report.setTestArtifactCount(artifactEntries.size());
        artifactEntries.forEach(report::addTestDevelopmentArtifact);

        buildImpactSection(report, suspiciousSongIds, playlistRefsBySongId, servicePlanRefsBySongId, songs);
        buildProtectedFamilySection(report, songsByFamilyId, families);
        buildRecommendedCleanupSection(report, emptyFamilies, exactDuplicates, crossFamilyGroups, orphanedSongs, songs, songsByFamilyId);

        return report;
    }

    private void buildImpactSection(
            SongDataAuditReport report,
            Set<Integer> suspiciousSongIds,
            Map<Integer, List<String>> playlistRefsBySongId,
            Map<Integer, List<String>> servicePlanRefsBySongId,
            List<SongRow> songs) {
        Map<Integer, SongRow> songById = songs.stream()
                .collect(Collectors.toMap(SongRow::id, song -> song));

        for (Integer songId : suspiciousSongIds) {
            List<String> playlistRefs = playlistRefsBySongId.getOrDefault(songId, List.of());
            List<String> servicePlanRefs = servicePlanRefsBySongId.getOrDefault(songId, List.of());
            if (playlistRefs.isEmpty() && servicePlanRefs.isEmpty()) {
                continue;
            }

            SongRow song = songById.get(songId);
            report.addPlaylistServicePlanImpact(
                    "songId=" + songId
                            + " | title=" + song.title()
                            + " | playlistRefs=" + playlistRefs
                            + " | servicePlanRefs=" + servicePlanRefs
                            + " | recommendation=REVIEW"
            );
        }
    }

    private void buildProtectedFamilySection(
            SongDataAuditReport report,
            Map<Integer, List<SongRow>> songsByFamilyId,
            List<FamilyRow> families) {
        Map<Integer, FamilyRow> familyById = families.stream()
                .collect(Collectors.toMap(FamilyRow::id, family -> family));
        for (Integer familyId : PROTECTED_IMPORTED_FAMILY_IDS.stream().sorted().toList()) {
            List<SongRow> members = songsByFamilyId.getOrDefault(familyId, List.of());
            FamilyRow family = familyById.get(familyId);
            Map<String, Long> countsByLanguage = members.stream()
                    .collect(Collectors.groupingBy(
                            song -> normalizeNullable(song.language()),
                            LinkedHashMap::new,
                            Collectors.counting()
                    ));
            boolean hasDuplicateLanguage = countsByLanguage.values().stream().anyMatch(count -> count > 1);
            long sourceTrackedCount = members.stream()
                    .filter(song -> song.sourceUrl() != null && !song.sourceUrl().isBlank())
                    .count();
            report.addProtectedImportedFamily(
                    "familyId=" + familyId
                            + " | canonicalTitle=" + (family == null ? "<missing family row>" : family.canonicalTitle())
                            + " | members=" + members.stream()
                                    .sorted(Comparator.comparingInt(SongRow::id))
                                    .map(song -> song.id() + ":" + song.title() + "[" + normalizeNullable(song.language()) + "]")
                                    .collect(Collectors.joining(", "))
                            + " | sourceTracking=" + sourceTrackedCount + "/" + members.size()
                            + " | duplicateLanguageStatus=" + (hasDuplicateLanguage ? "DUPLICATE_LANGUAGES" : "CLEAN")
                            + " | recommendation=KEEP"
            );
        }
    }

    private void buildRecommendedCleanupSection(
            SongDataAuditReport report,
            List<FamilyRow> emptyFamilies,
            List<List<SongRow>> exactDuplicates,
            List<List<SongRow>> crossFamilyGroups,
            List<SongRow> orphanedSongs,
            List<SongRow> songs,
            Map<Integer, List<SongRow>> songsByFamilyId) {
        for (FamilyRow family : emptyFamilies) {
            report.addRecommendedCleanupCandidate(
                    "familyId=" + family.id()
                            + " | canonicalTitle=" + family.canonicalTitle()
                            + " | recommendation="
                            + (looksLikeTestOrDevelopmentFamily(family) ? "ORPHAN CLEANUP" : "REVIEW")
            );
        }

        for (List<SongRow> group : exactDuplicates) {
            report.addRecommendedCleanupCandidate(
                    "duplicateSongs=" + formatSongGroup(group)
                            + " | recommendation=POSSIBLE DELETE"
            );
        }

        for (List<SongRow> group : crossFamilyGroups) {
            report.addRecommendedCleanupCandidate(
                    "crossFamilyGroup=" + formatSongGroup(group)
                            + " | recommendation=POSSIBLE MERGE"
            );
        }

        for (SongRow orphanedSong : orphanedSongs) {
            report.addRecommendedCleanupCandidate(
                    "songId=" + orphanedSong.id()
                            + " | title=" + orphanedSong.title()
                            + " | missingFamilyId=" + orphanedSong.familyId()
                            + " | recommendation=ORPHAN CLEANUP"
            );
        }

        List<SongRow> developmentSongs = songs.stream()
                .filter(this::looksLikeDevelopmentSong)
                .toList();
        for (SongRow song : developmentSongs) {
            report.addRecommendedCleanupCandidate(
                    "songId=" + song.id()
                            + " | title=" + song.title()
                            + " | developmentLookingTitle"
                            + " | recommendation=REVIEW"
            );
        }

        for (Map.Entry<Integer, List<SongRow>> entry : songsByFamilyId.entrySet()) {
            if (entry.getValue().size() > 1
                    && hasNearDuplicateTitles(entry.getValue())) {
                report.addRecommendedCleanupCandidate(
                        "familyId=" + entry.getKey()
                                + " | similarMemberTitles="
                                + entry.getValue().stream()
                                        .map(SongRow::title)
                                        .toList()
                                + " | recommendation=REVIEW"
                );
            }
        }
    }

    private List<SongRow> loadSongs() {
        return jdbcTemplate.query(
                """
                        select id, title, lyrics, language, family_id, source_url
                        from song
                        order by id
                        """,
                (resultSet, rowNum) -> new SongRow(
                        resultSet.getInt("id"),
                        resultSet.getString("title"),
                        resultSet.getString("lyrics"),
                        resultSet.getString("language"),
                        (Integer) resultSet.getObject("family_id"),
                        resultSet.getString("source_url")
                )
        );
    }

    private List<FamilyRow> loadFamilies() {
        return jdbcTemplate.query(
                """
                        select id, canonical_title, source_family_key
                        from song_family
                        order by id
                        """,
                (resultSet, rowNum) -> new FamilyRow(
                        resultSet.getInt("id"),
                        resultSet.getString("canonical_title"),
                        resultSet.getString("source_family_key")
                )
        );
    }

    private Map<Integer, List<String>> loadPlaylistReferencesBySongId() {
        Map<Integer, List<String>> references = new HashMap<>();
        jdbcTemplate.query(
                """
                        select ps.songs_id, p.id, p.name, ps.song_order
                        from playlist_songs ps
                        join playlist p on p.id = ps.playlist_id
                        order by ps.songs_id, p.id, ps.song_order
                        """,
                resultSet -> {
                    int songId = resultSet.getInt("songs_id");
                    references.computeIfAbsent(songId, ignored -> new ArrayList<>())
                            .add("playlistId=" + resultSet.getLong("id")
                                    + ", name=" + resultSet.getString("name")
                                    + ", order=" + resultSet.getInt("song_order"));
                }
        );
        return references;
    }

    private Map<Integer, List<String>> loadServicePlanReferencesBySongId() {
        Map<Integer, List<String>> references = new HashMap<>();
        jdbcTemplate.query(
                """
                        select sps.song_id, sp.id, sp.service_name, sp.service_date, sp.service_time, sps.song_order
                        from service_plan_songs sps
                        join service_plans sp on sp.id = sps.service_plan_id
                        order by sps.song_id, sp.id, sps.song_order
                        """,
                resultSet -> {
                    int songId = resultSet.getInt("song_id");
                    references.computeIfAbsent(songId, ignored -> new ArrayList<>())
                            .add("servicePlanId=" + resultSet.getLong("id")
                                    + ", serviceName=" + resultSet.getString("service_name")
                                    + ", serviceDate=" + resultSet.getString("service_date")
                                    + ", serviceTime=" + resultSet.getString("service_time")
                                    + ", order=" + resultSet.getInt("song_order"));
                }
        );
        return references;
    }

    private boolean familySharesCanonicalTitle(
            FamilyRow candidate,
            List<FamilyRow> allFamilies) {
        String normalizedCandidate = normalizeTitle(candidate.canonicalTitle());
        return allFamilies.stream()
                .filter(other -> other.id() != candidate.id())
                .anyMatch(other -> normalizeTitle(other.canonicalTitle()).equals(normalizedCandidate));
    }

    private boolean allSameNormalizedLyrics(List<SongRow> group) {
        return group.stream()
                .map(song -> normalizeLyrics(song.lyrics()))
                .distinct()
                .count() == 1;
    }

    private boolean hasNearDuplicateTitles(List<SongRow> songs) {
        Set<String> normalizedTitles = songs.stream()
                .map(song -> normalizeTitle(song.title()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return normalizedTitles.size() < songs.size();
    }

    private boolean looksLikeTestSong(SongRow song) {
        return song.title() != null
                && song.title().toLowerCase(Locale.ROOT).contains("unit test");
    }

    private boolean looksLikeDevelopmentSong(SongRow song) {
        return song.title() != null
                && PLACEHOLDER_TITLE_PATTERN.matcher(song.title().trim()).matches();
    }

    private boolean looksLikeTestOrDevelopmentFamily(FamilyRow family) {
        if (family.canonicalTitle() == null) {
            return false;
        }

        String normalized = family.canonicalTitle().trim().toLowerCase(Locale.ROOT);
        return normalized.contains("unit test")
                || normalized.startsWith("existing ")
                || PLACEHOLDER_TITLE_PATTERN.matcher(family.canonicalTitle().trim()).matches();
    }

    private String formatSongGroup(Collection<SongRow> group) {
        return group.stream()
                .sorted(Comparator.comparingInt(SongRow::id))
                .map(song -> "songId=" + song.id()
                        + ", title=" + song.title()
                        + ", language=" + normalizeNullable(song.language())
                        + ", familyId=" + song.familyId())
                .collect(Collectors.joining(" | "));
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? "NULL" : value;
    }

    private String normalizeTitle(String value) {
        if (value == null) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return normalized;
    }

    private String normalizeLyrics(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record SongRow(
            int id,
            String title,
            String lyrics,
            String language,
            Integer familyId,
            String sourceUrl) {
    }

    private record FamilyRow(
            int id,
            String canonicalTitle,
            String sourceFamilyKey) {
    }
}
