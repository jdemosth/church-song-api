package com.churchsong.dto.cleanup;

import java.util.ArrayList;
import java.util.List;

public class LegacyCleanupPlanReport {

    private final String mode;
    private final List<String> emptyFamilies;
    private final List<String> amazingGraceDuplicates;
    private final List<String> playlistReferences;
    private final List<String> safeDeletions;
    private final List<String> needsReview;
    private final List<String> protectedFamilies;
    private boolean committed;

    public LegacyCleanupPlanReport(String mode) {
        this.mode = mode;
        this.emptyFamilies = new ArrayList<>();
        this.amazingGraceDuplicates = new ArrayList<>();
        this.playlistReferences = new ArrayList<>();
        this.safeDeletions = new ArrayList<>();
        this.needsReview = new ArrayList<>();
        this.protectedFamilies = new ArrayList<>();
    }

    public void addEmptyFamily(String entry) {
        emptyFamilies.add(entry);
    }

    public void addAmazingGraceDuplicate(String entry) {
        amazingGraceDuplicates.add(entry);
    }

    public void addPlaylistReference(String entry) {
        playlistReferences.add(entry);
    }

    public void addSafeDeletion(String entry) {
        safeDeletions.add(entry);
    }

    public void addNeedsReview(String entry) {
        needsReview.add(entry);
    }

    public void addProtectedFamily(String entry) {
        protectedFamilies.add(entry);
    }

    public List<String> getEmptyFamilies() {
        return new ArrayList<>(emptyFamilies);
    }

    public List<String> getAmazingGraceDuplicates() {
        return new ArrayList<>(amazingGraceDuplicates);
    }

    public List<String> getPlaylistReferences() {
        return new ArrayList<>(playlistReferences);
    }

    public List<String> getSafeDeletions() {
        return new ArrayList<>(safeDeletions);
    }

    public List<String> getNeedsReview() {
        return new ArrayList<>(needsReview);
    }

    public List<String> getProtectedFamilies() {
        return new ArrayList<>(protectedFamilies);
    }

    public boolean isCommitted() {
        return committed;
    }

    public void setCommitted(boolean committed) {
        this.committed = committed;
    }

    public String toConsoleReport() {
        StringBuilder builder = new StringBuilder();
        builder.append("LEGACY CLEANUP \u2014 ")
                .append(mode)
                .append("\n\n");

        appendSection(builder, "Empty families", emptyFamilies);
        appendSection(builder, "Amazing Grace duplicates", amazingGraceDuplicates);
        appendSection(builder, "Playlist references", playlistReferences);
        appendSection(builder, "Safe deletions", safeDeletions);
        appendSection(builder, "Needs review", needsReview);
        appendSection(builder, "Protected multilingual families", protectedFamilies);

        if (committed) {
            builder.append("Database changes were committed.\n");
        } else {
            builder.append("No database changes were made.\n");
        }

        return builder.toString();
    }

    private void appendSection(
            StringBuilder builder,
            String title,
            List<String> entries) {
        builder.append(title).append(":\n");
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
