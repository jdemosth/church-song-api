package com.churchsong.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Song {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer familyId;
    private String title;
    private String author;
    private String lyrics;
    private String sourceUrl;
    private String sectionStructure;
    private Boolean sectionsConfirmed;

    @Enumerated(EnumType.STRING)
    private SongType songType;

    @Enumerated(EnumType.STRING)
    private SongLanguage language;

    // Required by JPA
    protected Song() {
        this.language = SongLanguage.UNKNOWN;
    }

    public Song(
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
                songType,
                SongLanguage.UNKNOWN
        );
    }

    public Song(
            Integer familyId,
            String title,
            String author,
            String lyrics,
            String sourceUrl,
            SongType songType,
            SongLanguage language) {

        setFamilyId(familyId);
        setTitle(title);
        setAuthor(author);
        setLyrics(lyrics);
        setSourceUrl(sourceUrl);
        setSongType(songType);
        setLanguage(language);
    }

    public Song(
            Integer familyId,
            String title,
            String author,
            String lyrics,
            SongType songType,
            SongLanguage language) {
        this(
                familyId,
                title,
                author,
                lyrics,
                null,
                songType,
                language
        );
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "id must be greater than 0."
            );
        }

        this.id = id;
    }

    public Integer getFamilyId() {
        return familyId;
    }

    public SongType getSongType() {
        return songType;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getLyrics() {
        return lyrics;
    }

    public String getSectionStructure() {
        return sectionStructure;
    }

    public boolean isSectionsConfirmed() {
        return Boolean.TRUE.equals(sectionsConfirmed);
    }

    @JsonIgnore
    public String getSourceUrl() {
        return sourceUrl;
    }

    public SongLanguage getLanguage() {
        return language == null
                ? SongLanguage.UNKNOWN
                : language;
    }

    public void setFamilyId(Integer familyId) {
        if (familyId != null && familyId <= 0) {
            throw new IllegalArgumentException(
                    "familyId must be greater than 0."
            );
        }

        this.familyId = familyId;
    }

    public void setLyrics(String lyrics) {
        if (lyrics == null || lyrics.trim().isEmpty()) {
            lyrics = null;
        } else {
            lyrics = lyrics.trim();
        }

        this.lyrics = lyrics;
    }

    public void setSourceUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.trim().isEmpty()) {
            this.sourceUrl = null;
            return;
        }

        sourceUrl = sourceUrl.trim();

        if (!sourceUrl.startsWith("http://")
                && !sourceUrl.startsWith("https://")) {
            throw new IllegalArgumentException(
                    "sourceUrl must start with http:// or https://."
            );
        }

        this.sourceUrl = sourceUrl;
    }

    public void setSectionStructure(String sectionStructure) {
        if (sectionStructure == null || sectionStructure.trim().isEmpty()) {
            this.sectionStructure = null;
            return;
        }

        this.sectionStructure = sectionStructure.trim();
    }

    public void setSectionsConfirmed(Boolean sectionsConfirmed) {
        this.sectionsConfirmed = Boolean.TRUE.equals(sectionsConfirmed);
    }

    public void setTitle(String title) {
        if (title != null) {
            title = title.trim();
        }

        if (title == null || title.isEmpty()) {
            throw new IllegalArgumentException(
                    "title cannot be null or empty."
            );
        }

        this.title = title;
    }

    public void setAuthor(String author) {
        if (author == null || author.trim().isEmpty()) {
            author = null;
        } else {
            author = author.trim();
        }

        this.author = author;
    }

    public void setSongType(SongType songType) {
        this.songType = songType;
    }

    public void setLanguage(SongLanguage language) {
        if (language == null) {
            language = SongLanguage.UNKNOWN;
        }

        this.language = language;
    }

    @Override
    public String toString() {
        return "Song ID: " + id
                + ", Family ID: " + familyId
                + ", Title: " + title
                + ", Author: " + author
                + ", Song Type: " + songType
                + ", Language: " + getLanguage();
    }
}
