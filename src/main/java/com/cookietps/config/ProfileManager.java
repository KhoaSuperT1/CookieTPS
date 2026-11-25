package com.cookietps.config;

import com.cookietps.model.RenderTransform;
import com.cookietps.model.ScreenProfile;
import com.cookietps.model.ScreenType;
import com.cookietps.model.ThirdPersonSettings;
import com.cookietps.model.ViewMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class ProfileManager {
    private final Plugin plugin;
    private final File profilesFile;
    private final Map<ScreenType, ScreenProfile> profiles;
    private YamlConfiguration config;

    public ProfileManager(Plugin plugin) {
        this.plugin = plugin;
        this.profilesFile = new File(plugin.getDataFolder(), "profiles.yml");
        this.profiles = new EnumMap<>(ScreenType.class);
        reload();
    }

    public void reload() {
        if (!profilesFile.exists()) {
            File parent = profilesFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            if (plugin.getResource("profiles.yml") != null) {
                plugin.saveResource("profiles.yml", false);
            }
        }
        this.config = YamlConfiguration.loadConfiguration(profilesFile);
        YamlConfiguration bundledDefaults = loadBundledDefaults();

        profiles.clear();
        for (ScreenType screenType : ScreenType.values()) {
            String path = "profiles." + screenType.name();
            ConfigurationSection section = config.getConfigurationSection(path);
            ConfigurationSection fallbackSection = bundledDefaults.getConfigurationSection(path);
            ScreenProfile fallback = createBuiltInDefault(screenType);
            if (fallbackSection != null) {
                fallback = readProfile(screenType, fallbackSection, fallback);
            }
            ScreenProfile loaded = section != null ? readProfile(screenType, section, fallback) : fallback;
            profiles.put(screenType, loaded);
        }
    }

    public ScreenProfile getProfile(ScreenType screenType) {
        ScreenType key = screenType != null ? screenType : ScreenType.GENERIC;
        ScreenProfile profile = profiles.get(key);
        if (profile != null) {
            return profile;
        }
        ScreenProfile generic = profiles.get(ScreenType.GENERIC);
        return generic != null ? generic : createBuiltInDefault(key);
    }

    public Map<ScreenType, ScreenProfile> getAllProfiles() {
        return Collections.unmodifiableMap(new EnumMap<>(profiles));
    }

    public void saveProfile(ScreenProfile profile) {
        if (profile == null) {
            return;
        }
        profiles.put(profile.screenType(), profile);
        if (config == null) {
            config = new YamlConfiguration();
        }
        String path = "profiles." + profile.screenType().name();
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            section = config.createSection(path);
        }
        writeProfile(section, profile);
        persistConfig();
    }

    public void resetProfile(ScreenType screenType) {
        if (screenType == null) {
            return;
        }
        YamlConfiguration bundledDefaults = loadBundledDefaults();
        ConfigurationSection fallbackSection = bundledDefaults.getConfigurationSection("profiles." + screenType.name());
        ScreenProfile defaultProfile = createBuiltInDefault(screenType);
        if (fallbackSection != null) {
            defaultProfile = readProfile(screenType, fallbackSection, defaultProfile);
        }
        saveProfile(defaultProfile);
    }

    private YamlConfiguration loadBundledDefaults() {
        InputStream stream = plugin.getResource("profiles.yml");
        if (stream == null) {
            return new YamlConfiguration();
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException ignored) {
            return new YamlConfiguration();
        }
    }

    private void persistConfig() {
        try {
            File parent = profilesFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            config.save(profilesFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save profiles.yml: " + e.getMessage());
        }
    }

    private ScreenProfile readProfile(ScreenType screenType, ConfigurationSection section, ScreenProfile fallback) {
        ViewMode viewMode = parseViewMode(section.getString("view-mode"), fallback.viewMode());
        boolean enabled = section.getBoolean("enabled", fallback.enabled());
        int columns = Math.max(1, section.getInt("columns", fallback.columns()));
        int rows = Math.max(1, section.getInt("rows", fallback.rows()));
        float slotSpacing = (float) section.getDouble("slot-spacing", fallback.slotSpacing());
        boolean showBackground = section.getBoolean("show-background", fallback.showBackground());
        boolean showTooltips = section.getBoolean("show-tooltips", fallback.showTooltips());
        boolean includePlayerInventory = section.getBoolean("include-player-inventory", fallback.includePlayerInventory());

        ConfigurationSection fpSection = section.getConfigurationSection("first-person");
        RenderTransform firstPerson = fpSection != null
            ? readTransform(fpSection, fallback.firstPersonTransform())
            : fallback.firstPersonTransform();

        ConfigurationSection tpSection = section.getConfigurationSection("third-person");
        RenderTransform thirdPerson = tpSection != null
            ? readTransform(tpSection, fallback.thirdPersonTransform())
            : fallback.thirdPersonTransform();
        ThirdPersonSettings tpSettings = tpSection != null
            ? readThirdPersonSettings(tpSection, fallback.thirdPersonSettings())
            : fallback.thirdPersonSettings();

        return new ScreenProfile(
            screenType.name().toLowerCase(Locale.ROOT),
            screenType,
            viewMode,
            enabled,
            firstPerson,
            thirdPerson,
            tpSettings,
            columns,
            rows,
            slotSpacing,
            showBackground,
            showTooltips,
            includePlayerInventory
        );
    }

    private RenderTransform readTransform(ConfigurationSection section, RenderTransform fallback) {
        return new RenderTransform(
            section.getDouble("distance", fallback.distance()),
            (float) section.getDouble("scale", fallback.scale()),
            section.getDouble("offset-x", fallback.offsetX()),
            section.getDouble("offset-y", fallback.offsetY()),
            section.getDouble("offset-z", fallback.offsetZ()),
            (float) section.getDouble("pitch-offset", fallback.pitchOffset()),
            (float) section.getDouble("yaw-offset", fallback.yawOffset()),
            (float) section.getDouble("roll-offset", fallback.rollOffset()),
            (float) section.getDouble("curvature", fallback.curvature()),
            Math.max(0, section.getInt("open-duration-ticks", fallback.openDurationTicks())),
            Math.max(0, section.getInt("close-duration-ticks", fallback.closeDurationTicks())),
            Math.max(0, section.getInt("teleport-duration-ticks", fallback.teleportDurationTicks()))
        );
    }

    private ThirdPersonSettings readThirdPersonSettings(ConfigurationSection section, ThirdPersonSettings fallback) {
        return new ThirdPersonSettings(
            section.getDouble("camera-distance", fallback.cameraDistance()),
            section.getDouble("camera-height-offset", fallback.cameraHeightOffset()),
            section.getDouble("camera-side-offset", fallback.cameraSideOffset()),
            (float) section.getDouble("camera-yaw-offset", fallback.cameraYawOffset()),
            (float) section.getDouble("camera-pitch-offset", fallback.cameraPitchOffset()),
            section.getDouble("min-clearance", fallback.minClearance()),
            Math.max(0, section.getInt("camera-teleport-duration-ticks", fallback.cameraTeleportDurationTicks())),
            (float) section.getDouble("avatar-body-yaw-offset", fallback.avatarBodyYawOffset()),
            section.getBoolean("head-track-hovered-slot", fallback.headTrackHoveredSlot()),
            section.getBoolean("fallback-to-first-person-on-occlusion", fallback.fallbackToFirstPersonOnOcclusion()),
            Math.max(0, section.getInt("open-transition-ticks", fallback.openTransitionTicks())),
            Math.max(0, section.getInt("close-transition-ticks", fallback.closeTransitionTicks())),
            section.getDouble("front-camera-distance", fallback.frontCameraDistance()),
            section.getDouble("front-camera-height-offset", fallback.frontCameraHeightOffset()),
            section.getDouble("front-camera-side-offset", fallback.frontCameraSideOffset()),
            (float) section.getDouble("front-camera-yaw-offset", fallback.frontCameraYawOffset()),
            (float) section.getDouble("front-camera-pitch-offset", fallback.frontCameraPitchOffset())
        );
    }

    private void writeProfile(ConfigurationSection section, ScreenProfile profile) {
        section.set("view-mode", profile.viewMode().name());
        section.set("enabled", profile.enabled());
        section.set("columns", profile.columns());
        section.set("rows", profile.rows());
        section.set("slot-spacing", profile.slotSpacing());
        section.set("show-background", profile.showBackground());
        section.set("show-tooltips", profile.showTooltips());
        section.set("include-player-inventory", profile.includePlayerInventory());

        ConfigurationSection fpSection = section.getConfigurationSection("first-person");
        if (fpSection == null) {
            fpSection = section.createSection("first-person");
        }
        writeTransform(fpSection, profile.firstPersonTransform());

        ConfigurationSection tpSection = section.getConfigurationSection("third-person");
        if (tpSection == null) {
            tpSection = section.createSection("third-person");
        }
        writeTransform(tpSection, profile.thirdPersonTransform());
        writeThirdPersonSettings(tpSection, profile.thirdPersonSettings());
    }

    private void writeTransform(ConfigurationSection section, RenderTransform transform) {
        section.set("distance", transform.distance());
        section.set("scale", transform.scale());
        section.set("offset-x", transform.offsetX());
        section.set("offset-y", transform.offsetY());
        section.set("offset-z", transform.offsetZ());
        section.set("pitch-offset", transform.pitchOffset());
        section.set("yaw-offset", transform.yawOffset());
        section.set("roll-offset", transform.rollOffset());
        section.set("curvature", transform.curvature());
        section.set("open-duration-ticks", transform.openDurationTicks());
        section.set("close-duration-ticks", transform.closeDurationTicks());
        section.set("teleport-duration-ticks", transform.teleportDurationTicks());
    }

    private void writeThirdPersonSettings(ConfigurationSection section, ThirdPersonSettings settings) {
        section.set("camera-distance", settings.cameraDistance());
        section.set("camera-height-offset", settings.cameraHeightOffset());
        section.set("camera-side-offset", settings.cameraSideOffset());
        section.set("camera-yaw-offset", settings.cameraYawOffset());
        section.set("camera-pitch-offset", settings.cameraPitchOffset());
        section.set("min-clearance", settings.minClearance());
        section.set("camera-teleport-duration-ticks", settings.cameraTeleportDurationTicks());
        section.set("avatar-body-yaw-offset", settings.avatarBodyYawOffset());
        section.set("head-track-hovered-slot", settings.headTrackHoveredSlot());
        section.set("fallback-to-first-person-on-occlusion", settings.fallbackToFirstPersonOnOcclusion());
        section.set("open-transition-ticks", settings.openTransitionTicks());
        section.set("close-transition-ticks", settings.closeTransitionTicks());
        section.set("front-camera-distance", settings.frontCameraDistance());
        section.set("front-camera-height-offset", settings.frontCameraHeightOffset());
        section.set("front-camera-side-offset", settings.frontCameraSideOffset());
        section.set("front-camera-yaw-offset", settings.frontCameraYawOffset());
        section.set("front-camera-pitch-offset", settings.frontCameraPitchOffset());
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

    private static ScreenProfile createBuiltInDefault(ScreenType screenType) {
        int columns = switch (screenType) {
            case CRAFTING -> 5;
            case FURNACE -> 3;
            default -> 9;
        };
        int rows = switch (screenType) {
            case DOUBLE_CHEST -> 6;
            case PLAYER_INVENTORY -> 4;
            default -> 3;
        };
        boolean includePlayerInv = screenType != ScreenType.PLAYER_INVENTORY;
        RenderTransform fp = new RenderTransform(2.20, 0.76f, -0.30, 0.14, 0.0, -3.0f, 8.5f, 3.5f, 0.0f, 4, 3, 2);
        RenderTransform tp = new RenderTransform(1.60, 1.12f, 2.45, -0.05, 0.0, 0.0f, -40.0f, 0.0f, 0.0f, 5, 4, 2);
        ThirdPersonSettings tpSettings = new ThirdPersonSettings(2.05, -0.05, 0.0, 26.0f, 2.0f, 1.15, 4, 0.0f, true, true, 10, 8, 2.45, 0.15, -0.85, 162.0f, 4.0f);
        return new ScreenProfile(
            screenType.name().toLowerCase(Locale.ROOT),
            screenType,
            ViewMode.AUTO,
            true,
            fp,
            tp,
            tpSettings,
            columns,
            rows,
            0.24f,
            true,
            true,
            includePlayerInv
        );
    }
}
