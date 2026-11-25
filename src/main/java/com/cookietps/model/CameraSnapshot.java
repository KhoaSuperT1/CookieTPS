package com.cookietps.model;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public record CameraSnapshot(
    UUID playerUuid,
    UUID worldUid,
    double x,
    double y,
    double z,
    float yaw,
    float pitch,
    GameMode gameMode,
    boolean allowFlight,
    boolean flying,
    boolean invisible,
    boolean invulnerable,
    long capturedAtMillis
) {
    public CameraSnapshot(
        UUID playerUuid,
        UUID worldUid,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        GameMode gameMode,
        boolean allowFlight,
        boolean flying,
        long capturedAtMillis
    ) {
        this(
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
            false,
            false,
            capturedAtMillis
        );
    }

    public Location toLocation() {
        World world = worldUid != null ? Bukkit.getWorld(worldUid) : null;
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }
}
