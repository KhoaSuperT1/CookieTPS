package com.cookietps.camera;

import com.cookietps.BukkitTestSupport;
import com.cookietps.model.ThirdPersonSettings;
import com.cookietps.model.ViewMode;
import org.bukkit.Location;
import org.bukkit.util.EulerAngle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WallOcclusionCalculatorTest {
    private static final double EPS = 1.0e-5;

    @Test
    void clearPathReturnsFullDesiredDistance() {
        assertEquals(3.0, WallOcclusionCalculator.computeSafeDistance(3.0, -1.0, 0.35, 1.25), EPS);
        assertEquals(3.0, WallOcclusionCalculator.computeSafeDistance(3.0, (Double) null, 0.35, 1.25), EPS);
        assertEquals(3.0, WallOcclusionCalculator.computeSafeDistance(3.0, Double.NaN, 0.35, 1.25), EPS);
        assertEquals(3.0, WallOcclusionCalculator.computeSafeDistance(3.0, Double.POSITIVE_INFINITY, 0.35, 1.25), EPS);

        Location avatarEye = new Location(null, 0.0, 65.62, 0.0, 0.0f, 0.0f);
        ThirdPersonSettings settings = new ThirdPersonSettings(3.0, 0.5, 0.0, 0.0f, 5.0f, 1.25, 4, 0.0f, true, true);
        Location desired = WallOcclusionCalculator.computeDesiredCameraLocation(avatarEye, settings);
        Location resolved = WallOcclusionCalculator.resolveCameraLocation(avatarEye, settings, null, 0.35);

        assertNotNull(resolved);
        assertEquals(desired.getX(), resolved.getX(), EPS);
        assertEquals(desired.getY(), resolved.getY(), EPS);
        assertEquals(desired.getZ(), resolved.getZ(), EPS);
    }

    @Test
    void blockedHitPullsBackWithSafetyMarginWhenAboveMinClearance() {
        double safe = WallOcclusionCalculator.computeSafeDistance(3.5, 2.25, 0.35, 1.25);
        assertEquals(1.90, safe, EPS);

        Location avatarEye = new Location(null, 0.0, 65.62, 0.0, 0.0f, 0.0f);
        ThirdPersonSettings settings = new ThirdPersonSettings(3.5, 0.0, 0.0, 0.0f, 0.0f, 1.25, 4, 0.0f, true, true);
        Location resolved = WallOcclusionCalculator.resolveCameraLocation(avatarEye, settings, 2.25, 0.35);

        assertNotNull(resolved);
        assertEquals(1.90, WallOcclusionCalculator.distanceBetween(avatarEye, resolved), EPS);
        assertTrue(resolved.getZ() < avatarEye.getZ());
    }

    @Test
    void hitCloserThanMinClearanceTriggersFallback() {
        double safe = WallOcclusionCalculator.computeSafeDistance(3.0, 1.40, 0.35, 1.25);
        assertEquals(-1.0, safe, EPS);

        assertEquals(-1.0, WallOcclusionCalculator.computeSafeDistance(1.0, -1.0, 0.35, 1.25), EPS);

        Location avatarEye = new Location(null, 5.0, 70.0, 5.0, 45.0f, 0.0f);
        ThirdPersonSettings fallbackEnabled = new ThirdPersonSettings(3.0, 0.4, -0.5, 10.0f, 8.0f, 1.25, 4, 0.0f, true, true);
        assertNull(WallOcclusionCalculator.resolveCameraLocation(avatarEye, fallbackEnabled, 1.40, 0.35));

        ThirdPersonSettings fallbackDisabled = new ThirdPersonSettings(3.0, 0.4, -0.5, 10.0f, 8.0f, 1.25, 4, 0.0f, true, false);
        Location clamped = WallOcclusionCalculator.resolveCameraLocation(avatarEye, fallbackDisabled, 0.40, 0.35);
        assertNotNull(clamped);
        assertEquals(0.25, WallOcclusionCalculator.distanceBetween(avatarEye, clamped), EPS);
    }

    @Test
    void computeHeadPoseTracksTargetAndClampsExtremes() {
        BukkitTestSupport.TestWorldState worldA = new BukkitTestSupport.TestWorldState();
        BukkitTestSupport.TestWorldState worldB = new BukkitTestSupport.TestWorldState();

        Location head = new Location(worldA.world, 0.0, 65.62, 0.0);
        Location straightAhead = new Location(worldA.world, 0.0, 65.62, 2.5);
        EulerAngle straightPose = WallOcclusionCalculator.computeHeadPose(head, straightAhead, 0.0f);
        assertEquals(0.0, straightPose.getX(), EPS);
        assertEquals(0.0, straightPose.getY(), EPS);
        assertEquals(0.0, straightPose.getZ(), EPS);

        Location upperRight = new Location(worldA.world, -2.0, 67.62, 2.0);
        EulerAngle angledPose = WallOcclusionCalculator.computeHeadPose(head, upperRight, 0.0f);
        assertTrue(angledPose.getX() < 0.0);
        assertTrue(angledPose.getY() > 0.0);
        assertTrue(Math.toDegrees(Math.abs(angledPose.getX())) <= 60.0 + 1.0e-4);
        assertTrue(Math.toDegrees(Math.abs(angledPose.getY())) <= 75.0 + 1.0e-4);

        Location extremeBehind = new Location(worldA.world, 0.0, 80.0, -5.0);
        EulerAngle clampedPose = WallOcclusionCalculator.computeHeadPose(head, extremeBehind, 0.0f);
        assertEquals(Math.toRadians(-60.0), clampedPose.getX(), 0.25);
        assertTrue(Math.toDegrees(Math.abs(clampedPose.getY())) <= 75.0 + 1.0e-4);

        Location otherWorldTarget = new Location(worldB.world, 2.0, 65.62, 2.0);
        EulerAngle crossWorld = WallOcclusionCalculator.computeHeadPose(head, otherWorldTarget, 0.0f);
        assertEquals(EulerAngle.ZERO.getX(), crossWorld.getX(), EPS);
        assertEquals(EulerAngle.ZERO.getY(), crossWorld.getY(), EPS);
    }

    @Test
    void thirdPersonFrontCameraPlacesInFrontAndResolvesOcclusion() {
        Location avatarEye = new Location(null, 0.0, 65.62, 0.0, 0.0f, 0.0f);
        ThirdPersonSettings settings = new ThirdPersonSettings(
            2.05, -0.05, 0.0, 26.0f, 2.0f, 1.15, 4, 0.0f, true, true,
            10, 8, 2.45, 0.15, -0.85, 162.0f, 4.0f
        );

        Location frontCam = WallOcclusionCalculator.computeDesiredCameraLocation(avatarEye, settings, ViewMode.THIRD_PERSON_FRONT);
        assertTrue(frontCam.getZ() > avatarEye.getZ());
        assertEquals(0.85, frontCam.getX(), EPS);
        assertEquals(65.77, frontCam.getY(), EPS);
        assertEquals(2.45, frontCam.getZ(), EPS);
        assertEquals(162.0f, frontCam.getYaw(), 1.0e-4f);
        assertEquals(4.0f, frontCam.getPitch(), 1.0e-4f);

        Location resolvedClear = WallOcclusionCalculator.resolveCameraLocation(
            avatarEye, settings, ViewMode.THIRD_PERSON_FRONT, null, 0.35
        );
        assertNotNull(resolvedClear);
        assertEquals(frontCam.getX(), resolvedClear.getX(), EPS);
        assertEquals(frontCam.getZ(), resolvedClear.getZ(), EPS);

        Location resolvedPulled = WallOcclusionCalculator.resolveCameraLocation(
            avatarEye, settings, ViewMode.THIRD_PERSON_FRONT, 2.0, 0.35
        );
        assertNotNull(resolvedPulled);
        assertEquals(1.65, WallOcclusionCalculator.distanceBetween(avatarEye, resolvedPulled), EPS);
        assertTrue(resolvedPulled.getZ() > avatarEye.getZ());

        Location blockedFallback = WallOcclusionCalculator.resolveCameraLocation(
            avatarEye, settings, ViewMode.THIRD_PERSON_FRONT, 1.2, 0.35
        );
        assertNull(blockedFallback);
    }
}
