package com.cookietps.api;

import com.cookietps.model.ScreenType;
import com.cookietps.model.SlotClickAction;
import com.cookietps.model.SpatialSession;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Collection;
import java.util.UUID;

public interface InputHandler {
    boolean hasActiveSession(UUID playerUuid);

    SpatialSession getSession(UUID playerUuid);

    Collection<SpatialSession> getActiveSessions();

    void startSession(Player player, Inventory topInventory, ScreenType screenType, boolean editorPreview);

    void handleSlotAction(Player player, SpatialSession session, SlotClickAction action);

    void stepHoveredSlot(Player player, SpatialSession session, int delta);

    void closeSession(Player player, boolean immediate);

    void shutdownAll();
}
