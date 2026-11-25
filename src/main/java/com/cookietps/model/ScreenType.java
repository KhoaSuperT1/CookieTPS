package com.cookietps.model;

import org.bukkit.block.DoubleChest;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

public enum ScreenType {
    CHEST,
    DOUBLE_CHEST,
    BARREL,
    SHULKER_BOX,
    ENDER_CHEST,
    CRAFTING,
    FURNACE,
    PLAYER_INVENTORY,
    GENERIC;

    public static ScreenType fromInventory(Inventory inventory) {
        if (inventory == null) {
            return GENERIC;
        }
        InventoryType type = inventory.getType();
        return switch (type) {
            case CHEST -> {
                if (inventory.getSize() >= 54 || inventory.getHolder(false) instanceof DoubleChest) {
                    yield DOUBLE_CHEST;
                }
                yield CHEST;
            }
            case BARREL -> BARREL;
            case SHULKER_BOX -> SHULKER_BOX;
            case ENDER_CHEST -> ENDER_CHEST;
            case WORKBENCH, CRAFTING -> CRAFTING;
            case FURNACE, BLAST_FURNACE, SMOKER -> FURNACE;
            case PLAYER -> PLAYER_INVENTORY;
            default -> GENERIC;
        };
    }
}
