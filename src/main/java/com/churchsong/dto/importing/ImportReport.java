package com.churchsong.dto.importing;

import java.util.ArrayList;
import java.util.List;

public class ImportReport {

    private final String title;
    private final String mode;
    private final String scope;
    private int familiesCreated;
    private int familiesExisting;
    private int familiesSkipped;
    private int songsCreated;
    private int songsExisting;
    private int songsSkipped;
    private boolean committed;
    private final List<String> warnings;
    private final List<String> errors;
    private final List<String> skippedFamilies;
    private final List<ImportSongResult> skippedSongs;

    public ImportReport(String title, String mode, String scope) {
        this.title = title;
        this.mode = mode;
        this.scope = scope;
        this.warnings = new ArrayList<>();
        this.errors = new ArrayList<>();
        this.skippedFamilies = new ArrayList<>();
        this.skippedSongs = new ArrayList<>();
    }

    public ImportReport(String mode, String scope) {
        this("MULTILINGUAL SONG IMPORT", mode, scope);
    }

    public String getTitle() {
        return title;
    }

    public String getMode() {
        return mode;
    }

    public String getScope() {
        return scope;
    }

    public int getFamiliesCreated() {
        return familiesCreated;
    }

    public void incrementFamiliesCreated() {
        familiesCreated++;
    }

    public int getFamiliesExisting() {
        return familiesExisting;
    }

    public void incrementFamiliesExisting() {
        familiesExisting++;
    }

    public int getFamiliesSkipped() {
        return familiesSkipped;
    }

    public void addSkippedFamily(String reason) {
        familiesSkipped++;
        skippedFamilies.add(reason);
    }

    public int getSongsCreated() {
        return songsCreated;
    }

    public void incrementSongsCreated() {
        songsCreated++;
    }

    public int getSongsExisting() {
        return songsExisting;
    }

    public void incrementSongsExisting() {
        songsExisting++;
    }

    public int getSongsSkipped() {
        return songsSkipped;
    }

    public void addSkippedSong(ImportSongResult skippedSong) {
        songsSkipped++;
        skippedSongs.add(skippedSong);
    }

    public boolean isCommitted() {
        return committed;
    }

    public void setCommitted(boolean committed) {
        this.committed = committed;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public List<String> getErrors() {
        return errors;
    }

    public void addError(String error) {
        errors.add(error);
    }

    public List<String> getSkippedFamilies() {
        return skippedFamilies;
    }

    public List<ImportSongResult> getSkippedSongs() {
        return skippedSongs;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public String toConsoleReport() {
        StringBuilder builder = new StringBuilder();
        builder.append(title).append("\n\n")
                .append("Mode: ").append(mode).append('\n')
                .append("Scope: ").append(scope).append("\n\n")
                .append("Families:\n")
                .append("  create: ").append(familiesCreated).append('\n')
                .append("  existing: ").append(familiesExisting).append('\n')
                .append("  skipped: ").append(familiesSkipped).append("\n\n")
                .append("Songs:\n")
                .append("  create: ").append(songsCreated).append('\n')
                .append("  existing: ").append(songsExisting).append('\n')
                .append("  skipped: ").append(songsSkipped).append("\n\n")
                .append("Warnings:\n")
                .append("  ").append(warnings.size()).append('\n')
                .append("Errors:\n")
                .append("  ").append(errors.size()).append("\n\n");

        if (!skippedFamilies.isEmpty()) {
            builder.append("Skipped Families:\n");
            skippedFamilies.forEach(reason -> builder.append(reason).append('\n'));
            builder.append('\n');
        }

        if (!skippedSongs.isEmpty()) {
            builder.append("Skipped Songs:\n");
            skippedSongs.forEach(result -> builder.append(result).append('\n'));
            builder.append('\n');
        }

        if (!warnings.isEmpty()) {
            builder.append("Warning Details:\n");
            warnings.forEach(warning -> builder.append("- ").append(warning).append('\n'));
            builder.append('\n');
        }

        if (!errors.isEmpty()) {
            builder.append("Error Details:\n");
            errors.forEach(error -> builder.append("- ").append(error).append('\n'));
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
