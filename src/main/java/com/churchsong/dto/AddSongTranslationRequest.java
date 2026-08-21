package com.churchsong.dto;

import com.churchsong.model.SongLanguage;

public class AddSongTranslationRequest {

    private SongLanguage language;
    private String title;
    private String author;
    private String lyrics;

    public AddSongTranslationRequest() {
    }

    public SongLanguage getLanguage() {
        return language;
    }

    public void setLanguage(SongLanguage language) {
        this.language = language;
    }

    public SongLanguage getRawLanguage() {
        return language;
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
}
