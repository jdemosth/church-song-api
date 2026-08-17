package com.churchsong.repository;

import com.churchsong.model.Playlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    Optional<Playlist> findByNameIgnoreCase(String name);

    List<Playlist> findByReusable(boolean reusable);

    Optional<Playlist> findByReusableFalseAndServiceDate(LocalDate serviceDate);
}
