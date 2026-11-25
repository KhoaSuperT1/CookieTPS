package com.cookietps.model;

import org.bukkit.inventory.ItemStack;

public record SlotInteractionResult(
    boolean handled,
    boolean shouldClose,
    boolean inventoryDirty,
    ItemStack cursorItem
) {}
