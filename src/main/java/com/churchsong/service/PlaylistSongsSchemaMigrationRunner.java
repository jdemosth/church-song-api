package com.churchsong.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class PlaylistSongsSchemaMigrationRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(
            PlaylistSongsSchemaMigrationRunner.class
    );

    private final PlaylistSongsSchemaMigrationService playlistSongsSchemaMigrationService;

    public PlaylistSongsSchemaMigrationRunner(
            PlaylistSongsSchemaMigrationService playlistSongsSchemaMigrationService) {
        this.playlistSongsSchemaMigrationService = playlistSongsSchemaMigrationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            playlistSongsSchemaMigrationService.migrateIfNeeded();
        } catch (RuntimeException exception) {
            logger.warn(
                    "Skipping automatic playlist_songs schema migration during startup. "
                            + "The application will continue using the existing schema.",
                    exception
            );
        }
    }
}
