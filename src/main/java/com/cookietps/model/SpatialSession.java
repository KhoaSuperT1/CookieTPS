package com.cookietps.model;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SpatialSession {
    private final UUID sessionId;
    private final UUID playerUuid;
    private final ScreenType screenType;
    private final Inventory topInventory;
    private final Inventory bottomInventory;
    private final boolean editorPreview;
    private final long openedAtMillis;

    private ScreenProfile profile;
    private ViewMode activeMode;
    private Location anchorOrigin;
    private Location feetOrigin;
    private Location eyeOrigin;
    private boolean closing;
    private int hoveredSlot;
    private ItemStack virtualCursor;
    private List<PanelSlotLayout> slotLayouts;
    private long lastSneakMillis;
    private int taskId;

    public SpatialSession(
        UUID playerUuid,
        ScreenType screenType,
        ScreenProfile profile,
        ViewMode activeMode,
        Inventory topInventory,
        Inventory bottomInventory,
        Location anchorOrigin,
        boolean editorPreview
    ) {
        this(
            playerUuid,
            screenType,
            profile,
            activeMode,
            topInventory,
            bottomInventory,
            anchorOrigin,
            anchorOrigin,
            anchorOrigin,
            editorPreview
        );
    }

    public SpatialSession(
        UUID playerUuid,
        ScreenType screenType,
        ScreenProfile profile,
        ViewMode activeMode,
        Inventory topInventory,
        Inventory bottomInventory,
        Location anchorOrigin,
        Location feetOrigin,
        Location eyeOrigin,
        boolean editorPreview
    ) {
        this.sessionId = UUID.randomUUID();
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
        this.screenType = Objects.requireNonNull(screenType, "screenType");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.activeMode = Objects.requireNonNull(activeMode, "activeMode");
        this.topInventory = topInventory;
        this.bottomInventory = bottomInventory;
        this.anchorOrigin = anchorOrigin != null ? anchorOrigin.clone() : null;
        this.feetOrigin = feetOrigin != null ? feetOrigin.clone() : null;
        this.eyeOrigin = eyeOrigin != null ? eyeOrigin.clone() : null;
        this.editorPreview = editorPreview;
        this.openedAtMillis = System.currentTimeMillis();
        this.closing = false;
        this.hoveredSlot = -1;
        this.virtualCursor = null;
        this.slotLayouts = Collections.emptyList();
        this.lastSneakMillis = 0L;
        this.taskId = -1;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public ScreenType getScreenType() {
        return screenType;
    }

    public ScreenProfile getProfile() {
        return profile;
    }

    public void setProfile(ScreenProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    public ViewMode getActiveMode() {
        return activeMode;
    }

    public void setActiveMode(ViewMode activeMode) {
        this.activeMode = Objects.requireNonNull(activeMode, "activeMode");
    }

    public Inventory getTopInventory() {
        return topInventory;
    }

    public Inventory getBottomInventory() {
        return bottomInventory;
    }

    public Location getAnchorOrigin() {
        return anchorOrigin != null ? anchorOrigin.clone() : null;
    }

    public void setAnchorOrigin(Location anchorOrigin) {
        this.anchorOrigin = anchorOrigin != null ? anchorOrigin.clone() : null;
    }

    public Location getFeetOrigin() {
        return feetOrigin != null ? feetOrigin.clone() : null;
    }

    public void setFeetOrigin(Location feetOrigin) {
        this.feetOrigin = feetOrigin != null ? feetOrigin.clone() : null;
    }

    public Location getEyeOrigin() {
        return eyeOrigin != null ? eyeOrigin.clone() : null;
    }

    public void setEyeOrigin(Location eyeOrigin) {
        this.eyeOrigin = eyeOrigin != null ? eyeOrigin.clone() : null;
    }

    public boolean isEditorPreview() {
        return editorPreview;
    }

    public boolean isClosing() {
        return closing;
    }

    public void setClosing(boolean closing) {
        this.closing = closing;
    }

    public int getHoveredSlot() {
        return hoveredSlot;
    }

    public void setHoveredSlot(int hoveredSlot) {
        this.hoveredSlot = hoveredSlot;
    }

    public ItemStack getVirtualCursor() {
        return virtualCursor;
    }

    public void setVirtualCursor(ItemStack virtualCursor) {
        this.virtualCursor = virtualCursor;
    }

    public List<PanelSlotLayout> getSlotLayouts() {
        return slotLayouts;
    }

    public void setSlotLayouts(List<PanelSlotLayout> slotLayouts) {
        if (slotLayouts == null || slotLayouts.isEmpty()) {
            this.slotLayouts = Collections.emptyList();
        } else {
            this.slotLayouts = Collections.unmodifiableList(new ArrayList<>(slotLayouts));
        }
    }

    public long getOpenedAtMillis() {
        return openedAtMillis;
    }

    public long getLastSneakMillis() {
        return lastSneakMillis;
    }

    public void setLastSneakMillis(long lastSneakMillis) {
        this.lastSneakMillis = lastSneakMillis;
    }

    public int getTaskId() {
        return taskId;
    }

    public void setTaskId(int taskId) {
        this.taskId = taskId;
    }
}
