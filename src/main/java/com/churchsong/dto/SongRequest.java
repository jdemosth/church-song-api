package com.churchsong.dto;

import com.churchsong.model.SongLanguage;
import com.churchsong.model.SongType;

public class SongRequest {

    private Integer familyId;
    private String title;
    private String author;
    private String lyrics;
    private String sectionStructure;
    private Boolean sectionsConfirmed;
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
            String sectionStructure,
            Boolean sectionsConfirmed,
            SongType songType,
            SongLanguage language) {

        this.familyId = familyId;
        this.title = title;
        this.author = author;
        this.lyrics = lyrics;
        this.sectionStructure = sectionStructure;
        this.sectionsConfirmed = sectionsConfirmed;
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
                null,
                false,
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

    public String getSectionStructure() {
        return sectionStructure;
    }

    public void setSectionStructure(String sectionStructure) {
        this.sectionStructure = sectionStructure;
    }

    public boolean isSectionsConfirmed() {
        return Boolean.TRUE.equals(sectionsConfirmed);
    }

    public Boolean getRawSectionsConfirmed() {
        return sectionsConfirmed;
    }

    public void setSectionsConfirmed(Boolean sectionsConfirmed) {
        this.sectionsConfirmed = sectionsConfirmed;
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

    public SongLanguage getRawLanguage() {
        return language;
    }

    public void setLanguage(SongLanguage language) {
        this.language = language;
    }
}
