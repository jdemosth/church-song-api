package com.churchsong.dto;

public class SongSectionDescriptorRequest {

    private String type;
    private Integer verseNumber;
    private String customLabel;

    public SongSectionDescriptorRequest() {
        // Required for JSON deserialization
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getVerseNumber() {
        return verseNumber;
    }

    public void setVerseNumber(Integer verseNumber) {
        this.verseNumber = verseNumber;
    }

    public String getCustomLabel() {
        return customLabel;
    }

    public void setCustomLabel(String customLabel) {
        this.customLabel = customLabel;
    }
}
