package com.churchsong;

import com.churchsong.model.Playlist;
import com.churchsong.model.ServicePlan;
import com.churchsong.model.Song;
import com.churchsong.model.SongLanguage;
import com.churchsong.model.SongType;
import com.churchsong.controller.ApiExceptionHandler;
import com.churchsong.controller.PlaylistController;
import com.churchsong.controller.ServicePlanController;
import com.churchsong.controller.SongController;
import com.churchsong.service.PlaylistLibrary;
import com.churchsong.service.ServicePlanLibrary;
import com.churchsong.service.SongLibrary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SongNullSongTypeCompatibilityTests {

    @Test
    void getEndpointsSerializeSongsWithNullSongType(
            @TempDir Path tempDir) throws Exception {
        withWebContext(
                tempDir.resolve("song-null-song-type-reads.db"),
                context -> {
                    SongLibrary songLibrary = context.getBean(SongLibrary.class);
                    PlaylistLibrary playlistLibrary = context.getBean(PlaylistLibrary.class);
                    ServicePlanLibrary servicePlanLibrary = context.getBean(ServicePlanLibrary.class);
                    TransactionTemplate transactionTemplate = transactionTemplate(context);
                    MockMvc mockMvc = mockMvc(context);

                    Song importedSong = new Song(
                            null,
                            "Imported Null SongType",
                            "Importer",
                            "Line 1\nLine 2",
                            null,
                            SongLanguage.ENGLISH
                    );
                    songLibrary.addSong(importedSong);

                    Playlist playlist =
                            playlistLibrary.createSavedServicePlaylist(
                                    "Null Type Playlist",
                                    "2026-08-18",
                                    "Grace"
                            );
                    playlistLibrary.addSongToPlaylist(
                            playlist,
                            importedSong
                    );

                    ServicePlan servicePlan =
                            servicePlanLibrary.createServicePlan(
                                    "Null Type Service",
                                    "2026-08-18",
                                    "19:00",
                                    List.of(importedSong)
                            );

                    transactionTemplate.executeWithoutResult(
                            status -> {
                                try {
                                    mockMvc.perform(get("/songs"))
                                            .andExpect(status().isOk())
                                            .andExpect(jsonPath("$[0].title")
                                                    .value("Imported Null SongType"))
                                            .andExpect(jsonPath("$[0].songType")
                                                    .value(nullValue()))
                                            .andExpect(jsonPath("$[0].language")
                                                    .value("ENGLISH"));

                                    mockMvc.perform(get("/playlists/{playlistId}", playlist.getId()))
                                            .andExpect(status().isOk())
                                            .andExpect(jsonPath("$.name")
                                                    .value("Null Type Playlist"))
                                            .andExpect(jsonPath("$.songs[0].title")
                                                    .value("Imported Null SongType"))
                                            .andExpect(jsonPath("$.songs[0].songType")
                                                    .value(nullValue()));

                                    mockMvc.perform(get("/service-plans/{servicePlanId}", servicePlan.getId()))
                                            .andExpect(status().isOk())
                                            .andExpect(jsonPath("$.serviceName")
                                                    .value("Null Type Service"))
                                            .andExpect(jsonPath("$.songs[0].title")
                                                    .value("Imported Null SongType"))
                                            .andExpect(jsonPath("$.songs[0].songType")
                                                    .value(nullValue()));
                                } catch (Exception exception) {
                                    throw new RuntimeException(exception);
                                }
                            });
                });
    }

    @Test
    void manualSongCreateAndUpdateStillRequireSongType(
            @TempDir Path tempDir) throws Exception {
        withWebContext(
                tempDir.resolve("song-null-song-type-write-validation.db"),
                context -> {
                    SongLibrary songLibrary = context.getBean(SongLibrary.class);
                    MockMvc mockMvc = mockMvc(context);

                    mockMvc.perform(
                                    post("/songs")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("""
                                                    {
                                                      "title": "Manual Missing Type",
                                                      "author": "Admin",
                                                      "lyrics": "Lyrics",
                                                      "language": "ENGLISH"
                                                    }
                                                    """))
                            .andExpect(status().isBadRequest())
                            .andExpect(content().json("""
                                    {"message":"songType cannot be null."}
                                    """));

                    Song existingSong = new Song(
                            "Existing Manual Song",
                            "Admin",
                            "Lyrics",
                            SongType.SLOW
                    );
                    songLibrary.addSong(existingSong);

                    mockMvc.perform(
                                    put("/songs/{id}", existingSong.getId())
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("""
                                                    {
                                                      "title": "Existing Manual Song",
                                                      "author": "Admin",
                                                      "lyrics": "Updated Lyrics",
                                                      "language": "ENGLISH"
                                                    }
                                                    """))
                            .andExpect(status().isBadRequest())
                            .andExpect(content().json("""
                                    {"message":"songType cannot be null."}
                                    """));
                });
    }

    private MockMvc mockMvc(
            ConfigurableApplicationContext context) {
        JsonMapper jsonMapper = context.getBean(
                "jacksonJsonMapper",
                JsonMapper.class
        );
        JacksonJsonHttpMessageConverter converter =
                new JacksonJsonHttpMessageConverter(jsonMapper);

        return MockMvcBuilders.standaloneSetup(
                        context.getBean(SongController.class),
                        context.getBean(PlaylistController.class),
                        context.getBean(ServicePlanController.class)
                )
                .setControllerAdvice(
                        context.getBean(ApiExceptionHandler.class)
                )
                .setMessageConverters(converter)
                .build();
    }

    private TransactionTemplate transactionTemplate(
            ConfigurableApplicationContext context) {
        return new TransactionTemplate(
                context.getBean(PlatformTransactionManager.class)
        );
    }

    private void withWebContext(
            Path databaseFile,
            ThrowingConsumer<ConfigurableApplicationContext> testBody)
            throws Exception {
        try (ConfigurableApplicationContext context =
                     new SpringApplicationBuilder(
                             ChurchSongApiApplication.class)
                             .run(
                                     "--spring.profiles.active=test",
                                     "--spring.datasource.url=jdbc:sqlite:" + databaseFile,
                                     "--spring.jpa.hibernate.ddl-auto=update",
                                     "--spring.sql.init.mode=always",
                                     "--spring.main.banner-mode=off",
                                     "--spring.main.web-application-type=none")) {
            testBody.accept(context);
        }
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }
}
