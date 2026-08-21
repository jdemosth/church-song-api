package com.churchsong.dto;

import com.churchsong.model.Song;

public class AddSongTranslationResponse {

    private final Song sourceSong;
    private final Song translationSong;
    private final SongFamilyVersionsResponse versions;

    public AddSongTranslationResponse(
            Song sourceSong,
            Song translationSong,
            SongFamilyVersionsResponse versions) {
        this.sourceSong = sourceSong;
        this.translationSong = translationSong;
        this.versions = versions;
    }

    public Song getSourceSong() {
        return sourceSong;
    }

    public Song getTranslationSong() {
        return translationSong;
    }

    public SongFamilyVersionsResponse getVersions() {
        return versions;
    }
}
