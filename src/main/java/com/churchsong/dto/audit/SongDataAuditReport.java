package com.churchsong.dto.audit;

import java.util.ArrayList;
import java.util.List;

public class SongDataAuditReport {

    private int totalSongs;
    private int totalFamilies;
    private int emptyFamilyCount;
    private int duplicateLanguageFamilyCount;
    private int exactDuplicateSongCount;
    private int nearDuplicateSongCount;
    private int crossFamilyDuplicateCount;
    private int orphanedFamilyReferenceCount;
    private int testArtifactCount;

    private final List<String> cleanFamilies = new ArrayList<>();
    private final List<String> suspiciousFamilies = new ArrayList<>();
    private final List<String> emptyFamilies = new ArrayList<>();
    private final List<String> orphanedSongReferences = new ArrayList<>();
    private final List<String> exactDuplicateSongs = new ArrayList<>();
    private final List<String> nearDuplicateSongs = new ArrayList<>();
    private final List<String> crossFamilyDuplicateCandidates = new ArrayList<>();
    private final List<String> testDevelopmentArtifacts = new ArrayList<>();
    private final List<String> playlistServicePlanImpact = new ArrayList<>();
    private final List<String> protectedImportedFamilies = new ArrayList<>();
    private final List<String> recommendedCleanupCandidates = new ArrayList<>();

    public int getTotalSongs() {
        return totalSongs;
    }

    public void setTotalSongs(int totalSongs) {
        this.totalSongs = totalSongs;
    }

    public int getTotalFamilies() {
        return totalFamilies;
    }

    public void setTotalFamilies(int totalFamilies) {
        this.totalFamilies = totalFamilies;
    }

    public int getEmptyFamilyCount() {
        return emptyFamilyCount;
    }

    public void setEmptyFamilyCount(int emptyFamilyCount) {
        this.emptyFamilyCount = emptyFamilyCount;
    }

    public int getDuplicateLanguageFamilyCount() {
        return duplicateLanguageFamilyCount;
    }

    public void setDuplicateLanguageFamilyCount(int duplicateLanguageFamilyCount) {
        this.duplicateLanguageFamilyCount = duplicateLanguageFamilyCount;
    }

    public int getExactDuplicateSongCount() {
        return exactDuplicateSongCount;
    }

    public void setExactDuplicateSongCount(int exactDuplicateSongCount) {
        this.exactDuplicateSongCount = exactDuplicateSongCount;
    }

    public int getNearDuplicateSongCount() {
        return nearDuplicateSongCount;
    }

    public void setNearDuplicateSongCount(int nearDuplicateSongCount) {
        this.nearDuplicateSongCount = nearDuplicateSongCount;
    }

    public int getCrossFamilyDuplicateCount() {
        return crossFamilyDuplicateCount;
    }

    public void setCrossFamilyDuplicateCount(int crossFamilyDuplicateCount) {
        this.crossFamilyDuplicateCount = crossFamilyDuplicateCount;
    }

    public int getOrphanedFamilyReferenceCount() {
        return orphanedFamilyReferenceCount;
    }

    public void setOrphanedFamilyReferenceCount(int orphanedFamilyReferenceCount) {
        this.orphanedFamilyReferenceCount = orphanedFamilyReferenceCount;
    }

    public int getTestArtifactCount() {
        return testArtifactCount;
    }

    public void setTestArtifactCount(int testArtifactCount) {
        this.testArtifactCount = testArtifactCount;
    }

    public List<String> getCleanFamilies() {
        return new ArrayList<>(cleanFamilies);
    }

    public void addCleanFamily(String entry) {
        cleanFamilies.add(entry);
    }

    public List<String> getSuspiciousFamilies() {
        return new ArrayList<>(suspiciousFamilies);
    }

    public void addSuspiciousFamily(String entry) {
        suspiciousFamilies.add(entry);
    }

    public List<String> getEmptyFamilies() {
        return new ArrayList<>(emptyFamilies);
    }

    public void addEmptyFamily(String entry) {
        emptyFamilies.add(entry);
    }

    public List<String> getOrphanedSongReferences() {
        return new ArrayList<>(orphanedSongReferences);
    }

    public void addOrphanedSongReference(String entry) {
        orphanedSongReferences.add(entry);
    }

