package com.churchsong.controller;

import com.churchsong.dto.PlaylistRequest;
import com.churchsong.dto.UseForTodayServiceRequest;
import com.churchsong.model.Playlist;
import com.churchsong.model.Song;
import com.churchsong.service.PlaylistLibrary;
import com.churchsong.service.SongLibrary;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
public class PlaylistController {

    private final PlaylistLibrary playlistLibrary;
    private final SongLibrary songLibrary;

    public PlaylistController(
            PlaylistLibrary playlistLibrary,
            SongLibrary songLibrary) {

        this.playlistLibrary = playlistLibrary;
        this.songLibrary = songLibrary;
    }

    @GetMapping("/playlists")
    public List<Playlist> getAllPlaylists() {
        return playlistLibrary.getPlaylistList();
    }

    @GetMapping("/playlists/search")
    public Playlist getPlaylistByName(
            @RequestParam String name) {

        return playlistLibrary.findPlaylistByName(name);
    }

    @PostMapping("/playlists")
    public Playlist addPlaylist(
            @RequestBody PlaylistRequest request) {

        Playlist playlist =
                new Playlist(request.getName());

        playlistLibrary.addPlaylist(playlist);

        return playlist;
    }

    @PostMapping("/playlists/{playlistId}/use-for-today-service")
    public Playlist useForTodayService(
            @PathVariable Long playlistId,
            @RequestBody UseForTodayServiceRequest request) {
        try {
            return playlistLibrary
                    .usePlaylistForTodayService(
                            playlistId,
                            request.getName(),
                            request.getServiceDate(),
                            request.isReplaceExisting());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage()
            );
        }
    }

    @PutMapping("/playlists/{currentName}")
    public Playlist renamePlaylist(
            @PathVariable String currentName,
            @RequestBody PlaylistRequest request) {

        return playlistLibrary.renamePlaylist(
                currentName,
                request.getName()
        );
    }

    @PutMapping("/playlists/{playlistId}/rename")
    public Playlist renamePlaylistById(
            @PathVariable Long playlistId,
            @RequestBody PlaylistRequest request) {

        return playlistLibrary.renamePlaylistById(
                playlistId,
                request.getName()
        );
    }

    @DeleteMapping("/playlists")
    public boolean deletePlaylist(
            @RequestParam String name) {

        return playlistLibrary
                .removePlaylistByName(name);
    }

    @PostMapping("/playlists/{playlistName}/songs/{songId}")
    public Playlist addSongToPlaylist(
            @PathVariable String playlistName,
            @PathVariable int songId) {

        Playlist playlist =
                playlistLibrary
                        .findPlaylistByName(
                                playlistName);

        Song song =
                songLibrary
                        .findSongById(songId);

        if (playlist == null || song == null) {
            return null;
        }

        return playlistLibrary
                .addSongToPlaylist(
                        playlist,
                        song);
    }

    @PostMapping("/playlists/id/{playlistId}/songs/{songId}")
    public Playlist addSongToPlaylistById(
            @PathVariable Long playlistId,
            @PathVariable int songId) {

        Playlist playlist =
                playlistLibrary.findPlaylistById(
                        playlistId);

        Song song =
                songLibrary.findSongById(songId);

        if (playlist == null || song == null) {
            return null;
        }

        return playlistLibrary.addSongToPlaylist(
                playlist,
                song
        );
    }

    @DeleteMapping("/playlists/{playlistName}/songs/{songId}")
    public Playlist removeSongFromPlaylist(
            @PathVariable String playlistName,
            @PathVariable int songId) {

        Playlist playlist =
                playlistLibrary
                        .findPlaylistByName(
                                playlistName);

        Song song =
                songLibrary
                        .findSongById(songId);

        if (playlist == null || song == null) {
            return null;
        }

        return playlistLibrary
                .removeSongFromPlaylist(
                        playlist,
                        song);
    }

    @DeleteMapping("/playlists/id/{playlistId}/songs/{songId}")
    public Playlist removeSongFromPlaylistById(
            @PathVariable Long playlistId,
            @PathVariable int songId) {

        Playlist playlist =
                playlistLibrary.findPlaylistById(
                        playlistId);

        Song song =
                songLibrary.findSongById(songId);

        if (playlist == null || song == null) {
            return null;
        }

        return playlistLibrary.removeSongFromPlaylist(
                playlist,
                song
        );
    }

    @PutMapping("/playlists/{playlistName}/songs/reorder")
    public Playlist reorderSong(
            @PathVariable String playlistName,
            @RequestParam int fromIndex,
            @RequestParam int toIndex) {

        Playlist playlist =
                playlistLibrary
                        .findPlaylistByName(
                                playlistName);

        if (playlist == null) {
            return null;
        }

        return playlistLibrary.moveSong(
                playlist,
                fromIndex,
                toIndex
        );
    }

    @PutMapping("/playlists/id/{playlistId}/songs/reorder")
    public Playlist reorderSongById(
            @PathVariable Long playlistId,
            @RequestParam int fromIndex,
            @RequestParam int toIndex) {

        Playlist playlist =
                playlistLibrary.findPlaylistById(
                        playlistId);

        if (playlist == null) {
            return null;
        }

        return playlistLibrary.moveSong(
                playlist,
                fromIndex,
                toIndex
        );
    }
}
