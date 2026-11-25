package com.cookietps.camera;

import com.cookietps.model.ThirdPersonSettings;
import com.cookietps.model.ViewMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.EulerAngle;

import java.util.Objects;

public final class WallOcclusionCalculator {
    public static final double DEFAULT_SAFETY_MARGIN = 0.35;
    private static final double MIN_CLAMPED_DISTANCE = 0.25;
    private static final double EPSILON = 1.0e-6;
    private static final double MAX_HEAD_YAW_DEGREES = 75.0;
    private static final double MAX_HEAD_PITCH_DEGREES = 60.0;

    private WallOcclusionCalculator() {
    }

    public static Location computeDesiredCameraLocation(Location avatarEye, ThirdPersonSettings settings) {
        return computeDesiredCameraLocation(avatarEye, settings, ViewMode.THIRD_PERSON);
    }

    public static Location computeDesiredCameraLocation(Location avatarEye, ThirdPersonSettings settings, ViewMode mode) {
        Objects.requireNonNull(avatarEye, "avatarEye");
        Objects.requireNonNull(settings, "settings");

        ViewMode effectiveMode = mode != null ? mode : ViewMode.THIRD_PERSON;
        double baseYaw = avatarEye.getYaw() + settings.avatarBodyYawOffset();
        double yawRad = Math.toRadians(baseYaw);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double rightX = -Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);

        if (effectiveMode == ViewMode.THIRD_PERSON_FRONT) {
            double distance = Math.max(0.0, settings.frontCameraDistance());
            double camX = avatarEye.getX() + forwardX * distance + rightX * settings.frontCameraSideOffset();
            double camY = avatarEye.getY() + settings.frontCameraHeightOffset();
            double camZ = avatarEye.getZ() + forwardZ * distance + rightZ * settings.frontCameraSideOffset();
            float camYaw = normalizeYawDegrees((float) (baseYaw + settings.frontCameraYawOffset()));
            float camPitch = clampPitchDegrees(settings.frontCameraPitchOffset());
            return new Location(avatarEye.getWorld(), camX, camY, camZ, camYaw, camPitch);
        }

        double distance = Math.max(0.0, settings.cameraDistance());
        double camX = avatarEye.getX() - forwardX * distance + rightX * settings.cameraSideOffset();
        double camY = avatarEye.getY() + settings.cameraHeightOffset();
        double camZ = avatarEye.getZ() - forwardZ * distance + rightZ * settings.cameraSideOffset();

