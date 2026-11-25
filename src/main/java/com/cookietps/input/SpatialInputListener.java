package com.cookietps.input;

import com.cookietps.CookieTPSPlugin;
import com.cookietps.api.InputHandler;
import com.cookietps.model.PlayerPreferences;
import com.cookietps.model.ScreenProfile;
import com.cookietps.model.ScreenType;
import com.cookietps.model.SlotClickAction;
import com.cookietps.model.SlotInteractionResult;
import com.cookietps.model.SpatialSession;
import com.cookietps.model.ViewMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpatialInputListener implements InputHandler, Listener {
    private static final String EDITOR_MENU_HOLDER_CLASS = "com.cookietps.editor.EditorMenuHolder";

    private final CookieTPSPlugin plugin;
    private final Map<UUID, SpatialSession> activeSessions;
    private final Map<UUID, Integer> lastInteractTick;

    public SpatialInputListener(CookieTPSPlugin plugin) {
        this.plugin = plugin;
        this.activeSessions = new ConcurrentHashMap<>();
        this.lastInteractTick = new ConcurrentHashMap<>();
    }

    @Override
    public boolean hasActiveSession(UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }
        SpatialSession session = activeSessions.get(playerUuid);
        return session != null && !session.isClosing();
    }

    @Override
    public SpatialSession getSession(UUID playerUuid) {
        return playerUuid != null ? activeSessions.get(playerUuid) : null;
    }

    @Override
    public Collection<SpatialSession> getActiveSessions() {
        return Collections.unmodifiableCollection(new ArrayList<>(activeSessions.values()));
    }

    @Override
    public void startSession(Player player, Inventory topInventory, ScreenType screenType, boolean editorPreview) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (activeSessions.containsKey(uuid)) {
            closeSession(player, true);
        }

        ScreenType resolvedType = screenType != null ? screenType : ScreenType.fromInventory(topInventory);
        ScreenProfile profile = plugin.getProfileManager().getProfile(resolvedType);
        if (profile == null) {
            return;
        }

        PlayerPreferences prefs = plugin.getPreferencesStore().load(uuid);
        ViewMode resolvedMode = resolveMode(prefs, profile, editorPreview);
        if (resolvedMode == ViewMode.DISABLED) {
            return;
        }

        Location feetOrigin = player.getLocation().clone();
        feetOrigin.setPitch(0.0f);
        Location eyeOrigin = player.getEyeLocation().clone();
        Location anchorOrigin = eyeOrigin.clone();
        if (resolvedMode.isThirdPersonFamily()) {
            anchorOrigin.setPitch(0.0f);
        }

        Inventory bottomInventory = resolvedType == ScreenType.PLAYER_INVENTORY ? null : player.getInventory();
        SpatialSession session = new SpatialSession(
            uuid,
            resolvedType,
            profile,
            resolvedMode,
            topInventory,
            bottomInventory,
            anchorOrigin,
            feetOrigin,
            eyeOrigin,
            editorPreview
        );
        activeSessions.put(uuid, session);

        if (resolvedMode.isThirdPersonFamily()) {
            boolean clearanceOk = plugin.getCameraController().checkClearance(eyeOrigin, profile.withViewMode(resolvedMode));
            boolean entered = clearanceOk && plugin.getCameraController().enterThirdPerson(player, session);
            if (!entered) {
                session.setActiveMode(ViewMode.FIRST_PERSON);
                session.setAnchorOrigin(eyeOrigin.clone());
            }
        }

        plugin.getSpatialRenderer().openPanel(player, session);

        if (!editorPreview && plugin.getConfigManager().isSoundFeedbackEnabled()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, 1.15f);
        }

        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickSession(player, session), 1L, 1L).getTaskId();
        session.setTaskId(taskId);
    }

    @Override
    public void handleSlotAction(Player player, SpatialSession session, SlotClickAction action) {
        if (player == null || session == null || session.isClosing() || action == null) {
            return;
        }
        if (action == SlotClickAction.CLOSE) {
            closeSession(player, false);
            return;
        }

        SlotInteractionResult result = plugin.getContainerBridge().executeClick(
            player,
            session,
            session.getHoveredSlot(),
            action
        );
        if (result.shouldClose()) {
            closeSession(player, false);
            return;
        }
        if (result.handled()) {
            if (plugin.getConfigManager().isSoundFeedbackEnabled()) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.35f);
            }
            plugin.getSpatialRenderer().syncContents(player, session);
            plugin.getSpatialRenderer().updateHover(player, session, session.getHoveredSlot());
            plugin.getSpatialRenderer().updateCursorVisual(player, session);
        }
    }

    @Override
    public void stepHoveredSlot(Player player, SpatialSession session, int delta) {
        if (player == null || session == null || session.isClosing()) {
            return;
        }
        int count = session.getSlotLayouts().size();
        if (count <= 0) {
            return;
        }
        int current = session.getHoveredSlot();
        int next = current < 0
            ? (delta >= 0 ? 0 : count - 1)
            : Math.floorMod(current + delta, count);
        plugin.getSpatialRenderer().updateHover(player, session, next);
        plugin.getSpatialRenderer().updateCursorVisual(player, session);
        if (plugin.getConfigManager().isSoundFeedbackEnabled()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.6f);
        }
    }

    @Override
    public void closeSession(Player player, boolean immediate) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        SpatialSession session = activeSessions.get(uuid);
        if (session == null) {
            return;
        }
        if (session.isClosing() && !immediate) {
            return;
        }
        session.setClosing(true);
        if (session.getTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(session.getTaskId());
            session.setTaskId(-1);
        }

        if (!immediate && plugin.getCameraController().isInThirdPerson(uuid)) {
            plugin.getSpatialRenderer().closePanel(player, session, false);
            plugin.getCameraController().exitThirdPersonAnimated(
                player,
                () -> finishSessionCleanup(player, session, false, false)
            );
            return;
        }

        finishSessionCleanup(player, session, immediate, true);
    }

    private void finishSessionCleanup(Player player, SpatialSession session, boolean immediate, boolean callClosePanel) {
        UUID uuid = player.getUniqueId();
        if (!activeSessions.remove(uuid, session)) {
            return;
        }
        session.setClosing(true);
        if (session.getTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(session.getTaskId());
            session.setTaskId(-1);
        }

        if (plugin.getConfigManager().isReturnCursorOnClose()) {
            plugin.getContainerBridge().returnVirtualCursor(player, session);
        }
        if (plugin.getCameraController().isInThirdPerson(uuid)) {
            plugin.getCameraController().exitThirdPerson(player, true);
        }
        if (callClosePanel) {
            plugin.getSpatialRenderer().closePanel(player, session, immediate);
        }

        if (!immediate && player.isOnline() && !session.isEditorPreview() && plugin.getConfigManager().isSoundFeedbackEnabled()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 0.85f);
        }
    }

    @Override
    public void shutdownAll() {
        for (SpatialSession session : new ArrayList<>(activeSessions.values())) {
            Player player = Bukkit.getPlayer(session.getPlayerUuid());
            if (player != null) {
                closeSession(player, true);
            } else {
                activeSessions.remove(session.getPlayerUuid());
                session.setClosing(true);
                if (session.getTaskId() != -1) {
                    Bukkit.getScheduler().cancelTask(session.getTaskId());
                    session.setTaskId(-1);
                }
            }
        }
        activeSessions.clear();
        lastInteractTick.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!plugin.getConfigManager().isPluginEnabled()) {
            return;
        }
        if (!player.hasPermission("cookietps.use")) {
            return;
        }
        if (plugin.getEditorController().isEditing(player.getUniqueId())) {
            return;
        }

        InventoryView view = event.getView();
        Inventory topInventory = view.getTopInventory();
        if (shouldSkipInventory(topInventory)) {
            return;
        }

        PlayerPreferences prefs = plugin.getPreferencesStore().load(player.getUniqueId());
        if (prefs == null || !prefs.enabled()) {
            return;
        }

        ScreenType screenType = ScreenType.fromInventory(topInventory);
        if (!plugin.getConfigManager().isScreenAllowed(screenType)) {
            return;
        }

        ScreenProfile profile = plugin.getProfileManager().getProfile(screenType);
        if (profile == null || !profile.enabled() || profile.viewMode() == ViewMode.DISABLED) {
            return;
        }

        ViewMode resolved = resolveMode(prefs, profile, false);
        if (resolved == ViewMode.DISABLED) {
            return;
        }

        event.setCancelled(true);
        startSession(player, topInventory, screenType, false);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        SpatialSession session = activeSessions.get(player.getUniqueId());
        if (session == null || session.isClosing() || session.isEditorPreview()) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }

        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            lastInteractTick.put(player.getUniqueId(), Bukkit.getCurrentTick());
            SlotClickAction click = player.isSneaking() ? SlotClickAction.SHIFT_LEFT_CLICK : SlotClickAction.LEFT_CLICK;
            handleSlotAction(player, session, click);
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            lastInteractTick.put(player.getUniqueId(), Bukkit.getCurrentTick());
            handleSlotAction(player, session, SlotClickAction.RIGHT_CLICK);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        SpatialSession session = activeSessions.get(player.getUniqueId());
        if (session == null || session.isClosing() || session.isEditorPreview()) {
            return;
        }
        if (!session.getActiveMode().isThirdPersonFamily() && player.getGameMode() != GameMode.SPECTATOR) {
            return;
        }
        int currentTick = Bukkit.getCurrentTick();
        Integer prevTick = lastInteractTick.get(player.getUniqueId());
        if (prevTick != null && prevTick == currentTick) {
            return;
        }
        event.setCancelled(true);
        lastInteractTick.put(player.getUniqueId(), currentTick);
        SlotClickAction click = player.isSneaking() ? SlotClickAction.SHIFT_LEFT_CLICK : SlotClickAction.LEFT_CLICK;
        handleSlotAction(player, session, click);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        SpatialSession session = activeSessions.get(player.getUniqueId());
        if (session == null || session.isClosing() || session.isEditorPreview()) {
            return;
        }
        event.setCancelled(true);
        SlotClickAction dropAction = player.isSneaking() ? SlotClickAction.DROP_ALL : SlotClickAction.DROP_ONE;
        handleSlotAction(player, session, dropAction);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        SpatialSession session = activeSessions.get(uuid);
        if (session != null) {
            if (session.isEditorPreview()) {
                return;
            }
            event.setCancelled(true);
            if (!session.isClosing()) {
                closeSession(player, false);
            }
            return;
        }

        if (!plugin.getConfigManager().isPluginEnabled()
            || !plugin.getConfigManager().isSneakSwapOpensInventory()
            || !player.isSneaking()
            || !player.hasPermission("cookietps.use")
            || plugin.getEditorController().isEditing(uuid)) {
            return;
        }
        PlayerPreferences prefs = plugin.getPreferencesStore().load(uuid);
        if (prefs == null || !prefs.enabled()) {
            return;
        }
        if (!plugin.getConfigManager().isScreenAllowed(ScreenType.PLAYER_INVENTORY)) {
            return;
        }
        ScreenProfile profile = plugin.getProfileManager().getProfile(ScreenType.PLAYER_INVENTORY);
        if (profile == null || !profile.enabled() || profile.viewMode() == ViewMode.DISABLED) {
            return;
        }
        ViewMode resolved = resolveMode(prefs, profile, false);
        if (resolved == ViewMode.DISABLED) {
            return;
        }
        event.setCancelled(true);
        startSession(player, player.getInventory(), ScreenType.PLAYER_INVENTORY, false);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        SpatialSession session = activeSessions.get(player.getUniqueId());
        if (session == null || session.isClosing() || session.isEditorPreview()) {
            return;
        }
        if (!session.getActiveMode().isThirdPersonFamily()) {
            return;
        }
        event.setCancelled(true);
        int delta = event.getNewSlot() - event.getPreviousSlot();
        if (delta > 4) {
            delta -= 9;
        } else if (delta < -4) {
            delta += 9;
        }
        if (delta != 0) {
            stepHoveredSlot(player, session, delta);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        SpatialSession session = activeSessions.get(player.getUniqueId());
        if (session != null && !session.isClosing() && !session.isEditorPreview()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        SpatialSession session = activeSessions.get(player.getUniqueId());
        if (session == null || session.isClosing() || session.isEditorPreview()) {
            return;
        }
        InventoryView view = event.getView();
        if (session.getTopInventory() != null && session.getTopInventory().equals(view.getTopInventory())) {
            closeSession(player, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getConfigManager().isPluginEnabled()
            || !plugin.getConfigManager().isJoinHintEnabled()
            || !player.hasPermission("cookietps.use")) {
            return;
        }
        player.sendMessage(
            Component.text("[CookieTPS] ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text("Press ", NamedTextColor.GRAY))
                .append(Component.text("[Shift + F]", NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(" or run ", NamedTextColor.GRAY))
                .append(Component.text("/ctps open", NamedTextColor.AQUA))
                .append(Component.text(" to open 3D Spatial Inventory (chests & crafting open automatically).", NamedTextColor.GRAY))
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        closeSession(player, true);
        lastInteractTick.remove(uuid);
        plugin.getPreferencesStore().unload(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        closeSession(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        closeSession(event.getEntity(), true);
    }

    private void tickSession(Player player, SpatialSession session) {
        if (!player.isOnline() || session.isClosing() || activeSessions.get(player.getUniqueId()) != session) {
            if (session.getTaskId() != -1) {
                Bukkit.getScheduler().cancelTask(session.getTaskId());
                session.setTaskId(-1);
            }
            return;
        }

        Location anchor = session.getAnchorOrigin();
        if (anchor == null || anchor.getWorld() == null || !anchor.getWorld().getUID().equals(player.getWorld().getUID())) {
            closeSession(player, true);
            return;
        }

        if (!session.isEditorPreview() && session.getActiveMode() == ViewMode.FIRST_PERSON) {
            Location feetOrigin = session.getFeetOrigin() != null ? session.getFeetOrigin() : anchor;
            double maxMove = plugin.getConfigManager().getAutoCloseMoveDistance();
            if (feetOrigin.getWorld() != null
                && feetOrigin.getWorld().getUID().equals(player.getWorld().getUID())
                && player.getLocation().distanceSquared(feetOrigin) > maxMove * maxMove) {
                closeSession(player, false);
                return;
            }
        }

        plugin.getSpatialRenderer().syncContents(player, session);

        int hovered = plugin.getSpatialRenderer().raycastHoveredSlot(player, session);
        plugin.getSpatialRenderer().updateHover(player, session, hovered);
        plugin.getSpatialRenderer().updateCursorVisual(player, session);

        if (session.getActiveMode().isThirdPersonFamily()) {
            Location mouseTarget = plugin.getSpatialRenderer().raycastMouseWorldTarget(player, session);
            plugin.getCameraController().updateCameraAndAvatar(player, session, mouseTarget);
        }
    }

    private ViewMode resolveMode(PlayerPreferences prefs, ScreenProfile profile, boolean editorPreview) {
        if (profile.viewMode() == ViewMode.DISABLED && !editorPreview) {
            return ViewMode.DISABLED;
        }
        if (profile.viewMode() != ViewMode.AUTO && profile.viewMode() != ViewMode.DISABLED) {
            return profile.viewMode();
        }
        if (!editorPreview && prefs != null && prefs.preferredMode() != ViewMode.AUTO && prefs.preferredMode() != ViewMode.DISABLED) {
            return prefs.preferredMode();
        }
        ViewMode globalDefault = plugin.getConfigManager().getDefaultViewMode();
        if (globalDefault != null && globalDefault != ViewMode.AUTO && globalDefault != ViewMode.DISABLED) {
            return globalDefault;
        }
        return ViewMode.FIRST_PERSON;
    }

    private static boolean shouldSkipInventory(Inventory topInventory) {
        if (topInventory == null) {
            return true;
        }
        InventoryHolder holder = topInventory.getHolder(false);
        if (holder != null) {
            if (EDITOR_MENU_HOLDER_CLASS.equals(holder.getClass().getName())) {
                return true;
            }
            if (holder instanceof BlockInventoryHolder
                || holder instanceof DoubleChest
                || holder instanceof Entity) {
                return false;
            }
            return true;
        }
        InventoryType type = topInventory.getType();
        if (type == InventoryType.ENDER_CHEST || type == InventoryType.WORKBENCH) {
            return false;
        }
        return topInventory.getLocation() == null;
    }
}
