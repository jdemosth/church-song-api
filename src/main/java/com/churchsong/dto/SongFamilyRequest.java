package com.churchsong.dto;

public class SongFamilyRequest {

    private Integer id;
    private String canonicalTitle;

    public SongFamilyRequest() {
    }

    public SongFamilyRequest(Integer id, String canonicalTitle) {
        this.id = id;
        this.canonicalTitle = canonicalTitle;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCanonicalTitle() {
        return canonicalTitle;
    }

    public void setCanonicalTitle(String canonicalTitle) {
        this.canonicalTitle = canonicalTitle;
    }
}