        float camYaw = normalizeYawDegrees((float) (baseYaw + settings.cameraYawOffset()));
        float camPitch = clampPitchDegrees(settings.cameraPitchOffset());
        return new Location(avatarEye.getWorld(), camX, camY, camZ, camYaw, camPitch);
    }

    public static Location computeCameraLocationAtDistance(
        Location avatarEye,
        Location desiredCamera,
        double targetDistance
    ) {
        Objects.requireNonNull(avatarEye, "avatarEye");
        Objects.requireNonNull(desiredCamera, "desiredCamera");

        double dx = desiredCamera.getX() - avatarEye.getX();
        double dy = desiredCamera.getY() - avatarEye.getY();
        double dz = desiredCamera.getZ() - avatarEye.getZ();
        double rayLength = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (rayLength < EPSILON || targetDistance >= rayLength) {
            return desiredCamera.clone();
        }

        double clampedDistance = Math.max(0.0, targetDistance);
        double ratio = clampedDistance / rayLength;
        return new Location(
            desiredCamera.getWorld(),
            avatarEye.getX() + dx * ratio,
            avatarEye.getY() + dy * ratio,
            avatarEye.getZ() + dz * ratio,
            desiredCamera.getYaw(),
            desiredCamera.getPitch()
        );
    }

    public static double computeSafeDistance(
        double desiredDistance,
        double hitDistance,
        double safetyMargin,
        double minClearance
    ) {
        if (desiredDistance <= 0.0) {
            return minClearance > 0.0 ? -1.0 : 0.0;
        }
        double margin = Math.max(0.0, safetyMargin);
        if (Double.isNaN(hitDistance) || Double.isInfinite(hitDistance) || hitDistance < 0.0) {
            return desiredDistance >= minClearance ? desiredDistance : -1.0;
        }
        double adjusted = Math.min(desiredDistance, hitDistance - margin);
        if (adjusted < minClearance) {
            return -1.0;
        }
        return adjusted;
    }

    public static double computeSafeDistance(
        double desiredDistance,
        Double hitDistance,
        double safetyMargin,
        double minClearance
    ) {
        return computeSafeDistance(
            desiredDistance,
            hitDistance != null ? hitDistance : -1.0,
            safetyMargin,
            minClearance
        );
    }

    public static Location resolveCameraLocation(
        Location avatarEye,
        ThirdPersonSettings settings,
        Double hitDistance,
        double safetyMargin
    ) {
        return resolveCameraLocation(avatarEye, settings, ViewMode.THIRD_PERSON, hitDistance, safetyMargin);
    }

    public static Location resolveCameraLocation(
        Location avatarEye,
        ThirdPersonSettings settings,
        ViewMode mode,
        Double hitDistance,
        double safetyMargin
    ) {
        Location desired = computeDesiredCameraLocation(avatarEye, settings, mode);
        double rayLength = distanceBetween(avatarEye, desired);
        double safeDistance = computeSafeDistance(rayLength, hitDistance, safetyMargin, settings.minClearance());
        if (safeDistance < 0.0) {
            if (settings.fallbackToFirstPersonOnOcclusion()) {
                return null;
            }
            double fallbackDistance = hitDistance != null && hitDistance >= 0.0
                ? Math.max(MIN_CLAMPED_DISTANCE, Math.min(rayLength, hitDistance - Math.max(0.0, safetyMargin)))
                : rayLength;
            return computeCameraLocationAtDistance(avatarEye, desired, fallbackDistance);
        }
        return computeCameraLocationAtDistance(avatarEye, desired, safeDistance);
    }

    public static double distanceBetween(Location first, Location second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        double dx = second.getX() - first.getX();
        double dy = second.getY() - first.getY();
        double dz = second.getZ() - first.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static EulerAngle computeHeadPose(
        Location avatarHead,
        Location targetWorldLocation,
        float avatarBodyYaw
    ) {
        if (avatarHead == null || targetWorldLocation == null) {
            return EulerAngle.ZERO;
        }
        World headWorld = avatarHead.getWorld();
        World targetWorld = targetWorldLocation.getWorld();
        if (headWorld != null && targetWorld != null && !headWorld.getUID().equals(targetWorld.getUID())) {
            return EulerAngle.ZERO;
        }

        double dx = targetWorldLocation.getX() - avatarHead.getX();
        double dy = targetWorldLocation.getY() - avatarHead.getY();
        double dz = targetWorldLocation.getZ() - avatarHead.getZ();
        double horizontal = Math.hypot(dx, dz);
        if (horizontal < 1.0e-5 && Math.abs(dy) < 1.0e-5) {
            return EulerAngle.ZERO;
        }

        double targetYawDeg = Math.toDegrees(Math.atan2(-dx, dz));
        double targetPitchDeg = Math.toDegrees(-Math.atan2(dy, horizontal));
        double relativeYawDeg = clamp(normalizeAngleDegrees(targetYawDeg - avatarBodyYaw), -MAX_HEAD_YAW_DEGREES, MAX_HEAD_YAW_DEGREES);
        double relativePitchDeg = clamp(targetPitchDeg, -MAX_HEAD_PITCH_DEGREES, MAX_HEAD_PITCH_DEGREES);
        return new EulerAngle(Math.toRadians(relativePitchDeg), Math.toRadians(relativeYawDeg), 0.0);
    }

    public static float normalizeYawDegrees(float yaw) {
        return (float) normalizeAngleDegrees(yaw);
    }

    public static float clampPitchDegrees(float pitch) {
        return (float) clamp(pitch, -89.0, 89.0);
    }

    public static double normalizeAngleDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) {
            wrapped -= 360.0;
        } else if (wrapped < -180.0) {
            wrapped += 360.0;
        }
        return wrapped;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