    public List<String> getExactDuplicateSongs() {
        return new ArrayList<>(exactDuplicateSongs);
    }

    public void addExactDuplicateSong(String entry) {
        exactDuplicateSongs.add(entry);
    }

    public List<String> getNearDuplicateSongs() {
        return new ArrayList<>(nearDuplicateSongs);
    }

    public void addNearDuplicateSong(String entry) {
        nearDuplicateSongs.add(entry);
    }

    public List<String> getCrossFamilyDuplicateCandidates() {
        return new ArrayList<>(crossFamilyDuplicateCandidates);
    }

    public void addCrossFamilyDuplicateCandidate(String entry) {
        crossFamilyDuplicateCandidates.add(entry);
    }

    public List<String> getTestDevelopmentArtifacts() {
        return new ArrayList<>(testDevelopmentArtifacts);
    }

    public void addTestDevelopmentArtifact(String entry) {
        testDevelopmentArtifacts.add(entry);
    }

    public List<String> getPlaylistServicePlanImpact() {
        return new ArrayList<>(playlistServicePlanImpact);
    }

    public void addPlaylistServicePlanImpact(String entry) {
        playlistServicePlanImpact.add(entry);
    }

    public List<String> getProtectedImportedFamilies() {
        return new ArrayList<>(protectedImportedFamilies);
    }

    public void addProtectedImportedFamily(String entry) {
        protectedImportedFamilies.add(entry);
    }

    public List<String> getRecommendedCleanupCandidates() {
        return new ArrayList<>(recommendedCleanupCandidates);
    }

    public void addRecommendedCleanupCandidate(String entry) {
        recommendedCleanupCandidates.add(entry);
    }

    public String toConsoleReport() {
        StringBuilder builder = new StringBuilder();
        builder.append("MULTILINGUAL DATABASE INTEGRITY AUDIT\n\n")
                .append("Total songs: ").append(totalSongs).append('\n')
                .append("Total families: ").append(totalFamilies).append('\n')
                .append("Empty families: ").append(emptyFamilyCount).append('\n')
                .append("Families with duplicate languages: ").append(duplicateLanguageFamilyCount).append('\n')
                .append("Exact duplicate songs: ").append(exactDuplicateSongCount).append('\n')
                .append("Near-duplicate songs: ").append(nearDuplicateSongCount).append('\n')
                .append("Cross-family duplicate candidates: ").append(crossFamilyDuplicateCount).append('\n')
                .append("Orphaned family references: ").append(orphanedFamilyReferenceCount).append('\n')
                .append("Test/development artifacts: ").append(testArtifactCount).append("\n\n");

        appendSection(builder, "CLEAN FAMILIES", cleanFamilies);
        appendSection(builder, "SUSPICIOUS FAMILIES", suspiciousFamilies);
        appendSection(builder, "EMPTY FAMILIES", emptyFamilies);
        appendSection(builder, "ORPHANED SONG REFERENCES", orphanedSongReferences);
        appendSection(builder, "POSSIBLE DUPLICATE SONGS", exactDuplicateSongs);
        appendSection(builder, "NEAR-DUPLICATE SONGS", nearDuplicateSongs);
        appendSection(builder, "POSSIBLE CROSS-FAMILY DUPLICATES", crossFamilyDuplicateCandidates);
        appendSection(builder, "TEST/DEVELOPMENT ARTIFACTS", testDevelopmentArtifacts);
        appendSection(builder, "PLAYLIST/SERVICE-PLAN IMPACT", playlistServicePlanImpact);
        appendSection(builder, "PROTECTED IMPORTED FAMILIES", protectedImportedFamilies);
        appendSection(builder, "RECOMMENDED CLEANUP CANDIDATES", recommendedCleanupCandidates);
        builder.append("Read-only audit only. No database changes were made.\n");
        return builder.toString();
    }

    private void appendSection(
            StringBuilder builder,
            String title,
            List<String> entries) {
        builder.append(title).append('\n');
        if (entries.isEmpty()) {
            builder.append("  none\n\n");
            return;
        }

        for (String entry : entries) {
            builder.append("- ").append(entry).append('\n');
        }
        builder.append('\n');
    }
}
