package com.churchsong.service;

import com.churchsong.model.Playlist;
import com.churchsong.model.ServicePlan;
import com.churchsong.model.ServicePlanStatus;
import com.churchsong.model.Song;
import com.churchsong.repository.ServicePlanRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ServicePlanLibrary {

    private final ServicePlanRepository servicePlanRepository;

    public ServicePlanLibrary(
            ServicePlanRepository servicePlanRepository) {
        this.servicePlanRepository =
                servicePlanRepository;
    }

    public List<ServicePlan> getServicePlans() {
        return sortServicePlans(
                servicePlanRepository.findAll());
    }

    public List<ServicePlan> getActiveServicePlans() {
        return sortServicePlans(
                servicePlanRepository.findAll()
                        .stream()
                        .filter(servicePlan ->
                                servicePlan.getStatus()
                                        == ServicePlanStatus.ACTIVE)
                        .toList());
    }

    public List<ServicePlan> getCompletedServiceHistory() {
        return servicePlanRepository.findAll()
                .stream()
                .filter(servicePlan ->
                        servicePlan.getStatus()
                                == ServicePlanStatus.COMPLETED)
                .sorted(
                        Comparator
                                .comparing(
                                        ServicePlan::getCompletedAt,
                                        Comparator.nullsLast(
                                                Comparator.reverseOrder()))
                                .thenComparing(
                                        ServicePlan::getServiceDate,
                                        Comparator.nullsLast(
                                                Comparator.reverseOrder())))
                .toList();
    }

    public List<ServicePlan> getUpcomingServicePlans() {
        LocalDate today = LocalDate.now();

        return sortServicePlans(
                servicePlanRepository.findAll()
                        .stream()
                        .filter(servicePlan ->
                                servicePlan.getStatus()
                                        == ServicePlanStatus.ACTIVE)
                        .filter(servicePlan ->
                                !servicePlan.getServiceDate()
                                        .isBefore(today))
                        .toList());
    }

    public ServicePlan findServicePlanById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException(
                    "id cannot be null."
            );
        }

        return servicePlanRepository.findById(id)
                .orElse(null);
    }

    public ServicePlan createServicePlan(
            String serviceName,
            String serviceType,
            String serviceDate,
            String serviceTime,
            String theme,
            Long sourcePlaylistId,
            List<Song> songs) {
        ServicePlan servicePlan =
                new ServicePlan(
                        normalizeServiceName(
                                serviceName),
                        parseServiceDate(
                                serviceDate),
                        parseServiceTime(
                                serviceTime));

        servicePlan.setServiceType(serviceType);
        servicePlan.setTheme(theme);
        servicePlan.setSourcePlaylistId(
                sourcePlaylistId);
        servicePlan.setStatus(
                ServicePlanStatus.ACTIVE);
        servicePlan.setCompletedAt(null);
        servicePlan.setSongs(songs);
        return servicePlanRepository.save(
                servicePlan);
    }

    public ServicePlan createServicePlan(
            String serviceName,
            String serviceDate,
            String serviceTime,
            List<Song> songs) {
        return createServicePlan(
                serviceName,
                null,
                serviceDate,
                serviceTime,
                null,
                null,
                songs);
    }

    public ServicePlan updateServicePlan(
            Long id,
            String serviceName,
            String serviceType,
            String serviceDate,
            String serviceTime,
            String theme) {
        ServicePlan servicePlan =
                findServicePlanById(id);

        if (servicePlan == null) {
            return null;
        }

        servicePlan.setServiceName(
                normalizeServiceName(
                        serviceName));
        servicePlan.setServiceType(serviceType);
        servicePlan.setServiceDate(
                parseServiceDate(serviceDate));
        servicePlan.setServiceTime(
                parseServiceTime(serviceTime));
        servicePlan.setTheme(theme);

        return servicePlanRepository.save(
                servicePlan);
    }

    public ServicePlan updateServicePlan(
            Long id,
            String serviceName,
            String serviceDate,
            String serviceTime) {
        return updateServicePlan(
                id,
                serviceName,
                null,
                serviceDate,
                serviceTime,
                null);
    }

    public ServicePlan duplicateServicePlan(
            Long id,
            String serviceName,
            String serviceDate,
            String serviceTime) {
        ServicePlan servicePlan =
                findServicePlanById(id);

        if (servicePlan == null) {
            return null;
        }

        return createServicePlan(
                serviceName,
                servicePlan.getServiceType(),
                serviceDate,
                serviceTime,
                servicePlan.getTheme(),
                servicePlan.getSourcePlaylistId(),
                servicePlan.getSongs());
    }

    public ServicePlan completeServicePlan(Long id) {
        ServicePlan servicePlan =
                findServicePlanById(id);

        if (servicePlan == null) {
            return null;
        }

        servicePlan.markCompleted(
                LocalDateTime.now());

        return servicePlanRepository.save(
                servicePlan);
    }

    public ServicePlan completePlaylistAsService(
            Playlist playlist) {
        if (playlist == null) {
            throw new IllegalArgumentException(
                    "playlist cannot be null."
            );
        }

        if (playlist.getServiceDate() == null) {
            throw new IllegalArgumentException(
                    "Only dated service playlists can be completed."
            );
        }

        ServicePlan completedService =
                new ServicePlan(
                        normalizeServiceName(
                                playlist.getName()),
                        playlist.getServiceDate(),
                        null);

        completedService.setServiceType(
                playlist.getServiceType());
        completedService.setTheme(
                playlist.getTheme());
        completedService.setSourcePlaylistId(
                playlist.getId());
        completedService.setSongs(
                playlist.getSongs());
        completedService.markCompleted(
                LocalDateTime.now());

        return servicePlanRepository.save(
                completedService);
    }

    public ServicePlan reuseCompletedServicePlan(
            Long id,
            String serviceName,
            String serviceType,
            String serviceDate,
            String serviceTime) {
        ServicePlan servicePlan =
                findServicePlanById(id);

        if (servicePlan == null) {
            return null;
        }

        if (!servicePlan.isCompleted()) {
            throw new IllegalArgumentException(
                    "Only completed services can be reused."
            );
        }

        String nextServiceName =
                serviceName == null
                        || serviceName.trim().isEmpty()
                        ? servicePlan.getServiceName()
                        : serviceName;
        String nextServiceType =
                serviceType == null
                        || serviceType.trim().isEmpty()
                        ? nextServiceName
                        : serviceType;

        return createServicePlan(
                nextServiceName,
                nextServiceType,
                serviceDate,
                serviceTime,
                servicePlan.getTheme(),
                servicePlan.getSourcePlaylistId(),
                servicePlan.getSongs());
    }

    public boolean deleteServicePlan(Long id) {
        ServicePlan servicePlan =
                findServicePlanById(id);

        if (servicePlan == null) {
            return false;
        }

        servicePlanRepository.delete(servicePlan);
        return true;
    }

    public boolean deleteCompletedServiceHistory(
            Long id) {
        ServicePlan servicePlan =
                findServicePlanById(id);

        if (servicePlan == null) {
            return false;
        }

        if (!servicePlan.isCompleted()) {
            throw new IllegalArgumentException(
                    "Only completed services can be deleted from Service History."
            );
        }

        servicePlanRepository.delete(servicePlan);
        return true;
    }

    public ServicePlan addSongToServicePlan(
            ServicePlan servicePlan,
            Song song) {
        if (servicePlan == null) {
            throw new IllegalArgumentException(
                    "servicePlan cannot be null."
            );
        }

        if (song == null) {
            throw new IllegalArgumentException(
                    "song cannot be null."
            );
        }

        if (servicePlan.isCompleted()) {
            throw new IllegalArgumentException(
                    "Completed services are read-only."
            );
        }

        servicePlan.addSong(song);
        return servicePlanRepository.save(
                servicePlan);
    }

    public ServicePlan removeSongFromServicePlan(
            ServicePlan servicePlan,
            Song song) {
        if (servicePlan == null) {
            throw new IllegalArgumentException(
                    "servicePlan cannot be null."
            );
        }

        if (song == null) {
            throw new IllegalArgumentException(
                    "song cannot be null."
            );
        }

        if (servicePlan.isCompleted()) {
            throw new IllegalArgumentException(
                    "Completed services are read-only."
            );
        }

        servicePlan.removeSong(song);
        return servicePlanRepository.save(
                servicePlan);
    }

    public ServicePlan moveSong(
            ServicePlan servicePlan,
            int fromIndex,
            int toIndex) {
        if (servicePlan == null) {
            throw new IllegalArgumentException(
                    "servicePlan cannot be null."
            );
        }

        if (servicePlan.isCompleted()) {
            throw new IllegalArgumentException(
                    "Completed services are read-only."
            );
        }

        servicePlan.moveSong(fromIndex, toIndex);
        return servicePlanRepository.save(
                servicePlan);
    }

    public boolean isSongUsedInAnyServicePlan(
            Integer songId) {
        if (songId == null) {
            return false;
        }

        return servicePlanRepository.findAll()
                .stream()
                .filter(servicePlan ->
                        servicePlan.getStatus()
                                == ServicePlanStatus.ACTIVE)
                .map(ServicePlan::getSongs)
                .flatMap(List::stream)
                .anyMatch(song ->
                        song.getId().equals(songId));
    }

    public List<String> getServicePlanNamesUsingSong(
            Integer songId) {
        if (songId == null) {
            return List.of();
        }

        List<String> servicePlanNames =
                new ArrayList<>();

        for (ServicePlan servicePlan :
                servicePlanRepository.findAll()) {
            if (servicePlan.getStatus()
                    == ServicePlanStatus.COMPLETED) {
                continue;
            }

            boolean containsSong =
                    servicePlan.getSongs()
                            .stream()
                            .anyMatch(song ->
                                    song.getId()
                                            .equals(songId));

            if (containsSong) {
                servicePlanNames.add(
                        servicePlan.getServiceName());
            }
        }

        return servicePlanNames;
    }

    private String normalizeServiceName(
            String serviceName) {
        if (serviceName != null) {
            serviceName = serviceName.trim();
        }

        if (serviceName == null
                || serviceName.isEmpty()) {
            throw new IllegalArgumentException(
                    "serviceName cannot be null or empty."
            );
        }

        return serviceName;
    }

    private LocalDate parseServiceDate(
            String serviceDate) {
        if (serviceDate != null) {
            serviceDate = serviceDate.trim();
        }

        if (serviceDate == null
                || serviceDate.isEmpty()) {
            throw new IllegalArgumentException(
                    "serviceDate cannot be null or empty."
            );
        }

        try {
            return LocalDate.parse(serviceDate);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "serviceDate must use YYYY-MM-DD."
            );
        }
    }

    private LocalTime parseServiceTime(
            String serviceTime) {
        if (serviceTime == null
                || serviceTime.trim().isEmpty()) {
            return null;
        }

        try {
            return LocalTime.parse(
                    serviceTime.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "serviceTime must use HH:MM."
            );
        }
    }

    private List<ServicePlan> sortServicePlans(
            List<ServicePlan> servicePlans) {
        return servicePlans.stream()
                .sorted(
                        Comparator.comparing(
                                ServicePlan::getServiceDate)
                                .thenComparing(
                                        ServicePlan::getServiceTime,
                                        Comparator.nullsLast(
                                                Comparator.naturalOrder()))
                                .thenComparing(
                                        ServicePlan::getId))
                .toList();
    }
}
