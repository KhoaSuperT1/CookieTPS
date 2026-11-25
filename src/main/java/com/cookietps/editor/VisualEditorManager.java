package com.cookietps.editor;

import com.cookietps.CookieTPSPlugin;
import com.cookietps.api.AdminEditorController;
import com.cookietps.api.InputHandler;
import com.cookietps.api.SpatialRenderer;
import com.cookietps.api.ThirdPersonCameraController;
import com.cookietps.model.RenderTransform;
import com.cookietps.model.ScreenProfile;
import com.cookietps.model.ScreenType;
import com.cookietps.model.SpatialSession;
import com.cookietps.model.ThirdPersonSettings;
import com.cookietps.model.ViewMode;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VisualEditorManager implements AdminEditorController, Listener {
    public static final String ADMIN_PERMISSION = "cookietps.admin";

    private static final int SLOT_DISTANCE = 0;
    private static final int SLOT_SCALE = 1;
    private static final int SLOT_CURVATURE = 2;
    private static final int SLOT_OFFSET = 3;
    private static final int SLOT_ROTATION = 4;
    private static final int SLOT_MODE = 5;
    private static final int SLOT_TP_CAMERA = 6;
    private static final int SLOT_MENU = 7;
    private static final int SLOT_SAVE_EXIT = 8;

    private static final int MENU_PRESET_FLAT = 1;
    private static final int MENU_PRESET_CURVED = 2;
    private static final int MENU_PRESET_COMPACT = 3;
    private static final int MENU_PRESET_SHOWCASE = 4;
    private static final int MENU_PREVIEW_MODE = 6;
    private static final int MENU_PROFILE_VIEW_MODE = 7;
    private static final int MENU_TOGGLE_BACKGROUND = 10;
    private static final int MENU_TOGGLE_TOOLTIPS = 11;
    private static final int MENU_TOGGLE_PLAYER_INV = 12;
    private static final int MENU_TOGGLE_WALL_FALLBACK = 13;
    private static final int MENU_TOGGLE_ENABLED = 14;
    private static final int MENU_SWITCH_SCREEN = 16;
    private static final int MENU_BACK = 18;
    private static final int MENU_RESET_PROFILE = 21;
    private static final int MENU_DISCARD_EXIT = 23;
    private static final int MENU_SAVE_ONLY = 25;
    private static final int MENU_SAVE_EXIT = 26;

    private final CookieTPSPlugin plugin;
    private final NamespacedKey toolSlotKey;
    private final Path backupDir;
    private final Map<UUID, EditorState> activeEditors;
    private int hudTaskId;

    public VisualEditorManager(CookieTPSPlugin plugin) {
        this.plugin = plugin;
        this.toolSlotKey = new NamespacedKey(plugin, "editor_tool_slot");
        this.backupDir = plugin.getDataFolder().toPath().resolve("editor-backups");
        this.activeEditors = new ConcurrentHashMap<>();
        this.hudTaskId = -1;
        ensureBackupDirectory();
    }

    @Override
    public boolean startEditor(Player admin, ScreenType screenType) {
        if (admin == null || !admin.hasPermission(ADMIN_PERMISSION)) {
            if (admin != null) {
                admin.sendMessage(prefixed("You lack permission (" + ADMIN_PERMISSION + ") to use the Visual Editor.", NamedTextColor.RED));
            }
            return false;
        }

        ScreenType targetType = screenType != null ? screenType : ScreenType.CHEST;
        UUID uuid = admin.getUniqueId();
        EditorState existing = activeEditors.get(uuid);
        if (existing != null) {
            if (existing.screenType == targetType) {
                ensurePreviewSession(admin, existing);
                updateHud(admin, existing);
                sendChatControlPanel(admin, existing);
                return true;
            }
            switchEditedScreenType(admin, existing, targetType);
            updateHud(admin, existing);
            sendChatControlPanel(admin, existing);
            return true;
        }

        restoreOrphanedDiskBackup(admin);

        InputHandler inputHandler = plugin.getInputHandler();
        if (inputHandler != null && inputHandler.hasActiveSession(uuid)) {
            inputHandler.closeSession(admin, true);
        }

        ScreenProfile baseProfile = EditorParameters.clampProfile(plugin.getProfileManager().getProfile(targetType));
        PlayerInventory inv = admin.getInventory();
        ItemStack[] clonedBackup = cloneContents(inv.getContents());
        int heldSlot = inv.getHeldItemSlot();
        saveDiskBackup(uuid, clonedBackup, heldSlot);

        BossBar bossBar = BossBar.bossBar(
            Component.text("[Editor] " + targetType.name(), NamedTextColor.GOLD),
            1.0f,
            BossBar.Color.BLUE,
            BossBar.Overlay.PROGRESS
        );

        EditorState state = new EditorState(
            uuid,
            targetType,
            baseProfile,
            ViewMode.FIRST_PERSON,
            0,
            "distance",
            bossBar,
            clonedBackup,
            heldSlot
        );
        activeEditors.put(uuid, state);

        equipHotbarTools(admin);
        admin.showBossBar(bossBar);
        ensureHudTaskRunning();
        ensurePreviewSession(admin, state);
        updateHud(admin, state);
        sendEditorIntro(admin, state);
        sendChatControlPanel(admin, state);
        playTone(admin, "minecraft:block.note_block.pling", 0.6f, 1.3f);
        return true;
    }

    @Override
    public void applyTransformDelta(Player admin, ScreenProfile updatedProfile) {
        if (admin == null || updatedProfile == null || !admin.hasPermission(ADMIN_PERMISSION)) {
            return;
        }
        EditorState state = activeEditors.get(admin.getUniqueId());
        if (state == null) {
            return;
        }

        ScreenProfile clamped = EditorParameters.clampProfile(updatedProfile);
        state.workingProfile = clamped;

        SpatialSession session = ensurePreviewSession(admin, state);
        if (session != null) {
            populateSessionOrigins(admin, state, session, state.previewMode);
            session.setProfile(clamped);
            session.setActiveMode(state.previewMode);
            SpatialRenderer renderer = plugin.getSpatialRenderer();
            if (renderer != null) {
                renderer.refreshTransform(admin, session, clamped);
                if (state.previewMode.isThirdPersonFamily()) {
                    ThirdPersonCameraController camera = plugin.getCameraController();
                    if (camera != null && camera.isInThirdPerson(admin.getUniqueId())) {
                        camera.updateCameraAndAvatar(
                            admin,
                            session,
                            resolveMouseHeadTarget(admin, session, renderer)
                        );
                    }
                }
            }
        }
        updateHud(admin, state);
    }

    @Override
    public void switchPreviewMode(Player admin, ViewMode mode) {
        if (admin == null || !admin.hasPermission(ADMIN_PERMISSION)) {
            return;
        }
        EditorState state = activeEditors.get(admin.getUniqueId());
        if (state == null) {
            return;
        }

        ViewMode targetMode = normalizePreviewMode(mode);
        SpatialSession session = ensurePreviewSession(admin, state);
        if (session == null) {
            return;
        }

        ThirdPersonCameraController camera = plugin.getCameraController();
        if (targetMode.isThirdPersonFamily()) {
            if (camera != null && camera.isInThirdPerson(admin.getUniqueId()) && state.previewMode != targetMode) {
                camera.exitThirdPerson(admin, true);
            }

            state.previewMode = targetMode;
            session = ensurePreviewSession(admin, state);
            if (session == null) {
                return;
            }
            populateSessionOrigins(admin, state, session, targetMode);
            session.setActiveMode(targetMode);
            session.setProfile(state.workingProfile);

            boolean entered = camera != null
                && (camera.isInThirdPerson(admin.getUniqueId()) || camera.enterThirdPerson(admin, session));
            if (!entered) {
                state.previewMode = ViewMode.FIRST_PERSON;
                populateSessionOrigins(admin, state, session, ViewMode.FIRST_PERSON);
                session.setActiveMode(ViewMode.FIRST_PERSON);
                refreshSessionTransform(admin, session, state.workingProfile);
                admin.sendMessage(prefixed(
                    "Third-Person preview unavailable here (wall clearance or camera module inactive). Staying in First-Person.",
                    NamedTextColor.YELLOW
                ));
                updateHud(admin, state);
                return;
            }

            refreshSessionTransform(admin, session, state.workingProfile);
            updateActiveParamForMode(state, targetMode);
            admin.sendMessage(prefixed(
                "Switched to " + targetMode.name() + " preview. Press Sneak or click/run /cookietps editor mode first to return to First-Person.",
                NamedTextColor.LIGHT_PURPLE
            ));
            updateHud(admin, state);
            sendChatControlPanel(admin, state);
            playTone(admin, "minecraft:entity.enderman.teleport", 0.45f, 1.35f);
            return;
        }

        if (camera != null && camera.isInThirdPerson(admin.getUniqueId())) {
            camera.exitThirdPerson(admin, true);
        }
        finishSwitchToFirstPerson(admin, state);
    }

    private void finishSwitchToFirstPerson(Player admin, EditorState state) {
        if (!admin.isOnline() || activeEditors.get(admin.getUniqueId()) != state) {
            return;
        }
        state.previewMode = ViewMode.FIRST_PERSON;
        SpatialSession session = ensurePreviewSession(admin, state);
        if (session != null) {
            populateSessionOrigins(admin, state, session, ViewMode.FIRST_PERSON);
            session.setActiveMode(ViewMode.FIRST_PERSON);
            session.setProfile(state.workingProfile);
            refreshSessionTransform(admin, session, state.workingProfile);
        }
        updateActiveParamForMode(state, ViewMode.FIRST_PERSON);
        if (admin.getInventory().getHeldItemSlot() != state.activeHotbarSlot) {
            admin.getInventory().setHeldItemSlot(state.activeHotbarSlot);
        }
        admin.sendMessage(prefixed("Switched to FIRST_PERSON preview.", NamedTextColor.AQUA));
        updateHud(admin, state);
        sendChatControlPanel(admin, state);
        playTone(admin, "minecraft:entity.enderman.teleport", 0.45f, 1.35f);
    }

    @Override
    public void openQuickSettingsMenu(Player admin) {
        if (admin == null || !admin.hasPermission(ADMIN_PERMISSION)) {
            return;
        }
        EditorState state = activeEditors.get(admin.getUniqueId());
        if (state == null) {
            admin.sendMessage(prefixed("Start the Visual Editor first with /cookietps editor [screen].", NamedTextColor.RED));
            return;
        }

        EditorMenuHolder holder = new EditorMenuHolder(admin.getUniqueId(), state.screenType);
        Inventory menu = Bukkit.createInventory(
            holder,
            27,
            Component.text("CookieTPS Editor: " + state.screenType.name(), NamedTextColor.DARK_AQUA, TextDecoration.BOLD)
        );
        holder.setInventory(menu);
        populateQuickSettingsMenu(menu, state);
        admin.openInventory(menu);
        playTone(admin, "minecraft:block.chest.open", 0.5f, 1.2f);
    }

    @Override
    public void saveActiveProfile(Player admin) {
        if (admin == null || !admin.hasPermission(ADMIN_PERMISSION)) {
            return;
        }
        EditorState state = activeEditors.get(admin.getUniqueId());
        if (state == null) {
            return;
        }
        ScreenProfile clamped = EditorParameters.clampProfile(state.workingProfile);
        state.workingProfile = clamped;
        plugin.getProfileManager().saveProfile(clamped);
        admin.sendMessage(prefixed("Saved profile " + state.screenType.name() + " to profiles.yml.", NamedTextColor.GREEN));
        playTone(admin, "minecraft:entity.experience_orb.pickup", 0.6f, 1.4f);
    }

    @Override
    public void resetActiveProfile(Player admin) {
        if (admin == null || !admin.hasPermission(ADMIN_PERMISSION)) {
            return;
        }
        EditorState state = activeEditors.get(admin.getUniqueId());
        if (state == null) {
            return;
        }
        plugin.getProfileManager().resetProfile(state.screenType);
        ScreenProfile reset = EditorParameters.clampProfile(plugin.getProfileManager().getProfile(state.screenType));
        applyTransformDelta(admin, reset);
        sendChatControlPanel(admin, state);
        admin.sendMessage(prefixed("Reset profile " + state.screenType.name() + " to default values.", NamedTextColor.YELLOW));
        playTone(admin, "minecraft:block.anvil.use", 0.4f, 1.2f);
    }

    @Override
    public void exitEditor(Player admin, boolean saveChanges) {
        if (admin == null) {
            return;
        }
        UUID uuid = admin.getUniqueId();
        EditorState state = activeEditors.remove(uuid);
        if (state == null) {
            return;
        }

        if (saveChanges && admin.hasPermission(ADMIN_PERMISSION)) {
            ScreenProfile clamped = EditorParameters.clampProfile(state.workingProfile);
            plugin.getProfileManager().saveProfile(clamped);
        }

        cleanupEditorSession(admin, state);
        if (saveChanges) {
            admin.sendMessage(prefixed("Saved " + state.screenType.name() + " and exited Visual Editor.", NamedTextColor.GREEN));
            playTone(admin, "minecraft:entity.player.levelup", 0.5f, 1.5f);
        } else {
            admin.sendMessage(prefixed("Discarded unsaved changes and exited Visual Editor.", NamedTextColor.YELLOW));
            playTone(admin, "minecraft:block.note_block.bass", 0.5f, 0.9f);
        }

        if (activeEditors.isEmpty()) {
            stopHudTask();
        }
    }

    @Override
    public boolean isEditing(UUID playerUuid) {
        return playerUuid != null && activeEditors.containsKey(playerUuid);
    }

    @Override
    public void shutdownAll() {
        stopHudTask();
        List<UUID> uuids = new ArrayList<>(activeEditors.keySet());
        for (UUID uuid : uuids) {
            EditorState state = activeEditors.remove(uuid);
            if (state == null) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                cleanupEditorSession(player, state);
            }
        }
    }

    public boolean setParameter(Player admin, String param, double value) {
        if (admin == null || !admin.hasPermission(ADMIN_PERMISSION)) {
            return false;
        }
        EditorState state = activeEditors.get(admin.getUniqueId());
        if (state == null) {
            admin.sendMessage(prefixed("You are not in the Visual Editor. Run /cookietps editor first.", NamedTextColor.RED));
            return false;
        }
        String canonical = EditorParameters.normalizeParameterName(param);
        if (canonical == null || !Double.isFinite(value)) {
            admin.sendMessage(prefixed("Invalid parameter or numeric value.", NamedTextColor.RED));
            return false;
        }

        ScreenProfile updated = EditorParameters.applyParameter(state.workingProfile, state.previewMode, canonical, value);
        state.activeParam = canonical;
        applyTransformDelta(admin, updated);
        double resulting = EditorParameters.getParameterValue(updated, state.previewMode, canonical);
        admin.sendMessage(prefixed(
            canonical + " = " + formatNumber(resulting) + " (" + state.previewMode.name() + ")",
            NamedTextColor.AQUA
        ));
        sendChatControlPanel(admin, state);
        playTone(admin, "minecraft:ui.button.click", 0.4f, 1.25f);
        return true;
    }

    public boolean adjustParameter(Player admin, String param, double delta) {
        if (admin == null || !admin.hasPermission(ADMIN_PERMISSION)) {
            return false;
        }
        EditorState state = activeEditors.get(admin.getUniqueId());
        if (state == null) {
            admin.sendMessage(prefixed("You are not in the Visual Editor. Run /cookietps editor first.", NamedTextColor.RED));
            return false;
        }
        String canonical = EditorParameters.normalizeParameterName(param);
        if (canonical == null || !Double.isFinite(delta)) {
            admin.sendMessage(prefixed("Invalid parameter or delta.", NamedTextColor.RED));
            return false;
        }

        ScreenProfile updated = EditorParameters.adjustParameter(state.workingProfile, state.previewMode, canonical, delta);
        state.activeParam = canonical;
        applyTransformDelta(admin, updated);
        sendChatControlPanel(admin, state);
        playTone(admin, "minecraft:ui.button.click", 0.35f, delta >= 0 ? 1.35f : 1.05f);
        return true;
    }

    public ScreenType getEditingScreenType(UUID playerUuid) {
        EditorState state = playerUuid != null ? activeEditors.get(playerUuid) : null;
        return state != null ? state.screenType : null;
    }

    public ViewMode getPreviewMode(UUID playerUuid) {
        EditorState state = playerUuid != null ? activeEditors.get(playerUuid) : null;
        return state != null ? state.previewMode : ViewMode.FIRST_PERSON;
    }

    public ScreenProfile getWorkingProfile(UUID playerUuid) {
        EditorState state = playerUuid != null ? activeEditors.get(playerUuid) : null;
        return state != null ? state.workingProfile : null;
    }

    public void sendChatPanel(Player admin) {
        if (admin == null || !admin.hasPermission(ADMIN_PERMISSION)) {
            return;
        }
        EditorState state = activeEditors.get(admin.getUniqueId());
        if (state != null) {
            sendChatControlPanel(admin, state);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        EditorState state = activeEditors.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action == Action.PHYSICAL) {
            return;
        }
        boolean leftClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!leftClick && !rightClick) {
            return;
        }
        handleHotbarToolClick(player, state, player.getInventory().getHeldItemSlot(), leftClick, player.isSneaking());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        EditorState state = activeEditors.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            handleHotbarToolClick(player, state, player.getInventory().getHeldItemSlot(), false, player.isSneaking());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        EditorState state = activeEditors.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        event.setCancelled(true);
        handleHotbarToolClick(player, state, player.getInventory().getHeldItemSlot(), true, player.isSneaking());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        EditorState state = activeEditors.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        int newSlot = event.getNewSlot();
        state.activeHotbarSlot = newSlot;
        String slotParam = primaryParamForSlot(newSlot, state.previewMode);
        if (slotParam != null) {
            state.activeParam = slotParam;
            sendChatControlPanel(player, state);
        }
        updateHud(player, state);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (isEditing(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (isEditing(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory topInventory = event.getInventory();
        if (topInventory.getHolder(false) instanceof EditorMenuHolder holder) {
            event.setCancelled(true);
            if (!player.hasPermission(ADMIN_PERMISSION) || !holder.getAdminUuid().equals(player.getUniqueId())) {
                player.closeInventory();
                return;
            }
            EditorState state = activeEditors.get(player.getUniqueId());
            if (state == null) {
                player.closeInventory();
                return;
            }
            Inventory clicked = event.getClickedInventory();
            if (clicked != null && clicked.equals(topInventory)) {
                handleQuickMenuClick(player, state, topInventory, event.getSlot(), event.isRightClick());
            }
            return;
        }
        if (isEditing(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        EditorState state = activeEditors.get(uuid);
        if (state == null || !state.previewMode.isThirdPersonFamily()) {
            return;
        }
        ThirdPersonCameraController camera = plugin.getCameraController();
        if (camera == null || !camera.isInThirdPerson(uuid)) {
            return;
        }
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            EditorState current = activeEditors.get(uuid);
            if (current != null && current.previewMode.isThirdPersonFamily()) {
                switchPreviewMode(player, ViewMode.FIRST_PERSON);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        EditorState state = activeEditors.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        cleanupEditorSession(player, state);
        if (activeEditors.isEmpty()) {
            stopHudTask();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!isEditing(player.getUniqueId())) {
            restoreOrphanedDiskBackup(player);
        }
    }

    private void handleHotbarToolClick(
        Player admin,
        EditorState state,
        int slot,
        boolean leftClick,
        boolean sneaking
    ) {
        if (!admin.hasPermission(ADMIN_PERMISSION)) {
            exitEditor(admin, false);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - state.lastClickMillis < 40L) {
            return;
        }
        state.lastClickMillis = now;
        state.activeHotbarSlot = slot;

        double sign = leftClick ? -1.0 : 1.0;
        switch (slot) {
            case SLOT_DISTANCE -> {
                double step = sneaking ? 0.02 : 0.10;
                adjustParameter(admin, "distance", sign * step);
            }
            case SLOT_SCALE -> {
                if (sneaking) {
                    adjustParameter(admin, "spacing", sign * 0.02);
                } else {
                    adjustParameter(admin, "scale", sign * 0.05);
                }
            }
            case SLOT_CURVATURE -> {
                if (sneaking && !leftClick) {
                    setParameter(admin, "curvature", 0.0);
                } else {
                    double step = sneaking ? 0.01 : 0.05;
                    adjustParameter(admin, "curvature", sign * step);
                }
            }
            case SLOT_OFFSET -> {
                String param = sneaking ? "offset-y" : "offset-x";
                adjustParameter(admin, param, sign * 0.05);
            }
            case SLOT_ROTATION -> {
                String param = sneaking ? "pitch" : "yaw";
                adjustParameter(admin, param, sign * 5.0);
            }
            case SLOT_MODE -> switchPreviewMode(admin, nextPreviewMode(state.previewMode));
            case SLOT_TP_CAMERA -> {
                boolean front = state.previewMode == ViewMode.THIRD_PERSON_FRONT;
                String param = front
                    ? (sneaking ? "front-cam-height" : "front-cam-distance")
                    : (sneaking ? "cam-height" : "cam-distance");
                adjustParameter(admin, param, sign * 0.10);
            }
            case SLOT_MENU -> openQuickSettingsMenu(admin);
            case SLOT_SAVE_EXIT -> exitEditor(admin, !sneaking);
            default -> {
            }
        }
    }

    private void handleQuickMenuClick(
        Player admin,
        EditorState state,
        Inventory menu,
        int slot,
        boolean rightClick
    ) {
        switch (slot) {
            case MENU_PRESET_FLAT -> {
                applyTransformDelta(admin, EditorParameters.applyFlatPreset(state.workingProfile, state.previewMode));
                admin.sendMessage(prefixed("Applied Flat preset.", NamedTextColor.AQUA));
            }
            case MENU_PRESET_CURVED -> {
                applyTransformDelta(admin, EditorParameters.applyCurvedPreset(state.workingProfile, state.previewMode));
                admin.sendMessage(prefixed("Applied Curved preset.", NamedTextColor.AQUA));
            }
            case MENU_PRESET_COMPACT -> {
                applyTransformDelta(admin, EditorParameters.applyCompactPreset(state.workingProfile, state.previewMode));
                admin.sendMessage(prefixed("Applied Compact preset.", NamedTextColor.AQUA));
            }
            case MENU_PRESET_SHOWCASE -> {
                applyTransformDelta(admin, EditorParameters.applyShowcasePreset(state.workingProfile, state.previewMode));
                admin.sendMessage(prefixed("Applied Showcase preset.", NamedTextColor.AQUA));
            }
            case MENU_PREVIEW_MODE -> {
                ViewMode next = nextPreviewMode(state.previewMode);
                admin.closeInventory();
                switchPreviewMode(admin, next);
                return;
            }
            case MENU_PROFILE_VIEW_MODE -> {
                applyTransformDelta(admin, EditorParameters.cycleViewMode(state.workingProfile));
            }
            case MENU_TOGGLE_BACKGROUND -> {
                applyTransformDelta(admin, state.workingProfile.withShowBackground(!state.workingProfile.showBackground()));
            }
            case MENU_TOGGLE_TOOLTIPS -> {
                applyTransformDelta(admin, state.workingProfile.withShowTooltips(!state.workingProfile.showTooltips()));
            }
            case MENU_TOGGLE_PLAYER_INV -> {
                applyTransformDelta(admin, state.workingProfile.withIncludePlayerInventory(!state.workingProfile.includePlayerInventory()));
            }
            case MENU_TOGGLE_WALL_FALLBACK -> {
                applyTransformDelta(admin, EditorParameters.toggleFallbackOnOcclusion(state.workingProfile));
            }
            case MENU_TOGGLE_ENABLED -> {
                applyTransformDelta(admin, state.workingProfile.withEnabled(!state.workingProfile.enabled()));
            }
            case MENU_SWITCH_SCREEN -> {
                ScreenType[] types = ScreenType.values();
                int idx = state.screenType.ordinal();
                int nextIdx = rightClick
                    ? (idx - 1 + types.length) % types.length
                    : (idx + 1) % types.length;
                switchEditedScreenType(admin, state, types[nextIdx]);
                updateHud(admin, state);
                admin.closeInventory();
                openQuickSettingsMenu(admin);
                return;
            }
            case MENU_BACK -> {
                admin.closeInventory();
                playTone(admin, "minecraft:ui.button.click", 0.45f, 1.0f);
                return;
            }
            case MENU_RESET_PROFILE -> resetActiveProfile(admin);
            case MENU_DISCARD_EXIT -> {
                admin.closeInventory();
                exitEditor(admin, false);
                return;
            }
            case MENU_SAVE_ONLY -> saveActiveProfile(admin);
            case MENU_SAVE_EXIT -> {
                admin.closeInventory();
                exitEditor(admin, true);
                return;
            }
            default -> {
                return;
            }
        }
        populateQuickSettingsMenu(menu, state);
        playTone(admin, "minecraft:ui.button.click", 0.45f, 1.25f);
    }

    private void switchEditedScreenType(Player admin, EditorState state, ScreenType nextType) {
        if (nextType == null || nextType == state.screenType) {
            return;
        }
        ThirdPersonCameraController camera = plugin.getCameraController();
        if (state.previewMode.isThirdPersonFamily() && camera != null && camera.isInThirdPerson(admin.getUniqueId())) {
            camera.exitThirdPerson(admin, true);
        }
        InputHandler inputHandler = plugin.getInputHandler();
        if (inputHandler != null && inputHandler.hasActiveSession(admin.getUniqueId())) {
            inputHandler.closeSession(admin, true);
        }

        state.screenType = nextType;
        state.workingProfile = EditorParameters.clampProfile(plugin.getProfileManager().getProfile(nextType));
        state.previewInventory = null;
        SpatialSession session = ensurePreviewSession(admin, state);
        if (session != null && state.previewMode.isThirdPersonFamily() && camera != null) {
            if (!camera.enterThirdPerson(admin, session)) {
                state.previewMode = ViewMode.FIRST_PERSON;
                populateSessionOrigins(admin, state, session, ViewMode.FIRST_PERSON);
                session.setActiveMode(ViewMode.FIRST_PERSON);
            }
        }
        if (session != null) {
            refreshSessionTransform(admin, session, state.workingProfile);
        }
    }

    private SpatialSession ensurePreviewSession(Player admin, EditorState state) {
        InputHandler inputHandler = plugin.getInputHandler();
        if (inputHandler == null) {
            return null;
        }
        SpatialSession session = inputHandler.getSession(admin.getUniqueId());
        if (session == null || !session.isEditorPreview() || session.getScreenType() != state.screenType) {
            if (session != null) {
                inputHandler.closeSession(admin, true);
            }
            if (state.previewInventory == null) {
                state.previewInventory = createPreviewInventory(state.screenType);
            }
            inputHandler.startSession(admin, state.previewInventory, state.screenType, true);
            session = inputHandler.getSession(admin.getUniqueId());
            ThirdPersonCameraController camera = plugin.getCameraController();
            if (!state.previewMode.isThirdPersonFamily() && camera != null && camera.isInThirdPerson(admin.getUniqueId())) {
                camera.exitThirdPerson(admin, true);
            }
        }
        if (session != null) {
            populateSessionOrigins(admin, state, session, state.previewMode);
            session.setProfile(state.workingProfile);
            session.setActiveMode(state.previewMode);
        }
        return session;
    }

    private void populateSessionOrigins(Player admin, EditorState state, SpatialSession session, ViewMode mode) {
        if (admin == null || state == null || session == null) {
            return;
        }
        ThirdPersonCameraController camera = plugin.getCameraController();
        boolean mounted = camera != null && camera.isInThirdPerson(admin.getUniqueId());

        Location feetOrigin;
        Location eyeOrigin;
        if (mounted && state.savedFeetOrigin != null && state.savedEyeOrigin != null) {
            feetOrigin = state.savedFeetOrigin.clone();
            eyeOrigin = state.savedEyeOrigin.clone();
        } else if (mounted && session.getFeetOrigin() != null && session.getEyeOrigin() != null) {
            feetOrigin = session.getFeetOrigin();
            eyeOrigin = session.getEyeOrigin();
        } else {
            feetOrigin = admin.getLocation().clone();
            feetOrigin.setPitch(0.0f);
            eyeOrigin = admin.getEyeLocation().clone();
            state.savedFeetOrigin = feetOrigin.clone();
            state.savedEyeOrigin = eyeOrigin.clone();
        }

        Location anchorOrigin = eyeOrigin.clone();
        if (mode != null && mode.isThirdPersonFamily()) {
            anchorOrigin.setPitch(0.0f);
        }
        session.setFeetOrigin(feetOrigin);
        session.setEyeOrigin(eyeOrigin);
        session.setAnchorOrigin(anchorOrigin);
    }

    private Location resolveMouseHeadTarget(Player admin, SpatialSession session, SpatialRenderer renderer) {
        Location target = renderer.raycastMouseWorldTarget(admin, session);
        if (target != null) {
            return target;
        }
        return renderer.getSlotWorldLocation(session, session.getHoveredSlot());
    }

    private void refreshSessionTransform(Player admin, SpatialSession session, ScreenProfile profile) {
        SpatialRenderer renderer = plugin.getSpatialRenderer();
        if (renderer != null && session != null) {
            renderer.refreshTransform(admin, session, profile);
        }
    }

    private void cleanupEditorSession(Player admin, EditorState state) {
        UUID uuid = admin.getUniqueId();
        ThirdPersonCameraController camera = plugin.getCameraController();
        if (camera != null && camera.isInThirdPerson(uuid)) {
            camera.exitThirdPerson(admin, true);
        }

        InputHandler inputHandler = plugin.getInputHandler();
        if (inputHandler != null && inputHandler.hasActiveSession(uuid)) {
            inputHandler.closeSession(admin, true);
        }

        if (admin.getOpenInventory().getTopInventory().getHolder(false) instanceof EditorMenuHolder) {
            admin.closeInventory();
        }

        admin.hideBossBar(state.bossBar);
        admin.sendActionBar(Component.empty());

        PlayerInventory inv = admin.getInventory();
        inv.setContents(cloneContents(state.backedUpContents));
        int heldSlot = Math.max(0, Math.min(8, state.backedUpHeldSlot));
        inv.setHeldItemSlot(heldSlot);
        admin.updateInventory();
        deleteDiskBackup(uuid);
    }

    private void equipHotbarTools(Player admin) {
        PlayerInventory inv = admin.getInventory();
        inv.clear();
        inv.setItem(SLOT_DISTANCE, createToolItem(
            SLOT_DISTANCE,
            Material.COMPASS,
            Component.text("Distance", NamedTextColor.AQUA, TextDecoration.BOLD),
            List.of(
                lore("LMB: -0.10 | RMB: +0.10"),
                lore("Shift: fine step (0.02)")
            )
        ));
        inv.setItem(SLOT_SCALE, createToolItem(
            SLOT_SCALE,
            Material.SPYGLASS,
            Component.text("Scale & Spacing", NamedTextColor.GREEN, TextDecoration.BOLD),
            List.of(
                lore("LMB/RMB: Scale (-/+ 0.05)"),
                lore("Shift+LMB/RMB: Slot Spacing (-/+ 0.02)")
            )
        ));
        inv.setItem(SLOT_CURVATURE, createToolItem(
            SLOT_CURVATURE,
            Material.BOW,
            Component.text("Curvature", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
            List.of(
                lore("LMB: -0.05 | RMB: +0.05"),
                lore("Shift+LMB: -0.01 | Shift+RMB: Reset 0.0")
            )
        ));
        inv.setItem(SLOT_OFFSET, createToolItem(
            SLOT_OFFSET,
            Material.PISTON,
            Component.text("Offset X / Y", NamedTextColor.YELLOW, TextDecoration.BOLD),
            List.of(
                lore("LMB/RMB: Offset X (-/+ 0.05)"),
                lore("Shift+LMB/RMB: Offset Y (-/+ 0.05)")
            )
        ));
        inv.setItem(SLOT_ROTATION, createToolItem(
            SLOT_ROTATION,
            Material.AMETHYST_SHARD,
            Component.text("Rotation Yaw / Pitch", NamedTextColor.GOLD, TextDecoration.BOLD),
            List.of(
                lore("LMB/RMB: Yaw (-/+ 5 deg)"),
                lore("Shift+LMB/RMB: Pitch (-/+ 5 deg)")
            )
        ));
        inv.setItem(SLOT_MODE, createToolItem(
            SLOT_MODE,
            Material.ENDER_EYE,
            Component.text("Switch Preview Mode", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD),
            List.of(
                lore("Click: Cycle FP -> TP -> TP-Front")
            )
        ));
        inv.setItem(SLOT_TP_CAMERA, createToolItem(
            SLOT_TP_CAMERA,
            Material.CLOCK,
            Component.text("TP Camera Offset", NamedTextColor.BLUE, TextDecoration.BOLD),
            List.of(
                lore("LMB/RMB: Cam Distance (-/+ 0.10)"),
                lore("Shift+LMB/RMB: Cam Height (-/+ 0.10)"),
                lore("Auto-targets Back or Front camera by mode")
            )
        ));
        inv.setItem(SLOT_MENU, createToolItem(
            SLOT_MENU,
            Material.CHEST,
            Component.text("Quick Settings Menu", NamedTextColor.GOLD, TextDecoration.BOLD),
            List.of(
                lore("Click: Open 27-slot Presets & Toggles GUI")
            )
        ));
        inv.setItem(SLOT_SAVE_EXIT, createToolItem(
            SLOT_SAVE_EXIT,
            Material.EMERALD,
            Component.text("Save & Exit", NamedTextColor.GREEN, TextDecoration.BOLD),
            List.of(
                lore("Click: Save Profile & Exit"),
                lore("Shift+Click: Discard Changes & Exit")
            )
        ));
        inv.setHeldItemSlot(0);
        admin.updateInventory();
    }

    private ItemStack createToolItem(int slot, Material material, Component name, List<Component> loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(loreLines);
            meta.getPersistentDataContainer().set(toolSlotKey, PersistentDataType.INTEGER, slot);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void populateQuickSettingsMenu(Inventory menu, EditorState state) {
        ItemStack filler = createMenuItem(
            Material.GRAY_STAINED_GLASS_PANE,
            Component.text(" "),
            List.of()
        );
        for (int i = 0; i < menu.getSize(); i++) {
            menu.setItem(i, filler);
        }

        ScreenProfile profile = state.workingProfile;
        RenderTransform active = EditorParameters.activeTransform(profile, state.previewMode);

        menu.setItem(MENU_PRESET_FLAT, createMenuItem(
            Material.PAPER,
            Component.text("Preset: Flat", NamedTextColor.WHITE, TextDecoration.BOLD),
            List.of(
                lore("Curvature: 0.0, centered clean plane"),
                lore("Click to apply to " + state.previewMode.name())
            )
        ));
        menu.setItem(MENU_PRESET_CURVED, createMenuItem(
            Material.BOW,
            Component.text("Preset: Curved", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
            List.of(
                lore("Curvature: 0.45, wrap-around arc"),
                lore("Click to apply to " + state.previewMode.name())
            )
        ));
        menu.setItem(MENU_PRESET_COMPACT, createMenuItem(
            Material.ENDER_PEARL,
            Component.text("Preset: Compact", NamedTextColor.AQUA, TextDecoration.BOLD),
            List.of(
                lore("Scale: 0.78 | Spacing: 0.22 | Dist: 1.75"),
                lore("Click to apply to " + state.previewMode.name())
            )
        ));
        menu.setItem(MENU_PRESET_SHOWCASE, createMenuItem(
            Material.NETHER_STAR,
            Component.text("Preset: Showcase", NamedTextColor.GOLD, TextDecoration.BOLD),
            List.of(
                lore("Scale: 1.30 | Spacing: 0.28 | Curv: 0.55"),
                lore("Click to apply to " + state.previewMode.name())
            )
        ));

        menu.setItem(MENU_PREVIEW_MODE, createMenuItem(
            Material.ENDER_EYE,
            Component.text("Preview Mode: " + state.previewMode.name(), NamedTextColor.DARK_PURPLE, TextDecoration.BOLD),
            List.of(
                lore("Active Transform Dist: " + formatNumber(active.distance())),
                lore("Click to cycle FP -> TP -> TP-Front live")
            )
        ));
        menu.setItem(MENU_PROFILE_VIEW_MODE, createMenuItem(
            Material.COMPARATOR,
            Component.text("Profile Default Mode: " + profile.viewMode().name(), NamedTextColor.YELLOW, TextDecoration.BOLD),
            List.of(
                lore("Click to cycle AUTO -> FP -> TP -> TP-Front -> DISABLED")
            )
        ));

        menu.setItem(MENU_TOGGLE_BACKGROUND, createToggleItem(
            profile.showBackground(),
            "Panel Background",
            "Backdrop TextDisplay behind slots"
        ));
        menu.setItem(MENU_TOGGLE_TOOLTIPS, createToggleItem(
            profile.showTooltips(),
            "Hover Tooltips",
            "Floating item tooltip TextDisplay"
        ));
        menu.setItem(MENU_TOGGLE_PLAYER_INV, createToggleItem(
            profile.includePlayerInventory(),
            "Include Player Inventory",
            "Render player storage + hotbar below container"
        ));
        menu.setItem(MENU_TOGGLE_WALL_FALLBACK, createToggleItem(
            profile.thirdPersonSettings().fallbackToFirstPersonOnOcclusion(),
            "Wall-Occlusion Fallback",
            "Auto-fallback to FP if clearance < minClearance"
        ));
        menu.setItem(MENU_TOGGLE_ENABLED, createToggleItem(
            profile.enabled(),
            "Screen Profile Enabled",
            "Allow 3D Spatial GUI for " + state.screenType.name()
        ));

        menu.setItem(MENU_SWITCH_SCREEN, createMenuItem(
            Material.PAINTING,
            Component.text("Screen: " + state.screenType.name(), NamedTextColor.AQUA, TextDecoration.BOLD),
            List.of(
                lore("Left-Click: Next ScreenType"),
                lore("Right-Click: Previous ScreenType")
            )
        ));

        menu.setItem(MENU_BACK, createMenuItem(
            Material.ARROW,
            Component.text("Back to Hotbar Tools", NamedTextColor.GRAY, TextDecoration.BOLD),
            List.of(lore("Close menu and continue editing"))
        ));
        menu.setItem(MENU_RESET_PROFILE, createMenuItem(
            Material.TNT,
            Component.text("Reset Profile to Defaults", NamedTextColor.RED, TextDecoration.BOLD),
            List.of(lore("Restores " + state.screenType.name() + " from profiles.yml defaults"))
        ));
        menu.setItem(MENU_DISCARD_EXIT, createMenuItem(
            Material.RED_CONCRETE,
            Component.text("Discard & Exit", NamedTextColor.RED, TextDecoration.BOLD),
            List.of(lore("Exit editor without saving changes"))
        ));
        menu.setItem(MENU_SAVE_ONLY, createMenuItem(
            Material.EMERALD_BLOCK,
            Component.text("Save Profile", NamedTextColor.GREEN, TextDecoration.BOLD),
            List.of(lore("Write current edits to profiles.yml"))
        ));
        menu.setItem(MENU_SAVE_EXIT, createMenuItem(
            Material.EMERALD,
            Component.text("Save & Exit", NamedTextColor.GREEN, TextDecoration.BOLD),
            List.of(lore("Save profile and restore your inventory"))
        ));
    }

    private ItemStack createToggleItem(boolean enabled, String label, String description) {
        Material mat = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
        NamedTextColor color = enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY;
        String status = enabled ? "ON" : "OFF";
        return createMenuItem(
            mat,
            Component.text(label + ": " + status, color, TextDecoration.BOLD),
            List.of(
                lore(description),
                lore("Click to toggle")
            )
        );
    }

    private ItemStack createMenuItem(Material material, Component name, List<Component> loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            if (!loreLines.isEmpty()) {
                meta.lore(loreLines);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void updateHud(Player admin, EditorState state) {
        String activeParam = state.activeParam != null ? state.activeParam : "distance";
        double activeVal = EditorParameters.getParameterValue(state.workingProfile, state.previewMode, activeParam);
        double min = EditorParameters.getParameterMin(activeParam);
        double max = EditorParameters.getParameterMax(activeParam);
        float progress = max > min
            ? (float) EditorParameters.clampDouble((activeVal - min) / (max - min), 0.0, 1.0)
            : 1.0f;

        String modeLabel = shortModeLabel(state.previewMode);
        NamedTextColor modeColor = switch (state.previewMode) {
            case THIRD_PERSON -> NamedTextColor.LIGHT_PURPLE;
            case THIRD_PERSON_FRONT -> NamedTextColor.GOLD;
            default -> NamedTextColor.GREEN;
        };
        BossBar.Color barColor = switch (state.previewMode) {
            case THIRD_PERSON -> BossBar.Color.PURPLE;
            case THIRD_PERSON_FRONT -> BossBar.Color.PINK;
            default -> BossBar.Color.BLUE;
        };

        Component bossTitle = Component.text("[Editor] ", NamedTextColor.GOLD, TextDecoration.BOLD)
            .append(Component.text(state.screenType.name(), NamedTextColor.AQUA))
            .append(Component.text(" | Mode: ", NamedTextColor.GRAY))
            .append(Component.text(modeLabel, modeColor))
            .append(Component.text(" | ", NamedTextColor.GRAY))
            .append(Component.text(activeParam + ": " + formatNumber(activeVal), NamedTextColor.WHITE));

        state.bossBar.name(bossTitle);
        state.bossBar.progress(progress);
        state.bossBar.color(barColor);

        admin.sendActionBar(buildActionBarComponent(state, activeParam, activeVal));
    }

    private Component buildActionBarComponent(EditorState state, String activeParam, double activeVal) {
        RenderTransform t = EditorParameters.activeTransform(state.workingProfile, state.previewMode);
        ThirdPersonSettings tp = state.workingProfile.thirdPersonSettings();
        String modeShort = shortModeLabel(state.previewMode);
        String summary = String.format(
            Locale.ROOT,
            "[%s:%s] %s=%.2f | D=%.2f S=%.2f C=%.2f Off=(%.2f,%.2f) Rot=(%.0f,%.0f)",
            state.screenType.name(),
            modeShort,
            activeParam,
            activeVal,
            t.distance(),
            t.scale(),
            t.curvature(),
            t.offsetX(),
            t.offsetY(),
            t.yawOffset(),
            t.pitchOffset()
        );
        if (state.previewMode == ViewMode.THIRD_PERSON) {
            summary += String.format(Locale.ROOT, " | Cam=%.2f/%.2f", tp.cameraDistance(), tp.cameraHeightOffset());
        } else if (state.previewMode == ViewMode.THIRD_PERSON_FRONT) {
            summary += String.format(Locale.ROOT, " | FrontCam=%.2f/%.2f", tp.frontCameraDistance(), tp.frontCameraHeightOffset());
        }
        return Component.text(summary, NamedTextColor.YELLOW);
    }

    private void sendEditorIntro(Player admin, EditorState state) {
        admin.sendMessage(
            Component.text("CookieTPS Visual Editor ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("opened for ", NamedTextColor.GRAY))
                .append(Component.text(state.screenType.name(), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(" (inventory safely backed up).", NamedTextColor.GRAY))
        );
    }

    private void sendChatControlPanel(Player admin, EditorState state) {
        String param = state.activeParam != null ? state.activeParam : "distance";
        double current = EditorParameters.getParameterValue(state.workingProfile, state.previewMode, param);
        boolean angular = isAngularParam(param);
        boolean integerTicks = isTickParam(param);
        String bigNeg = integerTicks ? "-5" : (angular ? "-15" : "-0.2");
        String smallNeg = integerTicks ? "-1" : (angular ? "-5" : "-0.05");
        String smallPos = integerTicks ? "+1" : (angular ? "+5" : "+0.05");
        String bigPos = integerTicks ? "+5" : (angular ? "+15" : "+0.2");
        String bigPosCmd = integerTicks ? "5" : (angular ? "15" : "0.2");
        String smallPosCmd = integerTicks ? "1" : (angular ? "5" : "0.05");
        double resetVal = defaultResetValue(param);

        Component panel = Component.text("[Editor:" + shortModeLabel(state.previewMode) + "] ", NamedTextColor.GOLD)
            .append(Component.text(param + "=" + formatNumber(current) + " ", NamedTextColor.WHITE))
            .append(button("[ " + bigNeg + " ]", NamedTextColor.RED, "/cookietps editor adjust " + param + " " + bigNeg, "Decrease " + param + " by " + bigNeg))
            .append(Component.space())
            .append(button("[ " + smallNeg + " ]", NamedTextColor.GOLD, "/cookietps editor adjust " + param + " " + smallNeg, "Fine decrease " + param))
            .append(Component.space())
            .append(button("[ " + smallPos + " ]", NamedTextColor.GREEN, "/cookietps editor adjust " + param + " " + smallPosCmd, "Fine increase " + param))
            .append(Component.space())
            .append(button("[ " + bigPos + " ]", NamedTextColor.DARK_GREEN, "/cookietps editor adjust " + param + " " + bigPosCmd, "Increase " + param + " by " + bigPos))
            .append(Component.space())
            .append(button("[ Reset ]", NamedTextColor.YELLOW, "/cookietps editor set " + param + " " + formatNumber(resetVal), "Reset " + param + " to " + formatNumber(resetVal)))
            .append(Component.space())
            .append(button("[ Menu ]", NamedTextColor.AQUA, "/cookietps editor menu", "Open Quick Settings Menu"))
            .append(Component.space())
            .append(button("[ Save ]", NamedTextColor.GREEN, "/cookietps editor save", "Save active profile"));

        if (state.previewMode != ViewMode.FIRST_PERSON) {
            panel = panel.append(Component.space())
                .append(button("[ FP ]", NamedTextColor.GREEN, "/cookietps editor mode first", "Switch preview to First-Person"));
        }
        if (state.previewMode != ViewMode.THIRD_PERSON) {
            panel = panel.append(Component.space())
                .append(button("[ TP ]", NamedTextColor.LIGHT_PURPLE, "/cookietps editor mode third", "Switch preview to Third-Person (Back)"));
        }
        if (state.previewMode != ViewMode.THIRD_PERSON_FRONT) {
            panel = panel.append(Component.space())
                .append(button("[ Front ]", NamedTextColor.GOLD, "/cookietps editor mode front", "Switch preview to Third-Person (Front)"));
        }

        admin.sendMessage(panel);
    }

    private Component button(String label, NamedTextColor color, String command, String hover) {
        return Component.text(label, color, TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

    private Inventory createPreviewInventory(ScreenType screenType) {
        Inventory inv = switch (screenType) {
            case DOUBLE_CHEST -> Bukkit.createInventory(null, 54, Component.text("Preview Double Chest"));
            case BARREL -> Bukkit.createInventory(null, InventoryType.BARREL, Component.text("Preview Barrel"));
            case SHULKER_BOX -> Bukkit.createInventory(null, InventoryType.SHULKER_BOX, Component.text("Preview Shulker Box"));
            case ENDER_CHEST -> Bukkit.createInventory(null, InventoryType.ENDER_CHEST, Component.text("Preview Ender Chest"));
            case CRAFTING -> Bukkit.createInventory(null, InventoryType.WORKBENCH, Component.text("Preview Crafting"));
            case FURNACE -> Bukkit.createInventory(null, InventoryType.FURNACE, Component.text("Preview Furnace"));
            case PLAYER_INVENTORY -> Bukkit.createInventory(null, 36, Component.text("Preview Player Inventory"));
            case CHEST, GENERIC -> Bukkit.createInventory(null, 27, Component.text("Preview " + screenType.name()));
        };

        if (screenType == ScreenType.CRAFTING) {
            inv.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
            inv.setItem(2, new ItemStack(Material.DIAMOND));
            inv.setItem(5, new ItemStack(Material.DIAMOND));
            inv.setItem(8, new ItemStack(Material.STICK));
            return inv;
        }
        if (screenType == ScreenType.FURNACE) {
            inv.setItem(0, new ItemStack(Material.RAW_GOLD, 16));
            inv.setItem(1, new ItemStack(Material.COAL, 32));
            inv.setItem(2, new ItemStack(Material.GOLD_INGOT, 8));
            return inv;
        }

        ItemStack[] samples = new ItemStack[] {
            new ItemStack(Material.DIAMOND_SWORD),
            new ItemStack(Material.GOLDEN_APPLE, 16),
            new ItemStack(Material.EMERALD, 64),
            new ItemStack(Material.ENDER_PEARL, 16),
            new ItemStack(Material.EXPERIENCE_BOTTLE, 32),
            new ItemStack(Material.TOTEM_OF_UNDYING),
            new ItemStack(Material.ENCHANTED_BOOK),
            new ItemStack(Material.NETHERITE_INGOT, 8),
            new ItemStack(Material.COMPASS)
        };
        for (int i = 0; i < Math.min(inv.getSize(), samples.length * 2); i += 2) {
            inv.setItem(i, samples[(i / 2) % samples.length].clone());
        }
        return inv;
    }

    private void ensureHudTaskRunning() {
        if (hudTaskId != -1) {
            return;
        }
        hudTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeEditors.isEmpty()) {
                stopHudTask();
                return;
            }
            ThirdPersonCameraController camera = plugin.getCameraController();
            SpatialRenderer renderer = plugin.getSpatialRenderer();
            InputHandler inputHandler = plugin.getInputHandler();
            for (EditorState state : activeEditors.values()) {
                Player admin = Bukkit.getPlayer(state.adminUuid);
                if (admin == null || !admin.isOnline()) {
                    continue;
                }
                state.hudTickCounter++;
                if (state.previewMode.isThirdPersonFamily()
                    && camera != null
                    && renderer != null
                    && inputHandler != null
                    && camera.isInThirdPerson(state.adminUuid)) {
                    SpatialSession session = inputHandler.getSession(state.adminUuid);
                    if (session != null && session.isEditorPreview()) {
                        camera.updateCameraAndAvatar(admin, session, resolveMouseHeadTarget(admin, session, renderer));
                    }
                }
                if (state.hudTickCounter % 5 == 0) {
                    String param = state.activeParam != null ? state.activeParam : "distance";
                    double val = EditorParameters.getParameterValue(state.workingProfile, state.previewMode, param);
                    admin.sendActionBar(buildActionBarComponent(state, param, val));
                }
            }
        }, 2L, 2L).getTaskId();
    }

    private void stopHudTask() {
        if (hudTaskId != -1) {
            Bukkit.getScheduler().cancelTask(hudTaskId);
            hudTaskId = -1;
        }
    }

    private void saveDiskBackup(UUID uuid, ItemStack[] contents, int heldSlot) {
        ensureBackupDirectory();
        Path target = backupPath(uuid);
        Path temp = backupDir.resolve(uuid + ".yml.tmp");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("held-slot", heldSlot);
        List<ItemStack> items = new ArrayList<>(contents.length);
        for (ItemStack item : contents) {
            items.add(item != null ? item.clone() : null);
        }
        yaml.set("contents", items);
        try {
            yaml.save(temp.toFile());
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save editor inventory backup for " + uuid + ": " + e.getMessage());
        }
    }

    private void restoreOrphanedDiskBackup(Player player) {
        UUID uuid = player.getUniqueId();
        Path file = backupPath(uuid);
        if (!Files.exists(file)) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        List<?> rawList = yaml.getList("contents");
        int heldSlot = Math.max(0, Math.min(8, yaml.getInt("held-slot", 0)));
        if (rawList != null) {
            PlayerInventory inv = player.getInventory();
            ItemStack[] restored = new ItemStack[inv.getSize()];
            for (int i = 0; i < Math.min(restored.length, rawList.size()); i++) {
                Object entry = rawList.get(i);
                if (entry instanceof ItemStack item) {
                    restored[i] = item.clone();
                }
            }
            inv.setContents(restored);
            inv.setHeldItemSlot(heldSlot);
            player.updateInventory();
            player.sendMessage(prefixed("Restored your inventory from an interrupted Visual Editor session.", NamedTextColor.YELLOW));
        }
        deleteDiskBackup(uuid);
    }

    private void deleteDiskBackup(UUID uuid) {
        try {
            Files.deleteIfExists(backupPath(uuid));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to remove editor inventory backup for " + uuid + ": " + e.getMessage());
        }
    }

    private Path backupPath(UUID uuid) {
        return backupDir.resolve(uuid + ".yml");
    }

    private void ensureBackupDirectory() {
        File dir = backupDir.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private static ItemStack[] cloneContents(ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] != null ? source[i].clone() : null;
        }
        return copy;
    }

    private static ViewMode normalizePreviewMode(ViewMode mode) {
        if (mode == ViewMode.THIRD_PERSON || mode == ViewMode.THIRD_PERSON_FRONT) {
            return mode;
        }
        return ViewMode.FIRST_PERSON;
    }

    private static ViewMode nextPreviewMode(ViewMode current) {
        if (current == null) {
            return ViewMode.FIRST_PERSON;
        }
        return switch (current) {
            case FIRST_PERSON -> ViewMode.THIRD_PERSON;
            case THIRD_PERSON -> ViewMode.THIRD_PERSON_FRONT;
            default -> ViewMode.FIRST_PERSON;
        };
    }

    private static void updateActiveParamForMode(EditorState state, ViewMode mode) {
        if (mode == ViewMode.THIRD_PERSON_FRONT) {
            if (state.activeHotbarSlot != SLOT_TP_CAMERA
                || "cam-distance".equals(state.activeParam)
                || "cam-height".equals(state.activeParam)) {
                state.activeParam = "front-cam-distance";
            }
        } else if (mode == ViewMode.THIRD_PERSON) {
            if (state.activeHotbarSlot != SLOT_TP_CAMERA
                || "front-cam-distance".equals(state.activeParam)
                || "front-cam-height".equals(state.activeParam)) {
                state.activeParam = "cam-distance";
            }
        } else {
            String slotParam = primaryParamForSlot(state.activeHotbarSlot, ViewMode.FIRST_PERSON);
            if (slotParam != null) {
                state.activeParam = slotParam;
            } else {
                state.activeParam = "distance";
            }
        }
    }

    private static String shortModeLabel(ViewMode mode) {
        if (mode == null) {
            return "FP";
        }
        return switch (mode) {
            case THIRD_PERSON -> "TP";
            case THIRD_PERSON_FRONT -> "TP-FRONT";
            default -> "FP";
        };
    }

    private static boolean isAngularParam(String param) {
        return "yaw".equals(param)
            || "pitch".equals(param)
            || "roll".equals(param)
            || "cam-yaw".equals(param)
            || "cam-pitch".equals(param)
            || "front-cam-yaw".equals(param)
            || "front-cam-pitch".equals(param);
    }

    private static boolean isTickParam(String param) {
        return "open-ticks".equals(param) || "close-ticks".equals(param);
    }

    private static String primaryParamForSlot(int slot, ViewMode mode) {
        return switch (slot) {
            case SLOT_DISTANCE -> "distance";
            case SLOT_SCALE -> "scale";
            case SLOT_CURVATURE -> "curvature";
            case SLOT_OFFSET -> "offset-x";
            case SLOT_ROTATION -> "yaw";
            case SLOT_TP_CAMERA -> mode == ViewMode.THIRD_PERSON_FRONT ? "front-cam-distance" : "cam-distance";
            default -> null;
        };
    }

    private static double defaultResetValue(String param) {
        return switch (param) {
            case "distance" -> 2.1;
            case "scale" -> 1.0;
            case "spacing" -> 0.24;
            case "cam-distance" -> 2.05;
            case "cam-height" -> -0.05;
            case "cam-side" -> 0.0;
            case "cam-yaw" -> 26.0;
            case "cam-pitch" -> 2.0;
            case "min-clearance" -> 1.15;
            case "open-ticks" -> 10.0;
            case "close-ticks" -> 8.0;
            case "front-cam-distance" -> 2.45;
            case "front-cam-height" -> 0.15;
            case "front-cam-side" -> -0.85;
            case "front-cam-yaw" -> 162.0;
            case "front-cam-pitch" -> 4.0;
            default -> 0.0;
        };
    }

    private static Component lore(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private static Component prefixed(String text, NamedTextColor color) {
        return Component.text("[CookieTPS] ", NamedTextColor.GOLD)
            .append(Component.text(text, color));
    }

    private static String formatNumber(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private void playTone(Player player, String soundKey, float volume, float pitch) {
        if (plugin.getConfigManager().isSoundFeedbackEnabled()) {
            player.playSound(player.getLocation(), soundKey, volume, pitch);
        }
    }

    private static final class EditorState {
        private final UUID adminUuid;
        private final BossBar bossBar;
        private final ItemStack[] backedUpContents;
        private final int backedUpHeldSlot;

        private ScreenType screenType;
        private ScreenProfile workingProfile;
        private ViewMode previewMode;
        private int activeHotbarSlot;
        private String activeParam;
        private Inventory previewInventory;
        private long lastClickMillis;
        private Location savedFeetOrigin;
        private Location savedEyeOrigin;
        private int hudTickCounter;

        private EditorState(
            UUID adminUuid,
            ScreenType screenType,
            ScreenProfile workingProfile,
            ViewMode previewMode,
            int activeHotbarSlot,
            String activeParam,
            BossBar bossBar,
            ItemStack[] backedUpContents,
            int backedUpHeldSlot
        ) {
            this.adminUuid = adminUuid;
            this.screenType = screenType;
            this.workingProfile = workingProfile;
            this.previewMode = previewMode;
            this.activeHotbarSlot = activeHotbarSlot;
            this.activeParam = activeParam;
            this.bossBar = bossBar;
            this.backedUpContents = backedUpContents;
            this.backedUpHeldSlot = backedUpHeldSlot;
            this.previewInventory = null;
            this.lastClickMillis = 0L;
        }
    }
}
