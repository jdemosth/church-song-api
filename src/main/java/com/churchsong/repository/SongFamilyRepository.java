package com.churchsong.repository;

import com.churchsong.model.SongFamily;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SongFamilyRepository extends JpaRepository<SongFamily, Integer> {
}
