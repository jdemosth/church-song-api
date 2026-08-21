package com.churchsong;

import com.churchsong.model.ServicePlan;
import com.churchsong.model.Playlist;
import com.churchsong.model.Song;
import com.churchsong.model.SongType;
import com.churchsong.service.ServicePlanLibrary;
import com.churchsong.service.SongLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServicePlanLibraryTests {

    @Test
    void completeServicePlanMarksItCompletedAndRemovesItFromUpcoming(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve(
                        "service-plan-complete.db");

        withContext(
                databaseFile,
                context -> {
                    SongLibrary songLibrary =
                            context.getBean(
                                    SongLibrary.class);
                    ServicePlanLibrary servicePlanLibrary =
                            context.getBean(
                                    ServicePlanLibrary.class);

                    Song firstSong = createSong(
                            songLibrary,
                            "Song A");
                    Song secondSong = createSong(
                            songLibrary,
                            "Song B");

                    ServicePlan servicePlan =
                            servicePlanLibrary.createServicePlan(
                                    "Sunday Morning",
                                    "Sunday Morning",
                                    "2026-08-23",
                                    "09:00",
                                    "Mercy",
                                    17L,
                                    List.of(
                                            firstSong,
                                            secondSong));

                    ServicePlan completedService =
                            servicePlanLibrary.completeServicePlan(
                                    servicePlan.getId());

                    assertEquals(
                            servicePlan.getId(),
                            completedService.getId());
                    assertTrue(
                            completedService.isCompleted());
                    assertNotNull(
                            completedService.getCompletedAt());
                    assertEquals(
                            List.of(
                                    "Song A",
                                    "Song B"),
                            completedService.getSongs()
                                    .stream()
                                    .map(Song::getTitle)
                                    .toList());
                    assertEquals(
                            0,
                            servicePlanLibrary
                                    .getUpcomingServicePlans()
                                    .size());
                    assertEquals(
                            List.of(
                                    completedService.getId()),
                            servicePlanLibrary
                                    .getCompletedServiceHistory()
                                    .stream()
                                    .map(ServicePlan::getId)
                                    .toList());
                });
    }

    @Test
    void createsServicePlanAndCopiesSongsInOrder(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve(
                        "service-plan-order.db");

        withContext(
                databaseFile,
                context -> {
                    SongLibrary songLibrary =
                            context.getBean(
                                    SongLibrary.class);
                    ServicePlanLibrary servicePlanLibrary =
                            context.getBean(
                                    ServicePlanLibrary.class);

                    Song firstSong = createSong(
                            songLibrary,
                            "Opening Song");
                    Song secondSong = createSong(
                            songLibrary,
                            "Closing Song");

                    ServicePlan servicePlan =
                            servicePlanLibrary.createServicePlan(
                                    "Sunday Worship",
                                    "2026-08-16",
                                    "09:30",
                                    List.of(
                                            secondSong,
                                            firstSong));

                    assertNotNull(
                            servicePlan.getId());
                    assertEquals(
                            2,
                            servicePlan.getSongs()
                                    .size());
                    assertEquals(
                            "Closing Song",
                            servicePlan.getSongs()
                                    .get(0)
                                    .getTitle());
                    assertEquals(
                            "Opening Song",
                            servicePlan.getSongs()
                                    .get(1)
                                    .getTitle());
                });
    }

    @Test
    void rejectsMissingServiceName(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve(
                        "service-plan-missing-name.db");

        withContext(
                databaseFile,
                context -> {
                    ServicePlanLibrary servicePlanLibrary =
                            context.getBean(
                                    ServicePlanLibrary.class);

                    IllegalArgumentException exception =
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            servicePlanLibrary.createServicePlan(
                                                    "   ",
                                                    "2026-08-16",
                                                    "",
                                                    List.of()));

                    assertEquals(
                            "serviceName cannot be null or empty.",
                            exception.getMessage());
                });
    }

    @Test
    void rejectsMissingServiceDate(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve(
                        "service-plan-missing-date.db");

        withContext(
                databaseFile,
                context -> {
                    ServicePlanLibrary servicePlanLibrary =
                            context.getBean(
                                    ServicePlanLibrary.class);

                    IllegalArgumentException exception =
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            servicePlanLibrary.createServicePlan(
                                                    "Sunday Worship",
                                                    " ",
                                                    "",
                                                    List.of()));

                    assertEquals(
                            "serviceDate cannot be null or empty.",
                            exception.getMessage());
                });
    }

    @Test
    void retrievesUpcomingServicesOnlyAndInChronologicalOrder(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve(
                        "service-plan-upcoming.db");

        withContext(
                databaseFile,
                context -> {
                    ServicePlanLibrary servicePlanLibrary =
                            context.getBean(
                                    ServicePlanLibrary.class);

                    servicePlanLibrary.createServicePlan(
                            "Past Rehearsal",
                            "2000-08-13",
                            "19:00",
                            List.of());
                    servicePlanLibrary.createServicePlan(
                            "Sunday Morning",
                            "2099-08-16",
                            "09:00",
                            List.of());
                    servicePlanLibrary.createServicePlan(
                            "Friday Prayer",
                            "2099-08-14",
                            "18:00",
                            List.of());

                    List<ServicePlan> upcomingServices =
                            servicePlanLibrary
                                    .getUpcomingServicePlans();

                    assertEquals(
                            2,
                            upcomingServices.size());
                    assertEquals(
                            "Friday Prayer",
                            upcomingServices.get(0)
                                    .getServiceName());
                    assertEquals(
                            "Sunday Morning",
                            upcomingServices.get(1)
                                    .getServiceName());
                    assertFalse(
                            upcomingServices.stream()
                                    .anyMatch(servicePlan ->
                                            servicePlan.getServiceName()
                                                    .equals(
                                                            "Past Rehearsal")));
                });
    }

    @Test
    void completePlaylistAsServiceCreatesCompletedSnapshot(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve(
                        "service-plan-complete-playlist.db");

        withContext(
                databaseFile,
                context -> {
                    SongLibrary songLibrary =
                            context.getBean(
                                    SongLibrary.class);
                    ServicePlanLibrary servicePlanLibrary =
                            context.getBean(
                                    ServicePlanLibrary.class);

                    Song firstSong = createSong(
                            songLibrary,
                            "Amazing Grace");
                    Song secondSong = createSong(
                            songLibrary,
                            "Li touche m");

                    Playlist playlist =
                            new Playlist(
                                    "Sunday Morning - Aug 23, 2026");
                    playlist.setReusable(false);
                    playlist.setServiceType(
                            "Sunday Morning");
                    playlist.setServiceDate(
                            java.time.LocalDate.parse(
                                    "2026-08-23"));
                    playlist.setTheme(
                            "The Mercy of God");
                    playlist.replaceSongs(
                            List.of(
                                    secondSong,
                                    firstSong));

                    ServicePlan completedService =
                            servicePlanLibrary
                                    .completePlaylistAsService(
                                            playlist);

                    assertTrue(
                            completedService.isCompleted());
                    assertEquals(
                            "Sunday Morning",
                            completedService
                                    .getServiceType());
                    assertEquals(
                            "The Mercy of God",
                            completedService
                                    .getTheme());
                    assertEquals(
                            List.of(
                                    "Li touche m",
                                    "Amazing Grace"),
                            completedService.getSongs()
                                    .stream()
                                    .map(Song::getTitle)
                                    .toList());
                    assertEquals(
                            List.of(
                                    "Li touche m",
                                    "Amazing Grace"),
                            playlist.getSongs()
                                    .stream()
                                    .map(Song::getTitle)
                                    .toList());
                });
    }

    @Test
    void reusingCompletedServiceCreatesNewActiveCopy(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve(
                        "service-plan-reuse.db");

        withContext(
                databaseFile,
                context -> {
                    SongLibrary songLibrary =
                            context.getBean(
                                    SongLibrary.class);
                    ServicePlanLibrary servicePlanLibrary =
                            context.getBean(
                                    ServicePlanLibrary.class);

                    Song firstSong = createSong(
                            songLibrary,
                            "Amazing Grace");
                    Song secondSong = createSong(
                            songLibrary,
                            "He Touched Me");

                    ServicePlan original =
                            servicePlanLibrary.createServicePlan(
                                    "Sunday Morning - Aug 23, 2026",
                                    "Sunday Morning",
                                    "2026-08-23",
                                    "09:00",
                                    "Mercy",
                                    9L,
                                    List.of(
                                            firstSong,
                                            secondSong));

                    ServicePlan completed =
                            servicePlanLibrary.completeServicePlan(
                                    original.getId());
                    ServicePlan reused =
                            servicePlanLibrary
                                    .reuseCompletedServicePlan(
                                            completed.getId(),
                                            "Friday Evening",
                                            "Friday Evening",
                                            "2026-08-30",
                                            "10:00");

                    assertNotNull(reused);
                    assertFalse(
                            reused.isCompleted());
                    assertEquals(
                            "Friday Evening",
                            reused.getServiceName());
                    assertEquals(
                            "Friday Evening",
                            reused.getServiceType());
                    assertEquals(
                            "Mercy",
                            reused.getTheme());
                    assertEquals(
                            List.of(
                                    "Amazing Grace",
                                    "He Touched Me"),
                            reused.getSongs()
                                    .stream()
                                    .map(Song::getTitle)
                                    .toList());
                    assertEquals(
                            2,
                            servicePlanLibrary
                                    .getServicePlans()
                                    .size());
                    assertEquals(
                            1,
                            servicePlanLibrary
                                    .getCompletedServiceHistory()
                                    .size());
                });
    }

    @Test
    void completedServiceCannotBeCompletedTwice(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve(
                        "service-plan-complete-twice.db");

        withContext(
                databaseFile,
                context -> {
                    ServicePlanLibrary servicePlanLibrary =
                            context.getBean(
                                    ServicePlanLibrary.class);

                    ServicePlan servicePlan =
                            servicePlanLibrary.createServicePlan(
                                    "Thursday Evening",
                                    "Thursday Evening",
                                    "2026-08-27",
                                    "19:00",
                                    null,
                                    null,
                                    List.of());

                    servicePlanLibrary.completeServicePlan(
                            servicePlan.getId());

                    IllegalStateException exception =
                            assertThrows(
                                    IllegalStateException.class,
                                    () ->
                                            servicePlanLibrary.completeServicePlan(
                                                    servicePlan.getId()));

                    assertEquals(
                            "This service has already been completed.",
                            exception.getMessage());
                });
    }

    @Test
    void deleteCompletedServiceHistoryRemovesOnlyThatRecord(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve(
                        "service-plan-delete-history.db");

        withContext(
                databaseFile,
                context -> {
                    ServicePlanLibrary servicePlanLibrary =
                            context.getBean(
                                    ServicePlanLibrary.class);

                    ServicePlan thursday =
                            servicePlanLibrary.createServicePlan(
                                    "Thursday Evening",
                                    "Thursday Evening",
                                    "2026-08-20",
                                    "19:00",
                                    null,
                                    null,
                                    List.of());
                    ServicePlan friday =
                            servicePlanLibrary.createServicePlan(
                                    "Friday Evening",
                                    "Friday Evening",
                                    "2026-08-21",
                                    "19:00",
                                    null,
                                    null,
                                    List.of());
                    ServicePlan sunday =
                            servicePlanLibrary.createServicePlan(
                                    "Sunday Morning",
                                    "Sunday Morning",
                                    "2026-08-23",
                                    "09:00",
                                    null,
                                    null,
                                    List.of());

                    servicePlanLibrary.completeServicePlan(
                            thursday.getId());
                    servicePlanLibrary.completeServicePlan(
                            friday.getId());
                    servicePlanLibrary.completeServicePlan(
                            sunday.getId());

                    assertTrue(
                            servicePlanLibrary
                                    .deleteCompletedServiceHistory(
                                            friday.getId()));
                    assertEquals(
                            List.of(
                                    "Sunday Morning",
                                    "Thursday Evening"),
                            servicePlanLibrary
                                    .getCompletedServiceHistory()
                                    .stream()
                                    .map(
                                            ServicePlan::getServiceName)
                                    .toList());
                    assertEquals(
                            2,
                            servicePlanLibrary
                                    .getServicePlans()
                                    .size());
                });
    }

    @Test
    void deleteCompletedServiceHistoryRejectsActiveService(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve(
                        "service-plan-delete-active-history.db");

        withContext(
                databaseFile,
                context -> {
                    ServicePlanLibrary servicePlanLibrary =
                            context.getBean(
                                    ServicePlanLibrary.class);

                    ServicePlan activePlan =
                            servicePlanLibrary.createServicePlan(
                                    "Sunday Morning",
                                    "Sunday Morning",
                                    "2026-08-23",
                                    "09:00",
                                    null,
                                    null,
                                    List.of());

                    IllegalArgumentException exception =
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            servicePlanLibrary.deleteCompletedServiceHistory(
                                                    activePlan.getId()));

                    assertEquals(
                            "Only completed services can be deleted from Service History.",
                            exception.getMessage());
                });
    }

    @Test
    void persistsServicePlansAfterRestart(
            @TempDir Path tempDir) {
        Path databaseFile =
                tempDir.resolve(
                        "service-plan-restart.db");

        withContext(
                databaseFile,
                context -> {
                    SongLibrary songLibrary =
                            context.getBean(
                                    SongLibrary.class);
                    ServicePlanLibrary servicePlanLibrary =
                            context.getBean(
                                    ServicePlanLibrary.class);

                    Song song = createSong(
                            songLibrary,
                            "Hope Song");

                    servicePlanLibrary.createServicePlan(
                            "Restart Check",
                            "2099-08-17",
                            "18:30",
                            List.of(song));
                });

        withContext(
                databaseFile,
                context -> {
                    ServicePlanLibrary servicePlanLibrary =
                            context.getBean(
                                    ServicePlanLibrary.class);

                    List<ServicePlan> servicePlans =
                            servicePlanLibrary
                                    .getServicePlans();

                    assertEquals(
                            1,
                            servicePlans.size());
                    assertEquals(
                            "Restart Check",
                            servicePlans.get(0)
                                    .getServiceName());
                    assertEquals(
                            1,
                            servicePlans.get(0)
                                    .getSongs()
                                    .size());
                    assertEquals(
                            "Hope Song",
                            servicePlans.get(0)
                                    .getSongs()
                                    .get(0)
                                    .getTitle());
                    assertTrue(
                            servicePlanLibrary
                                    .getUpcomingServicePlans()
                                    .stream()
                                    .anyMatch(servicePlan ->
                                            servicePlan.getServiceName()
                                                    .equals(
                                                            "Restart Check")));
                });
    }

    private void withContext(
            Path databaseFile,
            Consumer<ConfigurableApplicationContext> testBody) {
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
            new TransactionTemplate(
                    context.getBean(
                            PlatformTransactionManager.class))
                    .executeWithoutResult(
                            status -> testBody.accept(context));
        }
    }

    private Song createSong(
            SongLibrary songLibrary,
            String title) {
        Song song = new Song(
                title,
                "Test Author",
                "[Verse 1]\nLine 1",
                SongType.SLOW);
        songLibrary.addSong(song);
        return song;
    }
}
