package com.cookietps.config;

import com.cookietps.model.CameraSnapshot;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class CrashRecoveryStore {
    private final Plugin plugin;
    private final Path recoveryDir;

    public CrashRecoveryStore(Plugin plugin) {
        this.plugin = plugin;
        this.recoveryDir = plugin.getDataFolder().toPath().resolve("recovery");
        ensureDirectory();
    }

    public void saveSnapshot(CameraSnapshot snapshot) {
        if (snapshot == null || snapshot.playerUuid() == null || snapshot.worldUid() == null) {
            return;
        }
        ensureDirectory();
        Path target = snapshotPath(snapshot.playerUuid());
        Path temp = recoveryDir.resolve(snapshot.playerUuid() + ".yml.tmp");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("player-uuid", snapshot.playerUuid().toString());
        yaml.set("world-uid", snapshot.worldUid().toString());
        yaml.set("x", snapshot.x());
        yaml.set("y", snapshot.y());
        yaml.set("z", snapshot.z());
        yaml.set("yaw", snapshot.yaw());
        yaml.set("pitch", snapshot.pitch());
        yaml.set("game-mode", snapshot.gameMode() != null ? snapshot.gameMode().name() : GameMode.SURVIVAL.name());
        yaml.set("allow-flight", snapshot.allowFlight());
        yaml.set("flying", snapshot.flying());
        yaml.set("invisible", snapshot.invisible());
        yaml.set("invulnerable", snapshot.invulnerable());
        yaml.set("captured-at-millis", snapshot.capturedAtMillis());
        try {
            yaml.save(temp.toFile());
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to persist crash recovery snapshot for " + snapshot.playerUuid() + ": " + e.getMessage());
        }
    }

    public Optional<CameraSnapshot> getSnapshot(UUID playerUuid) {
        if (playerUuid == null) {
            return Optional.empty();
        }
        Path path = snapshotPath(playerUuid);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        return readSnapshot(path);
    }

    public List<CameraSnapshot> loadAllSnapshots() {
        ensureDirectory();
        List<CameraSnapshot> snapshots = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(recoveryDir, "*.yml")) {
            for (Path path : stream) {
                readSnapshot(path).ifPresent(snapshots::add);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to list crash recovery snapshots: " + e.getMessage());
        }
        return snapshots;
    }

    public void removeSnapshot(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        try {
            Files.deleteIfExists(snapshotPath(playerUuid));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to remove crash recovery snapshot for " + playerUuid + ": " + e.getMessage());
        }
    }

    private Optional<CameraSnapshot> readSnapshot(Path path) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
        String rawPlayer = yaml.getString("player-uuid");
        String rawWorld = yaml.getString("world-uid");
        if (rawPlayer == null || rawWorld == null) {
            return Optional.empty();
        }
        try {
            UUID playerUuid = UUID.fromString(rawPlayer);
            UUID worldUid = UUID.fromString(rawWorld);
            double x = yaml.getDouble("x");
            double y = yaml.getDouble("y");
            double z = yaml.getDouble("z");
            float yaw = (float) yaml.getDouble("yaw");
            float pitch = (float) yaml.getDouble("pitch");
            GameMode gameMode = parseGameMode(yaml.getString("game-mode"));
            boolean allowFlight = yaml.getBoolean("allow-flight", false);
            boolean flying = yaml.getBoolean("flying", false);
            boolean invisible = yaml.getBoolean("invisible", false);
            boolean invulnerable = yaml.getBoolean("invulnerable", false);
            long capturedAt = yaml.getLong("captured-at-millis", System.currentTimeMillis());
            return Optional.of(new CameraSnapshot(
                playerUuid,
                worldUid,
                x,
                y,
                z,
                yaw,
                pitch,
                gameMode,
                allowFlight,
                flying,
                invisible,
                invulnerable,
                capturedAt
            ));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Path snapshotPath(UUID playerUuid) {
        return recoveryDir.resolve(playerUuid + ".yml");
    }

    private void ensureDirectory() {
        File dir = recoveryDir.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private static GameMode parseGameMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return GameMode.SURVIVAL;
        }
        try {
            return GameMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return GameMode.SURVIVAL;
        }
    }
}
