package com.churchsong.model;

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

    private String title;
    private String author;
    private String lyrics;

    @Enumerated(EnumType.STRING)
    private SongType songType;

    // Required by JPA
    protected Song() {
    }

    public Song(
            String title,
            String author,
            String lyrics,
            SongType songType) {

        setTitle(title);
        setAuthor(author);
        setLyrics(lyrics);
        setSongType(songType);
    }

    public Integer getId() {
        return id;
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

    public void setLyrics(String lyrics) {
        if (lyrics == null || lyrics.trim().isEmpty()) {
            lyrics = null;
        } else {
            lyrics = lyrics.trim();
        }

        this.lyrics = lyrics;
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
        if (songType == null) {
            throw new IllegalArgumentException(
                    "songType cannot be null."
            );
        }

        this.songType = songType;
    }

    @Override
    public String toString() {
        return "Song ID: " + id
                + ", Title: " + title
                + ", Author: " + author
                + ", Song Type: " + songType;
    }
}