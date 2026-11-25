package com.cookietps.render;

import com.cookietps.CookieTPSPlugin;
import com.cookietps.api.SpatialRenderer;
import com.cookietps.model.PanelSlotLayout;
import com.cookietps.model.RenderTransform;
import com.cookietps.model.ScreenProfile;
import com.cookietps.model.ScreenType;
import com.cookietps.model.SpatialSession;
import com.cookietps.model.ThirdPersonSettings;
import com.cookietps.model.ViewMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DisplaySpatialRenderer implements SpatialRenderer {
    private static final float QUAD_BASE_WIDTH = 0.225f;
    private static final float QUAD_BASE_HEIGHT = 0.25f;
    private static final float HOVER_SCALE_MULTIPLIER = 1.18f;
    private static final float COLLAPSED_SCALE = 0.01f;
    private static final float VIEW_RANGE = 1.5f;
    private static final int DEFAULT_LINE_WIDTH = 200;
    private static final int TOOLTIP_LINE_WIDTH = 240;
    private static final int TITLE_LINE_WIDTH = 320;
    private static final int CURVED_BACKDROP_SEGMENTS = 7;

    private static final Color BACKDROP_COLOR = Color.fromARGB(235, 198, 198, 198);
    private static final Color HEADER_COLOR = Color.fromARGB(240, 210, 210, 210);
    private static final Color SLOT_TILE_COLOR = Color.fromARGB(225, 139, 139, 139);
    private static final Color CLOSE_BG_COLOR = Color.fromARGB(220, 175, 45, 45);
    private static final Color HOVER_HIGHLIGHT_COLOR = Color.fromARGB(150, 255, 255, 255);
    private static final Color TOOLTIP_BG_COLOR = Color.fromARGB(235, 16, 0, 16);
    private static final Color TRANSPARENT_COLOR = Color.fromARGB(0, 0, 0, 0);

    private final CookieTPSPlugin plugin;
    private final Map<UUID, RenderedPanel> panels;

    public DisplaySpatialRenderer(CookieTPSPlugin plugin) {
        this.plugin = plugin;
        this.panels = new ConcurrentHashMap<>();
    }

    @Override
    public void openPanel(Player player, SpatialSession session) {
        if (player == null || session == null || !player.isOnline()) {
            return;
        }
        RenderedPanel existing = panels.remove(session.getSessionId());
        if (existing != null) {
            existing.destroyImmediate();
        }

        List<PanelSlotLayout> layouts = plugin.getContainerBridge().buildSlotLayouts(
            session.getProfile(),
            session.getTopInventory(),
            session.getBottomInventory()
        );
        session.setSlotLayouts(layouts);

        Location origin = resolveOrigin(player, session);
        if (origin == null || origin.getWorld() == null) {
            return;
        }

        RenderTransform transform = resolveTransform(session);
        CurvedPanelMath.PanelFrame frame = CurvedPanelMath.computePanelFrame(origin, transform);

        RenderedPanel panel = buildPanelEntities(player, session, origin.getWorld(), frame, transform, layouts, true);
        panels.put(session.getSessionId(), panel);

        int openTicks = Math.max(0, transform.openDurationTicks());
        if (openTicks == 0) {
            applyTargetTransforms(panel, frame, transform, 0);
            syncContentsInternal(player, session, panel);
        } else {
            int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                RenderedPanel active = panels.get(session.getSessionId());
                if (active == null || session.isClosing() || !player.isOnline()) {
                    return;
                }
                applyTargetTransforms(active, frame, transform, openTicks);
                syncContentsInternal(player, session, active);
            }, 1L).getTaskId();
            panel.pendingTaskId = taskId;
        }
    }

    @Override
    public void updatePanel(Player player, SpatialSession session) {
        if (player == null || session == null) {
            return;
        }
        syncContents(player, session);
        updateHover(player, session, session.getHoveredSlot());
        updateCursorVisual(player, session);
    }

    @Override
    public void syncContents(Player player, SpatialSession session) {
        if (player == null || session == null || session.isClosing()) {
            return;
        }
        RenderedPanel panel = panels.get(session.getSessionId());
        if (panel == null) {
            return;
        }
        syncContentsInternal(player, session, panel);
    }

    @Override
    public void updateHover(Player player, SpatialSession session, int newHoveredSlot) {
        if (player == null || session == null || session.isClosing()) {
            return;
        }
        RenderedPanel panel = panels.get(session.getSessionId());
        if (panel == null) {
            return;
        }

        int previous = session.getHoveredSlot();
        if (previous == newHoveredSlot && panel.renderedHoverSlot == newHoveredSlot) {
            return;
        }
        session.setHoveredSlot(newHoveredSlot);
        panel.renderedHoverSlot = newHoveredSlot;

        RenderTransform transform = resolveTransform(session);
        ScreenProfile profile = session.getProfile();
        List<PanelSlotLayout> layouts = session.getSlotLayouts();

        if (previous >= 0 && previous < panel.slots.size()) {
            updateSlotScale(panel, panel.slots.get(previous), profile, transform, false);
        }
        if (newHoveredSlot >= 0 && newHoveredSlot < panel.slots.size()) {
            updateSlotScale(panel, panel.slots.get(newHoveredSlot), profile, transform, true);
        }

        if (newHoveredSlot >= 0 && newHoveredSlot < layouts.size()) {
            PanelSlotLayout layout = layouts.get(newHoveredSlot);
            CurvedPanelMath.SlotPose pose = CurvedPanelMath.computeSlotPose(
                panel.frame,
                layout.localX(),
                layout.localY(),
                layout.localZ() - 0.004f,
                transform
            );
            float scale = Math.max(0.05f, transform.scale());
            float cell = Math.max(0.12f, profile.slotSpacing()) * 0.92f * scale;
            if (layout.closeButton()) {
                cell *= 1.15f;
            }
            if (panel.hoverHighlight != null && panel.hoverHighlight.isValid()) {
                moveDisplay(panel.hoverHighlight, panel.world, pose.worldPosition(), 2);
                applyCenteredQuadTransform(panel.hoverHighlight, pose.orientation(), cell, cell, 1.0f, 2);
            }
            updateTooltip(panel, session, layout, pose, transform);
        } else {
            if (panel.hoverHighlight != null && panel.hoverHighlight.isValid()) {
                collapseDisplay(panel.hoverHighlight, panel.frame.orientation(), 2);
            }
            hideTooltip(panel);
        }
    }

    @Override
    public void updateCursorVisual(Player player, SpatialSession session) {
        if (player == null || session == null || session.isClosing()) {
            return;
        }
        RenderedPanel panel = panels.get(session.getSessionId());
        if (panel == null) {
            return;
        }

        ItemStack cursor = session.getVirtualCursor();
        if (cursor == null || cursor.getType().isAir() || cursor.getAmount() <= 0) {
            if (panel.cursorVisible) {
                panel.cursorVisible = false;
                if (panel.cursorItem != null && panel.cursorItem.isValid()) {
                    panel.cursorItem.setItemStack(null);
                    collapseDisplay(panel.cursorItem, panel.frame.orientation(), 1);
                }
                if (panel.cursorCount != null && panel.cursorCount.isValid()) {
                    panel.cursorCount.text(Component.empty());
                    collapseDisplay(panel.cursorCount, panel.frame.orientation(), 1);
                }
            }
            return;
        }

        RenderTransform transform = resolveTransform(session);
        ScreenProfile profile = session.getProfile();
        float scale = Math.max(0.05f, transform.scale());
        float spacing = Math.max(0.12f, profile.slotSpacing());

        CurvedPanelMath.SlotPose pose;
        int hovered = session.getHoveredSlot();
        List<PanelSlotLayout> layouts = session.getSlotLayouts();
        if (hovered >= 0 && hovered < layouts.size()) {
            PanelSlotLayout layout = layouts.get(hovered);
            pose = CurvedPanelMath.computeSlotPose(
                panel.frame,
                layout.localX(),
                layout.localY(),
                layout.localZ() + 0.085f,
                transform
            );
        } else {
            Location eye = player.getEyeLocation();
            double maxDist = plugin.getConfigManager().getMaxInteractionDistance()
                + (session.getActiveMode().isThirdPersonFamily() ? 4.0 : 1.5);
            Vector hit = CurvedPanelMath.intersectRayWithPanelPlane(
                eye.toVector(),
                eye.getDirection(),
                panel.frame,
                maxDist
            );
            if (hit != null) {
                Bounds bounds = computeGridBounds(layouts, spacing);
                Vector clampedHit = clampPointToPanelBounds(hit, panel.frame, bounds, scale, 0.75);
                Vector targetPos = clampedHit.add(panel.frame.normal().clone().multiply(0.085 * scale));
                pose = new CurvedPanelMath.SlotPose(
                    targetPos,
                    new Quaternionf(panel.frame.orientation()),
                    panel.frame.right(),
                    panel.frame.up(),
                    panel.frame.normal(),
                    0.0f
                );
            } else {
                pose = CurvedPanelMath.computeSlotPose(panel.frame, 0.0f, 0.0f, 0.085f, transform);
            }
        }

        panel.cursorVisible = true;
        float itemScale = spacing * 0.72f * scale;
        if (panel.cursorItem != null && panel.cursorItem.isValid()) {
            if (!isSameVisualItem(panel.lastCursorSnapshot, cursor)) {
                panel.cursorItem.setItemStack(cursor.clone());
            }
            moveDisplay(panel.cursorItem, panel.world, pose.worldPosition(), 1);
            panel.cursorItem.setInterpolationDelay(0);
            panel.cursorItem.setInterpolationDuration(1);
            panel.cursorItem.setTransformation(new Transformation(
                new Vector3f(0.0f, 0.0f, 0.0f),
                new Quaternionf(pose.orientation()),
                new Vector3f(itemScale, itemScale, itemScale * 0.25f),
                new Quaternionf()
            ));
        }

        if (panel.cursorCount != null && panel.cursorCount.isValid()) {
            if (cursor.getAmount() > 1) {
                panel.cursorCount.text(Component.text(String.valueOf(cursor.getAmount()), NamedTextColor.YELLOW, TextDecoration.BOLD));
                Vector countPos = pose.worldPosition().clone()
                    .add(pose.right().clone().multiply(0.28f * spacing * scale))
                    .add(pose.up().clone().multiply(-0.30f * spacing * scale))
                    .add(pose.normal().clone().multiply(0.035f * scale));
                moveDisplay(panel.cursorCount, panel.world, countPos, 1);
                float textScale = 0.45f * scale;
                panel.cursorCount.setInterpolationDelay(0);
                panel.cursorCount.setInterpolationDuration(1);
                panel.cursorCount.setTransformation(new Transformation(
                    new Vector3f(0.0f, 0.0f, 0.0f),
                    textOrientation(pose.orientation()),
                    new Vector3f(textScale, textScale, textScale),
                    new Quaternionf()
                ));
            } else {
                panel.cursorCount.text(Component.empty());
                collapseDisplay(panel.cursorCount, pose.orientation(), 1);
            }
        }
        panel.lastCursorSnapshot = cursor.clone();
    }

    @Override
    public void refreshTransform(Player player, SpatialSession session, ScreenProfile updatedProfile) {
        if (player == null || session == null || updatedProfile == null || session.isClosing()) {
            return;
        }
        session.setProfile(updatedProfile);
        RenderedPanel panel = panels.get(session.getSessionId());
        if (panel == null) {
            openPanel(player, session);
            return;
        }

        if (panel.pendingTaskId != -1) {
            Bukkit.getScheduler().cancelTask(panel.pendingTaskId);
            panel.pendingTaskId = -1;
        }

        List<PanelSlotLayout> newLayouts = plugin.getContainerBridge().buildSlotLayouts(
            updatedProfile,
            session.getTopInventory(),
            session.getBottomInventory()
        );
        RenderTransform nextTransform = resolveTransform(session);
        boolean wasCurved = Math.abs(panel.lastCurvature) >= 0.001f;
        boolean nowCurved = Math.abs(nextTransform.curvature()) >= 0.001f;
        boolean layoutChanged = newLayouts.size() != panel.slots.size()
            || panel.lastShowBackground != updatedProfile.showBackground()
            || wasCurved != nowCurved;

        if (layoutChanged) {
            session.setSlotLayouts(newLayouts);
            Location origin = resolveOrigin(player, session);
            if (origin == null || origin.getWorld() == null) {
                return;
            }
            RenderTransform transform = resolveTransform(session);
            CurvedPanelMath.PanelFrame frame = CurvedPanelMath.computePanelFrame(origin, transform);
            panel.destroyImmediate();
            RenderedPanel rebuilt = buildPanelEntities(player, session, origin.getWorld(), frame, transform, newLayouts, false);
            panels.put(session.getSessionId(), rebuilt);
            applyTargetTransforms(rebuilt, frame, transform, 0);
            syncContentsInternal(player, session, rebuilt);
            updateHover(player, session, session.getHoveredSlot());
            updateCursorVisual(player, session);
            return;
        }

        session.setSlotLayouts(newLayouts);
        Location origin = resolveOrigin(player, session);
        if (origin == null || origin.getWorld() == null) {
            return;
        }
        RenderTransform transform = resolveTransform(session);
        CurvedPanelMath.PanelFrame frame = CurvedPanelMath.computePanelFrame(origin, transform);
        panel.frame = frame;
        panel.world = origin.getWorld();
        panel.lastCurvature = transform.curvature();
        panel.renderedHoverSlot = -2;

        int duration = Math.max(1, transform.teleportDurationTicks());
        repositionPanelEntities(panel, session, frame, transform, duration);
        updateHover(player, session, session.getHoveredSlot());
        updateCursorVisual(player, session);
    }

    @Override
    public void closePanel(Player player, SpatialSession session, boolean immediate) {
        if (session == null) {
            return;
        }
        RenderedPanel panel = panels.get(session.getSessionId());
        if (panel == null) {
            return;
        }
        if (panel.pendingTaskId != -1) {
            Bukkit.getScheduler().cancelTask(panel.pendingTaskId);
            panel.pendingTaskId = -1;
        }

        RenderTransform transform = resolveTransform(session);
        int closeTicks = Math.max(0, transform.closeDurationTicks());
        if (immediate || closeTicks == 0 || player == null || !player.isOnline()) {
            panels.remove(session.getSessionId());
            panel.destroyImmediate();
            return;
        }

        for (Entity entity : panel.allEntities()) {
            if (entity instanceof Display display && display.isValid()) {
                collapseDisplay(display, panel.frame.orientation(), closeTicks);
            }
        }
        panel.pendingTaskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            panels.remove(session.getSessionId(), panel);
            panel.destroyImmediate();
        }, closeTicks + 1L).getTaskId();
    }

    @Override
    public int raycastHoveredSlot(Player player, SpatialSession session) {
        if (player == null || session == null || session.isClosing()) {
            return -1;
        }
        RenderedPanel panel = panels.get(session.getSessionId());
        if (panel == null || panel.world == null) {
            return -1;
        }
        Location eye = player.getEyeLocation();
        if (eye.getWorld() == null || !eye.getWorld().getUID().equals(panel.world.getUID())) {
            return -1;
        }
        Vector dir = eye.getDirection();
        if (dir.lengthSquared() < 1e-8) {
            return -1;
        }
        dir.normalize();

        double maxRayDist = plugin.getConfigManager().getMaxInteractionDistance()
            + (session.getActiveMode().isThirdPersonFamily() ? 4.0 : 1.5);
        RenderTransform transform = resolveTransform(session);
        CurvedPanelMath.RaycastHit hit = CurvedPanelMath.raycastSlotDetail(
            eye.toVector(),
            dir,
            maxRayDist,
            panel.frame,
            session.getSlotLayouts(),
            transform,
            session.getProfile().slotSpacing()
        );
        if (hit == null) {
            return -1;
        }

        RayTraceResult blockHit = eye.getWorld().rayTraceBlocks(
            eye,
            dir,
            hit.distance(),
            FluidCollisionMode.NEVER,
            true
        );
        if (blockHit != null && blockHit.getHitBlock() != null) {
            double blockDist = eye.toVector().distance(blockHit.getHitPosition());
            if (blockDist + 0.08 < hit.distance()) {
                return -1;
            }
        }
        return hit.spatialIndex();
    }

    @Override
    public Location raycastMouseWorldTarget(Player player, SpatialSession session) {
        if (player == null || session == null || session.isClosing()) {
            return null;
        }
        int hovered = session.getHoveredSlot();
        if (hovered >= 0) {
            return getSlotWorldLocation(session, hovered);
        }

        Location eye = player.getEyeLocation();
        World eyeWorld = eye.getWorld();
        Vector dir = eye.getDirection();
        if (dir.lengthSquared() < 1e-8) {
            RenderedPanel panel = panels.get(session.getSessionId());
            if (panel != null && panel.world != null) {
                return CurvedPanelMath.toLocation(panel.world, panel.frame.center(), panel.frame.normal());
            }
            return session.getAnchorOrigin();
        }
        dir.normalize();

        RenderTransform transform = resolveTransform(session);
        RenderedPanel panel = panels.get(session.getSessionId());
        if (panel != null && panel.world != null && eyeWorld != null && eyeWorld.getUID().equals(panel.world.getUID())) {
            double maxDist = Math.max(8.0, plugin.getConfigManager().getMaxInteractionDistance() + 4.0);
            Vector hit = CurvedPanelMath.intersectRayWithPanelPlane(eye.toVector(), dir, panel.frame, maxDist);
            if (hit != null) {
                float spacing = Math.max(0.12f, session.getProfile().slotSpacing());
                float scale = Math.max(0.05f, transform.scale());
                Bounds bounds = computeGridBounds(session.getSlotLayouts(), spacing);
                Vector clamped = clampPointToPanelBounds(hit, panel.frame, bounds, scale, 0.6);
                return CurvedPanelMath.toLocation(panel.world, clamped, panel.frame.normal());
            }
        }

        double fallbackDist = Math.max(1.5, transform.distance());
        Vector fallbackPoint = eye.toVector().add(dir.multiply(fallbackDist));
        return new Location(eyeWorld, fallbackPoint.getX(), fallbackPoint.getY(), fallbackPoint.getZ());
    }

    @Override
    public Location getSlotWorldLocation(SpatialSession session, int spatialSlotIndex) {
        if (session == null) {
            return null;
        }
        RenderedPanel panel = panels.get(session.getSessionId());
        if (panel == null || panel.world == null) {
            return session.getAnchorOrigin();
        }
        List<PanelSlotLayout> layouts = session.getSlotLayouts();
        RenderTransform transform = resolveTransform(session);
        if (spatialSlotIndex >= 0 && spatialSlotIndex < layouts.size()) {
            PanelSlotLayout layout = layouts.get(spatialSlotIndex);
            CurvedPanelMath.SlotPose pose = CurvedPanelMath.computeSlotPose(
                panel.frame,
                layout.localX(),
                layout.localY(),
                layout.localZ(),
                transform
            );
            return CurvedPanelMath.toLocation(panel.world, pose.worldPosition(), pose.normal());
        }
        return CurvedPanelMath.toLocation(panel.world, panel.frame.center(), panel.frame.normal());
    }

    @Override
    public void shutdownAll() {
        for (RenderedPanel panel : panels.values()) {
            panel.destroyImmediate();
        }
        panels.clear();
    }

    private RenderedPanel buildPanelEntities(
        Player player,
        SpatialSession session,
        World world,
        CurvedPanelMath.PanelFrame frame,
        RenderTransform transform,
        List<PanelSlotLayout> layouts,
        boolean startCollapsed
    ) {
        RenderedPanel panel = new RenderedPanel(session.getSessionId(), world, frame, session.getProfile(), resolveTransform(session).curvature());
        float spacing = Math.max(0.12f, session.getProfile().slotSpacing());
        Bounds bounds = computeGridBounds(layouts, spacing);
        Quaternionf frameOrientation = frame.orientation();

        if (session.getProfile().showBackground()) {
            float cur = transform.curvature();
            if (Math.abs(cur) < 0.001f) {
                CurvedPanelMath.SlotPose bgPose = CurvedPanelMath.computeSlotPose(
                    frame,
                    bounds.midX,
                    bounds.midY,
                    -0.032f,
                    transform
                );
                TextDisplay bg = spawnTextDisplay(
                    player,
                    world,
                    bgPose.worldPosition(),
                    Component.text("  "),
                    BACKDROP_COLOR,
                    false,
                    TextDisplay.TextAlignment.CENTER,
                    DEFAULT_LINE_WIDTH,
                    startCollapsed,
                    bgPose.orientation()
                );
                panel.backdrops.add(new BackdropSegment(bounds.midX, bounds.midY, bounds.width, bounds.height, bg));
            } else {
                float segWidth = bounds.width / CURVED_BACKDROP_SEGMENTS;
                for (int i = 0; i < CURVED_BACKDROP_SEGMENTS; i++) {
                    float segX = (bounds.minX - bounds.padX) + (i + 0.5f) * segWidth;
                    CurvedPanelMath.SlotPose segPose = CurvedPanelMath.computeSlotPose(
                        frame,
                        segX,
                        bounds.midY,
                        -0.032f,
                        transform
                    );
                    TextDisplay seg = spawnTextDisplay(
                        player,
                        world,
                        segPose.worldPosition(),
                        Component.text("  "),
                        BACKDROP_COLOR,
                        false,
                        TextDisplay.TextAlignment.CENTER,
                        DEFAULT_LINE_WIDTH,
                        startCollapsed,
                        segPose.orientation()
                    );
                    panel.backdrops.add(new BackdropSegment(segX, bounds.midY, segWidth * 1.05f, bounds.height, seg));
                }
            }
        }

        float titleY = bounds.maxY + (spacing * 0.95f);
        CurvedPanelMath.SlotPose titlePose = CurvedPanelMath.computeSlotPose(frame, 0.0f, titleY, -0.01f, transform);
        panel.titleDisplay = spawnTextDisplay(
            player,
            world,
            titlePose.worldPosition(),
            buildTitleComponent(session),
            HEADER_COLOR,
            false,
            TextDisplay.TextAlignment.CENTER,
            TITLE_LINE_WIDTH,
            startCollapsed,
            titlePose.orientation()
        );

        panel.hoverHighlight = spawnTextDisplay(
            player,
            world,
            frame.center(),
            Component.text("  "),
            HOVER_HIGHLIGHT_COLOR,
            false,
            TextDisplay.TextAlignment.CENTER,
            DEFAULT_LINE_WIDTH,
            true,
            frameOrientation
        );

        for (PanelSlotLayout layout : layouts) {
            if (layout.closeButton()) {
                CurvedPanelMath.SlotPose closePose = CurvedPanelMath.computeSlotPose(
                    frame,
                    layout.localX(),
                    layout.localY(),
                    layout.localZ(),
                    transform
                );
                TextDisplay closeBadge = spawnTextDisplay(
                    player,
                    world,
                    closePose.worldPosition(),
                    Component.text(" X ", NamedTextColor.WHITE, TextDecoration.BOLD),
                    CLOSE_BG_COLOR,
                    true,
                    TextDisplay.TextAlignment.CENTER,
                    DEFAULT_LINE_WIDTH,
                    startCollapsed,
                    closePose.orientation()
                );
                panel.slots.add(new SlotVisual(layout, null, null, closeBadge));
            } else {
                CurvedPanelMath.SlotPose tilePose = CurvedPanelMath.computeSlotPose(
                    frame,
                    layout.localX(),
                    layout.localY(),
                    layout.localZ() - 0.015f,
                    transform
                );
                TextDisplay tile = spawnTextDisplay(
                    player,
                    world,
                    tilePose.worldPosition(),
                    Component.text("  "),
                    SLOT_TILE_COLOR,
                    false,
                    TextDisplay.TextAlignment.CENTER,
                    DEFAULT_LINE_WIDTH,
                    startCollapsed,
                    tilePose.orientation()
                );

                CurvedPanelMath.SlotPose itemPose = CurvedPanelMath.computeSlotPose(
                    frame,
                    layout.localX(),
                    layout.localY(),
                    layout.localZ() + 0.03f,
                    transform
                );
                ItemDisplay itemDisplay = spawnItemDisplay(
                    player,
                    world,
                    itemPose.worldPosition(),
                    startCollapsed,
                    itemPose.orientation()
                );

                CurvedPanelMath.SlotPose countPose = CurvedPanelMath.computeSlotPose(
                    frame,
                    layout.localX() + (0.28f * spacing),
                    layout.localY() - (0.32f * spacing),
                    layout.localZ() + 0.075f,
                    transform
                );
                TextDisplay countDisplay = spawnTextDisplay(
                    player,
                    world,
                    countPose.worldPosition(),
                    Component.empty(),
                    TRANSPARENT_COLOR,
                    true,
                    TextDisplay.TextAlignment.RIGHT,
                    DEFAULT_LINE_WIDTH,
                    startCollapsed,
                    countPose.orientation()
                );
                panel.slots.add(new SlotVisual(layout, tile, itemDisplay, countDisplay));
            }
        }

        panel.tooltipDisplay = spawnTextDisplay(
            player,
            world,
            frame.center(),
            Component.empty(),
            TOOLTIP_BG_COLOR,
            true,
            TextDisplay.TextAlignment.LEFT,
            TOOLTIP_LINE_WIDTH,
            true,
            frameOrientation
        );

        panel.cursorItem = spawnItemDisplay(player, world, frame.center(), true, frameOrientation);
        panel.cursorCount = spawnTextDisplay(
            player,
            world,
            frame.center(),
            Component.empty(),
            TRANSPARENT_COLOR,
            true,
            TextDisplay.TextAlignment.RIGHT,
            DEFAULT_LINE_WIDTH,
            true,
            frameOrientation
        );

        return panel;
    }

    private void applyTargetTransforms(
        RenderedPanel panel,
        CurvedPanelMath.PanelFrame frame,
        RenderTransform transform,
        int interpolationTicks
    ) {
        float scale = Math.max(0.05f, transform.scale());
        float spacing = Math.max(0.12f, panel.profile.slotSpacing());

        for (BackdropSegment segment : panel.backdrops) {
            if (segment.display != null && segment.display.isValid()) {
                CurvedPanelMath.SlotPose pose = CurvedPanelMath.computeSlotPose(
                    frame,
                    segment.localX,
                    segment.localY,
                    -0.032f,
                    transform
                );
                applyCenteredQuadTransform(
                    segment.display,
                    pose.orientation(),
                    segment.width * scale,
                    segment.height * scale,
                    1.0f,
                    interpolationTicks
                );
            }
        }

        if (panel.titleDisplay != null && panel.titleDisplay.isValid()) {
            Bounds bounds = computeGridBounds(panel.slots.stream().map(s -> s.layout).toList(), spacing);
            CurvedPanelMath.SlotPose titlePose = CurvedPanelMath.computeSlotPose(
                frame,
                0.0f,
                bounds.maxY + (spacing * 0.95f),
                -0.01f,
                transform
            );
            float titleScale = 0.65f * scale;
            panel.titleDisplay.setInterpolationDelay(0);
            panel.titleDisplay.setInterpolationDuration(interpolationTicks);
            panel.titleDisplay.setTransformation(new Transformation(
                new Vector3f(0.0f, 0.0f, 0.0f),
                textOrientation(titlePose.orientation()),
                new Vector3f(titleScale, titleScale, titleScale),
                new Quaternionf()
            ));
        }

        for (SlotVisual visual : panel.slots) {
            PanelSlotLayout layout = visual.layout;
            if (layout.closeButton()) {
                if (visual.textBadge != null && visual.textBadge.isValid()) {
                    CurvedPanelMath.SlotPose closePose = CurvedPanelMath.computeSlotPose(
                        frame,
                        layout.localX(),
                        layout.localY(),
                        layout.localZ(),
                        transform
                    );
                    float closeScale = 0.72f * scale;
                    Quaternionf closeRot = textOrientation(closePose.orientation());
                    visual.textBadge.setInterpolationDelay(0);
                    visual.textBadge.setInterpolationDuration(interpolationTicks);
                    Vector3f shift = closeRot.transform(new Vector3f(0.0f, -0.10f * closeScale, 0.0f));
                    visual.textBadge.setTransformation(new Transformation(
                        shift,
                        closeRot,
                        new Vector3f(closeScale, closeScale, closeScale),
                        new Quaternionf()
                    ));
                }
            } else {
                if (visual.tile != null && visual.tile.isValid()) {
                    CurvedPanelMath.SlotPose tilePose = CurvedPanelMath.computeSlotPose(
                        frame,
                        layout.localX(),
                        layout.localY(),
                        layout.localZ() - 0.015f,
                        transform
                    );
                    float cell = spacing * 0.86f * scale;
                    applyCenteredQuadTransform(visual.tile, tilePose.orientation(), cell, cell, 1.0f, interpolationTicks);
                }
                if (visual.itemDisplay != null && visual.itemDisplay.isValid()) {
                    CurvedPanelMath.SlotPose itemPose = CurvedPanelMath.computeSlotPose(
                        frame,
                        layout.localX(),
                        layout.localY(),
                        layout.localZ() + 0.03f,
                        transform
                    );
                    float itemScale = spacing * 0.72f * scale;
                    visual.itemDisplay.setInterpolationDelay(0);
                    visual.itemDisplay.setInterpolationDuration(interpolationTicks);
                    visual.itemDisplay.setTransformation(new Transformation(
                        new Vector3f(0.0f, 0.0f, 0.0f),
                        new Quaternionf(itemPose.orientation()),
                        new Vector3f(itemScale, itemScale, itemScale * 0.25f),
                        new Quaternionf()
                    ));
                }
                if (visual.textBadge != null && visual.textBadge.isValid()) {
                    CurvedPanelMath.SlotPose countPose = CurvedPanelMath.computeSlotPose(
                        frame,
                        layout.localX() + (0.28f * spacing),
                        layout.localY() - (0.32f * spacing),
                        layout.localZ() + 0.075f,
                        transform
                    );
                    float countScale = 0.42f * scale;
                    visual.textBadge.setInterpolationDelay(0);
                    visual.textBadge.setInterpolationDuration(interpolationTicks);
                    visual.textBadge.setTransformation(new Transformation(
                        new Vector3f(0.0f, 0.0f, 0.0f),
                        textOrientation(countPose.orientation()),
                        new Vector3f(countScale, countScale, countScale),
                        new Quaternionf()
                    ));
                }
            }
        }
    }

    private void repositionPanelEntities(
        RenderedPanel panel,
        SpatialSession session,
        CurvedPanelMath.PanelFrame frame,
        RenderTransform transform,
        int durationTicks
    ) {
        panel.profile = session.getProfile();
        float scale = Math.max(0.05f, transform.scale());
        float spacing = Math.max(0.12f, session.getProfile().slotSpacing());
        List<PanelSlotLayout> layouts = session.getSlotLayouts();
        Bounds bounds = computeGridBounds(layouts, spacing);

        for (BackdropSegment segment : panel.backdrops) {
            if (segment.display != null && segment.display.isValid()) {
                CurvedPanelMath.SlotPose pose = CurvedPanelMath.computeSlotPose(
                    frame,
                    segment.localX,
                    segment.localY,
                    -0.032f,
                    transform
                );
                moveDisplay(segment.display, panel.world, pose.worldPosition(), durationTicks);
                applyCenteredQuadTransform(
                    segment.display,
                    pose.orientation(),
                    segment.width * scale,
                    segment.height * scale,
                    1.0f,
                    durationTicks
                );
            }
        }

        if (panel.titleDisplay != null && panel.titleDisplay.isValid()) {
            panel.titleDisplay.text(buildTitleComponent(session));
            CurvedPanelMath.SlotPose titlePose = CurvedPanelMath.computeSlotPose(
                frame,
                0.0f,
                bounds.maxY + (spacing * 0.95f),
                -0.01f,
                transform
            );
            moveDisplay(panel.titleDisplay, panel.world, titlePose.worldPosition(), durationTicks);
            float titleScale = 0.65f * scale;
            panel.titleDisplay.setInterpolationDelay(0);
            panel.titleDisplay.setInterpolationDuration(durationTicks);
            panel.titleDisplay.setTransformation(new Transformation(
                new Vector3f(0.0f, 0.0f, 0.0f),
                textOrientation(titlePose.orientation()),
                new Vector3f(titleScale, titleScale, titleScale),
                new Quaternionf()
            ));
        }

        int count = Math.min(panel.slots.size(), layouts.size());
        for (int i = 0; i < count; i++) {
            SlotVisual visual = panel.slots.get(i);
            PanelSlotLayout layout = layouts.get(i);
            visual.layout = layout;
            if (layout.closeButton()) {
                if (visual.textBadge != null && visual.textBadge.isValid()) {
                    CurvedPanelMath.SlotPose closePose = CurvedPanelMath.computeSlotPose(
                        frame,
                        layout.localX(),
                        layout.localY(),
                        layout.localZ(),
                        transform
                    );
                    moveDisplay(visual.textBadge, panel.world, closePose.worldPosition(), durationTicks);
                }
            } else {
                if (visual.tile != null && visual.tile.isValid()) {
                    CurvedPanelMath.SlotPose tilePose = CurvedPanelMath.computeSlotPose(
                        frame,
                        layout.localX(),
                        layout.localY(),
                        layout.localZ() - 0.015f,
                        transform
                    );
                    moveDisplay(visual.tile, panel.world, tilePose.worldPosition(), durationTicks);
                }
                if (visual.itemDisplay != null && visual.itemDisplay.isValid()) {
                    CurvedPanelMath.SlotPose itemPose = CurvedPanelMath.computeSlotPose(
                        frame,
                        layout.localX(),
                        layout.localY(),
                        layout.localZ() + 0.03f,
                        transform
                    );
                    moveDisplay(visual.itemDisplay, panel.world, itemPose.worldPosition(), durationTicks);
                }
                if (visual.textBadge != null && visual.textBadge.isValid()) {
                    CurvedPanelMath.SlotPose countPose = CurvedPanelMath.computeSlotPose(
                        frame,
                        layout.localX() + (0.28f * spacing),
                        layout.localY() - (0.32f * spacing),
                        layout.localZ() + 0.075f,
                        transform
                    );
                    moveDisplay(visual.textBadge, panel.world, countPose.worldPosition(), durationTicks);
                }
            }
        }

        applyTargetTransforms(panel, frame, transform, durationTicks);
    }

    private void syncContentsInternal(Player player, SpatialSession session, RenderedPanel panel) {
        List<PanelSlotLayout> layouts = session.getSlotLayouts();
        int count = Math.min(layouts.size(), panel.slots.size());
        boolean hoveredChanged = false;
        int hovered = session.getHoveredSlot();

        for (int i = 0; i < count; i++) {
            PanelSlotLayout layout = layouts.get(i);
            if (layout.closeButton()) {
                continue;
            }
            SlotVisual visual = panel.slots.get(i);
            ItemStack current = plugin.getContainerBridge().getSlotItem(session, layout);
            if (isSameVisualItem(visual.lastItemSnapshot, current)) {
                continue;
            }
            visual.lastItemSnapshot = current != null ? current.clone() : null;
            if (i == hovered) {
                hoveredChanged = true;
            }
            if (visual.itemDisplay != null && visual.itemDisplay.isValid()) {
                visual.itemDisplay.setItemStack(current != null ? current.clone() : null);
            }
            if (visual.textBadge != null && visual.textBadge.isValid()) {
                if (current != null && current.getAmount() > 1) {
                    visual.textBadge.text(Component.text(String.valueOf(current.getAmount()), NamedTextColor.WHITE, TextDecoration.BOLD));
                } else {
                    visual.textBadge.text(Component.empty());
                }
            }
        }

        if (hoveredChanged && hovered >= 0 && hovered < layouts.size()) {
            PanelSlotLayout layout = layouts.get(hovered);
            RenderTransform transform = resolveTransform(session);
            CurvedPanelMath.SlotPose pose = CurvedPanelMath.computeSlotPose(
                panel.frame,
                layout.localX(),
                layout.localY(),
                layout.localZ(),
                transform
            );
            updateTooltip(panel, session, layout, pose, transform);
        }
    }

    private void updateSlotScale(RenderedPanel panel, SlotVisual visual, ScreenProfile profile, RenderTransform transform, boolean hovered) {
        PanelSlotLayout layout = visual.layout;
        float scale = Math.max(0.05f, transform.scale());
        float spacing = Math.max(0.12f, profile.slotSpacing());
        float mult = hovered ? HOVER_SCALE_MULTIPLIER : 1.0f;

        if (layout.closeButton()) {
            if (visual.textBadge != null && visual.textBadge.isValid()) {
                CurvedPanelMath.SlotPose pose = CurvedPanelMath.computeSlotPose(
                    panel.frame,
                    layout.localX(),
                    layout.localY(),
                    layout.localZ() + (hovered ? 0.02f : 0.0f),
                    transform
                );
                moveDisplay(visual.textBadge, panel.world, pose.worldPosition(), 2);
                float closeScale = 0.72f * scale * mult;
                Quaternionf closeRot = textOrientation(pose.orientation());
                Vector3f shift = closeRot.transform(new Vector3f(0.0f, -0.10f * closeScale, 0.0f));
                visual.textBadge.setInterpolationDelay(0);
                visual.textBadge.setInterpolationDuration(2);
                visual.textBadge.setTransformation(new Transformation(
                    shift,
                    closeRot,
                    new Vector3f(closeScale, closeScale, closeScale),
                    new Quaternionf()
                ));
            }
            return;
        }

        if (visual.itemDisplay != null && visual.itemDisplay.isValid()) {
            CurvedPanelMath.SlotPose itemPose = CurvedPanelMath.computeSlotPose(
                panel.frame,
                layout.localX(),
                layout.localY(),
                layout.localZ() + 0.03f,
                transform
            );
            float itemScale = spacing * 0.72f * scale * mult;
            Vector3f forwardPop = hovered
                ? itemPose.orientation().transform(new Vector3f(0.0f, 0.0f, 0.025f * scale))
                : new Vector3f(0.0f, 0.0f, 0.0f);
            visual.itemDisplay.setInterpolationDelay(0);
            visual.itemDisplay.setInterpolationDuration(2);
            visual.itemDisplay.setTransformation(new Transformation(
                forwardPop,
                new Quaternionf(itemPose.orientation()),
                new Vector3f(itemScale, itemScale, itemScale * 0.25f),
                new Quaternionf()
            ));
        }
    }

    private void updateTooltip(
        RenderedPanel panel,
        SpatialSession session,
        PanelSlotLayout layout,
        CurvedPanelMath.SlotPose slotPose,
        RenderTransform transform
    ) {
        if (panel.tooltipDisplay == null || !panel.tooltipDisplay.isValid()) {
            return;
        }
        if (!session.getProfile().showTooltips()) {
            hideTooltip(panel);
            return;
        }

        Component tooltipText;
        if (layout.closeButton()) {
            tooltipText = Component.text("Close Spatial GUI", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text("[LMB / F] Close", NamedTextColor.GRAY));
        } else {
            ItemStack item = plugin.getContainerBridge().getSlotItem(session, layout);
            if (item == null || item.getType().isAir()) {
                hideTooltip(panel);
                return;
            }
            tooltipText = buildItemTooltip(item);
        }

        float scale = Math.max(0.05f, transform.scale());
        float spacing = Math.max(0.12f, session.getProfile().slotSpacing());
        Vector tooltipPos = slotPose.worldPosition().clone()
            .add(slotPose.right().clone().multiply(spacing * 0.7f * scale))
            .add(slotPose.up().clone().multiply(spacing * 0.2f * scale))
            .add(slotPose.normal().clone().multiply(0.075f * scale));

        panel.tooltipDisplay.text(tooltipText);
        moveDisplay(panel.tooltipDisplay, panel.world, tooltipPos, 2);
        float tipScale = 0.44f * scale;
        panel.tooltipDisplay.setInterpolationDelay(0);
        panel.tooltipDisplay.setInterpolationDuration(2);
        panel.tooltipDisplay.setTransformation(new Transformation(
            new Vector3f(0.0f, 0.0f, 0.0f),
            textOrientation(slotPose.orientation()),
            new Vector3f(tipScale, tipScale, tipScale),
            new Quaternionf()
        ));
    }

    private void hideTooltip(RenderedPanel panel) {
        if (panel.tooltipDisplay != null && panel.tooltipDisplay.isValid()) {
            panel.tooltipDisplay.text(Component.empty());
            collapseDisplay(panel.tooltipDisplay, panel.frame.orientation(), 1);
        }
    }

    private Component buildItemTooltip(ItemStack item) {
        String title = formatItemName(item);
        Component text = Component.text(title, NamedTextColor.AQUA, TextDecoration.BOLD);
        if (item.getAmount() > 1) {
            text = text.append(Component.text(" x" + item.getAmount(), NamedTextColor.GOLD));
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasLore()) {
            List<Component> lore = meta.lore();
            if (lore != null && !lore.isEmpty()) {
                int lines = Math.min(3, lore.size());
                for (int i = 0; i < lines; i++) {
                    String plain = PlainTextComponentSerializer.plainText().serialize(lore.get(i));
                    if (plain.length() > 36) {
                        plain = plain.substring(0, 33) + "...";
                    }
                    text = text.append(Component.newline())
                        .append(Component.text(plain, NamedTextColor.GRAY));
                }
                if (lore.size() > 3) {
                    text = text.append(Component.newline())
                        .append(Component.text("... +" + (lore.size() - 3) + " more", NamedTextColor.DARK_GRAY));
                }
            }
        }

        text = text.append(Component.newline())
            .append(Component.text("[LMB] Grab  [RMB] Split  [Shift] Quick  [F] Close", NamedTextColor.YELLOW));
        return text;
    }

    private static String formatItemName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            Component custom = meta.displayName();
            if (custom != null) {
                String plain = PlainTextComponentSerializer.plainText().serialize(custom);
                if (!plain.isBlank()) {
                    return plain;
                }
            }
        }
        String raw = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder out = new StringBuilder(raw.length());
        boolean capNext = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ') {
                out.append(c);
                capNext = true;
            } else if (capNext) {
                out.append(Character.toUpperCase(c));
                capNext = false;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private Component buildTitleComponent(SpatialSession session) {
        ViewMode mode = session.getActiveMode();
        String badge = switch (mode) {
            case THIRD_PERSON -> "[TP]";
            case THIRD_PERSON_FRONT -> "[TP-F]";
            default -> "[FP]";
        };
        NamedTextColor badgeColor = switch (mode) {
            case THIRD_PERSON -> NamedTextColor.DARK_PURPLE;
            case THIRD_PERSON_FRONT -> NamedTextColor.DARK_AQUA;
            default -> NamedTextColor.DARK_BLUE;
        };
        String screenTitle = formatScreenName(session.getScreenType());
        Component result = Component.text(badge + " ", badgeColor, TextDecoration.BOLD)
            .append(Component.text(screenTitle, NamedTextColor.DARK_GRAY, TextDecoration.BOLD));
        if (session.isEditorPreview()) {
            result = result.append(Component.text(" (PREVIEW)", NamedTextColor.GOLD, TextDecoration.BOLD));
        }
        return result;
    }

    private static String formatScreenName(ScreenType type) {
        return switch (type) {
            case CHEST -> "Chest";
            case DOUBLE_CHEST -> "Double Chest";
            case BARREL -> "Barrel";
            case SHULKER_BOX -> "Shulker Box";
            case ENDER_CHEST -> "Ender Chest";
            case CRAFTING -> "Crafting";
            case FURNACE -> "Furnace";
            case PLAYER_INVENTORY -> "Inventory";
            case GENERIC -> "Container";
        };
    }

    private TextDisplay spawnTextDisplay(
        Player player,
        World world,
        Vector position,
        Component text,
        Color background,
        boolean shadowed,
        TextDisplay.TextAlignment alignment,
        int lineWidth,
        boolean startCollapsed,
        Quaternionf panelOrientation
    ) {
        Location loc = new Location(world, position.getX(), position.getY(), position.getZ(), 0.0f, 0.0f);
        TextDisplay entity = world.spawn(loc, TextDisplay.class, display -> {
            display.setPersistent(false);
            display.setVisibleByDefault(false);
            display.setBillboard(Display.Billboard.FIXED);
            display.setShadowRadius(0.0f);
            display.setShadowStrength(0.0f);
            display.setViewRange(VIEW_RANGE);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setDefaultBackground(false);
            display.setBackgroundColor(background);
            display.setShadowed(shadowed);
            display.setSeeThrough(false);
            display.setAlignment(alignment);
            display.setLineWidth(lineWidth);
            display.text(text);
            if (startCollapsed) {
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(0);
                display.setTransformation(new Transformation(
                    new Vector3f(0.0f, 0.0f, 0.0f),
                    textOrientation(panelOrientation),
                    new Vector3f(COLLAPSED_SCALE, COLLAPSED_SCALE, COLLAPSED_SCALE),
                    new Quaternionf()
                ));
            }
        });
        player.showEntity(plugin, entity);
        return entity;
    }

    private ItemDisplay spawnItemDisplay(
        Player player,
        World world,
        Vector position,
        boolean startCollapsed,
        Quaternionf panelOrientation
    ) {
        Location loc = new Location(world, position.getX(), position.getY(), position.getZ(), 0.0f, 0.0f);
        ItemDisplay entity = world.spawn(loc, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setVisibleByDefault(false);
            display.setBillboard(Display.Billboard.FIXED);
            display.setShadowRadius(0.0f);
            display.setShadowStrength(0.0f);
            display.setViewRange(VIEW_RANGE);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
            display.setItemStack(null);
            if (startCollapsed) {
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(0);
                display.setTransformation(new Transformation(
                    new Vector3f(0.0f, 0.0f, 0.0f),
                    new Quaternionf(panelOrientation),
                    new Vector3f(COLLAPSED_SCALE, COLLAPSED_SCALE, COLLAPSED_SCALE),
                    new Quaternionf()
                ));
            }
        });
        player.showEntity(plugin, entity);
        return entity;
    }

    private static void moveDisplay(Display display, World world, Vector pos, int teleportTicks) {
        display.setTeleportDuration(Math.max(0, teleportTicks));
        display.teleport(new Location(world, pos.getX(), pos.getY(), pos.getZ(), 0.0f, 0.0f));
    }

    private static Quaternionf textOrientation(Quaternionf panelOrientation) {
        return new Quaternionf(panelOrientation).rotateY((float) Math.PI);
    }

    private static void applyCenteredQuadTransform(
        Display display,
        Quaternionf panelOrientation,
        float targetWidth,
        float targetHeight,
        float zScale,
        int interpolationTicks
    ) {
        float sx = Math.max(0.01f, targetWidth / QUAD_BASE_WIDTH);
        float sy = Math.max(0.01f, targetHeight / QUAD_BASE_HEIGHT);
        Quaternionf orientation = display instanceof TextDisplay
            ? textOrientation(panelOrientation)
            : new Quaternionf(panelOrientation);
        Vector3f translation = orientation.transform(new Vector3f(0.0f, -0.5f * targetHeight, 0.0f));
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(Math.max(0, interpolationTicks));
        display.setTransformation(new Transformation(
            translation,
            orientation,
            new Vector3f(sx, sy, zScale),
            new Quaternionf()
        ));
    }

    private static void collapseDisplay(Display display, Quaternionf panelOrientation, int interpolationTicks) {
        Quaternionf orientation = display instanceof TextDisplay
            ? textOrientation(panelOrientation)
            : new Quaternionf(panelOrientation);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(Math.max(0, interpolationTicks));
        display.setTransformation(new Transformation(
            new Vector3f(0.0f, 0.0f, 0.0f),
            orientation,
            new Vector3f(COLLAPSED_SCALE, COLLAPSED_SCALE, COLLAPSED_SCALE),
            new Quaternionf()
        ));
    }

    private static Vector clampPointToPanelBounds(
        Vector hit,
        CurvedPanelMath.PanelFrame frame,
        Bounds bounds,
        float scale,
        double extraMargin
    ) {
        Vector delta = hit.clone().subtract(frame.center());
        double lx = delta.dot(frame.right());
        double ly = delta.dot(frame.up());
        double maxHalfW = Math.max(0.75, (bounds.width * 0.5 + extraMargin) * scale);
        double maxHalfH = Math.max(0.75, (bounds.height * 0.5 + extraMargin) * scale);
        double clampedX = Math.max(-maxHalfW, Math.min(maxHalfW, lx));
        double clampedY = Math.max(-maxHalfH, Math.min(maxHalfH, ly));
        return frame.center().clone()
            .add(frame.right().clone().multiply(clampedX))
            .add(frame.up().clone().multiply(clampedY));
    }

    private static Location resolveOrigin(Player player, SpatialSession session) {
        ViewMode mode = session.getActiveMode();
        if (mode == ViewMode.THIRD_PERSON_FRONT) {
            Location anchor = firstValidLocation(session.getAnchorOrigin(), session.getEyeOrigin(), player.getEyeLocation());
            if (anchor == null) {
                return null;
            }
            Location frontOrigin = anchor.clone();
            frontOrigin.setPitch(0.0f);
            ScreenProfile profile = session.getProfile();
            if (profile != null && profile.thirdPersonSettings() != null) {
                ThirdPersonSettings tp = profile.thirdPersonSettings();
                float frontYaw = frontOrigin.getYaw() + tp.avatarBodyYawOffset() + tp.frontCameraYawOffset();
                frontOrigin.setYaw(normalizeYawDegrees(frontYaw));
            }
            return frontOrigin;
        }
        if (mode == ViewMode.THIRD_PERSON) {
            Location anchor = firstValidLocation(session.getAnchorOrigin(), session.getEyeOrigin(), player.getEyeLocation());
            if (anchor == null) {
                return null;
            }
            Location tpOrigin = anchor.clone();
            tpOrigin.setPitch(0.0f);
            return tpOrigin;
        }
        Location eye = firstValidLocation(session.getEyeOrigin(), session.getAnchorOrigin(), player.getEyeLocation());
        return eye != null ? eye.clone() : null;
    }

    private static Location firstValidLocation(Location first, Location second, Location fallback) {
        if (first != null && first.getWorld() != null) {
            return first;
        }
        if (second != null && second.getWorld() != null) {
            return second;
        }
        return fallback;
    }

    private static float normalizeYawDegrees(float yaw) {
        double wrapped = yaw % 360.0;
        if (wrapped >= 180.0) {
            wrapped -= 360.0;
        } else if (wrapped < -180.0) {
            wrapped += 360.0;
        }
        return (float) wrapped;
    }

    private static RenderTransform resolveTransform(SpatialSession session) {
        ScreenProfile profile = session.getProfile();
        if (session.getActiveMode().isThirdPersonFamily()) {
            return profile.thirdPersonTransform();
        }
        return profile.firstPersonTransform();
    }

    private static boolean isSameVisualItem(ItemStack a, ItemStack b) {
        boolean emptyA = a == null || a.getType() == Material.AIR || a.getAmount() <= 0;
        boolean emptyB = b == null || b.getType() == Material.AIR || b.getAmount() <= 0;
        if (emptyA && emptyB) {
            return true;
        }
        if (emptyA || emptyB) {
            return false;
        }
        return a.getAmount() == b.getAmount() && Objects.equals(a, b);
    }

    private static Bounds computeGridBounds(List<PanelSlotLayout> layouts, float spacing) {
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        boolean found = false;
        for (PanelSlotLayout layout : layouts) {
            if (layout.closeButton()) {
                continue;
            }
            found = true;
            minX = Math.min(minX, layout.localX());
            maxX = Math.max(maxX, layout.localX());
            minY = Math.min(minY, layout.localY());
            maxY = Math.max(maxY, layout.localY());
        }
        if (!found) {
            minX = -spacing;
            maxX = spacing;
            minY = -spacing;
            maxY = spacing;
        }
        float padX = spacing * 0.72f;
        float padY = spacing * 0.72f;
        float width = (maxX - minX) + (2.0f * padX);
        float height = (maxY - minY) + (2.0f * padY);
        float midX = (minX + maxX) * 0.5f;
        float midY = (minY + maxY) * 0.5f;
        return new Bounds(minX, maxX, minY, maxY, padX, padY, width, height, midX, midY);
    }

    private record Bounds(
        float minX,
        float maxX,
        float minY,
        float maxY,
        float padX,
        float padY,
        float width,
        float height,
        float midX,
        float midY
    ) {}

    private static final class BackdropSegment {
        private final float localX;
        private final float localY;
        private final float width;
        private final float height;
        private final TextDisplay display;

        private BackdropSegment(float localX, float localY, float width, float height, TextDisplay display) {
            this.localX = localX;
            this.localY = localY;
            this.width = width;
            this.height = height;
            this.display = display;
        }
    }

    private static final class SlotVisual {
        private PanelSlotLayout layout;
        private final TextDisplay tile;
        private final ItemDisplay itemDisplay;
        private final TextDisplay textBadge;
        private ItemStack lastItemSnapshot;

        private SlotVisual(PanelSlotLayout layout, TextDisplay tile, ItemDisplay itemDisplay, TextDisplay textBadge) {
            this.layout = layout;
            this.tile = tile;
            this.itemDisplay = itemDisplay;
            this.textBadge = textBadge;
        }
    }

    private static final class RenderedPanel {
        private final UUID sessionId;
        private World world;
        private CurvedPanelMath.PanelFrame frame;
        private ScreenProfile profile;
        private float lastCurvature;
        private final boolean lastShowBackground;
        private final List<BackdropSegment> backdrops;
        private final List<SlotVisual> slots;
        private TextDisplay titleDisplay;
        private TextDisplay hoverHighlight;
        private TextDisplay tooltipDisplay;
        private ItemDisplay cursorItem;
        private TextDisplay cursorCount;
        private ItemStack lastCursorSnapshot;
        private boolean cursorVisible;
        private int renderedHoverSlot;
        private int pendingTaskId;

        private RenderedPanel(
            UUID sessionId,
            World world,
            CurvedPanelMath.PanelFrame frame,
            ScreenProfile profile,
            float lastCurvature
        ) {
            this.sessionId = sessionId;
            this.world = world;
            this.frame = frame;
            this.profile = profile;
            this.lastCurvature = lastCurvature;
            this.lastShowBackground = profile.showBackground();
            this.backdrops = new ArrayList<>();
            this.slots = new ArrayList<>();
            this.renderedHoverSlot = -2;
            this.pendingTaskId = -1;
        }

        private List<Entity> allEntities() {
            List<Entity> all = new ArrayList<>();
            for (BackdropSegment seg : backdrops) {
                if (seg.display != null) {
                    all.add(seg.display);
                }
            }
            if (titleDisplay != null) {
                all.add(titleDisplay);
            }
            if (hoverHighlight != null) {
                all.add(hoverHighlight);
            }
            for (SlotVisual visual : slots) {
                if (visual.tile != null) {
                    all.add(visual.tile);
                }
                if (visual.itemDisplay != null) {
                    all.add(visual.itemDisplay);
                }
                if (visual.textBadge != null) {
                    all.add(visual.textBadge);
                }
            }
            if (tooltipDisplay != null) {
                all.add(tooltipDisplay);
            }
            if (cursorItem != null) {
                all.add(cursorItem);
            }
            if (cursorCount != null) {
                all.add(cursorCount);
            }
            return all;
        }

        private void destroyImmediate() {
            if (pendingTaskId != -1) {
                Bukkit.getScheduler().cancelTask(pendingTaskId);
                pendingTaskId = -1;
            }
            for (Entity entity : allEntities()) {
                if (entity != null && entity.isValid()) {
                    entity.remove();
                }
            }
            backdrops.clear();
            slots.clear();
        }
    }
}
