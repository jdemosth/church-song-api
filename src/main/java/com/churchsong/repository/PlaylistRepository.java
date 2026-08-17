package com.churchsong.repository;

import com.churchsong.model.Playlist;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    @Override
    @EntityGraph(attributePaths = "songs")
    Optional<Playlist> findById(Long id);

    Optional<Playlist> findByNameIgnoreCase(String name);

    List<Playlist> findByReusable(boolean reusable);

    @EntityGraph(attributePaths = "songs")
    Optional<Playlist> findByReusableFalseAndServiceDate(LocalDate serviceDate);
}
