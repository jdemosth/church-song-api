package com.churchsong.dto.normalization;

public class NormalizationDuplicateConflict {

    private final String relationshipType;
    private final long contextId;
    private final String contextLabel;
    private final int legacySongId;
    private final int canonicalSongId;
    private final int legacyOrder;
    private final String resolution;

    public NormalizationDuplicateConflict(
            String relationshipType,
            long contextId,
            String contextLabel,
            int legacySongId,
            int canonicalSongId,
            int legacyOrder,
            String resolution) {
        this.relationshipType = relationshipType;
        this.contextId = contextId;
        this.contextLabel = contextLabel;
        this.legacySongId = legacySongId;
        this.canonicalSongId = canonicalSongId;
        this.legacyOrder = legacyOrder;
        this.resolution = resolution;
    }

    public String getRelationshipType() {
        return relationshipType;
    }

    public long getContextId() {
        return contextId;
    }

    public String getContextLabel() {
        return contextLabel;
    }

    public int getLegacySongId() {
        return legacySongId;
    }

    public int getCanonicalSongId() {
        return canonicalSongId;
    }

    public int getLegacyOrder() {
        return legacyOrder;
    }

    public String getResolution() {
        return resolution;
    }
}
