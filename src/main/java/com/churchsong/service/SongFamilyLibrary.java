package com.churchsong.service;

import com.churchsong.model.Song;
import com.churchsong.model.SongFamily;
import com.churchsong.repository.SongFamilyRepository;
import com.churchsong.repository.SongRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class SongFamilyLibrary {

    private final SongFamilyRepository songFamilyRepository;
    private final SongRepository songRepository;

    public SongFamilyLibrary(
            SongFamilyRepository songFamilyRepository,
            SongRepository songRepository) {
        this.songFamilyRepository = songFamilyRepository;
        this.songRepository = songRepository;
    }

    public SongFamily addFamily(SongFamily songFamily) {
        validateSongFamily(songFamily);
        return songFamilyRepository.save(songFamily);
    }

    public SongFamily findFamilyById(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException(
                    "id must be greater than 0."
            );
        }

        return songFamilyRepository.findById(id)
                .orElse(null);
    }

    public List<SongFamily> getFamilyList() {
        return songFamilyRepository.findAll();
    }

    public boolean removeFamilyById(int id) {
        SongFamily songFamily = findFamilyById(id);

        if (songFamily == null) {
            return false;
        }

        songFamilyRepository.delete(songFamily);
        return true;
    }

    public List<Song> getSongsByFamilyId(int familyId) {
        if (familyId <= 0) {
            throw new IllegalArgumentException(
                    "familyId must be greater than 0."
            );
        }

        return songRepository.findByFamilyId(familyId)
                .stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private void validateSongFamily(SongFamily songFamily) {
        if (songFamily == null) {
            throw new IllegalArgumentException(
                    "Song family cannot be null."
            );
        }
    }
}
