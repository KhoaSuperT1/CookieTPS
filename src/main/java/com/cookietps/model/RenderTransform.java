package com.cookietps.model;

public record RenderTransform(
    double distance,
    float scale,
    double offsetX,
    double offsetY,
    double offsetZ,
    float pitchOffset,
    float yawOffset,
    float rollOffset,
    float curvature,
    int openDurationTicks,
    int closeDurationTicks,
    int teleportDurationTicks
) {
    public RenderTransform withDistance(double distance) {
        return new RenderTransform(
            distance,
            scale,
            offsetX,
            offsetY,
            offsetZ,
            pitchOffset,
            yawOffset,
            rollOffset,
            curvature,
            openDurationTicks,
            closeDurationTicks,
            teleportDurationTicks
        );
    }

    public RenderTransform withScale(float scale) {
        return new RenderTransform(
            distance,
            scale,
            offsetX,
            offsetY,
            offsetZ,
            pitchOffset,
            yawOffset,
            rollOffset,
            curvature,
            openDurationTicks,
            closeDurationTicks,
            teleportDurationTicks
        );
    }

    public RenderTransform withOffset(double offsetX, double offsetY, double offsetZ) {
        return new RenderTransform(
            distance,
            scale,
            offsetX,
            offsetY,
            offsetZ,
            pitchOffset,
            yawOffset,
            rollOffset,
            curvature,
            openDurationTicks,
            closeDurationTicks,
            teleportDurationTicks
        );
    }

    public RenderTransform withRotation(float pitchOffset, float yawOffset, float rollOffset) {
        return new RenderTransform(
            distance,
            scale,
            offsetX,
            offsetY,
            offsetZ,
            pitchOffset,
            yawOffset,
            rollOffset,
            curvature,
            openDurationTicks,
            closeDurationTicks,
            teleportDurationTicks
        );
    }

    public RenderTransform withCurvature(float curvature) {
        return new RenderTransform(
            distance,
            scale,
            offsetX,
            offsetY,
            offsetZ,
            pitchOffset,
            yawOffset,
            rollOffset,
            curvature,
            openDurationTicks,
            closeDurationTicks,
            teleportDurationTicks
        );
    }
}
