package com.churchsong.controller;

import com.churchsong.dto.ServicePlanRequest;
import com.churchsong.model.Playlist;
import com.churchsong.model.ServicePlan;
import com.churchsong.model.Song;
import com.churchsong.service.PlaylistLibrary;
import com.churchsong.service.ServicePlanLibrary;
import com.churchsong.service.SongLibrary;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
public class ServicePlanController {

    private final ServicePlanLibrary servicePlanLibrary;
    private final PlaylistLibrary playlistLibrary;
    private final SongLibrary songLibrary;

    public ServicePlanController(
            ServicePlanLibrary servicePlanLibrary,
            PlaylistLibrary playlistLibrary,
            SongLibrary songLibrary) {
        this.servicePlanLibrary =
                servicePlanLibrary;
        this.playlistLibrary = playlistLibrary;
        this.songLibrary = songLibrary;
    }

    @GetMapping("/service-plans")
    public List<ServicePlan> getServicePlans() {
        return servicePlanLibrary.getServicePlans();
    }

    @GetMapping("/service-plans/active")
    public List<ServicePlan> getActiveServicePlans() {
        return servicePlanLibrary.getActiveServicePlans();
    }

    @GetMapping("/service-plans/history")
    public List<ServicePlan> getCompletedServiceHistory() {
        return servicePlanLibrary
                .getCompletedServiceHistory();
    }

    @GetMapping("/service-plans/upcoming")
    public List<ServicePlan> getUpcomingServicePlans() {
        return servicePlanLibrary
                .getUpcomingServicePlans();
    }

    @GetMapping("/service-plans/{servicePlanId}")
    public ServicePlan getServicePlan(
            @PathVariable Long servicePlanId) {
        return servicePlanLibrary
                .findServicePlanById(servicePlanId);
    }

    @PostMapping("/service-plans")
    @ResponseStatus(HttpStatus.CREATED)
    public ServicePlan createServicePlan(
            @RequestBody ServicePlanRequest request) {
        return servicePlanLibrary.createServicePlan(
                request.getServiceName(),
                request.getServiceType(),
                request.getServiceDate(),
                request.getServiceTime(),
                request.getTheme(),
                request.getSourcePlaylistId(),
                findSongsByIds(request.getSongIds()));
    }

    @PutMapping("/service-plans/{servicePlanId}")
    public ServicePlan updateServicePlan(
            @PathVariable Long servicePlanId,
            @RequestBody ServicePlanRequest request) {
        return servicePlanLibrary.updateServicePlan(
                servicePlanId,
                request.getServiceName(),
                request.getServiceType(),
                request.getServiceDate(),
                request.getServiceTime(),
                request.getTheme());
    }

    @PostMapping("/service-plans/{servicePlanId}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public ServicePlan duplicateServicePlan(
            @PathVariable Long servicePlanId,
            @RequestBody ServicePlanRequest request) {
        return servicePlanLibrary.duplicateServicePlan(
                servicePlanId,
                request.getServiceName(),
                request.getServiceDate(),
                request.getServiceTime());
    }

    @PostMapping("/service-plans/{servicePlanId}/complete")
    public ServicePlan completeServicePlan(
            @PathVariable Long servicePlanId) {
        return servicePlanLibrary.completeServicePlan(
                servicePlanId);
    }

    @PostMapping("/service-plans/{servicePlanId}/reuse")
    @ResponseStatus(HttpStatus.CREATED)
    public ServicePlan reuseCompletedServicePlan(
            @PathVariable Long servicePlanId,
            @RequestBody ServicePlanRequest request) {
        return servicePlanLibrary.reuseCompletedServicePlan(
                servicePlanId,
                request.getServiceName(),
                request.getServiceType(),
                request.getServiceDate(),
                request.getServiceTime());
    }

    @PostMapping("/playlists/{playlistId}/complete-service")
    @ResponseStatus(HttpStatus.CREATED)
    public ServicePlan completePlaylistAsService(
            @PathVariable Long playlistId) {
        Playlist playlist =
                playlistLibrary.findPlaylistById(
                        playlistId);

        if (playlist == null) {
            return null;
        }

        return servicePlanLibrary
                .completePlaylistAsService(
                        playlist);
    }

    @DeleteMapping("/service-plans/{servicePlanId}")
    public boolean deleteServicePlan(
            @PathVariable Long servicePlanId) {
        return servicePlanLibrary.deleteServicePlan(
                servicePlanId);
    }

    @DeleteMapping(
            "/service-plans/history/{servicePlanId}")
    public boolean deleteCompletedServiceHistory(
            @PathVariable Long servicePlanId) {
        return servicePlanLibrary
                .deleteCompletedServiceHistory(
                        servicePlanId);
    }

    @PostMapping("/service-plans/{servicePlanId}/songs/{songId}")
    public ServicePlan addSongToServicePlan(
            @PathVariable Long servicePlanId,
            @PathVariable int songId) {
        ServicePlan servicePlan =
                servicePlanLibrary.findServicePlanById(
                        servicePlanId);
        Song song =
                songLibrary.findSongById(songId);

        if (servicePlan == null || song == null) {
            return null;
        }

        return servicePlanLibrary.addSongToServicePlan(
                servicePlan,
                song);
    }

    @DeleteMapping("/service-plans/{servicePlanId}/songs/{songId}")
    public ServicePlan removeSongFromServicePlan(
            @PathVariable Long servicePlanId,
            @PathVariable int songId) {
        ServicePlan servicePlan =
                servicePlanLibrary.findServicePlanById(
                        servicePlanId);
        Song song =
                songLibrary.findSongById(songId);

        if (servicePlan == null || song == null) {
            return null;
        }

        return servicePlanLibrary.removeSongFromServicePlan(
                servicePlan,
                song);
    }

    @PutMapping("/service-plans/{servicePlanId}/songs/reorder")
    public ServicePlan reorderSongs(
            @PathVariable Long servicePlanId,
            @RequestParam int fromIndex,
            @RequestParam int toIndex) {
        ServicePlan servicePlan =
                servicePlanLibrary.findServicePlanById(
                        servicePlanId);

        if (servicePlan == null) {
            return null;
        }

        return servicePlanLibrary.moveSong(
                servicePlan,
                fromIndex,
                toIndex);
    }

    private List<Song> findSongsByIds(
            List<Integer> songIds) {
        List<Song> songs = new ArrayList<>();

        if (songIds == null) {
            return songs;
        }

        for (Integer songId : songIds) {
            Song song = songLibrary.findSongById(
                    songId);

            if (song == null) {
                throw new IllegalArgumentException(
                        "Song with ID "
                                + songId
                                + " was not found."
                );
            }

            songs.add(song);
        }

        return songs;
    }
}
