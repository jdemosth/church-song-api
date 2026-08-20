package com.churchsong.dto;

import com.churchsong.model.Song;
import com.churchsong.model.SongLanguage;

import java.util.Map;

public class SongFamilyVersionsResponse {

    private final Integer familyId;
    private final Map<SongLanguage, Song> versions;

    public SongFamilyVersionsResponse(
            Integer familyId,
            Map<SongLanguage, Song> versions) {
        this.familyId = familyId;
        this.versions = versions;
    }

    public Integer getFamilyId() {
        return familyId;
    }

    public Map<SongLanguage, Song> getVersions() {
        return versions;
    }
}
