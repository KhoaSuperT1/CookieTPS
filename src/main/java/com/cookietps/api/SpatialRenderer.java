package com.cookietps.api;

import com.cookietps.model.ScreenProfile;
import com.cookietps.model.SpatialSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface SpatialRenderer {
    void openPanel(Player player, SpatialSession session);

    void updatePanel(Player player, SpatialSession session);

    void syncContents(Player player, SpatialSession session);

    void updateHover(Player player, SpatialSession session, int newHoveredSlot);

    void updateCursorVisual(Player player, SpatialSession session);

    void refreshTransform(Player player, SpatialSession session, ScreenProfile updatedProfile);

    void closePanel(Player player, SpatialSession session, boolean immediate);

    int raycastHoveredSlot(Player player, SpatialSession session);

    Location raycastMouseWorldTarget(Player player, SpatialSession session);

    Location getSlotWorldLocation(SpatialSession session, int spatialSlotIndex);

    void shutdownAll();
}
