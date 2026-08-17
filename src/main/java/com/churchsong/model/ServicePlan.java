package com.churchsong.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.LocalDate;
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
    private LocalDate serviceDate;
    private LocalTime serviceTime;

    @ManyToMany
    @JoinTable(
            name = "service_plan_songs",
            joinColumns = @JoinColumn(name = "service_plan_id"),
            inverseJoinColumns = @JoinColumn(name = "song_id")
    )
    @OrderColumn(name = "song_order")
    private List<Song> songs;

    protected ServicePlan() {
        this.songs = new ArrayList<>();
    }

    public ServicePlan(
            String serviceName,
            LocalDate serviceDate,
            LocalTime serviceTime) {
        setServiceName(serviceName);
        setServiceDate(serviceDate);
        setServiceTime(serviceTime);
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
