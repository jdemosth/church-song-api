package com.churchsong.service;

import com.churchsong.dto.AddSongTranslationRequest;
import com.churchsong.dto.AddSongTranslationResponse;
import com.churchsong.dto.SongFamilyVersionsResponse;
import com.churchsong.model.Song;
import com.churchsong.model.SongFamily;
import com.churchsong.model.SongLanguage;
import com.churchsong.repository.SongFamilyRepository;
import com.churchsong.repository.SongRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SongTranslationService {

    private final SongRepository songRepository;
    private final SongFamilyRepository songFamilyRepository;
    private final SongFamilyLibrary songFamilyLibrary;

    public SongTranslationService(
            SongRepository songRepository,
            SongFamilyRepository songFamilyRepository,
            SongFamilyLibrary songFamilyLibrary) {
        this.songRepository = songRepository;
        this.songFamilyRepository = songFamilyRepository;
        this.songFamilyLibrary = songFamilyLibrary;
    }

    @Transactional
    public AddSongTranslationResponse addTranslation(
            int sourceSongId,
            AddSongTranslationRequest request) {
        Song sourceSong = songRepository.findById(sourceSongId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Source song not found."
                ));

        SongLanguage language = validateRequest(request);

        Integer familyId = ensureFamilyId(sourceSong);
        List<Song> familySongs =
                songFamilyLibrary.getSongsByFamilyId(familyId);

        for (Song familySong : familySongs) {
            if (familySong == null) {
                continue;
            }

            if (familySong.getLanguage() == language) {
                throw new IllegalArgumentException(
                        "This song already has a "
                                + readableLanguageLabel(language)
                                + " version."
                );
            }
        }

        Song translationSong = new Song(
                familyId,
                request.getTitle(),
                request.getAuthor(),
                request.getLyrics(),
                sourceSong.getSongType(),
                language
        );

        translationSong =
                songRepository.saveAndFlush(
                        translationSong
                );

        SongFamilyVersionsResponse versions =
                songFamilyLibrary
                        .getLanguageVersionsByFamilyId(
                                familyId
                        );

        return new AddSongTranslationResponse(
                sourceSong,
                translationSong,
                versions
        );
    }

    private SongLanguage validateRequest(
            AddSongTranslationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Translation request cannot be null."
            );
        }

        SongLanguage language =
                request.getRawLanguage();

        if (language == null
                || !language.isSupportedFamilyLanguage()) {
            throw new IllegalArgumentException(
                    "Unsupported translation language."
            );
        }

        if (isBlank(request.getTitle())) {
            throw new IllegalArgumentException(
                    "Translation title cannot be blank."
            );
        }

        if (isBlank(request.getLyrics())) {
            throw new IllegalArgumentException(
                    "Translation lyrics cannot be blank."
            );
        }

        return language;
    }

    private Integer ensureFamilyId(Song sourceSong) {
        Integer existingFamilyId =
                sourceSong.getFamilyId();

        if (existingFamilyId != null) {
            return existingFamilyId;
        }

        Integer nextFamilyId =
                songFamilyRepository
                        .findTopByOrderByIdDesc()
                        .map(SongFamily::getId)
                        .map(id -> id + 1)
                        .orElse(1);

        SongFamily family = new SongFamily(
                nextFamilyId,
                sourceSong.getTitle()
        );
        songFamilyRepository.saveAndFlush(family);

        sourceSong.setFamilyId(nextFamilyId);
        songRepository.saveAndFlush(sourceSong);

        return nextFamilyId;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String readableLanguageLabel(
            SongLanguage language) {
        return switch (language) {
            case ENGLISH -> "English";
            case HAITIAN_CREOLE -> "Kreyòl";
            case SPANISH -> "Español";
            case FRENCH -> "Français";
            default -> language.name();
        };
    }
}
