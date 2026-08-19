package com.churchsong.dto.normalization;

public class NormalizationReferenceUpdate {

    private final String relationshipType;
    private final long contextId;
    private final String contextLabel;
    private final int order;
    private final int legacySongId;
    private final int canonicalSongId;
    private final String action;

    public NormalizationReferenceUpdate(
            String relationshipType,
            long contextId,
            String contextLabel,
            int order,
            int legacySongId,
            int canonicalSongId,
            String action) {
        this.relationshipType = relationshipType;
        this.contextId = contextId;
        this.contextLabel = contextLabel;
        this.order = order;
        this.legacySongId = legacySongId;
        this.canonicalSongId = canonicalSongId;
        this.action = action;
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

    public int getOrder() {
        return order;
    }

    public int getLegacySongId() {
        return legacySongId;
    }

    public int getCanonicalSongId() {
        return canonicalSongId;
    }

    public String getAction() {
        return action;
    }
}
