package com.cookietps.camera;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.EulerAngle;

public final class AvatarStandInRig {
    private static final double DEFAULT_HEAD_LERP_ALPHA = 0.45;

    private final ArmorStand stand;
    private float bodyYaw;

    private AvatarStandInRig(ArmorStand stand, float bodyYaw) {
        this.stand = stand;
        this.bodyYaw = bodyYaw;
    }

    public static AvatarStandInRig spawn(Plugin plugin, Player player, Location feetOrigin, float bodyYaw) {
        if (feetOrigin == null) {
            return null;
        }
        World world = feetOrigin.getWorld();
        if (world == null) {
            return null;
        }
        float normalizedYaw = WallOcclusionCalculator.normalizeYawDegrees(bodyYaw);
        Location spawnLoc = feetOrigin.clone();
        spawnLoc.setYaw(normalizedYaw);
        spawnLoc.setPitch(0.0f);

        ArmorStand stand = world.spawn(spawnLoc, ArmorStand.class, entity -> {
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);
            entity.setArms(true);
            entity.setBasePlate(false);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setCanPickupItems(false);
            entity.setCollidable(false);
            entity.setSilent(true);
            entity.setDisabledSlots(EquipmentSlot.values());
        });

        applyEquipment(stand, player);
        player.showEntity(plugin, stand);
        return new AvatarStandInRig(stand, normalizedYaw);
    }

    public void updateBodyRotation(Location feetOrigin, float newBodyYaw) {
        if (!isValid() || feetOrigin == null || feetOrigin.getWorld() == null) {
            return;
        }
        float normalizedYaw = WallOcclusionCalculator.normalizeYawDegrees(newBodyYaw);
        Location current = stand.getLocation();
        boolean worldMatches = current.getWorld() != null && current.getWorld().getUID().equals(feetOrigin.getWorld().getUID());
        boolean yawChanged = Math.abs(WallOcclusionCalculator.normalizeAngleDegrees(normalizedYaw - this.bodyYaw)) > 0.05;
        boolean moved = !worldMatches || current.distanceSquared(feetOrigin) > 1.0e-4;
        if (yawChanged || moved) {
            Location target = feetOrigin.clone();
            target.setYaw(normalizedYaw);
            target.setPitch(0.0f);
            stand.teleport(target);
            this.bodyYaw = normalizedYaw;
        }
    }

    public void setHeadPose(EulerAngle pose) {
        trackHeadPose(pose, DEFAULT_HEAD_LERP_ALPHA);
    }

    public void trackHeadPose(EulerAngle targetPose, double alpha) {
        if (!isValid() || targetPose == null) {
            return;
        }
        EulerAngle currentPose = stand.getHeadPose();
        EulerAngle smoothed = CameraTransitionMath.lerpHeadPose(currentPose, targetPose, alpha);
        stand.setHeadPose(smoothed);
    }

    public Location getHeadLocation() {
        if (!isValid()) {
            return null;
        }
        return stand.getEyeLocation();
    }

    public float getBodyYaw() {
        return bodyYaw;
    }

    public boolean isValid() {
        return stand != null && stand.isValid();
    }

    public void destroy() {
        if (stand != null && stand.isValid()) {
            stand.remove();
        }
    }

    private static void applyEquipment(ArmorStand stand, Player player) {
        EntityEquipment equipment = stand.getEquipment();
        if (equipment == null) {
            return;
        }
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        if (skull.getItemMeta() instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
            skull.setItemMeta(skullMeta);
        }
        equipment.setHelmet(skull);

        PlayerInventory inventory = player.getInventory();
        equipment.setChestplate(cloneOrNull(inventory.getChestplate()));
        equipment.setLeggings(cloneOrNull(inventory.getLeggings()));
        equipment.setBoots(cloneOrNull(inventory.getBoots()));
        equipment.setItemInMainHand(cloneOrNull(inventory.getItemInMainHand()));
        equipment.setItemInOffHand(cloneOrNull(inventory.getItemInOffHand()));
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return item != null ? item.clone() : null;
    }
}
