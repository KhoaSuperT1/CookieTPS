package com.cookietps.camera;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.EulerAngle;

public final class CameraTransitionMath {
    private CameraTransitionMath() {
    }

    public static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    public static double easeOutCubic(double t) {
        double c = clamp01(t);
        double inv = 1.0 - c;
        return 1.0 - inv * inv * inv;
    }

    public static double easeInOutCubic(double t) {
        double c = clamp01(t);
        if (c < 0.5) {
            return 4.0 * c * c * c;
        }
        double inv = -2.0 * c + 2.0;
        return 1.0 - (inv * inv * inv) / 2.0;
    }

    public static Location interpolateLocation(Location from, Location to, double t) {
        if (from == null && to == null) {
            return null;
        }
        if (from == null) {
            return to.clone();
        }
        if (to == null) {
            return from.clone();
        }
        double c = clamp01(t);
        double x = from.getX() + (to.getX() - from.getX()) * c;
        double y = from.getY() + (to.getY() - from.getY()) * c;
        double z = from.getZ() + (to.getZ() - from.getZ()) * c;
        double deltaYaw = WallOcclusionCalculator.normalizeAngleDegrees((double) to.getYaw() - (double) from.getYaw());
        float yaw = WallOcclusionCalculator.normalizeYawDegrees((float) (from.getYaw() + deltaYaw * c));
        float pitch = WallOcclusionCalculator.clampPitchDegrees((float) (from.getPitch() + (to.getPitch() - from.getPitch()) * c));
        World world = to.getWorld() != null ? to.getWorld() : from.getWorld();
        return new Location(world, x, y, z, yaw, pitch);
    }

    public static EulerAngle lerpHeadPose(EulerAngle current, EulerAngle target, double alpha) {
        if (target == null) {
            return current != null ? current : EulerAngle.ZERO;
        }
        if (current == null) {
            return target;
        }
        double a = clamp01(alpha);
        double x = current.getX() + (target.getX() - current.getX()) * a;
        double y = current.getY() + (target.getY() - current.getY()) * a;
        double z = current.getZ() + (target.getZ() - current.getZ()) * a;
        return new EulerAngle(x, y, z);
    }
}
