package com.churchsong.dto.migration;

import java.util.LinkedHashMap;
import java.util.Map;

public class SongLanguageSchemaMigrationReport {

    private final String mode;
    private boolean committed;
    private boolean alreadyMigrated;
    private String originalSongTableSql;
    private String newSongTableSql;
    private String integrityCheckResult;
    private int foreignKeyViolationCount;
    private CountsSnapshot preCounts;
    private CountsSnapshot postCounts;

    public SongLanguageSchemaMigrationReport(String mode) {
        this.mode = mode;
        this.committed = false;
        this.alreadyMigrated = false;
        this.preCounts = new CountsSnapshot();
        this.postCounts = new CountsSnapshot();
    }

    public String getMode() {
        return mode;
    }

    public boolean isCommitted() {
        return committed;
    }

    public void setCommitted(boolean committed) {
        this.committed = committed;
    }

    public boolean isAlreadyMigrated() {
        return alreadyMigrated;
    }

    public void setAlreadyMigrated(boolean alreadyMigrated) {
        this.alreadyMigrated = alreadyMigrated;
    }

    public String getOriginalSongTableSql() {
        return originalSongTableSql;
    }

    public void setOriginalSongTableSql(String originalSongTableSql) {
        this.originalSongTableSql = originalSongTableSql;
    }

    public String getNewSongTableSql() {
        return newSongTableSql;
    }

    public void setNewSongTableSql(String newSongTableSql) {
        this.newSongTableSql = newSongTableSql;
    }

    public String getIntegrityCheckResult() {
        return integrityCheckResult;
    }

    public void setIntegrityCheckResult(String integrityCheckResult) {
        this.integrityCheckResult = integrityCheckResult;
    }

    public int getForeignKeyViolationCount() {
        return foreignKeyViolationCount;
    }

    public void setForeignKeyViolationCount(int foreignKeyViolationCount) {
        this.foreignKeyViolationCount = foreignKeyViolationCount;
    }

    public CountsSnapshot getPreCounts() {
        return preCounts;
    }

    public void setPreCounts(CountsSnapshot preCounts) {
        this.preCounts = preCounts;
    }

    public CountsSnapshot getPostCounts() {
        return postCounts;
    }

    public void setPostCounts(CountsSnapshot postCounts) {
        this.postCounts = postCounts;
    }

    public String toConsoleReport() {
        StringBuilder builder = new StringBuilder();
        builder.append("SONG LANGUAGE SCHEMA MIGRATION\n\n");
        builder.append("Mode: ").append(mode).append("\n");
        builder.append("Committed: ").append(committed).append("\n");
        builder.append("Already migrated: ").append(alreadyMigrated).append("\n\n");
        builder.append("Original song table SQL:\n")
                .append(originalSongTableSql)
                .append("\n\n");
        builder.append("New song table SQL:\n")
                .append(newSongTableSql)
                .append("\n\n");
        builder.append("Pre-migration counts:\n")
                .append(preCounts.toConsoleString())
                .append("\n");
        builder.append("Post-migration counts:\n")
                .append(postCounts.toConsoleString())
                .append("\n");
        builder.append("Integrity check: ")
                .append(integrityCheckResult)
                .append("\n");
        builder.append("Foreign key violations: ")
                .append(foreignKeyViolationCount)
                .append("\n");
        return builder.toString();
    }

    public static class CountsSnapshot {
        private int songCount;
        private int familyCount;
        private int playlistSongCount;
        private int servicePlanSongCount;
        private Integer minSongId;
        private Integer maxSongId;
        private Map<String, Integer> languageCounts = new LinkedHashMap<>();

        public int getSongCount() {
            return songCount;
        }

        public void setSongCount(int songCount) {
            this.songCount = songCount;
        }

        public int getFamilyCount() {
            return familyCount;
        }

        public void setFamilyCount(int familyCount) {
            this.familyCount = familyCount;
        }

        public int getPlaylistSongCount() {
            return playlistSongCount;
        }

        public void setPlaylistSongCount(int playlistSongCount) {
            this.playlistSongCount = playlistSongCount;
        }

        public int getServicePlanSongCount() {
            return servicePlanSongCount;
        }

        public void setServicePlanSongCount(int servicePlanSongCount) {
            this.servicePlanSongCount = servicePlanSongCount;
        }

        public Integer getMinSongId() {
            return minSongId;
        }

        public void setMinSongId(Integer minSongId) {
            this.minSongId = minSongId;
        }

        public Integer getMaxSongId() {
            return maxSongId;
        }

        public void setMaxSongId(Integer maxSongId) {
            this.maxSongId = maxSongId;
        }

        public Map<String, Integer> getLanguageCounts() {
            return languageCounts;
        }

        public void setLanguageCounts(Map<String, Integer> languageCounts) {
            this.languageCounts = languageCounts;
        }

        public String toConsoleString() {
            StringBuilder builder = new StringBuilder();
            builder.append("  songs: ").append(songCount).append("\n");
            builder.append("  families: ").append(familyCount).append("\n");
            builder.append("  playlist_songs: ").append(playlistSongCount).append("\n");
            builder.append("  service_plan_songs: ").append(servicePlanSongCount).append("\n");
            builder.append("  minSongId: ").append(minSongId).append("\n");
            builder.append("  maxSongId: ").append(maxSongId).append("\n");
            builder.append("  languageCounts:\n");
            for (Map.Entry<String, Integer> entry : languageCounts.entrySet()) {
                builder.append("    ")
                        .append(entry.getKey())
                        .append(": ")
                        .append(entry.getValue())
                        .append("\n");
            }
            return builder.toString();
        }
    }
}
