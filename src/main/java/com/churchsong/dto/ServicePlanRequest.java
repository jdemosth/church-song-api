package com.churchsong.dto;

import java.util.List;

public class ServicePlanRequest {

    private String serviceName;
    private String serviceType;
    private String serviceDate;
    private String serviceTime;
    private String theme;
    private Long sourcePlaylistId;
    private List<Integer> songIds;

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getServiceDate() {
        return serviceDate;
    }

    public void setServiceDate(String serviceDate) {
        this.serviceDate = serviceDate;
    }

    public String getServiceTime() {
        return serviceTime;
    }

    public void setServiceTime(String serviceTime) {
        this.serviceTime = serviceTime;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public Long getSourcePlaylistId() {
        return sourcePlaylistId;
    }

    public void setSourcePlaylistId(Long sourcePlaylistId) {
        this.sourcePlaylistId = sourcePlaylistId;
    }

    public List<Integer> getSongIds() {
        return songIds;
    }

    public void setSongIds(List<Integer> songIds) {
        this.songIds = songIds;
    }
}
