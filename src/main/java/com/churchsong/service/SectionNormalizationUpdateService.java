package com.churchsong.service;

import com.churchsong.dto.normalization.SectionNormalizationUpdateCandidate;
import com.churchsong.dto.normalization.SectionNormalizationUpdateReport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class SectionNormalizationUpdateService {

    private static final TypeReference<List<SectionFixRecord>> SECTION_FIX_RECORDS =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public SectionNormalizationUpdateService(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public SectionNormalizationUpdateReport applySectionNormalization(
            Path fixesFile,
            boolean apply) {
        validateFilePath(fixesFile);
        List<SectionFixRecord> fixRecords = readFixesFile(fixesFile);
        List<SectionUpdatePlan> updatePlans = new ArrayList<>();

        SectionNormalizationUpdateReport report = new SectionNormalizationUpdateReport(
                apply ? "APPLY" : "DRY RUN"
        );

        for (SectionFixRecord fixRecord : fixRecords) {
            SectionUpdateInspection inspection = inspectRecord(fixRecord);
            report.addCandidate(
                    new SectionNormalizationUpdateCandidate(
                            inspection.songId(),
                            inspection.title(),
                            inspection.sourceUrl(),
                            inspection.currentSections(),
                            inspection.proposedSections(),
                            inspection.status()
                    )
            );

            switch (inspection.status()) {
                case "UPDATE" -> {
                    report.incrementSongsToUpdate();
                    updatePlans.add(inspection.updatePlan());
                }
                case "ALREADY_NORMALIZED" -> report.incrementAlreadyNormalized();
                case "NOT_FOUND" -> report.incrementNotFound();
                case "AMBIGUOUS_SOURCE_URL" -> report.incrementAmbiguousSourceUrlMatches();
                case "INVALID_RECORD" -> report.incrementInvalidRecords();
                default -> report.incrementErrors();
            }
        }

        if (!apply) {
            report.setCommitted(false);
            return report;
        }

        try {
            transactionTemplate.executeWithoutResult(status -> {
                for (SectionUpdatePlan updatePlan : updatePlans) {
                    int updatedRows = jdbcTemplate.update(
                            "update song set lyrics = ? where id = ? and source_url = ?",
                            updatePlan.normalizedLyrics(),
                            updatePlan.songId(),
                            updatePlan.sourceUrl()
                    );

                    if (updatedRows != 1) {
                        throw new IllegalStateException(
                                "Failed to update song id="
                                        + updatePlan.songId()
                                        + " for sourceUrl="
                                        + updatePlan.sourceUrl()
                        );
                    }
                }
            });
            report.setCommitted(true);
            return report;
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Section normalization apply failed and was rolled back: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private SectionUpdateInspection inspectRecord(SectionFixRecord fixRecord) {
        String title = trimToNull(fixRecord.title());
        String sourceUrl = trimToNull(fixRecord.sourceUrl());
        String normalizedLyrics = trimToNull(fixRecord.proposedNormalizedLyrics());
        List<String> currentSections = copyList(fixRecord.currentSectionStructure());
        List<String> proposedSections = copyList(fixRecord.proposedDetectedSections());

        if (title == null || sourceUrl == null || normalizedLyrics == null) {
            return new SectionUpdateInspection(
                    null,
                    title == null ? "(missing title)" : title,
                    sourceUrl == null ? "(missing sourceUrl)" : sourceUrl,
                    currentSections,
                    proposedSections,
                    "INVALID_RECORD",
                    null
            );
        }

        List<SongRow> matches = jdbcTemplate.query(
                """
                        select id, title, author, lyrics, source_url, language, song_type, family_id
                        from song
                        where source_url = ?
                        order by id
                        """,
                (resultSet, rowNum) -> new SongRow(
                        resultSet.getInt("id"),
                        resultSet.getString("title"),
                        resultSet.getString("author"),
                        trimToNull(resultSet.getString("lyrics")),
                        resultSet.getString("source_url"),
                        resultSet.getString("language"),
                        resultSet.getString("song_type"),
                        (Integer) resultSet.getObject("family_id")
                ),
                sourceUrl
        );

        if (matches.isEmpty()) {
            return new SectionUpdateInspection(
                    null,
                    title,
                    sourceUrl,
                    currentSections,
                    proposedSections,
                    "NOT_FOUND",
                    null
            );
        }

        if (matches.size() > 1) {
            return new SectionUpdateInspection(
                    null,
                    title,
                    sourceUrl,
                    currentSections,
                    proposedSections,
                    "AMBIGUOUS_SOURCE_URL",
                    null
            );
        }

        SongRow songRow = matches.getFirst();
        String currentLyrics = trimToNull(songRow.lyrics());
        if (normalizedLyrics.equals(currentLyrics)) {
            return new SectionUpdateInspection(
                    songRow.id(),
                    songRow.title(),
                    songRow.sourceUrl(),
                    currentSections,
                    proposedSections,
                    "ALREADY_NORMALIZED",
                    null
            );
        }

        return new SectionUpdateInspection(
                songRow.id(),
                songRow.title(),
                songRow.sourceUrl(),
                currentSections,
                proposedSections,
                "UPDATE",
                new SectionUpdatePlan(songRow.id(), songRow.sourceUrl(), normalizedLyrics)
        );
    }

    private List<SectionFixRecord> readFixesFile(Path fixesFile) {
        try {
            return objectMapper.readValue(
                    Files.readString(fixesFile),
                    SECTION_FIX_RECORDS
            );
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "Unable to read section fixes file "
                            + fixesFile
                            + ": "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private void validateFilePath(Path filePath) {
        if (filePath == null) {
            throw new IllegalArgumentException("sectionFixesFile is required.");
        }

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new IllegalArgumentException(
                    "sectionFixesFile does not exist: " + filePath
            );
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> copyList(List<String> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }

    private record SectionFixRecord(
            String title,
            String sourceUrl,
            List<String> currentSectionStructure,
            List<String> proposedDetectedSections,
            String currentLyrics,
            String proposedNormalizedLyrics) {
    }

    private record SongRow(
            Integer id,
            String title,
            String author,
            String lyrics,
            String sourceUrl,
            String language,
            String songType,
            Integer familyId) {
    }

    private record SectionUpdatePlan(
            Integer songId,
            String sourceUrl,
            String normalizedLyrics) {
    }

    private record SectionUpdateInspection(
            Integer songId,
            String title,
            String sourceUrl,
            List<String> currentSections,
            List<String> proposedSections,
            String status,
            SectionUpdatePlan updatePlan) {
    }
}
