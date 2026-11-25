package com.cookietps.bridge;

import com.cookietps.model.PanelSlotLayout;
import com.cookietps.model.ScreenProfile;
import com.cookietps.model.ScreenType;
import com.cookietps.model.SlotClickAction;
import com.cookietps.model.SlotInteractionResult;
import com.cookietps.model.SpatialSession;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class ContainerBridge {
    private static final int PLAYER_STORAGE_SLOTS = 36;

    public List<PanelSlotLayout> buildSlotLayouts(ScreenProfile profile, Inventory topInventory, Inventory bottomInventory) {
        List<PanelSlotLayout> raw = new ArrayList<>();
        ScreenType screenType = profile.screenType();
        float spacing = Math.max(0.12f, profile.slotSpacing());

        int topCols = resolveTopColumns(profile, topInventory);
        int topRows = resolveTopRows(profile, topInventory, topCols);

        boolean includeBottom = profile.includePlayerInventory()
            && bottomInventory != null
            && screenType != ScreenType.PLAYER_INVENTORY;

        float bottomHeight = includeBottom ? (4.0f * spacing + spacing * 0.35f) : 0.0f;
        float gapBetweenSections = includeBottom ? (spacing * 0.45f) : 0.0f;
        float topHeight = topRows * spacing;
        float totalHeight = topHeight + gapBetweenSections + bottomHeight;
        float topStartY = (totalHeight / 2.0f) - (spacing * 0.5f);

        int spatialIndex = 0;
        if (screenType == ScreenType.CRAFTING) {
            spatialIndex = appendCraftingLayouts(raw, topInventory, spacing, topStartY, spatialIndex);
        } else if (screenType == ScreenType.FURNACE) {
            spatialIndex = appendFurnaceLayouts(raw, spacing, topStartY, spatialIndex);
        } else if (screenType == ScreenType.PLAYER_INVENTORY) {
            spatialIndex = appendPlayerInventoryLayouts(raw, true, spacing, topStartY, spatialIndex);
        } else {
            int topSize = topInventory != null ? topInventory.getSize() : topCols * topRows;
            for (int i = 0; i < topSize; i++) {
                int col = i % topCols;
                int row = i / topCols;
                float lx = (col - (topCols - 1) / 2.0f) * spacing;
                float ly = topStartY - (row * spacing);
                raw.add(new PanelSlotLayout(spatialIndex++, i, true, false, lx, ly, 0.0f));
            }
        }

        if (includeBottom) {
            float bottomStartY = topStartY - topHeight - gapBetweenSections;
            spatialIndex = appendPlayerInventoryLayouts(raw, false, spacing, bottomStartY, spatialIndex);
        }

        int maxCols = Math.max(topCols, includeBottom ? 9 : topCols);
        float closeX = ((maxCols - 1) / 2.0f + 0.75f) * spacing;
        float closeY = topStartY + (spacing * 0.8f);
        raw.add(new PanelSlotLayout(spatialIndex, -1, false, true, closeX, closeY, 0.0f));

        return List.copyOf(raw);
    }

    public ItemStack getSlotItem(SpatialSession session, PanelSlotLayout layout) {
        if (session == null || layout == null || layout.closeButton()) {
            return null;
        }
        Inventory target = layout.topInventory() ? session.getTopInventory() : session.getBottomInventory();
        if (target == null) {
            return null;
        }
        int slot = layout.containerSlotIndex();
        if (slot < 0 || slot >= target.getSize()) {
            return null;
        }
        return normalizeItem(target.getItem(slot));
    }

    public void setSlotItem(SpatialSession session, PanelSlotLayout layout, ItemStack item) {
        if (session == null || layout == null || layout.closeButton()) {
            return;
        }
        Inventory target = layout.topInventory() ? session.getTopInventory() : session.getBottomInventory();
        if (target == null) {
            return;
        }
        int slot = layout.containerSlotIndex();
        if (slot < 0 || slot >= target.getSize()) {
            return;
        }
        ItemStack normalized = normalizeItem(item);
        target.setItem(slot, normalized != null ? normalized.clone() : null);
    }

    public SlotInteractionResult executeClick(Player player, SpatialSession session, int spatialSlotIndex, SlotClickAction action) {
        if (session == null || session.isClosing()) {
            return new SlotInteractionResult(false, false, false, session != null ? session.getVirtualCursor() : null);
        }
        if (action == SlotClickAction.CLOSE) {
            return new SlotInteractionResult(true, true, false, normalizeItem(session.getVirtualCursor()));
        }

        List<PanelSlotLayout> layouts = session.getSlotLayouts();
        if (spatialSlotIndex < 0 || spatialSlotIndex >= layouts.size()) {
            if (!session.isEditorPreview() && (action == SlotClickAction.DROP_ONE || action == SlotClickAction.DROP_ALL)) {
                ItemStack cursor = normalizeItem(session.getVirtualCursor());
                if (cursor != null) {
                    int dropCount = action == SlotClickAction.DROP_ALL ? cursor.getAmount() : 1;
                    dropFromPlayer(player, withAmount(cursor, dropCount));
                    int remaining = cursor.getAmount() - dropCount;
                    ItemStack nextCursor = remaining > 0 ? withAmount(cursor, remaining) : null;
                    session.setVirtualCursor(nextCursor);
                    return new SlotInteractionResult(true, false, false, nextCursor);
                }
            }
            return new SlotInteractionResult(false, false, false, normalizeItem(session.getVirtualCursor()));
        }

        PanelSlotLayout layout = layouts.get(spatialSlotIndex);
        if (layout.closeButton()) {
            boolean shouldClose = action == SlotClickAction.LEFT_CLICK
                || action == SlotClickAction.RIGHT_CLICK
                || action == SlotClickAction.SHIFT_LEFT_CLICK;
            return new SlotInteractionResult(true, shouldClose, false, normalizeItem(session.getVirtualCursor()));
        }

        if (session.isEditorPreview()) {
            return new SlotInteractionResult(true, false, false, null);
        }

        ItemStack slotItem = cloneOrNull(getSlotItem(session, layout));
        ItemStack cursor = cloneOrNull(normalizeItem(session.getVirtualCursor()));
        boolean resultOnlySlot = isResultOnlySlot(session, layout);
        boolean dirty = false;

        switch (action) {
            case LEFT_CLICK -> {
                if (cursor == null && slotItem != null) {
                    cursor = slotItem;
                    setSlotItem(session, layout, null);
                    handleResultConsumed(player, session, layout);
                    dirty = true;
                } else if (cursor != null && slotItem == null) {
                    if (!resultOnlySlot) {
                        setSlotItem(session, layout, cursor);
                        cursor = null;
                        dirty = true;
                    }
                } else if (cursor != null) {
                    if (cursor.isSimilar(slotItem)) {
                        int max = cursor.getMaxStackSize();
                        int space = max - (resultOnlySlot ? cursor.getAmount() : slotItem.getAmount());
                        if (resultOnlySlot) {
                            if (space >= slotItem.getAmount()) {
                                cursor.setAmount(cursor.getAmount() + slotItem.getAmount());
                                setSlotItem(session, layout, null);
                                handleResultConsumed(player, session, layout);
                                dirty = true;
                            }
                        } else if (space > 0) {
                            int move = Math.min(space, cursor.getAmount());
                            slotItem.setAmount(slotItem.getAmount() + move);
                            setSlotItem(session, layout, slotItem);
                            int remaining = cursor.getAmount() - move;
                            cursor = remaining > 0 ? withAmount(cursor, remaining) : null;
                            dirty = true;
                        }
                    } else if (!resultOnlySlot) {
                        setSlotItem(session, layout, cursor);
                        cursor = slotItem;
                        dirty = true;
                    }
                }
            }
            case RIGHT_CLICK -> {
                if (cursor == null && slotItem != null) {
                    int take = resultOnlySlot ? slotItem.getAmount() : (slotItem.getAmount() + 1) / 2;
                    cursor = withAmount(slotItem, take);
                    int remaining = slotItem.getAmount() - take;
                    setSlotItem(session, layout, remaining > 0 ? withAmount(slotItem, remaining) : null);
                    if (remaining <= 0) {
                        handleResultConsumed(player, session, layout);
                    }
                    dirty = true;
                } else if (cursor != null && !resultOnlySlot) {
                    if (slotItem == null) {
                        setSlotItem(session, layout, withAmount(cursor, 1));
                        int remaining = cursor.getAmount() - 1;
                        cursor = remaining > 0 ? withAmount(cursor, remaining) : null;
                        dirty = true;
                    } else if (cursor.isSimilar(slotItem)) {
                        if (slotItem.getAmount() < slotItem.getMaxStackSize()) {
                            slotItem.setAmount(slotItem.getAmount() + 1);
                            setSlotItem(session, layout, slotItem);
                            int remaining = cursor.getAmount() - 1;
                            cursor = remaining > 0 ? withAmount(cursor, remaining) : null;
                            dirty = true;
                        }
                    } else {
                        setSlotItem(session, layout, cursor);
                        cursor = slotItem;
                        dirty = true;
                    }
                } else if (cursor != null && slotItem != null && cursor.isSimilar(slotItem)) {
                    int space = cursor.getMaxStackSize() - cursor.getAmount();
                    if (space >= slotItem.getAmount()) {
                        cursor.setAmount(cursor.getAmount() + slotItem.getAmount());
                        setSlotItem(session, layout, null);
                        handleResultConsumed(player, session, layout);
                        dirty = true;
                    }
                }
            }
            case SHIFT_LEFT_CLICK -> {
                if (slotItem != null) {
                    ItemStack remainder = quickMove(session, layout, slotItem);
                    if (remainder == null || remainder.getAmount() != slotItem.getAmount()) {
                        setSlotItem(session, layout, remainder);
                        if (remainder == null) {
                            handleResultConsumed(player, session, layout);
                        }
                        dirty = true;
                    }
                }
            }
            case DROP_ONE, DROP_ALL -> {
                if (slotItem != null) {
                    int dropCount = (action == SlotClickAction.DROP_ALL || resultOnlySlot)
                        ? slotItem.getAmount()
                        : 1;
                    dropFromPlayer(player, withAmount(slotItem, dropCount));
                    int remaining = slotItem.getAmount() - dropCount;
                    setSlotItem(session, layout, remaining > 0 ? withAmount(slotItem, remaining) : null);
                    if (remaining <= 0) {
                        handleResultConsumed(player, session, layout);
                    }
                    dirty = true;
                } else if (cursor != null) {
                    int dropCount = action == SlotClickAction.DROP_ALL ? cursor.getAmount() : 1;
                    dropFromPlayer(player, withAmount(cursor, dropCount));
                    int remaining = cursor.getAmount() - dropCount;
                    cursor = remaining > 0 ? withAmount(cursor, remaining) : null;
                }
            }
            case CLOSE -> {
            }
        }

        if (dirty && session.getScreenType() == ScreenType.CRAFTING && layout.topInventory() && layout.containerSlotIndex() != 0) {
            refreshCraftingResult(player, session.getTopInventory());
        }

        session.setVirtualCursor(cursor);
        return new SlotInteractionResult(true, false, dirty, cursor);
    }

    public void returnVirtualCursor(Player player, SpatialSession session) {
        if (session == null) {
            return;
        }
        ItemStack cursor = normalizeItem(session.getVirtualCursor());
        session.setVirtualCursor(null);
        if (cursor == null || session.isEditorPreview() || player == null) {
            return;
        }
        PlayerInventory playerInv = player.getInventory();
        HashMap<Integer, ItemStack> leftover = playerInv.addItem(cursor.clone());
        if (!leftover.isEmpty()) {
            Location dropLoc = player.getLocation();
            World world = dropLoc.getWorld();
            if (world != null) {
                for (ItemStack extra : leftover.values()) {
                    if (extra != null && !extra.getType().isAir()) {
                        world.dropItemNaturally(dropLoc, extra);
                    }
                }
            }
        }
    }

    private int appendCraftingLayouts(
        List<PanelSlotLayout> out,
        Inventory topInventory,
        float spacing,
        float topStartY,
        int startIndex
    ) {
        int spatialIndex = startIndex;
        int size = topInventory != null ? topInventory.getSize() : 10;
        int gridDim = size <= 5 ? 2 : 3;
        float centerCol = gridDim == 2 ? 1.5f : 2.0f;

        for (int r = 0; r < gridDim; r++) {
            for (int c = 0; c < gridDim; c++) {
                int matrixSlot = 1 + (r * gridDim) + c;
                if (matrixSlot < size) {
                    float lx = (c - centerCol) * spacing;
                    float ly = topStartY - (r * spacing);
                    out.add(new PanelSlotLayout(spatialIndex++, matrixSlot, true, false, lx, ly, 0.0f));
                }
            }
        }

        float resultX = (gridDim + 1.0f - centerCol) * spacing;
        float resultY = topStartY - (((gridDim - 1) / 2.0f) * spacing);
        out.add(new PanelSlotLayout(spatialIndex++, 0, true, false, resultX, resultY, 0.0f));
        return spatialIndex;
    }

    private int appendFurnaceLayouts(List<PanelSlotLayout> out, float spacing, float topStartY, int startIndex) {
        int spatialIndex = startIndex;
        out.add(new PanelSlotLayout(spatialIndex++, 0, true, false, -spacing, topStartY, 0.0f));
        out.add(new PanelSlotLayout(spatialIndex++, 1, true, false, -spacing, topStartY - (2.0f * spacing), 0.0f));
        out.add(new PanelSlotLayout(spatialIndex++, 2, true, false, spacing, topStartY - spacing, 0.0f));
        return spatialIndex;
    }

    private int appendPlayerInventoryLayouts(
        List<PanelSlotLayout> out,
        boolean topInventory,
        float spacing,
        float startY,
        int startIndex
    ) {
        int spatialIndex = startIndex;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = 9 + (row * 9) + col;
                float lx = (col - 4.0f) * spacing;
                float ly = startY - (row * spacing);
                out.add(new PanelSlotLayout(spatialIndex++, slot, topInventory, false, lx, ly, 0.0f));
            }
        }
        float hotbarY = startY - (3.0f * spacing) - (spacing * 0.35f);
        for (int col = 0; col < 9; col++) {
            float lx = (col - 4.0f) * spacing;
            out.add(new PanelSlotLayout(spatialIndex++, col, topInventory, false, lx, hotbarY, 0.0f));
        }
        return spatialIndex;
    }

    private int resolveTopColumns(ScreenProfile profile, Inventory topInventory) {
        return switch (profile.screenType()) {
            case CRAFTING -> (topInventory != null && topInventory.getSize() <= 5) ? 4 : 5;
            case FURNACE -> 3;
            case PLAYER_INVENTORY -> 9;
            default -> Math.max(1, profile.columns());
        };
    }

    private int resolveTopRows(ScreenProfile profile, Inventory topInventory, int cols) {
        return switch (profile.screenType()) {
            case CRAFTING -> (topInventory != null && topInventory.getSize() <= 5) ? 2 : 3;
            case FURNACE -> 3;
            case PLAYER_INVENTORY -> 4;
            default -> {
                if (topInventory != null && topInventory.getSize() > 0) {
                    yield Math.max(1, (int) Math.ceil((double) topInventory.getSize() / cols));
                }
                yield Math.max(1, profile.rows());
            }
        };
    }

    private boolean isResultOnlySlot(SpatialSession session, PanelSlotLayout layout) {
        if (!layout.topInventory()) {
            return false;
        }
        return switch (session.getScreenType()) {
            case FURNACE -> layout.containerSlotIndex() == 2;
            case CRAFTING -> layout.containerSlotIndex() == 0;
            default -> false;
        };
    }

    private void handleResultConsumed(Player player, SpatialSession session, PanelSlotLayout layout) {
        if (!layout.topInventory() || session.getScreenType() != ScreenType.CRAFTING || layout.containerSlotIndex() != 0) {
            return;
        }
        Inventory top = session.getTopInventory();
        if (top == null) {
            return;
        }
        for (int i = 1; i < top.getSize(); i++) {
            ItemStack ingredient = normalizeItem(top.getItem(i));
            if (ingredient != null) {
                int remaining = ingredient.getAmount() - 1;
                top.setItem(i, remaining > 0 ? withAmount(ingredient, remaining) : null);
            }
        }
        refreshCraftingResult(player, top);
    }

    private void refreshCraftingResult(Player player, Inventory topInventory) {
        if (!(topInventory instanceof CraftingInventory crafting) || player == null) {
            return;
        }
        ItemStack[] matrix = crafting.getMatrix();
        Recipe recipe = Bukkit.getCraftingRecipe(matrix, player.getWorld());
        crafting.setResult(recipe != null ? recipe.getResult().clone() : null);
    }

    private ItemStack quickMove(SpatialSession session, PanelSlotLayout sourceLayout, ItemStack sourceItem) {
        ItemStack remaining = sourceItem.clone();
        if (sourceLayout.topInventory()) {
            Inventory bottom = session.getBottomInventory();
            if (bottom != null) {
                return moveIntoRange(bottom, remaining, 0, Math.min(PLAYER_STORAGE_SLOTS, bottom.getSize()) - 1);
            }
            Inventory top = session.getTopInventory();
            if (top != null && session.getScreenType() == ScreenType.PLAYER_INVENTORY) {
                if (sourceLayout.containerSlotIndex() < 9) {
                    return moveIntoRange(top, remaining, 9, Math.min(35, top.getSize() - 1));
                }
                return moveIntoRange(top, remaining, 0, Math.min(8, top.getSize() - 1));
            }
            return remaining;
        }

        Inventory top = session.getTopInventory();
        if (top == null) {
            return remaining;
        }
        if (session.getScreenType() == ScreenType.FURNACE && top instanceof FurnaceInventory furnace) {
            if (furnace.isFuel(remaining)) {
                ItemStack afterFuel = moveIntoRange(top, remaining, 1, 1);
                if (afterFuel == null || afterFuel.getAmount() != remaining.getAmount()) {
                    return afterFuel;
                }
            }
            return moveIntoRange(top, remaining, 0, 0);
        }
        if (session.getScreenType() == ScreenType.CRAFTING) {
            return moveIntoRange(top, remaining, 1, top.getSize() - 1);
        }
        return moveIntoRange(top, remaining, 0, top.getSize() - 1);
    }

    private ItemStack moveIntoRange(Inventory target, ItemStack item, int minSlot, int maxSlot) {
        if (target == null || item == null || minSlot > maxSlot || minSlot < 0 || maxSlot >= target.getSize()) {
            return item;
        }
        ItemStack remaining = item.clone();
        int maxStack = remaining.getMaxStackSize();

        for (int slot = minSlot; slot <= maxSlot && remaining.getAmount() > 0; slot++) {
            ItemStack existing = normalizeItem(target.getItem(slot));
            if (existing != null && existing.isSimilar(remaining) && existing.getAmount() < maxStack) {
                int move = Math.min(maxStack - existing.getAmount(), remaining.getAmount());
                ItemStack updated = existing.clone();
                updated.setAmount(existing.getAmount() + move);
                target.setItem(slot, updated);
                remaining.setAmount(remaining.getAmount() - move);
            }
        }

        for (int slot = minSlot; slot <= maxSlot && remaining.getAmount() > 0; slot++) {
            ItemStack existing = normalizeItem(target.getItem(slot));
            if (existing == null) {
                int move = Math.min(maxStack, remaining.getAmount());
                target.setItem(slot, withAmount(remaining, move));
                remaining.setAmount(remaining.getAmount() - move);
            }
        }

        return remaining.getAmount() > 0 ? remaining : null;
    }

    private void dropFromPlayer(Player player, ItemStack item) {
        if (player == null || item == null || item.getType().isAir()) {
            return;
        }
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) {
            return;
        }
        Vector direction = eye.getDirection().normalize();
        Location spawnLoc = eye.clone().add(direction.clone().multiply(0.45));
        Item dropped = world.dropItem(spawnLoc, item.clone());
        dropped.setVelocity(direction.multiply(0.32));
        dropped.setPickupDelay(40);
    }

    private static ItemStack normalizeItem(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
            return null;
        }
        return item;
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return item != null ? item.clone() : null;
    }

    private static ItemStack withAmount(ItemStack template, int amount) {
        ItemStack copy = template.clone();
        copy.setAmount(amount);
        return copy;
    }
}
