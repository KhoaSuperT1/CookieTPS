package com.cookietps.model;

import java.util.UUID;

public record PlayerPreferences(
    UUID playerUuid,
    boolean enabled,
    ViewMode preferredMode
) {
    public PlayerPreferences withEnabled(boolean enabled) {
        return new PlayerPreferences(playerUuid, enabled, preferredMode);
    }

    public PlayerPreferences withPreferredMode(ViewMode preferredMode) {
        return new PlayerPreferences(playerUuid, enabled, preferredMode);
    }
}
