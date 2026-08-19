package com.churchsong.dto.normalization;

import java.util.ArrayList;
import java.util.List;

public class MultilingualFamilyNormalizationReport {

    private final String mode;
    private final int legacyFamilyId;
    private final int canonicalFamilyId;
    private final List<String> mappings;
    private final List<NormalizationCandidateSong> candidateSongs;
    private final List<NormalizationReferenceUpdate> relationshipUpdates;
    private final List<NormalizationDuplicateConflict> duplicateConflicts;
    private final List<String> protectedCanonicalSongs;
    private final List<String> protectedCanonicalFamilies;
    private final List<String> otherReferenceTables;
    private boolean familyRemovableAfterMigration;
    private boolean committed;

    public MultilingualFamilyNormalizationReport(
            String mode,
            int legacyFamilyId,
            int canonicalFamilyId) {
        this.mode = mode;
        this.legacyFamilyId = legacyFamilyId;
        this.canonicalFamilyId = canonicalFamilyId;
        this.mappings = new ArrayList<>();
        this.candidateSongs = new ArrayList<>();
        this.relationshipUpdates = new ArrayList<>();
        this.duplicateConflicts = new ArrayList<>();
        this.protectedCanonicalSongs = new ArrayList<>();
        this.protectedCanonicalFamilies = new ArrayList<>();
        this.otherReferenceTables = new ArrayList<>();
    }

    public void addMapping(String mapping) {
        mappings.add(mapping);
    }

    public void addCandidateSong(NormalizationCandidateSong song) {
        candidateSongs.add(song);
    }

    public void addRelationshipUpdate(NormalizationReferenceUpdate update) {
        relationshipUpdates.add(update);
    }

    public void addDuplicateConflict(NormalizationDuplicateConflict conflict) {
        duplicateConflicts.add(conflict);
    }

    public void addProtectedCanonicalSong(String song) {
        protectedCanonicalSongs.add(song);
    }

    public void addProtectedCanonicalFamily(String family) {
        protectedCanonicalFamilies.add(family);
    }

    public void addOtherReferenceTable(String table) {
        otherReferenceTables.add(table);
    }

    public String getMode() {
        return mode;
    }

    public int getLegacyFamilyId() {
        return legacyFamilyId;
    }

    public int getCanonicalFamilyId() {
        return canonicalFamilyId;
    }

    public List<String> getMappings() {
        return new ArrayList<>(mappings);
    }

    public List<NormalizationCandidateSong> getCandidateSongs() {
        return new ArrayList<>(candidateSongs);
    }

    public List<NormalizationReferenceUpdate> getRelationshipUpdates() {
        return new ArrayList<>(relationshipUpdates);
    }

    public List<NormalizationDuplicateConflict> getDuplicateConflicts() {
        return new ArrayList<>(duplicateConflicts);
    }

    public List<String> getProtectedCanonicalSongs() {
        return new ArrayList<>(protectedCanonicalSongs);
    }

    public List<String> getProtectedCanonicalFamilies() {
        return new ArrayList<>(protectedCanonicalFamilies);
    }

    public List<String> getOtherReferenceTables() {
        return new ArrayList<>(otherReferenceTables);
    }

    public List<NormalizationCandidateSong> getSafeLegacySongs() {
        return candidateSongs.stream()
                .filter(NormalizationCandidateSong::isSafeToRemoveAfterMigration)
                .toList();
    }

    public List<NormalizationCandidateSong> getReviewLegacySongs() {
        return candidateSongs.stream()
                .filter(song -> !song.isSafeToRemoveAfterMigration())
                .toList();
    }

    public boolean isFamilyRemovableAfterMigration() {
        return familyRemovableAfterMigration;
    }

    public void setFamilyRemovableAfterMigration(boolean familyRemovableAfterMigration) {
        this.familyRemovableAfterMigration = familyRemovableAfterMigration;
    }

    public boolean isCommitted() {
        return committed;
    }

    public void setCommitted(boolean committed) {
        this.committed = committed;
    }

