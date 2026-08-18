package com.churchsong.dto;

import com.churchsong.model.SongLanguage;
import com.churchsong.model.SongType;

public class SongRequest {

    private Integer familyId;
    private String title;
    private String author;
    private String lyrics;
    private SongType songType;
    private SongLanguage language;

    public SongRequest() {
        // Required for JSON deserialization
    }

    public SongRequest(
            Integer familyId,
            String title,
            String author,
            String lyrics,
            SongType songType,
            SongLanguage language) {

        this.familyId = familyId;
        this.title = title;
        this.author = author;
        this.lyrics = lyrics;
        this.songType = songType;
        this.language = language;
    }

    public SongRequest(
            String title,
            String author,
            String lyrics,
            SongType songType) {
        this(
                null,
                title,
                author,
                lyrics,
                songType,
                SongLanguage.UNKNOWN
        );
    }

    public Integer getFamilyId() {
        return familyId;
    }

    public void setFamilyId(Integer familyId) {
        this.familyId = familyId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getLyrics() {
        return lyrics;
    }

    public void setLyrics(String lyrics) {
        this.lyrics = lyrics;
    }

    public SongType getSongType() {
        return songType;
    }

    public void setSongType(SongType songType) {
        this.songType = songType;
    }

    public SongLanguage getLanguage() {
        return language == null
                ? SongLanguage.UNKNOWN
                : language;
    }

    public void setLanguage(SongLanguage language) {
        this.language = language;
    }
}
