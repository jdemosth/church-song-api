package com.churchsong.dto.normalization;

import java.util.ArrayList;
import java.util.List;

public class NormalizationCandidateSong {

    private final int id;
    private final String title;
    private final String language;
    private final Integer familyId;
    private final int replacementSongId;
    private final List<String> playlistReferences;
    private final List<String> servicePlanReferences;
    private final List<String> otherReferences;
    private final boolean safeToRemoveAfterMigration;

    public NormalizationCandidateSong(
            int id,
            String title,
            String language,
            Integer familyId,
            int replacementSongId,
            List<String> playlistReferences,
            List<String> servicePlanReferences,
            List<String> otherReferences,
            boolean safeToRemoveAfterMigration) {
        this.id = id;
        this.title = title;
        this.language = language;
        this.familyId = familyId;
        this.replacementSongId = replacementSongId;
        this.playlistReferences = new ArrayList<>(playlistReferences);
        this.servicePlanReferences = new ArrayList<>(servicePlanReferences);
        this.otherReferences = new ArrayList<>(otherReferences);
        this.safeToRemoveAfterMigration = safeToRemoveAfterMigration;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getLanguage() {
        return language;
    }

    public Integer getFamilyId() {
        return familyId;
    }

    public int getReplacementSongId() {
        return replacementSongId;
    }

    public List<String> getPlaylistReferences() {
        return new ArrayList<>(playlistReferences);
    }

    public List<String> getServicePlanReferences() {
        return new ArrayList<>(servicePlanReferences);
    }

    public List<String> getOtherReferences() {
        return new ArrayList<>(otherReferences);
    }

    public boolean isSafeToRemoveAfterMigration() {
        return safeToRemoveAfterMigration;
    }
}
