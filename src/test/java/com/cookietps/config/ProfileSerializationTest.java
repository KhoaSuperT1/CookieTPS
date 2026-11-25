package com.cookietps.config;

import com.cookietps.BukkitTestSupport;
import com.cookietps.model.RenderTransform;
import com.cookietps.model.ScreenProfile;
import com.cookietps.model.ScreenType;
import com.cookietps.model.ThirdPersonSettings;
import com.cookietps.model.ViewMode;
import org.bukkit.block.DoubleChest;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileSerializationTest {
    private static final Path BUNDLED_CONFIG = Path.of("src", "main", "resources", "config.yml");
    private static final Path BUNDLED_PROFILES = Path.of("src", "main", "resources", "profiles.yml");

    @Test
    void profileManagerLoadsAllScreenTypesAndRoundtripsYaml(@TempDir Path tempDir) {
        Plugin plugin = BukkitTestSupport.createConfigPluginProxy(tempDir, BUNDLED_CONFIG, BUNDLED_PROFILES);
        ProfileManager manager = new ProfileManager(plugin);

        Map<ScreenType, ScreenProfile> all = manager.getAllProfiles();
        assertEquals(ScreenType.values().length, all.size());
        for (ScreenType type : ScreenType.values()) {
            ScreenProfile loaded = manager.getProfile(type);
            assertNotNull(loaded);
            assertEquals(type, loaded.screenType());
        }

        ScreenProfile originalChest = manager.getProfile(ScreenType.CHEST);
        RenderTransform customFp = new RenderTransform(3.25, 1.45f, 0.25, -0.35, 0.15, 12.5f, -25.0f, 3.0f, 0.65f, 6, 5, 3);
        RenderTransform customTp = new RenderTransform(2.9, 1.2f, 0.6, 0.4, -0.1, -4.0f, 18.0f, 1.5f, -0.35f, 7, 6, 4);
        ThirdPersonSettings customTpSettings = new ThirdPersonSettings(
            3.8, 0.9, -0.8, 22.0f, 11.0f, 1.65, 5, 15.0f, false, false,
            14, 11, 2.95, 0.35, -1.15, 154.0f, 9.5f
        );
        ScreenProfile edited = new ScreenProfile(
            "chest",
            ScreenType.CHEST,
            ViewMode.THIRD_PERSON_FRONT,
            false,
            customFp,
            customTp,
            customTpSettings,
            9,
            3,
            0.31f,
            false,
            false,
            false
        );

        manager.saveProfile(edited);

        ProfileManager reloadedManager = new ProfileManager(plugin);
        ScreenProfile roundtripped = reloadedManager.getProfile(ScreenType.CHEST);
        assertEquals(ViewMode.THIRD_PERSON_FRONT, roundtripped.viewMode());
        assertFalse(roundtripped.enabled());
        assertEquals(0.31f, roundtripped.slotSpacing(), 1.0e-4f);
        assertFalse(roundtripped.showBackground());
        assertFalse(roundtripped.showTooltips());
        assertFalse(roundtripped.includePlayerInventory());
        assertEquals(customFp.distance(), roundtripped.firstPersonTransform().distance(), 1.0e-5);
        assertEquals(customFp.scale(), roundtripped.firstPersonTransform().scale(), 1.0e-4f);
        assertEquals(customFp.curvature(), roundtripped.firstPersonTransform().curvature(), 1.0e-4f);
        assertEquals(customFp.yawOffset(), roundtripped.firstPersonTransform().yawOffset(), 1.0e-4f);
        assertEquals(customTp.curvature(), roundtripped.thirdPersonTransform().curvature(), 1.0e-4f);
        ThirdPersonSettings rtTp = roundtripped.thirdPersonSettings();
        assertEquals(customTpSettings.cameraDistance(), rtTp.cameraDistance(), 1.0e-5);
        assertEquals(customTpSettings.cameraHeightOffset(), rtTp.cameraHeightOffset(), 1.0e-5);
        assertEquals(customTpSettings.cameraSideOffset(), rtTp.cameraSideOffset(), 1.0e-5);
        assertEquals(customTpSettings.cameraYawOffset(), rtTp.cameraYawOffset(), 1.0e-4f);
        assertEquals(customTpSettings.cameraPitchOffset(), rtTp.cameraPitchOffset(), 1.0e-4f);
        assertEquals(customTpSettings.minClearance(), rtTp.minClearance(), 1.0e-5);
        assertEquals(customTpSettings.cameraTeleportDurationTicks(), rtTp.cameraTeleportDurationTicks());
        assertEquals(customTpSettings.avatarBodyYawOffset(), rtTp.avatarBodyYawOffset(), 1.0e-4f);
        assertFalse(rtTp.headTrackHoveredSlot());
        assertFalse(rtTp.fallbackToFirstPersonOnOcclusion());
        assertEquals(customTpSettings.openTransitionTicks(), rtTp.openTransitionTicks());
        assertEquals(customTpSettings.closeTransitionTicks(), rtTp.closeTransitionTicks());
        assertEquals(customTpSettings.frontCameraDistance(), rtTp.frontCameraDistance(), 1.0e-5);
        assertEquals(customTpSettings.frontCameraHeightOffset(), rtTp.frontCameraHeightOffset(), 1.0e-5);
        assertEquals(customTpSettings.frontCameraSideOffset(), rtTp.frontCameraSideOffset(), 1.0e-5);
        assertEquals(customTpSettings.frontCameraYawOffset(), rtTp.frontCameraYawOffset(), 1.0e-4f);
        assertEquals(customTpSettings.frontCameraPitchOffset(), rtTp.frontCameraPitchOffset(), 1.0e-4f);

        reloadedManager.resetProfile(ScreenType.CHEST);
        ScreenProfile reset = reloadedManager.getProfile(ScreenType.CHEST);
        assertEquals(originalChest.viewMode(), reset.viewMode());
        assertEquals(originalChest.enabled(), reset.enabled());
        assertEquals(originalChest.firstPersonTransform().distance(), reset.firstPersonTransform().distance(), 1.0e-5);
        assertEquals(originalChest.firstPersonTransform().curvature(), reset.firstPersonTransform().curvature(), 1.0e-4f);
    }

    @Test
    void screenTypeFromInventoryMatchesSupportedContainers() {
        assertEquals(ScreenType.GENERIC, ScreenType.fromInventory(null));

        Inventory smallChest = new BukkitTestSupport.TestInventoryState(InventoryType.CHEST, 27, null).inventory;
        assertEquals(ScreenType.CHEST, ScreenType.fromInventory(smallChest));

        Inventory largeChest = new BukkitTestSupport.TestInventoryState(InventoryType.CHEST, 54, null).inventory;
        assertEquals(ScreenType.DOUBLE_CHEST, ScreenType.fromInventory(largeChest));

        DoubleChest doubleHolder = new DoubleChest(null);
        Inventory holderDoubleChest = new BukkitTestSupport.TestInventoryState(InventoryType.CHEST, 27, doubleHolder).inventory;
        assertEquals(ScreenType.DOUBLE_CHEST, ScreenType.fromInventory(holderDoubleChest));

        assertEquals(ScreenType.BARREL, ScreenType.fromInventory(
            new BukkitTestSupport.TestInventoryState(InventoryType.BARREL, 27, null).inventory));
        assertEquals(ScreenType.SHULKER_BOX, ScreenType.fromInventory(
            new BukkitTestSupport.TestInventoryState(InventoryType.SHULKER_BOX, 27, null).inventory));
        assertEquals(ScreenType.ENDER_CHEST, ScreenType.fromInventory(
            new BukkitTestSupport.TestInventoryState(InventoryType.ENDER_CHEST, 27, null).inventory));
        assertEquals(ScreenType.CRAFTING, ScreenType.fromInventory(
            new BukkitTestSupport.TestInventoryState(InventoryType.WORKBENCH, 10, null).inventory));
        assertEquals(ScreenType.FURNACE, ScreenType.fromInventory(
            new BukkitTestSupport.TestInventoryState(InventoryType.FURNACE, 3, null).inventory));
        assertEquals(ScreenType.FURNACE, ScreenType.fromInventory(
            new BukkitTestSupport.TestInventoryState(InventoryType.BLAST_FURNACE, 3, null).inventory));
        assertEquals(ScreenType.FURNACE, ScreenType.fromInventory(
            new BukkitTestSupport.TestInventoryState(InventoryType.SMOKER, 3, null).inventory));
        assertEquals(ScreenType.PLAYER_INVENTORY, ScreenType.fromInventory(
            new BukkitTestSupport.TestInventoryState(InventoryType.PLAYER, 41, null).inventory));
        assertEquals(ScreenType.GENERIC, ScreenType.fromInventory(
            new BukkitTestSupport.TestInventoryState(InventoryType.HOPPER, 5, null).inventory));
    }

    @Test
    void configManagerWhitelistAndBlacklistFilteringPersists(@TempDir Path tempDir) {
        Plugin plugin = BukkitTestSupport.createConfigPluginProxy(tempDir, BUNDLED_CONFIG, BUNDLED_PROFILES);
        ConfigManager config = new ConfigManager(plugin);

        assertTrue(config.isPluginEnabled());
        assertTrue(config.isSneakSwapOpensInventory());
        assertTrue(config.isJoinHintEnabled());
        assertFalse(config.isScreenFilterWhitelistMode());
        assertTrue(config.isScreenAllowed(ScreenType.CHEST));
        assertTrue(config.isScreenAllowed(ScreenType.FURNACE));

        config.setSneakSwapOpensInventory(false);
        assertFalse(config.isSneakSwapOpensInventory());

        config.addFilteredScreen(ScreenType.FURNACE);
        assertFalse(config.isScreenAllowed(ScreenType.FURNACE));
        assertTrue(config.isScreenAllowed(ScreenType.CHEST));

        config.setScreenFilterWhitelistMode(true);
        config.setFilteredScreens(List.of(ScreenType.CHEST, ScreenType.BARREL));
        assertTrue(config.isScreenAllowed(ScreenType.CHEST));
        assertTrue(config.isScreenAllowed(ScreenType.BARREL));
        assertFalse(config.isScreenAllowed(ScreenType.ENDER_CHEST));
        assertFalse(config.isScreenAllowed(null));

        ConfigManager reloaded = new ConfigManager(plugin);
        assertFalse(reloaded.isSneakSwapOpensInventory());
        assertTrue(reloaded.isJoinHintEnabled());
        assertTrue(reloaded.isScreenFilterWhitelistMode());
        assertTrue(reloaded.isScreenAllowed(ScreenType.CHEST));
        assertTrue(reloaded.isScreenAllowed(ScreenType.BARREL));
        assertFalse(reloaded.isScreenAllowed(ScreenType.ENDER_CHEST));
    }
}
