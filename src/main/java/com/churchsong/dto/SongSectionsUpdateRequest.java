package com.churchsong.dto;

import java.util.List;

public class SongSectionsUpdateRequest {

    private Boolean sectionsConfirmed;
    private List<SongSectionDescriptorRequest> sections;

    public SongSectionsUpdateRequest() {
        // Required for JSON deserialization
    }

    public boolean isSectionsConfirmed() {
        return Boolean.TRUE.equals(sectionsConfirmed);
    }

    public Boolean getRawSectionsConfirmed() {
        return sectionsConfirmed;
    }

    public void setSectionsConfirmed(Boolean sectionsConfirmed) {
        this.sectionsConfirmed = sectionsConfirmed;
    }

    public List<SongSectionDescriptorRequest> getSections() {
        return sections;
    }

    public void setSections(List<SongSectionDescriptorRequest> sections) {
        this.sections = sections;
    }
}
