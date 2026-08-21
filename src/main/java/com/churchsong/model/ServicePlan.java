package com.churchsong.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "service_plans")
public class ServicePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String serviceName;
    private String serviceType;
    private LocalDate serviceDate;
    private LocalTime serviceTime;
    private String theme;
    private Long sourcePlaylistId;
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    private ServicePlanStatus status;

    @ManyToMany
    @JoinTable(
            name = "service_plan_songs",
            joinColumns = @JoinColumn(name = "service_plan_id"),
            inverseJoinColumns = @JoinColumn(name = "song_id")
    )
    @OrderColumn(name = "song_order")
    private List<Song> songs;

    protected ServicePlan() {
        this.status = ServicePlanStatus.ACTIVE;
        this.songs = new ArrayList<>();
    }

    public ServicePlan(
            String serviceName,
            LocalDate serviceDate,
            LocalTime serviceTime) {
        setServiceName(serviceName);
        setServiceDate(serviceDate);
        setServiceTime(serviceTime);
        this.status = ServicePlanStatus.ACTIVE;
        this.songs = new ArrayList<>();
    }

    public Long getId() {
        return id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        if (serviceName != null) {
            serviceName = serviceName.trim();
        }

        if (serviceName == null || serviceName.isEmpty()) {
            throw new IllegalArgumentException(
                    "serviceName cannot be null or empty."
            );
        }

        this.serviceName = serviceName;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        if (serviceType == null || serviceType.trim().isEmpty()) {
            this.serviceType = null;
            return;
        }

        this.serviceType = serviceType.trim();
    }

    public void setServiceDate(LocalDate serviceDate) {
        if (serviceDate == null) {
            throw new IllegalArgumentException(
                    "serviceDate cannot be null."
            );
        }

        this.serviceDate = serviceDate;
    }

    public LocalTime getServiceTime() {
        return serviceTime;
    }

    public void setServiceTime(LocalTime serviceTime) {
        this.serviceTime = serviceTime;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        if (theme == null || theme.trim().isEmpty()) {
            this.theme = null;
            return;
        }

        this.theme = theme.trim();
    }

    public Long getSourcePlaylistId() {
        return sourcePlaylistId;
    }

    public void setSourcePlaylistId(Long sourcePlaylistId) {
        this.sourcePlaylistId = sourcePlaylistId;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public ServicePlanStatus getStatus() {
        return status == null
                ? ServicePlanStatus.ACTIVE
                : status;
    }

    public void setStatus(ServicePlanStatus status) {
        this.status =
                status == null
                        ? ServicePlanStatus.ACTIVE
                        : status;
    }

    public boolean isCompleted() {
        return getStatus() == ServicePlanStatus.COMPLETED;
    }

    public void markCompleted(LocalDateTime completedAt) {
        if (isCompleted()) {
            throw new IllegalStateException(
                    "This service has already been completed."
            );
        }

        if (completedAt == null) {
            throw new IllegalArgumentException(
                    "completedAt cannot be null."
            );
        }

        this.status = ServicePlanStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    public List<Song> getSongs() {
        return new ArrayList<>(songs);
    }

    public void setSongs(List<Song> songs) {
        this.songs = new ArrayList<>();

        if (songs == null) {
            return;
        }

        for (Song song : songs) {
            addSong(song);
        }
    }

    public void addSong(Song song) {
        if (song == null) {
            throw new IllegalArgumentException(
                    "song cannot be null."
            );
        }

        songs.add(song);
    }

    public void removeSong(Song song) {
        if (song == null) {
            throw new IllegalArgumentException(
                    "song cannot be null."
            );
        }

        boolean removed = songs.removeIf(
                existingSong ->
                        existingSong.getId().equals(
                                song.getId())
        );

        if (!removed) {
            throw new IllegalArgumentException(
                    "song with ID "
                            + song.getId()
                            + " is not in the service plan."
            );
        }
    }

    public void moveSong(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= songs.size()) {
            throw new IllegalArgumentException(
                    "fromIndex is invalid."
            );
        }

        if (toIndex < 0 || toIndex >= songs.size()) {
            throw new IllegalArgumentException(
                    "toIndex is invalid."
            );
        }

        Song song = songs.remove(fromIndex);
        songs.add(toIndex, song);
    }
}
