package com.cookietps.bridge;

import com.cookietps.BukkitTestSupport;
import com.cookietps.model.PanelSlotLayout;
import com.cookietps.model.ScreenProfile;
import com.cookietps.model.ScreenType;
import com.cookietps.model.SlotClickAction;
import com.cookietps.model.SlotInteractionResult;
import com.cookietps.model.SpatialSession;
import com.cookietps.model.ViewMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryActionHandlerTest {
    private final ContainerBridge bridge = new ContainerBridge();

    @BeforeAll
    static void initServer() {
        BukkitTestSupport.ensureServer();
    }

    @Test
    void buildSlotLayoutsCoversChestCraftingFurnaceAndPlayerInventory() {
        BukkitTestSupport.TestPlayerInventoryState playerInv = new BukkitTestSupport.TestPlayerInventoryState();

        BukkitTestSupport.TestInventoryState chest = new BukkitTestSupport.TestInventoryState(InventoryType.CHEST, 27, null);
        ScreenProfile chestProfile = BukkitTestSupport.defaultProfile(ScreenType.CHEST, true);
        List<PanelSlotLayout> chestLayouts = bridge.buildSlotLayouts(chestProfile, chest.inventory, playerInv.inventory);
        assertEquals(27 + 36 + 1, chestLayouts.size());
        assertTrue(chestLayouts.get(chestLayouts.size() - 1).closeButton());

        BukkitTestSupport.TestInventoryState crafting = new BukkitTestSupport.TestInventoryState(InventoryType.WORKBENCH, 10, null);
        ScreenProfile craftingProfile = BukkitTestSupport.defaultProfile(ScreenType.CRAFTING, true);
        List<PanelSlotLayout> craftLayouts = bridge.buildSlotLayouts(craftingProfile, crafting.inventory, playerInv.inventory);
        assertEquals(10 + 36 + 1, craftLayouts.size());
        assertEquals(0, craftLayouts.get(9).containerSlotIndex());

        BukkitTestSupport.TestInventoryState furnace = new BukkitTestSupport.TestInventoryState(InventoryType.FURNACE, 3, null);
        ScreenProfile furnaceProfile = BukkitTestSupport.defaultProfile(ScreenType.FURNACE, true);
        List<PanelSlotLayout> furnaceLayouts = bridge.buildSlotLayouts(furnaceProfile, furnace.inventory, playerInv.inventory);
        assertEquals(3 + 36 + 1, furnaceLayouts.size());

        ScreenProfile playerProfile = BukkitTestSupport.defaultProfile(ScreenType.PLAYER_INVENTORY, false);
        List<PanelSlotLayout> playerOnly = bridge.buildSlotLayouts(playerProfile, playerInv.inventory, playerInv.inventory);
        assertEquals(36 + 1, playerOnly.size());
    }

    @Test
    void leftClickPickupPlaceMergeAndSwapConservesItems() {
        BukkitTestSupport.TestWorldState worldState = new BukkitTestSupport.TestWorldState();
        BukkitTestSupport.TestPlayerInventoryState playerInv = new BukkitTestSupport.TestPlayerInventoryState();
        Player player = BukkitTestSupport.createPlayerProxy(UUID.randomUUID(), worldState, playerInv);
        BukkitTestSupport.TestInventoryState chest = new BukkitTestSupport.TestInventoryState(InventoryType.CHEST, 27, null);
        chest.inventory.setItem(0, BukkitTestSupport.item(Material.DIAMOND, 20));
        chest.inventory.setItem(1, BukkitTestSupport.item(Material.DIAMOND, 50));
        chest.inventory.setItem(2, BukkitTestSupport.item(Material.EMERALD, 5));

        SpatialSession session = createSession(player, ScreenType.CHEST, chest, playerInv, worldState);

        SlotInteractionResult pickup = bridge.executeClick(player, session, 0, SlotClickAction.LEFT_CLICK);
        assertTrue(pickup.handled());
        assertTrue(pickup.inventoryDirty());
        assertNull(chest.inventory.getItem(0));
        assertEquals(20, session.getVirtualCursor().getAmount());

        SlotInteractionResult merge = bridge.executeClick(player, session, 1, SlotClickAction.LEFT_CLICK);
        assertTrue(merge.handled());
        assertEquals(64, chest.inventory.getItem(1).getAmount());
        assertNotNull(session.getVirtualCursor());
        assertEquals(6, session.getVirtualCursor().getAmount());

        SlotInteractionResult swap = bridge.executeClick(player, session, 2, SlotClickAction.LEFT_CLICK);
        assertTrue(swap.handled());
        assertEquals(Material.DIAMOND, chest.inventory.getItem(2).getType());
        assertEquals(6, chest.inventory.getItem(2).getAmount());
        assertEquals(Material.EMERALD, session.getVirtualCursor().getType());
        assertEquals(5, session.getVirtualCursor().getAmount());

        SlotInteractionResult place = bridge.executeClick(player, session, 3, SlotClickAction.LEFT_CLICK);
        assertTrue(place.handled());
        assertNull(session.getVirtualCursor());
        assertEquals(Material.EMERALD, chest.inventory.getItem(3).getType());
        assertEquals(5, chest.inventory.getItem(3).getAmount());
        assertEquals(75, chest.totalAmount());
    }

    @Test
    void rightClickHalfSplitAndSinglePlaceConservesItems() {
        BukkitTestSupport.TestWorldState worldState = new BukkitTestSupport.TestWorldState();
        BukkitTestSupport.TestPlayerInventoryState playerInv = new BukkitTestSupport.TestPlayerInventoryState();
        Player player = BukkitTestSupport.createPlayerProxy(UUID.randomUUID(), worldState, playerInv);
        BukkitTestSupport.TestInventoryState chest = new BukkitTestSupport.TestInventoryState(InventoryType.CHEST, 27, null);
        chest.inventory.setItem(0, BukkitTestSupport.item(Material.IRON_INGOT, 5));
        chest.inventory.setItem(1, BukkitTestSupport.item(Material.IRON_INGOT, 10));

        SpatialSession session = createSession(player, ScreenType.CHEST, chest, playerInv, worldState);

        bridge.executeClick(player, session, 0, SlotClickAction.RIGHT_CLICK);
        assertEquals(2, chest.inventory.getItem(0).getAmount());
        assertEquals(3, session.getVirtualCursor().getAmount());

        bridge.executeClick(player, session, 2, SlotClickAction.RIGHT_CLICK);
        assertEquals(1, chest.inventory.getItem(2).getAmount());
        assertEquals(2, session.getVirtualCursor().getAmount());

        bridge.executeClick(player, session, 1, SlotClickAction.RIGHT_CLICK);
        assertEquals(11, chest.inventory.getItem(1).getAmount());
        assertEquals(1, session.getVirtualCursor().getAmount());

        bridge.executeClick(player, session, 4, SlotClickAction.RIGHT_CLICK);
        assertNull(session.getVirtualCursor());
        assertEquals(1, chest.inventory.getItem(4).getAmount());
        assertEquals(15, chest.totalAmount());
    }

    @Test
    void shiftClickQuickMoveMovesBetweenTopAndBottomWithoutDuplication() {
        BukkitTestSupport.TestWorldState worldState = new BukkitTestSupport.TestWorldState();
        BukkitTestSupport.TestPlayerInventoryState playerInv = new BukkitTestSupport.TestPlayerInventoryState();
        Player player = BukkitTestSupport.createPlayerProxy(UUID.randomUUID(), worldState, playerInv);
        BukkitTestSupport.TestInventoryState chest = new BukkitTestSupport.TestInventoryState(InventoryType.CHEST, 27, null);

        chest.inventory.setItem(0, BukkitTestSupport.item(Material.GOLD_INGOT, 32));
        playerInv.inventory.setItem(9, BukkitTestSupport.item(Material.GOLD_INGOT, 50));

        SpatialSession session = createSession(player, ScreenType.CHEST, chest, playerInv, worldState);

        SlotInteractionResult toPlayer = bridge.executeClick(player, session, 0, SlotClickAction.SHIFT_LEFT_CLICK);
        assertTrue(toPlayer.handled());
        assertTrue(toPlayer.inventoryDirty());
        assertNull(chest.inventory.getItem(0));
        assertEquals(64, playerInv.inventory.getItem(9).getAmount());
        assertEquals(18, playerInv.inventory.getItem(0).getAmount());
        assertEquals(82, chest.totalAmount() + playerInv.totalAmount());

        int bottomSpatialIdx = findSpatialIndex(session.getSlotLayouts(), false, 0);
        SlotInteractionResult toChest = bridge.executeClick(player, session, bottomSpatialIdx, SlotClickAction.SHIFT_LEFT_CLICK);
        assertTrue(toChest.handled());
        assertNull(playerInv.inventory.getItem(0));
        assertEquals(18, chest.inventory.getItem(0).getAmount());
        assertEquals(82, chest.totalAmount() + playerInv.totalAmount());
    }

    @Test
    void dropCloseAndReturnVirtualCursorConserveAllItems() {
        BukkitTestSupport.TestWorldState worldState = new BukkitTestSupport.TestWorldState();
        BukkitTestSupport.TestPlayerInventoryState playerInv = new BukkitTestSupport.TestPlayerInventoryState();
        Player player = BukkitTestSupport.createPlayerProxy(UUID.randomUUID(), worldState, playerInv);
        BukkitTestSupport.TestInventoryState chest = new BukkitTestSupport.TestInventoryState(InventoryType.CHEST, 27, null);
        chest.inventory.setItem(0, BukkitTestSupport.item(Material.COAL, 10));
        chest.inventory.setItem(1, BukkitTestSupport.item(Material.APPLE, 4));

        SpatialSession session = createSession(player, ScreenType.CHEST, chest, playerInv, worldState);

        bridge.executeClick(player, session, 0, SlotClickAction.DROP_ONE);
        assertEquals(9, chest.inventory.getItem(0).getAmount());
        assertEquals(1, droppedTotal(worldState.droppedItems));

        bridge.executeClick(player, session, 0, SlotClickAction.DROP_ALL);
        assertNull(chest.inventory.getItem(0));
        assertEquals(10, droppedTotal(worldState.droppedItems));

        bridge.executeClick(player, session, 1, SlotClickAction.LEFT_CLICK);
        assertEquals(4, session.getVirtualCursor().getAmount());

        int closeIdx = session.getSlotLayouts().size() - 1;
        SlotInteractionResult closeClick = bridge.executeClick(player, session, closeIdx, SlotClickAction.LEFT_CLICK);
        assertTrue(closeClick.handled());
        assertTrue(closeClick.shouldClose());

        SlotInteractionResult closeAction = bridge.executeClick(player, session, -1, SlotClickAction.CLOSE);
        assertTrue(closeAction.shouldClose());

        bridge.returnVirtualCursor(player, session);
        assertNull(session.getVirtualCursor());
        assertEquals(4, playerInv.totalAmount());
        assertEquals(14, chest.totalAmount() + playerInv.totalAmount() + droppedTotal(worldState.droppedItems));
    }

    private SpatialSession createSession(
        Player player,
        ScreenType type,
        BukkitTestSupport.TestInventoryState top,
        BukkitTestSupport.TestPlayerInventoryState bottom,
        BukkitTestSupport.TestWorldState worldState
    ) {
        ScreenProfile profile = BukkitTestSupport.defaultProfile(type, true);
        SpatialSession session = new SpatialSession(
            player.getUniqueId(),
            type,
            profile,
            ViewMode.FIRST_PERSON,
            top.inventory,
            bottom.inventory,
            new Location(worldState.world, 0.0, 65.62, 0.0),
            false
        );
        session.setSlotLayouts(bridge.buildSlotLayouts(profile, top.inventory, bottom.inventory));
        return session;
    }

    private static int findSpatialIndex(List<PanelSlotLayout> layouts, boolean top, int containerSlot) {
        for (PanelSlotLayout layout : layouts) {
            if (!layout.closeButton() && layout.topInventory() == top && layout.containerSlotIndex() == containerSlot) {
                return layout.spatialIndex();
            }
        }
        return -1;
    }

    private static int droppedTotal(List<ItemStack> dropped) {
        int sum = 0;
        for (ItemStack item : dropped) {
            if (item != null) {
                sum += item.getAmount();
            }
        }
        return sum;
    }
}
