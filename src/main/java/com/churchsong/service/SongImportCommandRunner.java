package com.churchsong.service;

import com.churchsong.dto.importing.ImportReport;
import com.churchsong.dto.cleanup.TestDataCleanupReport;
import com.churchsong.dto.cleanup.LegacyCleanupPlanReport;
import com.churchsong.dto.normalization.MultilingualFamilyNormalizationReport;
import com.churchsong.dto.normalization.SectionNormalizationUpdateReport;
import com.churchsong.dto.audit.SongDataAuditReport;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class SongImportCommandRunner implements ApplicationRunner {

    private final SongImportService songImportService;
    private final TestDataCleanupService testDataCleanupService;
    private final MultilingualFamilyNormalizationService multilingualFamilyNormalizationService;
    private final SectionNormalizationUpdateService sectionNormalizationUpdateService;
    private final SongDataAuditService songDataAuditService;
    private final LegacyCleanupPlanningService legacyCleanupPlanningService;
    private final ConfigurableApplicationContext applicationContext;

    public SongImportCommandRunner(
            SongImportService songImportService,
            TestDataCleanupService testDataCleanupService,
            MultilingualFamilyNormalizationService multilingualFamilyNormalizationService,
            SectionNormalizationUpdateService sectionNormalizationUpdateService,
            SongDataAuditService songDataAuditService,
            LegacyCleanupPlanningService legacyCleanupPlanningService,
            ConfigurableApplicationContext applicationContext) {
        this.songImportService = songImportService;
        this.testDataCleanupService = testDataCleanupService;
        this.multilingualFamilyNormalizationService = multilingualFamilyNormalizationService;
        this.sectionNormalizationUpdateService = sectionNormalizationUpdateService;
        this.songDataAuditService = songDataAuditService;
        this.legacyCleanupPlanningService = legacyCleanupPlanningService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        boolean cleanupTestData = arguments.containsOption("cleanup-test-data");
        boolean normalizeMultilingualFamily = arguments.containsOption("normalize-multilingual-family");
        boolean applySectionNormalization = arguments.containsOption("apply-section-normalization");
        boolean auditSongData = arguments.containsOption("audit-song-data");
        boolean planLegacyCleanup = arguments.containsOption("plan-legacy-cleanup");
        boolean safeSongsOnly = arguments.containsOption("safe-songs-only");
        boolean hasSongsFile = arguments.containsOption("songs-file");
        boolean hasFamiliesFile = arguments.containsOption("families-file");

        if ((cleanupTestData || normalizeMultilingualFamily || applySectionNormalization || auditSongData || planLegacyCleanup)
                && (hasSongsFile || hasFamiliesFile || safeSongsOnly)) {
            throw new IllegalArgumentException(
                    "Use either a maintenance command or the multilingual import options, not both."
            );
        }

        if (cleanupTestData) {
            runCleanup(arguments);
            return;
        }

        if (normalizeMultilingualFamily) {
            runMultilingualFamilyNormalization(arguments);
            return;
        }

        if (applySectionNormalization) {
            runSectionNormalizationUpdate(arguments);
            return;
        }

        if (auditSongData) {
            runSongDataAudit(arguments);
            return;
        }

        if (planLegacyCleanup) {
            runLegacyCleanupPlan(arguments);
            return;
        }

        if (!hasSongsFile && !hasFamiliesFile && !safeSongsOnly) {
            return;
        }

        if (safeSongsOnly) {
            runSafeSongImport(arguments, hasSongsFile, hasFamiliesFile);
            return;
        }

        if (!hasSongsFile || !hasFamiliesFile) {
            throw new IllegalArgumentException(
                    "Both --songs-file and --families-file are required for song import."
            );
        }

        if (arguments.containsOption("dry-run")
                && arguments.containsOption("apply")) {
            throw new IllegalArgumentException(
                    "Use either --dry-run or --apply, not both."
            );
        }

        boolean apply = arguments.containsOption("apply");
        boolean approvedFamiliesOnly = arguments.containsOption(
                "approved-families-only"
        );

        Path songsFile = Path.of(getRequiredOption(arguments, "songs-file"));
        Path familiesFile = Path.of(getRequiredOption(arguments, "families-file"));

        ImportReport report = songImportService.importReviewedSongs(
                songsFile,
                familiesFile,
                approvedFamiliesOnly,
                apply
        );

        System.out.println(report.toConsoleReport());
        int exitCode = report.hasErrors() ? 1 : 0;
        SpringApplication.exit(applicationContext, () -> exitCode);
    }

    private void runSafeSongImport(
            ApplicationArguments arguments,
            boolean hasSongsFile,
            boolean hasFamiliesFile) {
        if (!hasSongsFile) {
            throw new IllegalArgumentException(
                    "--safe-songs-only requires --songs-file."
            );
        }

        if (hasFamiliesFile) {
            throw new IllegalArgumentException(
                    "--safe-songs-only does not accept --families-file."
            );
        }

        if (arguments.containsOption("approved-families-only")) {
            throw new IllegalArgumentException(
                    "--approved-families-only is not valid with --safe-songs-only."
            );
        }

        if (arguments.containsOption("dry-run")
                && arguments.containsOption("apply")) {
            throw new IllegalArgumentException(
                    "Use either --dry-run or --apply, not both."
            );
        }

        boolean apply = arguments.containsOption("apply");
        Path songsFile = Path.of(getRequiredOption(arguments, "songs-file"));
        ImportReport report = songImportService.importSafeSongsOnly(
                songsFile,
                apply
        );

        System.out.println(report.toConsoleReport());
        int exitCode = report.hasErrors() ? 1 : 0;
        SpringApplication.exit(applicationContext, () -> exitCode);
    }

    private void runCleanup(ApplicationArguments arguments) {
        if (arguments.containsOption("dry-run")
                && arguments.containsOption("apply")) {
            throw new IllegalArgumentException(
                    "Use either --dry-run or --apply, not both."
            );
        }

        boolean apply = arguments.containsOption("apply");
        TestDataCleanupReport report =
                testDataCleanupService.cleanupAutomatedTestData(apply);
        System.out.println(report.toConsoleReport());
        SpringApplication.exit(applicationContext, () -> 0);
    }

    private void runMultilingualFamilyNormalization(ApplicationArguments arguments) {
        if (arguments.containsOption("dry-run")
                && arguments.containsOption("apply")) {
            throw new IllegalArgumentException(
                    "Use either --dry-run or --apply, not both."
            );
        }

        boolean apply = arguments.containsOption("apply");
        MultilingualFamilyNormalizationReport report =
                multilingualFamilyNormalizationService.normalizeLegacyDuplicateFamily(apply);
        System.out.println(report.toConsoleReport());
        SpringApplication.exit(applicationContext, () -> 0);
    }

    private void runSongDataAudit(ApplicationArguments arguments) {
        if (arguments.containsOption("apply")) {
            throw new IllegalArgumentException(
                    "--audit-song-data is read-only and does not support --apply."
            );
        }

        SongDataAuditReport report = songDataAuditService.auditSongData();
        System.out.println(report.toConsoleReport());
        SpringApplication.exit(applicationContext, () -> 0);
    }

    private void runSectionNormalizationUpdate(ApplicationArguments arguments) {
        if (arguments.containsOption("dry-run")
                && arguments.containsOption("apply")) {
            throw new IllegalArgumentException(
                    "Use either --dry-run or --apply, not both."
            );
        }

        boolean apply = arguments.containsOption("apply");
        Path sectionFixesFile = Path.of(
                getRequiredOption(arguments, "section-fixes-file")
        );
        SectionNormalizationUpdateReport report =
                sectionNormalizationUpdateService.applySectionNormalization(
                        sectionFixesFile,
                        apply
                );
        System.out.println(report.toConsoleReport());
        SpringApplication.exit(applicationContext, () -> 0);
    }

    private void runLegacyCleanupPlan(ApplicationArguments arguments) {
        if (arguments.containsOption("dry-run")
                && arguments.containsOption("apply")) {
            throw new IllegalArgumentException(
                    "Use either --dry-run or --apply, not both."
            );
        }

        boolean apply = arguments.containsOption("apply");
        LegacyCleanupPlanReport report = legacyCleanupPlanningService.planLegacyCleanup(apply);
        System.out.println(report.toConsoleReport());
        SpringApplication.exit(applicationContext, () -> 0);
    }

    private String getRequiredOption(
            ApplicationArguments arguments,
            String optionName) {
        return arguments.getOptionValues(optionName)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Missing value for --" + optionName
                ));
    }
}
