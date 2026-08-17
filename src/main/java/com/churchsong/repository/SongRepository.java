package com.churchsong.repository;

import com.churchsong.model.Song;
import com.churchsong.model.SongType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SongRepository extends JpaRepository<Song, Integer> {

    Optional<Song> findByTitleIgnoreCase(String title);

    List<Song> findBySongType(SongType songType);
}