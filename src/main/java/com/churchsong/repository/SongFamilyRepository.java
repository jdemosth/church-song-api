package com.churchsong.repository;

import com.churchsong.model.SongFamily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SongFamilyRepository extends JpaRepository<SongFamily, Integer> {
    Optional<SongFamily> findBySourceFamilyKey(String sourceFamilyKey);

    Optional<SongFamily> findTopByOrderByIdDesc();
}
