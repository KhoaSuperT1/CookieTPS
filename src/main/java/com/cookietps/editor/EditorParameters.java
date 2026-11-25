package com.cookietps.editor;

import com.cookietps.model.RenderTransform;
import com.cookietps.model.ScreenProfile;
import com.cookietps.model.ThirdPersonSettings;
import com.cookietps.model.ViewMode;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class EditorParameters {
    public static final double MIN_DISTANCE = 1.0;
    public static final double MAX_DISTANCE = 6.0;
    public static final float MIN_SCALE = 0.35f;
    public static final float MAX_SCALE = 2.5f;
    public static final float MIN_CURVATURE = -1.0f;
    public static final float MAX_CURVATURE = 1.5f;
    public static final double MIN_OFFSET = -3.0;
    public static final double MAX_OFFSET = 3.0;
    public static final float MIN_ANGLE = -180.0f;
    public static final float MAX_ANGLE = 180.0f;
    public static final float MIN_CAM_PITCH = -85.0f;
    public static final float MAX_CAM_PITCH = 85.0f;
    public static final float MIN_SLOT_SPACING = 0.22f;
    public static final float MAX_SLOT_SPACING = 0.70f;
    public static final double MIN_CAM_DISTANCE = 1.2;
    public static final double MAX_CAM_DISTANCE = 7.0;
    public static final double MIN_CAM_HEIGHT = -1.5;
    public static final double MAX_CAM_HEIGHT = 3.0;
    public static final double MIN_CAM_SIDE = -3.0;
    public static final double MAX_CAM_SIDE = 3.0;
    public static final double MIN_CLEARANCE = 0.4;
    public static final double MAX_CLEARANCE = 3.0;
    public static final int MIN_TRANSITION_TICKS = 0;
    public static final int MAX_TRANSITION_TICKS = 40;

    public static final List<String> PARAMETER_NAMES = List.of(
        "distance",
        "scale",
        "curvature",
        "offset-x",
        "offset-y",
        "offset-z",
        "yaw",
        "pitch",
        "roll",
        "spacing",
        "cam-distance",
        "cam-height",
        "cam-side",
        "cam-yaw",
        "cam-pitch",
        "min-clearance",
        "open-ticks",
        "close-ticks",
        "front-cam-distance",
        "front-cam-height",
        "front-cam-side",
        "front-cam-yaw",
        "front-cam-pitch"
    );

    private EditorParameters() {
    }

    public static double clampDouble(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    public static float clampFloat(float value, float min, float max) {
        if (!Float.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    public static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static RenderTransform clampTransform(RenderTransform transform) {
        Objects.requireNonNull(transform, "transform");
        return new RenderTransform(
            clampDouble(transform.distance(), MIN_DISTANCE, MAX_DISTANCE),
            clampFloat(transform.scale(), MIN_SCALE, MAX_SCALE),
            clampDouble(transform.offsetX(), MIN_OFFSET, MAX_OFFSET),
            clampDouble(transform.offsetY(), MIN_OFFSET, MAX_OFFSET),
            clampDouble(transform.offsetZ(), MIN_OFFSET, MAX_OFFSET),
            clampFloat(transform.pitchOffset(), MIN_ANGLE, MAX_ANGLE),
            clampFloat(transform.yawOffset(), MIN_ANGLE, MAX_ANGLE),
            clampFloat(transform.rollOffset(), MIN_ANGLE, MAX_ANGLE),
            clampFloat(transform.curvature(), MIN_CURVATURE, MAX_CURVATURE),
            Math.max(0, transform.openDurationTicks()),
            Math.max(0, transform.closeDurationTicks()),
            Math.max(0, transform.teleportDurationTicks())
        );
    }

    public static ThirdPersonSettings clampThirdPersonSettings(ThirdPersonSettings settings) {
        Objects.requireNonNull(settings, "settings");
        return new ThirdPersonSettings(
            clampDouble(settings.cameraDistance(), MIN_CAM_DISTANCE, MAX_CAM_DISTANCE),
            clampDouble(settings.cameraHeightOffset(), MIN_CAM_HEIGHT, MAX_CAM_HEIGHT),
            clampDouble(settings.cameraSideOffset(), MIN_CAM_SIDE, MAX_CAM_SIDE),
            clampFloat(settings.cameraYawOffset(), MIN_ANGLE, MAX_ANGLE),
            clampFloat(settings.cameraPitchOffset(), MIN_CAM_PITCH, MAX_CAM_PITCH),
            clampDouble(settings.minClearance(), MIN_CLEARANCE, MAX_CLEARANCE),
            Math.max(0, settings.cameraTeleportDurationTicks()),
            clampFloat(settings.avatarBodyYawOffset(), MIN_ANGLE, MAX_ANGLE),
            settings.headTrackHoveredSlot(),
            settings.fallbackToFirstPersonOnOcclusion(),
            clampInt(settings.openTransitionTicks(), MIN_TRANSITION_TICKS, MAX_TRANSITION_TICKS),
            clampInt(settings.closeTransitionTicks(), MIN_TRANSITION_TICKS, MAX_TRANSITION_TICKS),
            clampDouble(settings.frontCameraDistance(), MIN_CAM_DISTANCE, MAX_CAM_DISTANCE),
            clampDouble(settings.frontCameraHeightOffset(), MIN_CAM_HEIGHT, MAX_CAM_HEIGHT),
            clampDouble(settings.frontCameraSideOffset(), MIN_CAM_SIDE, MAX_CAM_SIDE),
            clampFloat(settings.frontCameraYawOffset(), MIN_ANGLE, MAX_ANGLE),
            clampFloat(settings.frontCameraPitchOffset(), MIN_CAM_PITCH, MAX_CAM_PITCH)
        );
    }

    public static ScreenProfile clampProfile(ScreenProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return new ScreenProfile(
            profile.id(),
            profile.screenType(),
            profile.viewMode(),
            profile.enabled(),
            clampTransform(profile.firstPersonTransform()),
            clampTransform(profile.thirdPersonTransform()),
            clampThirdPersonSettings(profile.thirdPersonSettings()),
            Math.max(1, profile.columns()),
            Math.max(1, profile.rows()),
            clampFloat(profile.slotSpacing(), MIN_SLOT_SPACING, MAX_SLOT_SPACING),
            profile.showBackground(),
            profile.showTooltips(),
            profile.includePlayerInventory()
        );
    }

    public static RenderTransform activeTransform(ScreenProfile profile, ViewMode mode) {
        Objects.requireNonNull(profile, "profile");
        return mode != null && mode.isThirdPersonFamily()
            ? profile.thirdPersonTransform()
            : profile.firstPersonTransform();
    }

    public static RenderTransform getActiveTransform(ScreenProfile profile, ViewMode mode) {
        return activeTransform(profile, mode);
    }

    public static ScreenProfile withActiveTransform(ScreenProfile profile, ViewMode mode, RenderTransform transform) {
        Objects.requireNonNull(profile, "profile");
        RenderTransform clamped = clampTransform(transform);
        return mode != null && mode.isThirdPersonFamily()
            ? profile.withThirdPersonTransform(clamped)
            : profile.withFirstPersonTransform(clamped);
    }

    public static ScreenProfile withSlotSpacing(ScreenProfile profile, float slotSpacing) {
        Objects.requireNonNull(profile, "profile");
        float clamped = clampFloat(slotSpacing, MIN_SLOT_SPACING, MAX_SLOT_SPACING);
        return new ScreenProfile(
            profile.id(),
            profile.screenType(),
            profile.viewMode(),
            profile.enabled(),
            profile.firstPersonTransform(),
            profile.thirdPersonTransform(),
            profile.thirdPersonSettings(),
            profile.columns(),
            profile.rows(),
            clamped,
            profile.showBackground(),
            profile.showTooltips(),
            profile.includePlayerInventory()
        );
    }

    public static boolean isValidParameter(String param) {
        return normalizeParameterName(param) != null;
    }

    public static String normalizeParameterName(String param) {
        if (param == null || param.isBlank()) {
            return null;
        }
        String key = param.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "distance", "dist" -> "distance";
            case "scale" -> "scale";
            case "curvature", "curve" -> "curvature";
            case "offset-x", "offsetx", "x" -> "offset-x";
            case "offset-y", "offsety", "y" -> "offset-y";
            case "offset-z", "offsetz", "z" -> "offset-z";
            case "yaw", "yaw-offset" -> "yaw";
            case "pitch", "pitch-offset" -> "pitch";
            case "roll", "roll-offset" -> "roll";
            case "spacing", "slot-spacing" -> "spacing";
            case "cam-distance", "camera-distance" -> "cam-distance";
            case "cam-height", "camera-height", "camera-height-offset" -> "cam-height";
            case "cam-side", "camera-side", "camera-side-offset" -> "cam-side";
            case "cam-yaw", "camera-yaw", "camera-yaw-offset", "camyaw" -> "cam-yaw";
            case "cam-pitch", "camera-pitch", "camera-pitch-offset", "campitch" -> "cam-pitch";
            case "min-clearance", "clearance" -> "min-clearance";
            case "open-ticks", "open-transition-ticks", "openticks", "opentransitionticks" -> "open-ticks";
            case "close-ticks", "close-transition-ticks", "closeticks", "closetransitionticks" -> "close-ticks";
            case "front-cam-distance", "front-camera-distance", "front-distance" -> "front-cam-distance";
            case "front-cam-height", "front-camera-height", "front-camera-height-offset", "front-height" -> "front-cam-height";
            case "front-cam-side", "front-camera-side", "front-camera-side-offset", "front-side" -> "front-cam-side";
            case "front-cam-yaw", "front-camera-yaw", "front-camera-yaw-offset", "front-yaw" -> "front-cam-yaw";
            case "front-cam-pitch", "front-camera-pitch", "front-camera-pitch-offset", "front-pitch" -> "front-cam-pitch";
            default -> null;
        };
    }

    public static double getParameterValue(ScreenProfile profile, ViewMode mode, String param) {
        Objects.requireNonNull(profile, "profile");
        String canonical = normalizeParameterName(param);
        if (canonical == null) {
            throw new IllegalArgumentException("Unknown parameter: " + param);
        }
        RenderTransform transform = activeTransform(profile, mode);
        ThirdPersonSettings tp = profile.thirdPersonSettings();
        return switch (canonical) {
            case "distance" -> transform.distance();
            case "scale" -> transform.scale();
            case "curvature" -> transform.curvature();
            case "offset-x" -> transform.offsetX();
            case "offset-y" -> transform.offsetY();
            case "offset-z" -> transform.offsetZ();
            case "yaw" -> transform.yawOffset();
            case "pitch" -> transform.pitchOffset();
            case "roll" -> transform.rollOffset();
            case "spacing" -> profile.slotSpacing();
            case "cam-distance" -> tp.cameraDistance();
            case "cam-height" -> tp.cameraHeightOffset();
            case "cam-side" -> tp.cameraSideOffset();
            case "cam-yaw" -> tp.cameraYawOffset();
            case "cam-pitch" -> tp.cameraPitchOffset();
            case "min-clearance" -> tp.minClearance();
            case "open-ticks" -> tp.openTransitionTicks();
            case "close-ticks" -> tp.closeTransitionTicks();
            case "front-cam-distance" -> tp.frontCameraDistance();
            case "front-cam-height" -> tp.frontCameraHeightOffset();
            case "front-cam-side" -> tp.frontCameraSideOffset();
            case "front-cam-yaw" -> tp.frontCameraYawOffset();
            case "front-cam-pitch" -> tp.frontCameraPitchOffset();
            default -> throw new IllegalArgumentException("Unknown parameter: " + param);
        };
    }

    public static double getParameterMin(String param) {
        String canonical = normalizeParameterName(param);
        if (canonical == null) {
            return 0.0;
        }
        return switch (canonical) {
            case "distance" -> MIN_DISTANCE;
            case "scale" -> MIN_SCALE;
            case "curvature" -> MIN_CURVATURE;
            case "offset-x", "offset-y", "offset-z" -> MIN_OFFSET;
            case "yaw", "pitch", "roll", "cam-yaw", "front-cam-yaw" -> MIN_ANGLE;
            case "cam-pitch", "front-cam-pitch" -> MIN_CAM_PITCH;
            case "spacing" -> MIN_SLOT_SPACING;
            case "cam-distance", "front-cam-distance" -> MIN_CAM_DISTANCE;
            case "cam-height", "front-cam-height" -> MIN_CAM_HEIGHT;
            case "cam-side", "front-cam-side" -> MIN_CAM_SIDE;
            case "min-clearance" -> MIN_CLEARANCE;
            case "open-ticks", "close-ticks" -> MIN_TRANSITION_TICKS;
            default -> 0.0;
        };
    }

    public static double getParameterMax(String param) {
        String canonical = normalizeParameterName(param);
        if (canonical == null) {
            return 1.0;
        }
        return switch (canonical) {
            case "distance" -> MAX_DISTANCE;
            case "scale" -> MAX_SCALE;
            case "curvature" -> MAX_CURVATURE;
            case "offset-x", "offset-y", "offset-z" -> MAX_OFFSET;
            case "yaw", "pitch", "roll", "cam-yaw", "front-cam-yaw" -> MAX_ANGLE;
            case "cam-pitch", "front-cam-pitch" -> MAX_CAM_PITCH;
            case "spacing" -> MAX_SLOT_SPACING;
            case "cam-distance", "front-cam-distance" -> MAX_CAM_DISTANCE;
            case "cam-height", "front-cam-height" -> MAX_CAM_HEIGHT;
            case "cam-side", "front-cam-side" -> MAX_CAM_SIDE;
            case "min-clearance" -> MAX_CLEARANCE;
            case "open-ticks", "close-ticks" -> MAX_TRANSITION_TICKS;
            default -> 1.0;
        };
    }

    public static ScreenProfile applyParameter(ScreenProfile profile, ViewMode mode, String param, double value) {
        Objects.requireNonNull(profile, "profile");
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Parameter value must be finite");
        }
        String canonical = normalizeParameterName(param);
        if (canonical == null) {
            throw new IllegalArgumentException("Unknown parameter: " + param);
        }

        RenderTransform current = activeTransform(profile, mode);
        ThirdPersonSettings tp = profile.thirdPersonSettings();

        return switch (canonical) {
            case "distance" -> withActiveTransform(
                profile,
                mode,
                current.withDistance(clampDouble(value, MIN_DISTANCE, MAX_DISTANCE))
            );
            case "scale" -> withActiveTransform(
                profile,
                mode,
                current.withScale(clampFloat((float) value, MIN_SCALE, MAX_SCALE))
            );
            case "curvature" -> withActiveTransform(
                profile,
                mode,
                current.withCurvature(clampFloat((float) value, MIN_CURVATURE, MAX_CURVATURE))
            );
            case "offset-x" -> withActiveTransform(
                profile,
                mode,
                current.withOffset(
                    clampDouble(value, MIN_OFFSET, MAX_OFFSET),
                    current.offsetY(),
                    current.offsetZ()
                )
            );
            case "offset-y" -> withActiveTransform(
                profile,
                mode,
                current.withOffset(
                    current.offsetX(),
                    clampDouble(value, MIN_OFFSET, MAX_OFFSET),
                    current.offsetZ()
                )
            );
            case "offset-z" -> withActiveTransform(
                profile,
                mode,
                current.withOffset(
                    current.offsetX(),
                    current.offsetY(),
                    clampDouble(value, MIN_OFFSET, MAX_OFFSET)
                )
            );
            case "yaw" -> withActiveTransform(
                profile,
                mode,
                current.withRotation(
                    current.pitchOffset(),
                    clampFloat((float) value, MIN_ANGLE, MAX_ANGLE),
                    current.rollOffset()
                )
            );
            case "pitch" -> withActiveTransform(
                profile,
                mode,
                current.withRotation(
                    clampFloat((float) value, MIN_ANGLE, MAX_ANGLE),
                    current.yawOffset(),
                    current.rollOffset()
                )
            );
            case "roll" -> withActiveTransform(
                profile,
                mode,
                current.withRotation(
                    current.pitchOffset(),
                    current.yawOffset(),
                    clampFloat((float) value, MIN_ANGLE, MAX_ANGLE)
                )
            );
            case "spacing" -> withSlotSpacing(profile, (float) value);
            case "cam-distance", "cam-height", "cam-side", "cam-yaw", "cam-pitch",
                 "min-clearance", "open-ticks", "close-ticks",
                 "front-cam-distance", "front-cam-height", "front-cam-side",
                 "front-cam-yaw", "front-cam-pitch" -> profile.withThirdPersonSettings(
                updateThirdPersonSetting(tp, canonical, value)
            );
            default -> throw new IllegalArgumentException("Unknown parameter: " + param);
        };
    }

    private static ThirdPersonSettings updateThirdPersonSetting(ThirdPersonSettings tp, String canonical, double value) {
        double camDist = tp.cameraDistance();
        double camHeight = tp.cameraHeightOffset();
        double camSide = tp.cameraSideOffset();
        float camYaw = tp.cameraYawOffset();
        float camPitch = tp.cameraPitchOffset();
        double minClearance = tp.minClearance();
        int openTicks = tp.openTransitionTicks();
        int closeTicks = tp.closeTransitionTicks();
        double frontDist = tp.frontCameraDistance();
        double frontHeight = tp.frontCameraHeightOffset();
        double frontSide = tp.frontCameraSideOffset();
        float frontYaw = tp.frontCameraYawOffset();
        float frontPitch = tp.frontCameraPitchOffset();

        switch (canonical) {
            case "cam-distance" -> camDist = clampDouble(value, MIN_CAM_DISTANCE, MAX_CAM_DISTANCE);
            case "cam-height" -> camHeight = clampDouble(value, MIN_CAM_HEIGHT, MAX_CAM_HEIGHT);
            case "cam-side" -> camSide = clampDouble(value, MIN_CAM_SIDE, MAX_CAM_SIDE);
            case "cam-yaw" -> camYaw = clampFloat((float) value, MIN_ANGLE, MAX_ANGLE);
            case "cam-pitch" -> camPitch = clampFloat((float) value, MIN_CAM_PITCH, MAX_CAM_PITCH);
            case "min-clearance" -> minClearance = clampDouble(value, MIN_CLEARANCE, MAX_CLEARANCE);
            case "open-ticks" -> openTicks = clampInt((int) Math.round(value), MIN_TRANSITION_TICKS, MAX_TRANSITION_TICKS);
            case "close-ticks" -> closeTicks = clampInt((int) Math.round(value), MIN_TRANSITION_TICKS, MAX_TRANSITION_TICKS);
            case "front-cam-distance" -> frontDist = clampDouble(value, MIN_CAM_DISTANCE, MAX_CAM_DISTANCE);
            case "front-cam-height" -> frontHeight = clampDouble(value, MIN_CAM_HEIGHT, MAX_CAM_HEIGHT);
            case "front-cam-side" -> frontSide = clampDouble(value, MIN_CAM_SIDE, MAX_CAM_SIDE);
            case "front-cam-yaw" -> frontYaw = clampFloat((float) value, MIN_ANGLE, MAX_ANGLE);
            case "front-cam-pitch" -> frontPitch = clampFloat((float) value, MIN_CAM_PITCH, MAX_CAM_PITCH);
            default -> throw new IllegalArgumentException("Unknown third-person parameter: " + canonical);
        }

        return new ThirdPersonSettings(
            camDist,
            camHeight,
            camSide,
            camYaw,
            camPitch,
            minClearance,
            tp.cameraTeleportDurationTicks(),
            tp.avatarBodyYawOffset(),
            tp.headTrackHoveredSlot(),
            tp.fallbackToFirstPersonOnOcclusion(),
            openTicks,
            closeTicks,
            frontDist,
            frontHeight,
            frontSide,
            frontYaw,
            frontPitch
        );
    }

    public static ScreenProfile adjustParameter(ScreenProfile profile, ViewMode mode, String param, double delta) {
        if (!Double.isFinite(delta)) {
            throw new IllegalArgumentException("Delta must be finite");
        }
        double current = getParameterValue(profile, mode, param);
        return applyParameter(profile, mode, param, current + delta);
    }

    public static ScreenProfile applyFlatPreset(ScreenProfile profile, ViewMode mode) {
        boolean tp = mode != null && mode.isThirdPersonFamily();
        RenderTransform current = activeTransform(profile, mode)
            .withCurvature(0.0f)
            .withScale(1.0f)
            .withOffset(0.0, tp ? 0.35 : -0.05, 0.0)
            .withRotation(0.0f, tp ? -12.0f : 0.0f, 0.0f);
        return withSlotSpacing(withActiveTransform(profile, mode, current), 0.24f);
    }

    public static ScreenProfile applyCurvedPreset(ScreenProfile profile, ViewMode mode) {
        boolean tp = mode != null && mode.isThirdPersonFamily();
        RenderTransform current = activeTransform(profile, mode)
            .withCurvature(0.45f)
            .withScale(1.02f)
            .withDistance(tp ? 2.35 : 2.15);
        return withSlotSpacing(withActiveTransform(profile, mode, current), 0.24f);
    }

    public static ScreenProfile applyCompactPreset(ScreenProfile profile, ViewMode mode) {
        RenderTransform current = activeTransform(profile, mode)
            .withDistance(1.75)
            .withScale(0.78f)
            .withCurvature(0.25f);
        return withSlotSpacing(withActiveTransform(profile, mode, current), 0.22f);
    }

    public static ScreenProfile applyShowcasePreset(ScreenProfile profile, ViewMode mode) {
        RenderTransform current = activeTransform(profile, mode)
            .withDistance(2.75)
            .withScale(1.30f)
            .withCurvature(0.55f);
        return withSlotSpacing(withActiveTransform(profile, mode, current), 0.28f);
    }

    public static ScreenProfile toggleFallbackOnOcclusion(ScreenProfile profile) {
        ThirdPersonSettings tp = profile.thirdPersonSettings();
        return profile.withThirdPersonSettings(new ThirdPersonSettings(
            tp.cameraDistance(),
            tp.cameraHeightOffset(),
            tp.cameraSideOffset(),
            tp.cameraYawOffset(),
            tp.cameraPitchOffset(),
            tp.minClearance(),
            tp.cameraTeleportDurationTicks(),
            tp.avatarBodyYawOffset(),
            tp.headTrackHoveredSlot(),
            !tp.fallbackToFirstPersonOnOcclusion(),
            tp.openTransitionTicks(),
            tp.closeTransitionTicks(),
            tp.frontCameraDistance(),
            tp.frontCameraHeightOffset(),
            tp.frontCameraSideOffset(),
            tp.frontCameraYawOffset(),
            tp.frontCameraPitchOffset()
        ));
    }

    public static ScreenProfile cycleViewMode(ScreenProfile profile) {
        ViewMode next = switch (profile.viewMode()) {
            case AUTO -> ViewMode.FIRST_PERSON;
            case FIRST_PERSON -> ViewMode.THIRD_PERSON;
            case THIRD_PERSON -> ViewMode.THIRD_PERSON_FRONT;
            case THIRD_PERSON_FRONT -> ViewMode.DISABLED;
            case DISABLED -> ViewMode.AUTO;
        };
        return profile.withViewMode(next);
    }
}
