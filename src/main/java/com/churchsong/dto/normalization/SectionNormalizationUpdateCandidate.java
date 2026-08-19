package com.churchsong.dto.normalization;

import java.util.ArrayList;
import java.util.List;

public class SectionNormalizationUpdateCandidate {

    private final Integer songId;
    private final String title;
    private final String sourceUrl;
    private final List<String> currentDetectedSections;
    private final List<String> proposedDetectedSections;
    private final String status;

    public SectionNormalizationUpdateCandidate(
            Integer songId,
            String title,
            String sourceUrl,
            List<String> currentDetectedSections,
            List<String> proposedDetectedSections,
            String status) {
        this.songId = songId;
        this.title = title;
        this.sourceUrl = sourceUrl;
        this.currentDetectedSections = new ArrayList<>(currentDetectedSections);
        this.proposedDetectedSections = new ArrayList<>(proposedDetectedSections);
        this.status = status;
    }

    public Integer getSongId() {
        return songId;
    }

    public String getTitle() {
        return title;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public List<String> getCurrentDetectedSections() {
        return new ArrayList<>(currentDetectedSections);
    }

    public List<String> getProposedDetectedSections() {
        return new ArrayList<>(proposedDetectedSections);
    }

    public String getStatus() {
        return status;
    }
}
