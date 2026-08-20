package com.churchsong.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderColumn;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Playlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String serviceType;
    private Boolean reusable;
    private LocalDate serviceDate;
    private String theme;
    private Long sourcePlaylistId;

    @ManyToMany
    @OrderColumn(name = "song_order")
    private List<Song> songs;

    // Required by JPA
    protected Playlist() {
        this.reusable = true;
        this.songs = new ArrayList<>();
    }

    public Playlist(String name) {
        setName(name);
        this.reusable = true;
        this.songs = new ArrayList<>();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isReusable() {
        return reusable == null || reusable;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        if (serviceType == null || serviceType.trim().isEmpty()) {
            this.serviceType = null;
            return;
        }

        this.serviceType = serviceType.trim();
    }

    public void setReusable(boolean reusable) {
        this.reusable = reusable;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public void setServiceDate(LocalDate serviceDate) {
        this.serviceDate = serviceDate;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        if (theme == null || theme.trim().isEmpty()) {
            this.theme = null;
            return;
        }

        this.theme = theme.trim();
    }

    public Long getSourcePlaylistId() {
        return sourcePlaylistId;
    }

    public void setSourcePlaylistId(Long sourcePlaylistId) {
        this.sourcePlaylistId = sourcePlaylistId;
    }

    public void setName(String name) {
        if (name != null) {
            name = name.trim();
        }

        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(
                    "name cannot be null or empty."
            );
        }

        this.name = name;
    }

    public List<Song> getSongs() {
        return new ArrayList<>(songs);
    }

    public void addSong(Song song) {
        if (song == null) {
            throw new IllegalArgumentException(
                    "song cannot be null."
            );
        }

        if (isSongInPlaylist(song)) {
            throw new IllegalArgumentException(
                    "song with ID "
                            + song.getId()
                            + " is already in the playlist."
            );
        }

        songs.add(song);
    }

    public void removeSong(Song song) {
        if (song == null) {
            throw new IllegalArgumentException(
                    "song cannot be null."
            );
        }

        if (!isSongInPlaylist(song)) {
            throw new IllegalArgumentException(
                    "song with ID "
                            + song.getId()
                            + " is not in the playlist."
            );
        }

        songs.removeIf(
                existingSong ->
                        existingSong.getId() == song.getId()
        );
    }

    public void moveSong(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= songs.size()) {
            throw new IllegalArgumentException(
                    "fromIndex is invalid."
            );
        }

        if (toIndex < 0 || toIndex >= songs.size()) {
            throw new IllegalArgumentException(
                    "toIndex is invalid."
            );
        }

        Song song = songs.remove(fromIndex);
        songs.add(toIndex, song);
    }

    public void replaceSongs(List<Song> songs) {
        this.songs = new ArrayList<>();

        if (songs == null) {
            return;
        }

        this.songs.addAll(songs);
    }

    private boolean isSongInPlaylist(Song song) {
        for (Song existingSong : songs) {
            if (existingSong.getId() == song.getId()) {
                return true;
            }
        }

        return false;
    }
}
