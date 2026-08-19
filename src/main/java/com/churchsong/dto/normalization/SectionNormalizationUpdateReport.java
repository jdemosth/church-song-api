package com.churchsong.dto.normalization;

import java.util.ArrayList;
import java.util.List;

public class SectionNormalizationUpdateReport {

    private final String mode;
    private final List<SectionNormalizationUpdateCandidate> candidates;
    private int songsToUpdate;
    private int alreadyNormalized;
    private int notFound;
    private int ambiguousSourceUrlMatches;
    private int invalidRecords;
    private int errors;
    private boolean committed;

    public SectionNormalizationUpdateReport(String mode) {
        this.mode = mode;
        this.candidates = new ArrayList<>();
    }

    public void addCandidate(SectionNormalizationUpdateCandidate candidate) {
        candidates.add(candidate);
    }

    public List<SectionNormalizationUpdateCandidate> getCandidates() {
        return new ArrayList<>(candidates);
    }

    public int getCandidateCount() {
        return candidates.size();
    }

    public int getSongsToUpdate() {
        return songsToUpdate;
    }

    public void incrementSongsToUpdate() {
        songsToUpdate += 1;
    }

    public int getAlreadyNormalized() {
        return alreadyNormalized;
    }

    public void incrementAlreadyNormalized() {
        alreadyNormalized += 1;
    }

    public int getNotFound() {
        return notFound;
    }

    public void incrementNotFound() {
        notFound += 1;
    }

    public int getAmbiguousSourceUrlMatches() {
        return ambiguousSourceUrlMatches;
    }

    public void incrementAmbiguousSourceUrlMatches() {
        ambiguousSourceUrlMatches += 1;
    }

    public int getInvalidRecords() {
        return invalidRecords;
    }

    public void incrementInvalidRecords() {
        invalidRecords += 1;
    }

    public int getErrors() {
        return errors;
    }

    public void incrementErrors() {
        errors += 1;
    }

    public boolean isCommitted() {
        return committed;
    }

    public void setCommitted(boolean committed) {
        this.committed = committed;
    }

    public String toConsoleReport() {
        StringBuilder builder = new StringBuilder();
        builder.append("SECTION NORMALIZATION UPDATE\n\n");
        builder.append("Mode: ").append(mode).append("\n\n");
        builder.append("Candidates: ").append(getCandidateCount()).append('\n');
        builder.append("Songs to update: ").append(songsToUpdate).append('\n');
        builder.append("Already normalized: ").append(alreadyNormalized).append('\n');
        builder.append("Not found: ").append(notFound).append('\n');
        builder.append("Ambiguous sourceUrl matches: ")
                .append(ambiguousSourceUrlMatches)
                .append('\n');
        builder.append("Invalid records: ").append(invalidRecords).append('\n');
        builder.append("Errors: ").append(errors).append("\n\n");
        builder.append("Candidates:\n");
        if (candidates.isEmpty()) {
            builder.append("  none\n\n");
        } else {
            for (SectionNormalizationUpdateCandidate candidate : candidates) {
                builder.append("- songId=")
                        .append(candidate.getSongId())
                        .append(" | title=")
                        .append(candidate.getTitle())
                        .append(" | sourceUrl=")
                        .append(candidate.getSourceUrl())
                        .append(" | currentSections=")
                        .append(candidate.getCurrentDetectedSections())
                        .append(" | proposedSections=")
                        .append(candidate.getProposedDetectedSections())
                        .append(" | status=")
                        .append(candidate.getStatus())
                        .append('\n');
            }
            builder.append('\n');
        }

        if (committed) {
            builder.append("Database changes were committed.\n");
        } else {
            builder.append("No database changes were made.\n");
        }

        return builder.toString();
    }
}
