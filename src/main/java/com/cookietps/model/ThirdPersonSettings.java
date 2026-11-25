package com.cookietps.model;

public record ThirdPersonSettings(
    double cameraDistance,
    double cameraHeightOffset,
    double cameraSideOffset,
    float cameraYawOffset,
    float cameraPitchOffset,
    double minClearance,
    int cameraTeleportDurationTicks,
    float avatarBodyYawOffset,
    boolean headTrackHoveredSlot,
    boolean fallbackToFirstPersonOnOcclusion,
    int openTransitionTicks,
    int closeTransitionTicks,
    double frontCameraDistance,
    double frontCameraHeightOffset,
    double frontCameraSideOffset,
    float frontCameraYawOffset,
    float frontCameraPitchOffset
) {
    public ThirdPersonSettings(
        double cameraDistance,
        double cameraHeightOffset,
        double cameraSideOffset,
        float cameraYawOffset,
        float cameraPitchOffset,
        double minClearance,
        int cameraTeleportDurationTicks,
        float avatarBodyYawOffset,
        boolean headTrackHoveredSlot,
        boolean fallbackToFirstPersonOnOcclusion
    ) {
        this(
            cameraDistance,
            cameraHeightOffset,
            cameraSideOffset,
            cameraYawOffset,
            cameraPitchOffset,
            minClearance,
            cameraTeleportDurationTicks,
            avatarBodyYawOffset,
            headTrackHoveredSlot,
            fallbackToFirstPersonOnOcclusion,
            10,
            8,
            2.45,
            0.15,
            -0.85,
            162.0f,
            4.0f
        );
    }
}
