package com.churchsong.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class SongFamily {

    @Id
    private Integer id;

    private String canonicalTitle;

    protected SongFamily() {
    }

    public SongFamily(Integer id, String canonicalTitle) {
        setId(id);
        setCanonicalTitle(canonicalTitle);
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "id must be greater than 0."
            );
        }

        this.id = id;
    }

    public String getCanonicalTitle() {
        return canonicalTitle;
    }

    public void setCanonicalTitle(String canonicalTitle) {
        if (canonicalTitle != null) {
            canonicalTitle = canonicalTitle.trim();
        }

        if (canonicalTitle == null || canonicalTitle.isEmpty()) {
            throw new IllegalArgumentException(
                    "canonicalTitle cannot be null or empty."
            );
        }

        this.canonicalTitle = canonicalTitle;
    }
}
