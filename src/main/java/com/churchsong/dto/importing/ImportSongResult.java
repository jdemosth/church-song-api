package com.churchsong.dto.importing;

public class ImportSongResult {

    private final String title;
    private final String sourceUrl;
    private final String status;
    private final String reason;

    public ImportSongResult(
            String title,
            String sourceUrl,
            String status,
            String reason) {
        this.title = title;
        this.sourceUrl = sourceUrl;
        this.status = status;
        this.reason = reason;
    }

    public String getTitle() {
        return title;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "- " + title
                + " [" + sourceUrl + "]: "
                + status
                + " - "
                + reason;
    }
}
