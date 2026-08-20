package com.churchsong.dto;

public class PlaylistMetadataRequest {

    private String name;
    private String serviceType;
    private String customServiceType;
    private String serviceDate;
    private String theme;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getCustomServiceType() {
        return customServiceType;
    }

    public void setCustomServiceType(String customServiceType) {
        this.customServiceType = customServiceType;
    }

    public String getServiceDate() {
        return serviceDate;
    }

    public void setServiceDate(String serviceDate) {
        this.serviceDate = serviceDate;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }
}
