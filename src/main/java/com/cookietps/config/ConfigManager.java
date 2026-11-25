package com.cookietps.config;

import com.cookietps.model.ScreenType;
import com.cookietps.model.ViewMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ConfigManager {
    private final Plugin plugin;
    private boolean pluginEnabled;
    private ViewMode defaultViewMode;
    private boolean screenFilterWhitelistMode;
    private EnumSet<ScreenType> filteredScreens;
    private double maxInteractionDistance;
    private double autoCloseMoveDistance;
    private boolean returnCursorOnClose;
    private boolean soundFeedbackEnabled;
    private boolean sneakSwapOpensInventory;
    private boolean joinHintEnabled;

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
        this.filteredScreens = EnumSet.noneOf(ScreenType.class);
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.pluginEnabled = config.getBoolean("enabled", true);
        this.defaultViewMode = parseViewMode(config.getString("default-view-mode", "AUTO"), ViewMode.AUTO);
        String filterMode = config.getString("screen-filter.mode", "BLACKLIST");
        this.screenFilterWhitelistMode = "WHITELIST".equalsIgnoreCase(filterMode);

        EnumSet<ScreenType> parsedScreens = EnumSet.noneOf(ScreenType.class);
        List<String> rawScreens = config.getStringList("screen-filter.screens");
        for (String entry : rawScreens) {
            ScreenType type = parseScreenType(entry);
            if (type != null) {
                parsedScreens.add(type);
            }
        }
        this.filteredScreens = parsedScreens;

        this.maxInteractionDistance = Math.max(1.5, config.getDouble("interaction.max-distance", 5.5));
        this.autoCloseMoveDistance = Math.max(1.0, config.getDouble("interaction.auto-close-move-distance", 4.0));
        this.returnCursorOnClose = config.getBoolean("interaction.return-cursor-on-close", true);
        this.soundFeedbackEnabled = config.getBoolean("interaction.sound-feedback", true);
        this.sneakSwapOpensInventory = config.getBoolean("hotkeys.sneak-swap-opens-inventory", true);
        this.joinHintEnabled = config.getBoolean("hotkeys.join-hint", true);
    }

    public boolean isPluginEnabled() {
        return pluginEnabled;
    }

    public ViewMode getDefaultViewMode() {
        return defaultViewMode;
    }

    public boolean isScreenFilterWhitelistMode() {
        return screenFilterWhitelistMode;
    }

    public Set<ScreenType> getFilteredScreens() {
        return Collections.unmodifiableSet(EnumSet.copyOf(filteredScreens));
    }

    public boolean isScreenAllowed(ScreenType screenType) {
        if (screenType == null) {
            return false;
        }
        if (screenFilterWhitelistMode) {
            return filteredScreens.contains(screenType);
        }
        return !filteredScreens.contains(screenType);
    }

    public double getMaxInteractionDistance() {
        return maxInteractionDistance;
    }

    public double getAutoCloseMoveDistance() {
        return autoCloseMoveDistance;
    }

    public boolean isReturnCursorOnClose() {
        return returnCursorOnClose;
    }

    public boolean isSoundFeedbackEnabled() {
        return soundFeedbackEnabled;
    }

    public boolean isSneakSwapOpensInventory() {
        return sneakSwapOpensInventory;
    }

    public void setSneakSwapOpensInventory(boolean enabled) {
        this.sneakSwapOpensInventory = enabled;
        plugin.getConfig().set("hotkeys.sneak-swap-opens-inventory", enabled);
        saveConfig();
    }

    public boolean isJoinHintEnabled() {
        return joinHintEnabled;
    }

    public void setPluginEnabled(boolean enabled) {
        this.pluginEnabled = enabled;
        plugin.getConfig().set("enabled", enabled);
        saveConfig();
    }

    public void setDefaultViewMode(ViewMode mode) {
        this.defaultViewMode = mode != null ? mode : ViewMode.AUTO;
        plugin.getConfig().set("default-view-mode", this.defaultViewMode.name());
        saveConfig();
    }

    public void setScreenFilterWhitelistMode(boolean whitelistMode) {
        this.screenFilterWhitelistMode = whitelistMode;
        plugin.getConfig().set("screen-filter.mode", whitelistMode ? "WHITELIST" : "BLACKLIST");
        saveConfig();
    }

    public void addFilteredScreen(ScreenType screenType) {
        if (screenType == null) {
            return;
        }
        filteredScreens.add(screenType);
        persistFilteredScreens();
    }

    public void removeFilteredScreen(ScreenType screenType) {
        if (screenType == null) {
            return;
        }
        filteredScreens.remove(screenType);
        persistFilteredScreens();
    }

    public boolean toggleFilteredScreen(ScreenType screenType) {
        if (screenType == null) {
            return false;
        }
        boolean added;
        if (filteredScreens.contains(screenType)) {
            filteredScreens.remove(screenType);
            added = false;
        } else {
            filteredScreens.add(screenType);
            added = true;
        }
        persistFilteredScreens();
        return added;
    }

    public void setFilteredScreens(Collection<ScreenType> screens) {
        EnumSet<ScreenType> next = EnumSet.noneOf(ScreenType.class);
        if (screens != null) {
            for (ScreenType screen : screens) {
                if (screen != null) {
                    next.add(screen);
                }
            }
        }
        this.filteredScreens = next;
        persistFilteredScreens();
    }

    public void saveConfig() {
        plugin.saveConfig();
    }

    private void persistFilteredScreens() {
        List<String> serialized = new ArrayList<>(filteredScreens.size());
        for (ScreenType type : filteredScreens) {
            serialized.add(type.name());
        }
        plugin.getConfig().set("screen-filter.screens", serialized);
        saveConfig();
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

    private static ScreenType parseScreenType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ScreenType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
