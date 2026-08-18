package com.churchsong.dto.cleanup;

import java.util.ArrayList;
import java.util.List;

public class CleanupCandidateSong {

    private final int id;
    private final String title;
    private final Integer familyId;
    private final String language;
    private final String songType;
    private final List<String> playlistReferences;
    private final List<String> servicePlanReferences;
    private final boolean safeToDelete;

    public CleanupCandidateSong(
            int id,
            String title,
            Integer familyId,
            String language,
            String songType,
            List<String> playlistReferences,
            List<String> servicePlanReferences,
            boolean safeToDelete) {
        this.id = id;
        this.title = title;
        this.familyId = familyId;
        this.language = language;
        this.songType = songType;
        this.playlistReferences = new ArrayList<>(playlistReferences);
        this.servicePlanReferences = new ArrayList<>(servicePlanReferences);
        this.safeToDelete = safeToDelete;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Integer getFamilyId() {
        return familyId;
    }

    public String getLanguage() {
        return language;
    }

    public String getSongType() {
        return songType;
    }

    public List<String> getPlaylistReferences() {
        return new ArrayList<>(playlistReferences);
    }

    public List<String> getServicePlanReferences() {
        return new ArrayList<>(servicePlanReferences);
    }

    public boolean isSafeToDelete() {
        return safeToDelete;
    }
}
