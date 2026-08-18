package com.churchsong.dto.cleanup;

import java.util.ArrayList;
import java.util.List;

public class CleanupCandidateFamily {

    private final int id;
    private final String canonicalTitle;
    private final String sourceFamilyKey;
    private final List<Integer> memberSongIds;
    private final boolean safeToDelete;

    public CleanupCandidateFamily(
            int id,
            String canonicalTitle,
            String sourceFamilyKey,
            List<Integer> memberSongIds,
            boolean safeToDelete) {
        this.id = id;
        this.canonicalTitle = canonicalTitle;
        this.sourceFamilyKey = sourceFamilyKey;
        this.memberSongIds = new ArrayList<>(memberSongIds);
        this.safeToDelete = safeToDelete;
    }

    public int getId() {
        return id;
    }

    public String getCanonicalTitle() {
        return canonicalTitle;
    }

    public String getSourceFamilyKey() {
        return sourceFamilyKey;
    }

    public List<Integer> getMemberSongIds() {
        return new ArrayList<>(memberSongIds);
    }

    public boolean isSafeToDelete() {
        return safeToDelete;
    }
}
