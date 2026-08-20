package com.churchsong.controller;

import com.churchsong.dto.SongFamilyVersionsResponse;
import com.churchsong.dto.SongFamilyRequest;
import com.churchsong.model.Song;
import com.churchsong.model.SongFamily;
import com.churchsong.service.SongFamilyLibrary;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
public class SongFamilyController {

    private final SongFamilyLibrary songFamilyLibrary;

    public SongFamilyController(SongFamilyLibrary songFamilyLibrary) {
        this.songFamilyLibrary = songFamilyLibrary;
    }

    @GetMapping("/song-families")
    public List<SongFamily> getAllSongFamilies() {
        return songFamilyLibrary.getFamilyList();
    }

    @PostMapping("/song-families")
    @ResponseStatus(HttpStatus.CREATED)
    public SongFamily addSongFamily(
            @RequestBody SongFamilyRequest request) {
        SongFamily songFamily = new SongFamily(
                request.getId(),
                request.getCanonicalTitle()
        );

        return songFamilyLibrary.addFamily(songFamily);
    }

    @GetMapping("/song-families/{id}/songs")
    public List<Song> getSongsByFamilyId(
            @PathVariable int id) {
        return songFamilyLibrary.getSongsByFamilyId(id);
    }

    @GetMapping("/song-families/{id}/versions")
    public SongFamilyVersionsResponse getSongFamilyVersions(
            @PathVariable int id) {
        return songFamilyLibrary
                .getLanguageVersionsByFamilyId(id);
    }
}
