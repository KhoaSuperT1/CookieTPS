package com.cookietps.camera;

import com.cookietps.CookieTPSPlugin;
import com.cookietps.api.ThirdPersonCameraController;
import com.cookietps.model.CameraSnapshot;
import com.cookietps.model.ScreenProfile;
import com.cookietps.model.SpatialSession;
import com.cookietps.model.ThirdPersonSettings;
import com.cookietps.model.ViewMode;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpectatorCameraController implements ThirdPersonCameraController, Listener {
    private static final double CLAMP_MIN_DISTANCE = 0.35;
    private static final double MIN_PASSENGER_EYE_OFFSET = 0.2;
    private static final double MAX_PASSENGER_EYE_OFFSET = 3.0;
    private static final double DEFAULT_VEHICLE_ATTACHMENT_OFFSET = 0.6;
    private static final int DEFAULT_CLOSE_TICKS = 8;

    private final CookieTPSPlugin plugin;
    private final Map<UUID, CameraRig> rigs;
    private final Set<UUID> exitingPlayers;

    public SpectatorCameraController(CookieTPSPlugin plugin) {
        this.plugin = plugin;
        this.rigs = new ConcurrentHashMap<>();
        this.exitingPlayers = ConcurrentHashMap.newKeySet();
    }

    @Override
    public boolean enterThirdPerson(Player player, SpatialSession session) {
        if (player == null || !player.isOnline() || session == null || session.getProfile() == null) {
            return false;
        }
        ThirdPersonSettings settings = session.getProfile().thirdPersonSettings();
        if (settings == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        if (rigs.containsKey(uuid)) {
            exitThirdPerson(player, true);
        }

        Location feetOrigin = resolveFeetOrigin(player, session);
        Location eyeOrigin = resolveEyeOrigin(player, session);
        World world = eyeOrigin.getWorld();
        if (world == null || feetOrigin.getWorld() == null) {
            return false;
        }

        ViewMode mode = resolveCameraMode(session, session.getProfile());
        Location desiredCamera = WallOcclusionCalculator.computeDesiredCameraLocation(eyeOrigin, settings, mode);
        Double hitDistance = rayTraceHitDistance(eyeOrigin, desiredCamera);
        Location targetCamera = WallOcclusionCalculator.resolveCameraLocation(
            eyeOrigin,
            settings,
            mode,
            hitDistance,
            WallOcclusionCalculator.DEFAULT_SAFETY_MARGIN
        );
        if (targetCamera == null) {
            return false;
        }

        Location savedLocation = player.getLocation();
        if (savedLocation.getWorld() == null) {
            return false;
        }
        boolean previousInvisible = player.isInvisible();
        boolean previousInvulnerable = player.isInvulnerable();
        CameraSnapshot snapshot = new CameraSnapshot(
            uuid,
            savedLocation.getWorld().getUID(),
            savedLocation.getX(),
            savedLocation.getY(),
            savedLocation.getZ(),
            savedLocation.getYaw(),
            savedLocation.getPitch(),
            player.getGameMode(),
            player.getAllowFlight(),
            player.isFlying(),
            previousInvisible,
            previousInvulnerable,
            System.currentTimeMillis()
        );
        plugin.getCrashRecoveryStore().saveSnapshot(snapshot);

        float bodyYaw = feetOrigin.getYaw() + settings.avatarBodyYawOffset();
        AvatarStandInRig avatarRig = AvatarStandInRig.spawn(plugin, player, feetOrigin, bodyYaw);
        if (avatarRig == null) {
            plugin.getCrashRecoveryStore().removeSnapshot(uuid);
            return false;
        }

        player.setInvisible(true);
        player.setInvulnerable(true);

        if (mode == ViewMode.THIRD_PERSON_FRONT) {
            player.setRotation(targetCamera.getYaw(), targetCamera.getPitch());
        }

        double initialEyeOffset = clampEyeOffset(player.getEyeHeight() - DEFAULT_VEHICLE_ATTACHMENT_OFFSET);
        Location initialMountLoc = toMountLocation(eyeOrigin, initialEyeOffset);
        Interaction cameraMount = world.spawn(initialMountLoc, Interaction.class, entity -> {
            entity.setInteractionWidth(0.0f);
            entity.setInteractionHeight(0.0f);
            entity.setResponsive(false);
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);
            entity.setInvulnerable(true);
            entity.setGravity(false);
        });
        player.showEntity(plugin, cameraMount);

        if (!cameraMount.addPassenger(player) || !cameraMount.getPassengers().contains(player)) {
            cameraMount.remove();
            avatarRig.destroy();
            player.setInvisible(previousInvisible);
            player.setInvulnerable(previousInvulnerable);
            plugin.getCrashRecoveryStore().removeSnapshot(uuid);
            return false;
        }

        double measuredOffset = player.getEyeLocation().getY() - cameraMount.getLocation().getY();
        double calibratedOffset = Double.isFinite(measuredOffset)
            && measuredOffset >= MIN_PASSENGER_EYE_OFFSET
            && measuredOffset <= MAX_PASSENGER_EYE_OFFSET
            ? measuredOffset
            : initialEyeOffset;
        if (Math.abs(calibratedOffset - initialEyeOffset) > 0.01) {
            cameraMount.teleport(
                toMountLocation(eyeOrigin, calibratedOffset),
                TeleportFlag.EntityState.RETAIN_PASSENGERS
            );
        }

        CameraRig rig = new CameraRig(
            snapshot,
            avatarRig,
            cameraMount,
            feetOrigin,
            eyeOrigin,
            targetCamera,
            calibratedOffset,
            mode,
            settings
        );
        rigs.put(uuid, rig);

        int openTicks = Math.max(0, settings.openTransitionTicks());
        if (openTicks <= 0) {
            rig.currentCameraEye = targetCamera.clone();
            cameraMount.teleport(
                toMountLocation(targetCamera, calibratedOffset),
                TeleportFlag.EntityState.RETAIN_PASSENGERS
            );
            return true;
        }

        final int[] stepHolder = {0};
        rig.openTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()
                || exitingPlayers.contains(uuid)
                || rigs.get(uuid) != rig
                || rig.stopTriggered
                || rig.closingAnimated
                || !rig.cameraMount.isValid()) {
                cancelTask(rig.openTask);
                rig.openTask = null;
                return;
            }
            stepHolder[0]++;
            double progress = (double) stepHolder[0] / openTicks;
            double eased = CameraTransitionMath.easeOutCubic(progress);
            Location stepEye = CameraTransitionMath.interpolateLocation(rig.openStartEye, rig.targetCameraEye, eased);
            rig.currentCameraEye = stepEye.clone();
            if (!rig.cameraMount.getPassengers().contains(player)) {
                rig.cameraMount.addPassenger(player);
            }
            rig.cameraMount.teleport(
                toMountLocation(stepEye, rig.passengerEyeOffset),
                TeleportFlag.EntityState.RETAIN_PASSENGERS
            );
            if (stepHolder[0] >= openTicks) {
                cancelTask(rig.openTask);
                rig.openTask = null;
            }
        }, 1L, 1L);

        return true;
    }

    @Override
    public void updateCameraAndAvatar(Player player, SpatialSession session, Location hoveredWorldTarget) {
        if (player == null || !player.isOnline() || session == null || session.getProfile() == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (exitingPlayers.contains(uuid)) {
            return;
        }
        CameraRig rig = rigs.get(uuid);
        if (rig == null || rig.stopTriggered || rig.closingAnimated) {
            return;
        }
        ThirdPersonSettings settings = session.getProfile().thirdPersonSettings();
        if (settings == null) {
            return;
        }

        if (rig.cameraMount.isValid() && !rig.cameraMount.getPassengers().contains(player)) {
            rig.cameraMount.addPassenger(player);
        }

        Location currentFeetOrigin = resolveFeetOrigin(player, session);
        Location currentEyeOrigin = resolveEyeOrigin(player, session);
        ViewMode currentMode = resolveCameraMode(session, session.getProfile());
        if (!settings.equals(rig.lastSettings)
            || rig.mode != currentMode
            || hasLocationChanged(rig.eyeOrigin, currentEyeOrigin)
            || hasLocationChanged(rig.feetOrigin, currentFeetOrigin)) {
            repositionRig(rig, currentFeetOrigin, currentEyeOrigin, currentMode, settings);
        }

        if (rig.avatarRig.isValid()) {
            if (settings.headTrackHoveredSlot() && hoveredWorldTarget != null) {
                Location headLoc = rig.avatarRig.getHeadLocation();
                EulerAngle pose = WallOcclusionCalculator.computeHeadPose(
                    headLoc,
                    hoveredWorldTarget,
                    rig.avatarRig.getBodyYaw()
                );
                rig.avatarRig.setHeadPose(pose);
            } else {
                rig.avatarRig.setHeadPose(EulerAngle.ZERO);
            }
        }
    }

    @Override
    public boolean checkClearance(Location avatarEyeLocation, ScreenProfile profile) {
        if (profile == null) {
            return false;
        }
        ViewMode mode = profile.viewMode() == ViewMode.THIRD_PERSON_FRONT
            ? ViewMode.THIRD_PERSON_FRONT
            : ViewMode.THIRD_PERSON;
        return checkClearance(avatarEyeLocation, profile, mode);
    }

    public boolean checkClearance(Location avatarEyeLocation, ScreenProfile profile, ViewMode mode) {
        if (avatarEyeLocation == null || avatarEyeLocation.getWorld() == null || profile == null) {
            return false;
        }
        ThirdPersonSettings settings = profile.thirdPersonSettings();
        if (settings == null) {
            return false;
        }
        Location desiredCamera = WallOcclusionCalculator.computeDesiredCameraLocation(avatarEyeLocation, settings, mode);
        double desiredDistance = WallOcclusionCalculator.distanceBetween(avatarEyeLocation, desiredCamera);
        Double hitDistance = rayTraceHitDistance(avatarEyeLocation, desiredCamera);
        double safeDistance = WallOcclusionCalculator.computeSafeDistance(
            desiredDistance,
            hitDistance,
            WallOcclusionCalculator.DEFAULT_SAFETY_MARGIN,
            settings.minClearance()
        );
        return safeDistance >= 0.0;
    }

    @Override
    public void exitThirdPerson(Player player, boolean restoreState) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!exitingPlayers.add(uuid)) {
            return;
        }
        try {
            CameraRig rig = rigs.remove(uuid);
            if (rig != null) {
                cleanupRig(rig);
            }
            if (restoreState) {
                CameraSnapshot snapshot = rig != null
                    ? rig.snapshot
                    : plugin.getCrashRecoveryStore().getSnapshot(uuid).orElse(null);
                if (snapshot != null && player.isOnline()) {
                    restoreSnapshot(player, snapshot);
                    plugin.getCrashRecoveryStore().removeSnapshot(uuid);
                }
            } else {
                plugin.getCrashRecoveryStore().removeSnapshot(uuid);
            }
        } finally {
            exitingPlayers.remove(uuid);
        }
    }

    @Override
    public void exitThirdPersonAnimated(Player player, Runnable onFinished) {
        if (player == null) {
            runSafely(onFinished);
            return;
        }
        UUID uuid = player.getUniqueId();
        CameraRig rig = rigs.get(uuid);
        if (rig == null || !player.isOnline()) {
            exitThirdPerson(player, true);
            runSafely(onFinished);
            return;
        }
        if (rig.closingAnimated) {
            return;
        }
        rig.closingAnimated = true;
        cancelTask(rig.openTask);
        rig.openTask = null;

        int closeTicks = rig.lastSettings != null
            ? Math.max(0, rig.lastSettings.closeTransitionTicks())
            : DEFAULT_CLOSE_TICKS;
        if (closeTicks <= 0 || !rig.cameraMount.isValid()) {
            exitThirdPerson(player, true);
            runSafely(onFinished);
            return;
        }

        Location startEye = rig.currentCameraEye != null
            ? rig.currentCameraEye.clone()
            : rig.targetCameraEye.clone();
        Location endEye = rig.eyeOrigin.clone();
        final int[] stepHolder = {0};
        rig.closeTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || rigs.get(uuid) != rig) {
                cancelTask(rig.closeTask);
                rig.closeTask = null;
                exitThirdPerson(player, true);
                runSafely(onFinished);
                return;
            }
            stepHolder[0]++;
            if (rig.cameraMount.isValid()) {
                double progress = (double) stepHolder[0] / closeTicks;
                double eased = CameraTransitionMath.easeInOutCubic(progress);
                Location stepEye = CameraTransitionMath.interpolateLocation(startEye, endEye, eased);
                rig.currentCameraEye = stepEye.clone();
                if (!rig.cameraMount.getPassengers().contains(player)) {
                    rig.cameraMount.addPassenger(player);
                }
                rig.cameraMount.teleport(
                    toMountLocation(stepEye, rig.passengerEyeOffset),
                    TeleportFlag.EntityState.RETAIN_PASSENGERS
                );
            }
            if (stepHolder[0] >= closeTicks) {
                cancelTask(rig.closeTask);
                rig.closeTask = null;
                exitThirdPerson(player, true);
                runSafely(onFinished);
            }
        }, 1L, 1L);
    }

    @Override
    public boolean isInThirdPerson(UUID playerUuid) {
        return playerUuid != null && rigs.containsKey(playerUuid);
    }

    @Override
    public void recoverOnJoin(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Optional<CameraSnapshot> optionalSnapshot = plugin.getCrashRecoveryStore().getSnapshot(uuid);
        if (optionalSnapshot.isEmpty()) {
            return;
        }
        restoreSnapshot(player, optionalSnapshot.get());
        plugin.getCrashRecoveryStore().removeSnapshot(uuid);
    }

    @Override
    public void recoverAllPending() {
        List<CameraSnapshot> snapshots = plugin.getCrashRecoveryStore().loadAllSnapshots();
        for (CameraSnapshot snapshot : snapshots) {
            Player online = Bukkit.getPlayer(snapshot.playerUuid());
            if (online != null && online.isOnline()) {
                restoreSnapshot(online, snapshot);
                plugin.getCrashRecoveryStore().removeSnapshot(snapshot.playerUuid());
            }
        }
    }

    @Override
    public void shutdownAll() {
        List<UUID> activeIds = new ArrayList<>(rigs.keySet());
        for (UUID uuid : activeIds) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                exitThirdPerson(player, true);
            } else {
                CameraRig rig = rigs.remove(uuid);
                if (rig != null) {
                    cleanupRig(rig);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (exitingPlayers.contains(uuid)) {
            return;
        }
        CameraRig rig = rigs.get(uuid);
        if (rig == null || rig.cameraMount == null || !rig.cameraMount.isValid()) {
            return;
        }
        if (!event.getDismounted().getUniqueId().equals(rig.cameraMount.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        if (rig.stopTriggered || rig.closingAnimated) {
            return;
        }
        rig.stopTriggered = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            SpatialSession session = plugin.getInputHandler().getSession(uuid);
            if (session != null && session.isEditorPreview() && plugin.getEditorController().isEditing(uuid)) {
                plugin.getEditorController().switchPreviewMode(player, ViewMode.FIRST_PERSON);
                if (isInThirdPerson(uuid)) {
                    exitThirdPerson(player, true);
                }
                return;
            }
            if (plugin.getInputHandler().hasActiveSession(uuid)) {
                plugin.getInputHandler().closeSession(player, false);
            } else if (isInThirdPerson(uuid)) {
                exitThirdPersonAnimated(player, null);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        recoverOnJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isInThirdPerson(player.getUniqueId())) {
            exitThirdPerson(player, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        if (!isInThirdPerson(uuid)) {
            return;
        }
        CameraRig rig = rigs.get(uuid);
        CameraSnapshot snapshot = rig != null ? rig.snapshot : null;
        exitThirdPerson(player, false);
        if (snapshot != null && player.isOnline()) {
            player.setInvisible(snapshot.invisible());
            player.setInvulnerable(snapshot.invulnerable());
            if (snapshot.gameMode() != null) {
                player.setGameMode(snapshot.gameMode());
            }
        }
    }

    private void repositionRig(
        CameraRig rig,
        Location feetOrigin,
        Location eyeOrigin,
        ViewMode mode,
        ThirdPersonSettings settings
    ) {
        if (feetOrigin == null || eyeOrigin == null || eyeOrigin.getWorld() == null) {
            return;
        }
        Location desiredCamera = WallOcclusionCalculator.computeDesiredCameraLocation(eyeOrigin, settings, mode);
        Double hitDistance = rayTraceHitDistance(eyeOrigin, desiredCamera);
        Location resolved = WallOcclusionCalculator.resolveCameraLocation(
            eyeOrigin,
            settings,
            mode,
            hitDistance,
            WallOcclusionCalculator.DEFAULT_SAFETY_MARGIN
        );
        if (resolved == null) {
            double rayLength = WallOcclusionCalculator.distanceBetween(eyeOrigin, desiredCamera);
            double clamped = hitDistance != null && hitDistance >= 0.0
                ? Math.max(CLAMP_MIN_DISTANCE, Math.min(rayLength, hitDistance - WallOcclusionCalculator.DEFAULT_SAFETY_MARGIN))
                : rayLength;
            resolved = WallOcclusionCalculator.computeCameraLocationAtDistance(eyeOrigin, desiredCamera, clamped);
        }
        rig.targetCameraEye = resolved.clone();
        if (rig.openTask == null && rig.cameraMount.isValid()) {
            rig.currentCameraEye = resolved.clone();
            rig.cameraMount.teleport(
                toMountLocation(resolved, rig.passengerEyeOffset),
                TeleportFlag.EntityState.RETAIN_PASSENGERS
            );
        }
        rig.avatarRig.updateBodyRotation(feetOrigin, feetOrigin.getYaw() + settings.avatarBodyYawOffset());
        rig.feetOrigin = feetOrigin.clone();
        rig.eyeOrigin = eyeOrigin.clone();
        rig.mode = mode;
        rig.lastSettings = settings;
    }

    private Double rayTraceHitDistance(Location avatarEye, Location desiredCamera) {
        World world = avatarEye.getWorld();
        if (world == null) {
            return null;
        }
        Vector delta = desiredCamera.toVector().subtract(avatarEye.toVector());
        double rayLength = delta.length();
        if (rayLength < 1.0e-5) {
            return null;
        }
        Vector direction = delta.normalize();
        RayTraceResult result = world.rayTraceBlocks(
            avatarEye,
            direction,
            rayLength + WallOcclusionCalculator.DEFAULT_SAFETY_MARGIN,
            FluidCollisionMode.NEVER,
            true
        );
        if (result == null) {
            return null;
        }
        return result.getHitPosition().distance(avatarEye.toVector());
    }

    private void restoreSnapshot(Player player, CameraSnapshot snapshot) {
        player.setInvisible(snapshot.invisible());
        player.setInvulnerable(snapshot.invulnerable());
        if (snapshot.gameMode() != null) {
            player.setGameMode(snapshot.gameMode());
        }
        Location restoreLocation = snapshot.toLocation();
        if (restoreLocation != null) {
            player.teleport(restoreLocation);
        }
        player.setAllowFlight(snapshot.allowFlight());
        player.setFlying(snapshot.allowFlight() && snapshot.flying());
    }

    private void cleanupRig(CameraRig rig) {
        cancelTask(rig.openTask);
        rig.openTask = null;
        cancelTask(rig.closeTask);
        rig.closeTask = null;
        if (rig.cameraMount != null && rig.cameraMount.isValid()) {
            rig.cameraMount.eject();
            rig.cameraMount.remove();
        }
        if (rig.avatarRig != null) {
            rig.avatarRig.destroy();
        }
    }

    private static Location resolveFeetOrigin(Player player, SpatialSession session) {
        Location feet = session.getFeetOrigin();
        if (feet == null) {
            feet = player.getLocation();
        }
        Location clone = feet.clone();
        clone.setPitch(0.0f);
        return clone;
    }

    private static Location resolveEyeOrigin(Player player, SpatialSession session) {
        Location eye = session.getEyeOrigin();
        if (eye == null) {
            eye = session.getAnchorOrigin();
        }
        if (eye == null) {
            eye = player.getEyeLocation();
        }
        return eye.clone();
    }

    private static ViewMode resolveCameraMode(SpatialSession session, ScreenProfile profile) {
        if (session != null && session.getActiveMode() != null && session.getActiveMode().isThirdPersonFamily()) {
            return session.getActiveMode();
        }
        if (profile != null && profile.viewMode() != null && profile.viewMode().isThirdPersonFamily()) {
            return profile.viewMode();
        }
        return ViewMode.THIRD_PERSON;
    }

    private static Location toMountLocation(Location eyeLocation, double passengerEyeOffset) {
        Location mountLoc = eyeLocation.clone();
        mountLoc.setY(eyeLocation.getY() - passengerEyeOffset);
        return mountLoc;
    }

    private static double clampEyeOffset(double offset) {
        return Math.max(MIN_PASSENGER_EYE_OFFSET, Math.min(MAX_PASSENGER_EYE_OFFSET, offset));
    }

    private static void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    private static void runSafely(Runnable task) {
        if (task != null) {
            task.run();
        }
    }

    private static boolean hasLocationChanged(Location previous, Location current) {
        if (previous == null || current == null) {
            return previous != current;
        }
        if (previous.getWorld() == null || current.getWorld() == null) {
            return true;
        }
        if (!previous.getWorld().getUID().equals(current.getWorld().getUID())) {
            return true;
        }
        if (previous.distanceSquared(current) > 1.0e-4) {
            return true;
        }
        return Math.abs(WallOcclusionCalculator.normalizeAngleDegrees(previous.getYaw() - current.getYaw())) > 0.05;
    }

    private static final class CameraRig {
        private final CameraSnapshot snapshot;
        private final AvatarStandInRig avatarRig;
        private final Interaction cameraMount;
        private final Location openStartEye;
        private final double passengerEyeOffset;
        private Location feetOrigin;
        private Location eyeOrigin;
        private Location targetCameraEye;
        private Location currentCameraEye;
        private ViewMode mode;
        private ThirdPersonSettings lastSettings;
        private BukkitTask openTask;
        private BukkitTask closeTask;
        private boolean stopTriggered;
        private boolean closingAnimated;

        private CameraRig(
            CameraSnapshot snapshot,
            AvatarStandInRig avatarRig,
            Interaction cameraMount,
            Location feetOrigin,
            Location eyeOrigin,
            Location targetCameraEye,
            double passengerEyeOffset,
            ViewMode mode,
            ThirdPersonSettings lastSettings
        ) {
            this.snapshot = snapshot;
            this.avatarRig = avatarRig;
            this.cameraMount = cameraMount;
            this.openStartEye = eyeOrigin.clone();
            this.feetOrigin = feetOrigin.clone();
            this.eyeOrigin = eyeOrigin.clone();
            this.targetCameraEye = targetCameraEye.clone();
            this.currentCameraEye = eyeOrigin.clone();
            this.passengerEyeOffset = passengerEyeOffset;
            this.mode = mode;
            this.lastSettings = lastSettings;
            this.openTask = null;
            this.closeTask = null;
            this.stopTriggered = false;
            this.closingAnimated = false;
        }
    }
}
