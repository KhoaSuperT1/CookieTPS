package com.cookietps.config;

import com.cookietps.model.PlayerPreferences;
import com.cookietps.model.ViewMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerPreferencesStore {
    private final Plugin plugin;
    private final Path playersDir;
    private final Map<UUID, PlayerPreferences> cache;

    public PlayerPreferencesStore(Plugin plugin) {
        this.plugin = plugin;
        this.playersDir = plugin.getDataFolder().toPath().resolve("players");
        this.cache = new ConcurrentHashMap<>();
        ensureDirectory();
    }

    public PlayerPreferences load(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        PlayerPreferences cached = cache.get(playerUuid);
        if (cached != null) {
            return cached;
        }
        Path file = playersDir.resolve(playerUuid + ".yml");
        PlayerPreferences prefs;
        if (Files.exists(file)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
            boolean enabled = yaml.getBoolean("enabled", true);
            ViewMode mode = parseViewMode(yaml.getString("preferred-mode"), ViewMode.AUTO);
            prefs = new PlayerPreferences(playerUuid, enabled, mode);
        } else {
            prefs = new PlayerPreferences(playerUuid, true, ViewMode.AUTO);
        }
        cache.put(playerUuid, prefs);
        return prefs;
    }

    public void save(PlayerPreferences preferences) {
        if (preferences == null || preferences.playerUuid() == null) {
            return;
        }
        cache.put(preferences.playerUuid(), preferences);
        ensureDirectory();
        Path target = playersDir.resolve(preferences.playerUuid() + ".yml");
        Path temp = playersDir.resolve(preferences.playerUuid() + ".yml.tmp");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", preferences.enabled());
        yaml.set("preferred-mode", preferences.preferredMode().name());
        try {
            yaml.save(temp.toFile());
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save preferences for " + preferences.playerUuid() + ": " + e.getMessage());
        }
    }

    public void unload(UUID playerUuid) {
        if (playerUuid != null) {
            cache.remove(playerUuid);
        }
    }

    private void ensureDirectory() {
        File dir = playersDir.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private static ViewMode parseViewMode(String raw, ViewMode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return ViewMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
