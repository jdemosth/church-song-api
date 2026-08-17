package com.churchsong.service;

import com.churchsong.model.ServicePlan;
import com.churchsong.model.Song;
import com.churchsong.repository.ServicePlanRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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

    public List<ServicePlan> getUpcomingServicePlans() {
        LocalDate today = LocalDate.now();

        return sortServicePlans(
                servicePlanRepository.findAll()
                        .stream()
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
            String serviceDate,
            String serviceTime,
            List<Song> songs) {
        ServicePlan servicePlan =
                new ServicePlan(
                        normalizeServiceName(
                                serviceName),
                        parseServiceDate(
                                serviceDate),
                        parseServiceTime(
                                serviceTime));

        servicePlan.setSongs(songs);
        return servicePlanRepository.save(
                servicePlan);
    }

    public ServicePlan updateServicePlan(
            Long id,
            String serviceName,
            String serviceDate,
            String serviceTime) {
        ServicePlan servicePlan =
                findServicePlanById(id);

        if (servicePlan == null) {
            return null;
        }

        servicePlan.setServiceName(
                normalizeServiceName(
                        serviceName));
        servicePlan.setServiceDate(
                parseServiceDate(serviceDate));
        servicePlan.setServiceTime(
                parseServiceTime(serviceTime));

        return servicePlanRepository.save(
                servicePlan);
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
                serviceDate,
                serviceTime,
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
