package com.churchsong.model;

import java.util.List;

public enum SongLanguage {
    ENGLISH,
    HAITIAN_CREOLE,
    SPANISH,
    FRENCH,
    UNKNOWN;

    public static List<SongLanguage> supportedFamilyLanguages() {
        return List.of(
                ENGLISH,
                HAITIAN_CREOLE,
                SPANISH,
                FRENCH
        );
    }

    public boolean isSupportedFamilyLanguage() {
        return supportedFamilyLanguages().contains(this);
    }
}
