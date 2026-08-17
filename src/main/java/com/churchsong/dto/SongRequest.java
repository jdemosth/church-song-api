package com.churchsong.dto;

import com.churchsong.model.SongType;

public class SongRequest {

    private String title;
    private String author;
    private String lyrics;
    private SongType songType;

    public SongRequest() {
        // Required for JSON deserialization
    }

    public SongRequest(
            String title,
            String author,
            String lyrics,
            SongType songType) {

        this.title = title;
        this.author = author;
        this.lyrics = lyrics;
        this.songType = songType;
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
}