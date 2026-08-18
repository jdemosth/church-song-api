package com.churchsong.service;

import com.churchsong.dto.importing.ImportReport;
import com.churchsong.dto.cleanup.TestDataCleanupReport;
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
    private final ConfigurableApplicationContext applicationContext;

    public SongImportCommandRunner(
            SongImportService songImportService,
            TestDataCleanupService testDataCleanupService,
            ConfigurableApplicationContext applicationContext) {
        this.songImportService = songImportService;
        this.testDataCleanupService = testDataCleanupService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        boolean cleanupTestData = arguments.containsOption("cleanup-test-data");
        boolean hasSongsFile = arguments.containsOption("songs-file");
        boolean hasFamiliesFile = arguments.containsOption("families-file");

        if (cleanupTestData && (hasSongsFile || hasFamiliesFile)) {
            throw new IllegalArgumentException(
                    "Use either --cleanup-test-data or the multilingual import options, not both."
            );
        }

        if (cleanupTestData) {
            runCleanup(arguments);
            return;
        }

        if (!hasSongsFile && !hasFamiliesFile) {
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
