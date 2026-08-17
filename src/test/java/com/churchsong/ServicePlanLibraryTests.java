package com.churchsong;

import com.churchsong.model.ServicePlan;
import com.churchsong.model.Song;
import com.churchsong.model.SongType;
import com.churchsong.service.ServicePlanLibrary;
import com.churchsong.service.SongLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

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
                            "2026-08-13",
                            "19:00",
                            List.of());
                    servicePlanLibrary.createServicePlan(
                            "Sunday Morning",
                            "2026-08-16",
                            "09:00",
                            List.of());
                    servicePlanLibrary.createServicePlan(
                            "Friday Prayer",
                            "2026-08-14",
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
                            "2026-08-17",
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
                             .properties(
                                     "spring.datasource.url=jdbc:sqlite:" + databaseFile,
                                     "spring.jpa.hibernate.ddl-auto=update",
                                     "spring.sql.init.mode=always",
                                     "spring.main.banner-mode=off")
                             .run()) {
            testBody.accept(context);
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
