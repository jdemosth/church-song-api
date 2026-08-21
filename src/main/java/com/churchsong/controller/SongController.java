package com.churchsong.controller;

import com.churchsong.dto.AddSongTranslationRequest;
import com.churchsong.dto.AddSongTranslationResponse;
import com.churchsong.dto.SongRequest;
import com.churchsong.dto.SongSectionsUpdateRequest;
import com.churchsong.model.Song;
import com.churchsong.model.SongLanguage;
import com.churchsong.service.SongLibrary;
import com.churchsong.service.SongSectionStructureService;
import com.churchsong.service.SongTranslationService;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
public class SongController {

    private final SongLibrary songLibrary;
    private final SongSectionStructureService songSectionStructureService;
    private final SongTranslationService songTranslationService;

    public SongController(
            SongLibrary songLibrary,
            SongSectionStructureService songSectionStructureService,
            SongTranslationService songTranslationService) {
        this.songLibrary = songLibrary;
        this.songSectionStructureService =
                songSectionStructureService;
        this.songTranslationService =
                songTranslationService;
    }

    @GetMapping("/songs")
    public List<Song> getAllSongs() {
        return songLibrary.getSongList();
    }

    @GetMapping("/songs/{id}")
    public Song getSongById(@PathVariable int id) {
        return songLibrary.findSongById(id);
    }

    @GetMapping("/songs/search")
    public Song getSongByTitle(@RequestParam String title) {
        return songLibrary.findSongByTitle(title);
    }

    @PostMapping("/songs")
    public Song addSong(@RequestBody SongRequest request) {
        if (request.getSongType() == null) {
            throw new IllegalArgumentException(
                    "songType cannot be null."
            );
        }

        Song song = new Song(
                request.getFamilyId(),
                request.getTitle(),
                request.getAuthor(),
                request.getLyrics(),
                request.getSongType(),
                request.getLanguage()
        );
        song.setSectionStructure(
                request.getSectionStructure()
        );
        song.setSectionsConfirmed(
                request.getRawSectionsConfirmed()
        );

        songLibrary.addSong(song);

        return song;
    }

    @PutMapping("/songs/{id}")
    public Song updateSong(
            @PathVariable int id,
            @RequestBody SongRequest request) {

        Song song = songLibrary.findSongById(id);

        if (song == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Song not found."
            );
        }

        if (request.getSongType() == null) {
            throw new IllegalArgumentException(
                    "songType cannot be null."
            );
        }

        song.setTitle(request.getTitle());
        song.setAuthor(request.getAuthor());
        song.setLyrics(request.getLyrics());
        song.setSectionStructure(
                request.getSectionStructure()
        );
        song.setSectionsConfirmed(
                request.getRawSectionsConfirmed()
        );
        song.setSongType(request.getSongType());

        if (request.getFamilyId() != null) {
            song.setFamilyId(request.getFamilyId());
        }

        if (request.getRawLanguage() != null) {
            song.setLanguage(request.getRawLanguage());
        }

        return songLibrary.updateSong(song);
    }

    @PutMapping("/songs/{id}/sections")
    public Song updateSongSections(
            @PathVariable int id,
            @RequestBody SongSectionsUpdateRequest request) {
        try {
            return songSectionStructureService
                    .updateSongSections(id, request);
        } catch (IllegalArgumentException exception) {
            if ("Song not found.".equals(exception.getMessage())) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        exception.getMessage()
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage()
            );
        }
    }

    @PostMapping("/songs/{id}/translations")
    @ResponseStatus(HttpStatus.CREATED)
    public AddSongTranslationResponse addTranslation(
            @PathVariable int id,
            @RequestBody AddSongTranslationRequest request) {
        Song sourceSong =
                songLibrary.findSongById(id);

        if (sourceSong == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Song not found."
            );
        }

        return songTranslationService
                .addTranslation(id, request);
    }

    @DeleteMapping("/songs/{id}")
    public boolean deleteSong(@PathVariable int id) {
        try {
            return songLibrary.removeSongById(id);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage()
            );
        }
    }
}
