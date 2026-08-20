package com.churchsong.service;

import com.churchsong.model.Playlist;
import com.churchsong.model.Song;
import com.churchsong.repository.PlaylistRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

@Service
public class PlaylistLibrary {

    private static final String OTHER_SERVICE_TYPE = "Other";
    private static final DateTimeFormatter
            SAVED_PLAYLIST_DATE_FORMATTER =
            DateTimeFormatter.ofPattern(
                    "MMM d, uuuu",
                    Locale.US);

    private final PlaylistRepository playlistRepository;

    public PlaylistLibrary(
            PlaylistRepository playlistRepository) {

        this.playlistRepository = playlistRepository;
    }

    public void addPlaylist(Playlist playlist) {
        validatePlaylist(playlist);
        playlistRepository.save(playlist);
    }

    public Playlist findPlaylistByName(String name) {
        String normalizedName = normalizeName(name);

        return playlistRepository.findAll()
                .stream()
                .filter(playlist ->
                        playlist.getName()
                                .equalsIgnoreCase(
                                        normalizedName))
                .findFirst()
                .orElse(null);
    }

    public Playlist findPlaylistById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException(
                    "id cannot be null."
            );
        }

        return playlistRepository.findById(id)
                .orElse(null);
    }

    public Playlist createSavedServicePlaylist(
            String requestedServiceType,
            String requestedCustomServiceType,
            String requestedName,
            String requestedServiceDate,
            String theme) {
        LocalDate serviceDate =
                parseRequiredServiceDate(
                        requestedServiceDate);
        String serviceType = resolveOptionalServiceType(
                requestedServiceType,
                requestedCustomServiceType);
        Playlist playlist = new Playlist(
                resolveSavedPlaylistName(
                        serviceType,
                        serviceDate,
                        requestedName));

        playlist.setReusable(false);
        playlist.setServiceType(serviceType);
        playlist.setServiceDate(serviceDate);
        playlist.setTheme(theme);

        return playlistRepository.save(playlist);
    }

    public Playlist createSavedServicePlaylist(
            String name,
            String requestedServiceDate,
            String theme) {
        return createSavedServicePlaylist(
                null,
                null,
                name,
                requestedServiceDate,
                theme);
    }

    @Transactional
    public Playlist copyPlaylistForService(
            Long sourcePlaylistId,
            String requestedServiceType,
            String requestedCustomServiceType,
            String requestedName,
            String requestedServiceDate,
            String theme) {
        Playlist sourcePlaylist =
                findPlaylistById(sourcePlaylistId);

        if (sourcePlaylist == null) {
            throw new IllegalArgumentException(
                    "source playlist does not exist.");
        }

        Playlist copiedPlaylist =
                createSavedServicePlaylist(
                        requestedServiceType,
                        requestedCustomServiceType,
                        requestedName,
                        requestedServiceDate,
                        theme);

        copiedPlaylist.setSourcePlaylistId(
                sourcePlaylist.getId());
        copiedPlaylist.replaceSongs(
                sourcePlaylist.getSongs());

        return playlistRepository.save(copiedPlaylist);
    }

    public Playlist copyPlaylistForService(
            Long sourcePlaylistId,
            String name,
            String requestedServiceDate,
            String theme) {
        return copyPlaylistForService(
                sourcePlaylistId,
                null,
                null,
                name,
                requestedServiceDate,
                theme);
    }

    @Transactional
    public Playlist updatePlaylistMetadata(
            Long playlistId,
            String requestedServiceType,
            String requestedCustomServiceType,
            String requestedName,
            String requestedServiceDate,
            String theme) {
        Playlist playlist = findPlaylistById(playlistId);

        if (playlist == null) {
            return null;
        }

        LocalDate serviceDate =
                playlist.isReusable()
                        ? parseOptionalServiceDate(
                                requestedServiceDate)
                        : parseRequiredServiceDate(
                                requestedServiceDate);
        String serviceType = resolveOptionalServiceType(
                requestedServiceType,
                requestedCustomServiceType);

        playlist.setServiceDate(serviceDate);
        playlist.setServiceType(serviceType);

        if (!playlist.isReusable()) {
            playlist.setName(
                    resolveSavedPlaylistName(
                            serviceType,
                            serviceDate,
                            requestedName != null
                                    ? requestedName
                                    : playlist.getName()));
        } else if (requestedName != null
                && !requestedName.trim().isEmpty()) {
            playlist.setName(
                    normalizeName(requestedName));
        }

        playlist.setTheme(theme);

        return playlistRepository.save(playlist);
    }

    public Playlist updatePlaylistMetadata(
            Long playlistId,
            String name,
            String requestedServiceDate,
            String theme) {
        return updatePlaylistMetadata(
                playlistId,
                null,
                null,
                name,
                requestedServiceDate,
                theme);
    }

    public boolean removePlaylistById(Long playlistId) {
        Playlist playlist = findPlaylistById(playlistId);

        if (playlist == null) {
            return false;
        }

        playlistRepository.delete(playlist);
        return true;
    }

    public boolean removePlaylistByName(String name) {
        Playlist playlist =
                findPlaylistByName(name);

        if (playlist != null) {
            playlistRepository.delete(playlist);
            return true;
        }

        return false;
    }

    public Playlist renamePlaylist(
            String currentName,
            String newName) {

        Playlist playlist =
                findPlaylistByName(currentName);

        if (playlist == null) {
            return null;
        }

        playlist.setName(
                normalizeName(newName));

        return playlistRepository.save(
                playlist);
    }

    public Playlist renamePlaylistById(
            Long playlistId,
            String newName) {

        Playlist playlist =
                findPlaylistById(playlistId);

        if (playlist == null) {
            return null;
        }

        playlist.setName(normalizeName(newName));

        return playlistRepository.save(
                playlist);
    }

    public List<Playlist> getPlaylistList() {
        return playlistRepository.findAll();
    }

    public Playlist findWorkingPlaylistByDate(
            LocalDate serviceDate) {
        if (serviceDate == null) {
            throw new IllegalArgumentException(
                    "serviceDate cannot be null."
            );
        }

        return playlistRepository
                .findByReusableFalseAndServiceDate(
                        serviceDate)
                .orElse(null);
    }

    @Transactional
    public Playlist usePlaylistForTodayService(
            Long sourcePlaylistId,
            String requestedName,
            String requestedServiceDate,
            boolean replaceExisting) {

        Playlist sourcePlaylist =
                findPlaylistById(sourcePlaylistId);

        if (sourcePlaylist == null) {
            return null;
        }

        if (!sourcePlaylist.isReusable()) {
            throw new IllegalArgumentException(
                    "Only reusable playlists can be used for today's service."
            );
        }

        LocalDate serviceDate =
                parseServiceDate(
                        requestedServiceDate);
        Playlist existingWorkingPlaylist =
                findWorkingPlaylistByDate(
                        serviceDate);

        if (existingWorkingPlaylist != null) {
            if (!replaceExisting) {
                throw new IllegalStateException(
                        "A working service playlist already exists for "
                                + serviceDate
                                + "."
                );
            }

            playlistRepository.delete(
                    existingWorkingPlaylist);
        }

        Playlist workingPlaylist =
                new Playlist(
                        normalizeWorkingPlaylistName(
                                requestedName,
                                serviceDate));

        workingPlaylist.setReusable(false);
        workingPlaylist.setServiceDate(
                serviceDate);
        workingPlaylist.setSourcePlaylistId(
                sourcePlaylist.getId());
        workingPlaylist.replaceSongs(
                sourcePlaylist.getSongs());

        return playlistRepository.save(
                workingPlaylist);
    }

    public String generateTodayServiceName(
            String requestedServiceDate) {
        return buildWorkingPlaylistName(
                parseServiceDate(
                        requestedServiceDate));
    }

    @Transactional
    public Playlist addSongToPlaylist(
            Playlist playlist,
            Song song) {

        if (playlist == null) {
            throw new IllegalArgumentException(
                    "playlist cannot be null."
            );
        }

        if (song == null) {
            throw new IllegalArgumentException(
                    "song cannot be null."
            );
        }

        Playlist managedPlaylist =
                getManagedPlaylist(playlist);

        managedPlaylist.addSong(song);

        return playlistRepository.save(
                managedPlaylist);
    }

    @Transactional
    public Playlist removeSongFromPlaylist(
            Playlist playlist,
            Song song) {

        if (playlist == null) {
            throw new IllegalArgumentException(
                    "playlist cannot be null."
            );
        }

        if (song == null) {
            throw new IllegalArgumentException(
                    "song cannot be null."
            );
        }

        Playlist managedPlaylist =
                getManagedPlaylist(playlist);

        managedPlaylist.removeSong(song);

        return playlistRepository.save(
                managedPlaylist);
    }

    @Transactional
    public Playlist moveSong(
            Playlist playlist,
            int fromIndex,
            int toIndex) {

        if (playlist == null) {
            throw new IllegalArgumentException(
                    "playlist cannot be null."
            );
        }

        Playlist managedPlaylist =
                getManagedPlaylist(playlist);

        managedPlaylist.moveSong(
                fromIndex,
                toIndex);

        return playlistRepository.save(
                managedPlaylist);
    }

    private Playlist getManagedPlaylist(
            Playlist playlist) {
        if (playlist.getId() == null) {
            throw new IllegalArgumentException(
                    "playlist must have an ID.");
        }

        Playlist managedPlaylist =
                findPlaylistById(playlist.getId());

        if (managedPlaylist == null) {
            throw new IllegalArgumentException(
                    "playlist does not exist.");
        }

        return managedPlaylist;
    }

    private void validatePlaylist(
            Playlist playlist) {

        if (playlist == null) {
            throw new IllegalArgumentException(
                    "playlist cannot be null."
            );
        }
    }

    private String normalizeName(String name) {
        if (name != null) {
            name = name.trim();
        }

        if (name == null
                || name.isEmpty()) {

            throw new IllegalArgumentException(
                    "name cannot be null or empty."
            );
        }

        return name;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalizedValue =
                value.trim();

        return normalizedValue.isEmpty()
                ? null
                : normalizedValue;
    }

    private LocalDate parseServiceDate(
            String requestedServiceDate) {
        if (requestedServiceDate == null
                || requestedServiceDate.trim()
                        .isEmpty()) {
            return LocalDate.now();
        }

        try {
            return LocalDate.parse(
                    requestedServiceDate.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "serviceDate must use YYYY-MM-DD."
            );
        }
    }

    private LocalDate parseRequiredServiceDate(
            String requestedServiceDate) {
        if (requestedServiceDate == null
                || requestedServiceDate.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "serviceDate cannot be null or empty.");
        }

        try {
            return LocalDate.parse(requestedServiceDate.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "serviceDate must use YYYY-MM-DD.");
        }
    }

    private LocalDate parseOptionalServiceDate(
            String requestedServiceDate) {
        if (requestedServiceDate == null
                || requestedServiceDate.trim().isEmpty()) {
            return null;
        }

        return parseRequiredServiceDate(requestedServiceDate);
    }

    private String normalizeWorkingPlaylistName(
            String requestedName,
            LocalDate serviceDate) {
        if (requestedName == null
                || requestedName.trim().isEmpty()) {
            return buildWorkingPlaylistName(
                    serviceDate);
        }

        return normalizeName(requestedName);
    }

    private String buildWorkingPlaylistName(
            LocalDate serviceDate) {
        String weekday =
                serviceDate.getDayOfWeek()
                        .getDisplayName(
                                TextStyle.FULL,
                                Locale.US);
        String month = String.format(
                "%02d",
                serviceDate.getMonthValue());
        String day = String.format(
                "%02d",
                serviceDate.getDayOfMonth());
        String year =
                String.valueOf(
                        serviceDate.getYear());

        return weekday
                + " — "
                + month
                + "/"
                + day
                + "/"
                + year;
    }

    private String resolveOptionalServiceType(
            String requestedServiceType,
            String requestedCustomServiceType) {
        String normalizedServiceType =
                normalizeOptionalText(
                        requestedServiceType);

        if (normalizedServiceType == null) {
            return null;
        }

        if (OTHER_SERVICE_TYPE.equalsIgnoreCase(
                normalizedServiceType)) {
            String customServiceType =
                    normalizeOptionalText(
                            requestedCustomServiceType);

            if (customServiceType == null) {
                throw new IllegalArgumentException(
                        "customServiceType cannot be null or empty when serviceType is Other.");
            }

            return customServiceType;
        }

        return normalizedServiceType;
    }

    private String resolveSavedPlaylistName(
            String serviceType,
            LocalDate serviceDate,
            String requestedName) {
        if (serviceType != null) {
            if (serviceDate == null) {
                throw new IllegalArgumentException(
                        "serviceDate cannot be null or empty.");
            }

            return buildSavedPlaylistName(
                    serviceType,
                    serviceDate);
        }

        return normalizeName(requestedName);
    }

    private String buildSavedPlaylistName(
            String serviceType,
            LocalDate serviceDate) {
        return serviceType
                + " – "
                + serviceDate.format(
                        SAVED_PLAYLIST_DATE_FORMATTER);
    }
}
