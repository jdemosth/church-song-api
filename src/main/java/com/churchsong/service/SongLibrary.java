package com.churchsong.service;

import com.churchsong.model.Song;
import com.churchsong.model.SongType;
import com.churchsong.repository.PlaylistRepository;
import com.churchsong.repository.SongRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class SongLibrary {

    private final SongRepository songRepository;
    private final PlaylistRepository playlistRepository;
    private final ServicePlanLibrary servicePlanLibrary;

    public SongLibrary(
            SongRepository songRepository,
            PlaylistRepository playlistRepository,
            ServicePlanLibrary servicePlanLibrary) {

        this.songRepository = songRepository;
        this.playlistRepository = playlistRepository;
        this.servicePlanLibrary = servicePlanLibrary;
    }

    public void addSong(Song song) {
        validateSong(song);
        songRepository.saveAndFlush(song);
    }

    public Song updateSong(Song song) {
        if (song == null) {
            throw new IllegalArgumentException(
                    "Song cannot be null."
            );
        }

        return songRepository.saveAndFlush(song);
    }

    public Song findSongById(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException(
                    "id must be greater than 0."
            );
        }

        return songRepository.findById(id)
                .orElse(null);
    }

    public Song findSongByTitle(String title) {
        if (title != null) {
            title = title.trim();
        }

        if (title == null || title.isEmpty()) {
            throw new IllegalArgumentException(
                    "title cannot be null or empty."
            );
        }

        return songRepository
                .findByTitleIgnoreCase(title)
                .orElse(null);
    }

    public List<Song> findSongsByType(SongType songType) {
        if (songType == null) {
            throw new IllegalArgumentException(
                    "songType cannot be null."
            );
        }

        return songRepository.findBySongType(songType)
                .stream()
                .filter(Objects::nonNull)
                .toList();
    }

    public boolean removeSongById(int id) {
        Song song = findSongById(id);

        if (song != null) {
            if (playlistRepository.findAll()
                    .stream()
                    .anyMatch(playlist ->
                            playlist.getSongs()
                                    .stream()
                                    .anyMatch(existingSong ->
                                            existingSong.getId()
                                                    .equals(song.getId())))) {

                throw new IllegalStateException(
                        "Cannot delete this song because it is still used in a playlist. Remove it from playlists first."
                );
            }

            if (servicePlanLibrary
                    .isSongUsedInAnyServicePlan(
                            song.getId())) {

                throw new IllegalStateException(
                        "Cannot delete this song because it is still used in a service plan. Remove it from service plans first."
                );
            }

            songRepository.delete(song);
            return true;
        }

        return false;
    }

    public List<Song> getSongList() {
        return songRepository.findAll()
                .stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private void validateSong(Song song) {
        if (song == null) {
            throw new IllegalArgumentException(
                    "Song cannot be null."
            );
        }
    }
}
