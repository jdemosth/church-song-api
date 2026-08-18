package com.churchsong.dto.cleanup;

import java.util.ArrayList;
import java.util.List;

public class TestDataCleanupReport {

    private final String mode;
    private final List<CleanupCandidateSong> candidateSongs;
    private final List<CleanupCandidateFamily> candidateFamilies;
    private final List<CleanupCandidateSong> safeSongDeletes;
    private final List<CleanupCandidateSong> reviewSongs;
    private final List<CleanupCandidateFamily> safeFamilyDeletes;
    private final List<CleanupCandidateFamily> reviewFamilies;
    private final List<String> protectedSongs;
    private final List<String> protectedFamilies;
    private boolean committed;

    public TestDataCleanupReport(String mode) {
        this.mode = mode;
        this.candidateSongs = new ArrayList<>();
        this.candidateFamilies = new ArrayList<>();
        this.safeSongDeletes = new ArrayList<>();
        this.reviewSongs = new ArrayList<>();
        this.safeFamilyDeletes = new ArrayList<>();
        this.reviewFamilies = new ArrayList<>();
        this.protectedSongs = new ArrayList<>();
        this.protectedFamilies = new ArrayList<>();
    }

    public void addCandidateSong(CleanupCandidateSong song) {
        candidateSongs.add(song);
        if (song.isSafeToDelete()) {
            safeSongDeletes.add(song);
        } else {
            reviewSongs.add(song);
        }
    }

    public void addCandidateFamily(CleanupCandidateFamily family) {
        candidateFamilies.add(family);
        if (family.isSafeToDelete()) {
            safeFamilyDeletes.add(family);
        } else {
            reviewFamilies.add(family);
        }
    }

    public void addProtectedSong(String protectedSong) {
        protectedSongs.add(protectedSong);
    }

    public void addProtectedFamily(String protectedFamily) {
        protectedFamilies.add(protectedFamily);
    }

    public List<CleanupCandidateSong> getCandidateSongs() {
        return new ArrayList<>(candidateSongs);
    }

    public List<CleanupCandidateFamily> getCandidateFamilies() {
        return new ArrayList<>(candidateFamilies);
    }

    public List<CleanupCandidateSong> getSafeSongDeletes() {
        return new ArrayList<>(safeSongDeletes);
    }

    public List<CleanupCandidateSong> getReviewSongs() {
        return new ArrayList<>(reviewSongs);
    }

    public List<CleanupCandidateFamily> getSafeFamilyDeletes() {
        return new ArrayList<>(safeFamilyDeletes);
    }

    public List<CleanupCandidateFamily> getReviewFamilies() {
        return new ArrayList<>(reviewFamilies);
    }

    public List<String> getProtectedSongs() {
        return new ArrayList<>(protectedSongs);
    }

    public List<String> getProtectedFamilies() {
        return new ArrayList<>(protectedFamilies);
    }

    public String getMode() {
        return mode;
    }

    public boolean isCommitted() {
        return committed;
    }

    public void setCommitted(boolean committed) {
        this.committed = committed;
    }

    public String toConsoleReport() {
        StringBuilder builder = new StringBuilder();
        builder.append("TEST DATA CLEANUP — ")
                .append(mode)
                .append("\n\n")
                .append("Candidate test songs: ")
                .append(candidateSongs.size())
                .append('\n')
                .append("Safe to delete:\n")
                .append("  ")
                .append(safeSongDeletes.size())
                .append('\n')
                .append("Needs review:\n")
                .append("  ")
                .append(reviewSongs.size())
                .append("\n\n")
                .append("Candidate test families: ")
                .append(candidateFamilies.size())
                .append('\n')
                .append("Safe to delete:\n")
                .append("  ")
                .append(safeFamilyDeletes.size())
                .append('\n')
                .append("Needs review:\n")
                .append("  ")
                .append(reviewFamilies.size())
                .append("\n\n")
                .append("Protected multilingual songs:\n")
                .append("  ")
                .append(protectedSongs.size())
                .append('\n')
                .append("Protected multilingual families:\n")
                .append("  ")
                .append(protectedFamilies.size())
                .append("\n\n");

        if (!candidateSongs.isEmpty()) {
            builder.append("Candidate Test Songs:\n");
            for (CleanupCandidateSong song : candidateSongs) {
                builder.append("- ID ")
                        .append(song.getId())
                        .append(" | ")
                        .append(song.getTitle())
                        .append(" | familyId=")
                        .append(song.getFamilyId())
                        .append(" | language=")
                        .append(song.getLanguage())
                        .append(" | songType=")
                        .append(song.getSongType())
                        .append(" | status=")
                        .append(song.isSafeToDelete() ? "SAFE_TO_DELETE" : "REVIEW")
                        .append('\n');

                for (String playlistReference : song.getPlaylistReferences()) {
                    builder.append("  playlist: ")
                            .append(playlistReference)
                            .append('\n');
                }

                for (String servicePlanReference : song.getServicePlanReferences()) {
                    builder.append("  service plan: ")
                            .append(servicePlanReference)
                            .append('\n');
                }
            }
            builder.append('\n');
        }

        if (!candidateFamilies.isEmpty()) {
            builder.append("Candidate Test Families:\n");
            for (CleanupCandidateFamily family : candidateFamilies) {
                builder.append("- ID ")
                        .append(family.getId())
                        .append(" | ")
                        .append(family.getCanonicalTitle())
                        .append(" | sourceFamilyKey=")
                        .append(family.getSourceFamilyKey())
                        .append(" | memberSongIds=")
                        .append(family.getMemberSongIds())
                        .append(" | status=")
                        .append(family.isSafeToDelete() ? "SAFE_TO_DELETE" : "REVIEW")
                        .append('\n');
            }
            builder.append('\n');
        }

        if (!protectedSongs.isEmpty()) {
            builder.append("Protected Multilingual Songs:\n");
            for (String protectedSong : protectedSongs) {
                builder.append("- ").append(protectedSong).append('\n');
            }
            builder.append('\n');
        }

        if (!protectedFamilies.isEmpty()) {
            builder.append("Protected Multilingual Families:\n");
            for (String protectedFamily : protectedFamilies) {
                builder.append("- ").append(protectedFamily).append('\n');
            }
            builder.append('\n');
        }

        if (committed) {
            builder.append("Database changes were committed.\n");
        } else {
            builder.append("No database changes were made.\n");
        }

        return builder.toString();
    }
}
