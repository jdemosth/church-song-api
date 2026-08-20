package com.churchsong.service;

import com.churchsong.dto.SongFamilyVersionsResponse;
import com.churchsong.model.Song;
import com.churchsong.model.SongFamily;
import com.churchsong.model.SongLanguage;
import com.churchsong.model.SongType;
import com.churchsong.repository.SongFamilyRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SongFamilyLibrary {

    private final SongFamilyRepository songFamilyRepository;
    private final JdbcTemplate jdbcTemplate;

    public SongFamilyLibrary(
            SongFamilyRepository songFamilyRepository,
            JdbcTemplate jdbcTemplate) {
        this.songFamilyRepository = songFamilyRepository;
        this.jdbcTemplate = jdbcTemplate;
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

        return jdbcTemplate.query(
                """
                        select
                            coalesce(nullif(id, 0), rowid) as resolved_id,
                            family_id,
                            title,
                            author,
                            lyrics,
                            source_url,
                            song_type,
                            language
                        from song
                        where family_id = ?
                        order by id
                """,
                (resultSet, rowNum) -> {
                    Integer familyIdValue =
                            resultSet.getInt("family_id");
                    Integer songId =
                            resultSet.getInt("resolved_id");
                    Song song = new Song(
                            familyIdValue,
                            resultSet.getString("title"),
                            resultSet.getString("author"),
                            resultSet.getString("lyrics"),
                            resultSet.getString("source_url"),
                            SongType.valueOf(
                                    resultSet.getString("song_type")),
                            SongLanguage.valueOf(
                                    resultSet.getString("language"))
                    );
                    song.setId(songId);
                    return song;
                },
                familyId
        );
    }

    public SongFamilyVersionsResponse getLanguageVersionsByFamilyId(
            int familyId) {
        Map<SongLanguage, Song> versions =
                new LinkedHashMap<>();

        for (SongLanguage language :
                SongLanguage.supportedFamilyLanguages()) {
            versions.put(language, null);
        }

        for (Song song : getSongsByFamilyId(familyId)) {
            if (song == null) {
                continue;
            }

            SongLanguage language =
                    song.getLanguage();

            if (!versions.containsKey(language)) {
                continue;
            }

            versions.put(language, song);
        }

        return new SongFamilyVersionsResponse(
                familyId,
                versions
        );
    }

    private void validateSongFamily(SongFamily songFamily) {
        if (songFamily == null) {
            throw new IllegalArgumentException(
                    "Song family cannot be null."
            );
        }
    }
}
