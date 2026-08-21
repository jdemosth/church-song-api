package com.churchsong.service;

import com.churchsong.dto.SongSectionDescriptorRequest;
import com.churchsong.dto.SongSectionsUpdateRequest;
import com.churchsong.model.Song;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class SongSectionStructureService {

    private static final List<String> SUPPORTED_SECTION_TYPES = List.of(
            "VERSE",
            "CHORUS",
            "BRIDGE",
            "PRE_CHORUS",
            "REFRAIN",
            "INTRO",
            "OUTRO",
            "OTHER"
    );
    private static final Pattern BLOCK_NAME_PATTERN = Pattern.compile(
            "^block\\s+\\d+$",
            Pattern.CASE_INSENSITIVE
    );

    private final SongLibrary songLibrary;
    private final ObjectMapper objectMapper;

    public SongSectionStructureService(
            SongLibrary songLibrary) {
        this.songLibrary = songLibrary;
        this.objectMapper = new ObjectMapper();
    }

    public Song updateSongSections(
            int songId,
            SongSectionsUpdateRequest request) {
        Song song = songLibrary.findSongById(songId);

        if (song == null) {
            throw new IllegalArgumentException("Song not found.");
        }

        validateRequest(song, request);
        song.setSectionStructure(
                serializeSections(request.getSections())
        );
        song.setSectionsConfirmed(
                request.getRawSectionsConfirmed() == null
                        ? true
                        : request.getRawSectionsConfirmed()
        );
        return songLibrary.updateSong(song);
    }

    private void validateRequest(
            Song song,
            SongSectionsUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Section update request is required."
            );
        }

        List<SongSectionDescriptorRequest> sections =
                request.getSections();

        if (sections == null || sections.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one section is required."
            );
        }

        int expectedSectionCount =
                buildLegacySections(song.getLyrics()).size();

        if (expectedSectionCount != sections.size()) {
            throw new IllegalArgumentException(
                    "Section count does not match the song's existing section structure."
            );
        }

        for (SongSectionDescriptorRequest section : sections) {
            validateDescriptor(section);
        }
    }

    private void validateDescriptor(
            SongSectionDescriptorRequest section) {
        if (section == null) {
            throw new IllegalArgumentException(
                    "Section metadata cannot contain empty entries."
            );
        }

        String type = normalizeSectionType(section.getType());

        if (!SUPPORTED_SECTION_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "Unsupported section type: " + section.getType()
            );
        }

        if ("VERSE".equals(type)) {
            if (section.getVerseNumber() == null
                    || section.getVerseNumber() <= 0) {
                throw new IllegalArgumentException(
                        "Verse sections require a positive verse number."
                );
            }
            return;
        }

        if ("OTHER".equals(type)) {
            String customLabel = trimToNull(section.getCustomLabel());

            if (customLabel == null) {
                throw new IllegalArgumentException(
                        "Other sections require a custom label."
                );
            }

            if (BLOCK_NAME_PATTERN.matcher(customLabel).matches()) {
                throw new IllegalArgumentException(
                        "Artificial Block labels are not valid saved section metadata."
                );
            }
        }
    }

    private String serializeSections(
            List<SongSectionDescriptorRequest> sections) {
        List<Map<String, Object>> serialized = new ArrayList<>();

        for (SongSectionDescriptorRequest section : sections) {
            String type = normalizeSectionType(section.getType());
            Integer verseNumber =
                    "VERSE".equals(type)
                            ? section.getVerseNumber()
                            : null;
            String customLabel =
                    "OTHER".equals(type)
                            ? trimToNull(section.getCustomLabel())
                            : "";

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", type);
            entry.put("verseNumber", verseNumber);
            entry.put("customLabel", customLabel == null ? "" : customLabel);
            entry.put("name", formatSectionName(type, verseNumber, customLabel));
            serialized.add(entry);
        }

        try {
            return objectMapper.writeValueAsString(serialized);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Unable to serialize section metadata.",
                    exception
            );
        }
    }

    private List<LegacySection> buildLegacySections(String lyrics) {
        if (lyrics == null || lyrics.isBlank()) {
            return List.of();
        }

        List<LegacySection> sections = new ArrayList<>();
        List<String> currentLines = new ArrayList<>();

        for (String rawLine : lyrics.split("\n")) {
            String line = rawLine.trim();

            if (line.isEmpty()) {
                continue;
            }

            if (line.matches("^\\[(.+)]$")) {
                if (!currentLines.isEmpty()) {
                    sections.add(new LegacySection(currentLines));
                }
                currentLines = new ArrayList<>();
                continue;
            }

            currentLines.add(line);
        }

        if (!currentLines.isEmpty()) {
            sections.add(new LegacySection(currentLines));
        }

        return sections;
    }

    private String normalizeSectionType(String type) {
        return String.valueOf(type == null ? "" : type)
                .trim()
                .toUpperCase()
                .replace('-', '_')
                .replace(' ', '_');
    }

    private String formatSectionName(
            String type,
            Integer verseNumber,
            String customLabel) {
        return switch (type) {
            case "VERSE" -> "Verse " + verseNumber;
            case "CHORUS" -> "Chorus";
            case "BRIDGE" -> "Bridge";
            case "PRE_CHORUS" -> "Pre-Chorus";
            case "REFRAIN" -> "Refrain";
            case "INTRO" -> "Intro";
            case "OUTRO" -> "Outro";
            case "OTHER" -> customLabel;
            default -> "";
        };
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record LegacySection(List<String> lines) {
    }
}
