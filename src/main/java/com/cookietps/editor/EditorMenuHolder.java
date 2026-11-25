package com.cookietps.editor;

import com.cookietps.model.ScreenType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Objects;
import java.util.UUID;

public final class EditorMenuHolder implements InventoryHolder {
    private final UUID adminUuid;
    private final ScreenType screenType;
    private Inventory inventory;

    public EditorMenuHolder(UUID adminUuid, ScreenType screenType) {
        this.adminUuid = Objects.requireNonNull(adminUuid, "adminUuid");
        this.screenType = Objects.requireNonNull(screenType, "screenType");
    }

    public UUID getAdminUuid() {
        return adminUuid;
    }

    public ScreenType getScreenType() {
        return screenType;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