    public String toConsoleReport() {
        StringBuilder builder = new StringBuilder();
        builder.append("MULTILINGUAL FAMILY NORMALIZATION — ")
                .append(mode)
                .append("\n\n")
                .append("Legacy family:\n  ")
                .append(legacyFamilyId)
                .append("\n\n")
                .append("Canonical family:\n  ")
                .append(canonicalFamilyId)
                .append("\n\n")
                .append("Mappings:\n");

        for (String mapping : mappings) {
            builder.append("- ").append(mapping).append('\n');
        }

        builder.append("\nPlaylist references:\n");
        appendSongReferences(builder, "playlist");
        builder.append("\nService-plan references:\n");
        appendSongReferences(builder, "service plan");

        builder.append("\nRelationship rows to update:\n");
        if (relationshipUpdates.isEmpty()) {
            builder.append("  none\n");
        } else {
            for (NormalizationReferenceUpdate update : relationshipUpdates) {
                builder.append("- ")
                        .append(update.getRelationshipType())
                        .append("Id=")
                        .append(update.getContextId())
                        .append(", ")
                        .append(update.getContextLabel())
                        .append(", order=")
                        .append(update.getOrder())
                        .append(" | ")
                        .append(update.getLegacySongId())
                        .append(" -> ")
                        .append(update.getCanonicalSongId())
                        .append(" | action=")
                        .append(update.getAction())
                        .append('\n');
            }
        }

        builder.append("\nDuplicate relationship conflicts:\n");
        if (duplicateConflicts.isEmpty()) {
            builder.append("  none\n");
        } else {
            for (NormalizationDuplicateConflict conflict : duplicateConflicts) {
                builder.append("- ")
                        .append(conflict.getRelationshipType())
                        .append("Id=")
                        .append(conflict.getContextId())
                        .append(", ")
                        .append(conflict.getContextLabel())
                        .append(", order=")
                        .append(conflict.getLegacyOrder())
                        .append(" | legacy=")
                        .append(conflict.getLegacySongId())
                        .append(" | canonical=")
                        .append(conflict.getCanonicalSongId())
                        .append(" | resolution=")
                        .append(conflict.getResolution())
                        .append('\n');
            }
        }

        builder.append("\nLegacy songs safe to delete:\n");
        for (NormalizationCandidateSong song : getSafeLegacySongs()) {
            builder.append("- ID ")
                    .append(song.getId())
                    .append(" | replacement=")
                    .append(song.getReplacementSongId())
                    .append(" | ")
                    .append(song.getLanguage())
                    .append(" | ")
                    .append(song.getTitle())
                    .append('\n');
        }

        builder.append("\nLegacy songs requiring review:\n");
        if (getReviewLegacySongs().isEmpty()) {
            builder.append("  none\n");
        } else {
            for (NormalizationCandidateSong song : getReviewLegacySongs()) {
                builder.append("- ID ")
                        .append(song.getId())
                        .append(" | replacement=")
                        .append(song.getReplacementSongId())
                        .append(" | ")
                        .append(song.getLanguage())
                        .append(" | ")
                        .append(song.getTitle())
                        .append('\n');
            }
        }

        builder.append("\nFamily ")
                .append(legacyFamilyId)
                .append(" removable after migration:\n  ")
                .append(familyRemovableAfterMigration ? "YES" : "NO")
                .append("\n\nProtected canonical songs:\n");
        for (String protectedSong : protectedCanonicalSongs) {
            builder.append("- ").append(protectedSong).append('\n');
        }

        builder.append("\nProtected canonical family:\n");
        for (String protectedFamily : protectedCanonicalFamilies) {
            builder.append("- ").append(protectedFamily).append('\n');
        }

        builder.append("\nOther song reference tables found:\n");
        if (otherReferenceTables.isEmpty()) {
            builder.append("  none\n");
        } else {
            for (String table : otherReferenceTables) {
                builder.append("- ").append(table).append('\n');
            }
        }

        builder.append('\n')
                .append(committed
                        ? "Database changes were committed.\n"
                        : "No database changes were made.\n");
        return builder.toString();
    }

    private void appendSongReferences(
            StringBuilder builder,
            String referenceType) {
        boolean foundAny = false;
        for (NormalizationCandidateSong song : candidateSongs) {
            List<String> references = "playlist".equals(referenceType)
                    ? song.getPlaylistReferences()
                    : song.getServicePlanReferences();

            for (String reference : references) {
                foundAny = true;
                builder.append("- songId=")
                        .append(song.getId())
                        .append(" -> ")
                        .append(reference)
                        .append('\n');
            }
        }

        if (!foundAny) {
            builder.append("  none\n");
        }
    }
}
