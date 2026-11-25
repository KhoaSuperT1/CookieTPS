package com.cookietps.editor;

import com.cookietps.BukkitTestSupport;
import com.cookietps.model.RenderTransform;
import com.cookietps.model.ScreenProfile;
import com.cookietps.model.ScreenType;
import com.cookietps.model.ThirdPersonSettings;
import com.cookietps.model.ViewMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorParameterClampingTest {
    private static final double EPS = 1.0e-5;
    private static final float FEPS = 1.0e-4f;

    @Test
    void clampTransformEnforcesMinMaxAndNonFiniteSafety() {
        RenderTransform tooLow = new RenderTransform(
            -5.0, 0.01f, -99.0, -50.0, -10.0, -360.0f, -200.0f, -999.0f, -9.0f, -5, -2, -1
        );
        RenderTransform clampedLow = EditorParameters.clampTransform(tooLow);
        assertEquals(EditorParameters.MIN_DISTANCE, clampedLow.distance(), EPS);
        assertEquals(EditorParameters.MIN_SCALE, clampedLow.scale(), FEPS);
        assertEquals(EditorParameters.MIN_OFFSET, clampedLow.offsetX(), EPS);
        assertEquals(EditorParameters.MIN_OFFSET, clampedLow.offsetY(), EPS);
        assertEquals(EditorParameters.MIN_OFFSET, clampedLow.offsetZ(), EPS);
        assertEquals(EditorParameters.MIN_ANGLE, clampedLow.pitchOffset(), FEPS);
        assertEquals(EditorParameters.MIN_ANGLE, clampedLow.yawOffset(), FEPS);
        assertEquals(EditorParameters.MIN_ANGLE, clampedLow.rollOffset(), FEPS);
        assertEquals(EditorParameters.MIN_CURVATURE, clampedLow.curvature(), FEPS);
        assertEquals(0, clampedLow.openDurationTicks());
        assertEquals(0, clampedLow.closeDurationTicks());
        assertEquals(0, clampedLow.teleportDurationTicks());

        RenderTransform tooHigh = new RenderTransform(
            99.0, 12.0f, 50.0, 25.0, 15.0, 540.0f, 360.0f, 720.0f, 10.0f, 8, 6, 4
        );
        RenderTransform clampedHigh = EditorParameters.clampTransform(tooHigh);
        assertEquals(EditorParameters.MAX_DISTANCE, clampedHigh.distance(), EPS);
        assertEquals(EditorParameters.MAX_SCALE, clampedHigh.scale(), FEPS);
        assertEquals(EditorParameters.MAX_OFFSET, clampedHigh.offsetX(), EPS);
        assertEquals(EditorParameters.MAX_OFFSET, clampedHigh.offsetY(), EPS);
        assertEquals(EditorParameters.MAX_OFFSET, clampedHigh.offsetZ(), EPS);
        assertEquals(EditorParameters.MAX_ANGLE, clampedHigh.pitchOffset(), FEPS);
        assertEquals(EditorParameters.MAX_ANGLE, clampedHigh.yawOffset(), FEPS);
        assertEquals(EditorParameters.MAX_ANGLE, clampedHigh.rollOffset(), FEPS);
        assertEquals(EditorParameters.MAX_CURVATURE, clampedHigh.curvature(), FEPS);

        RenderTransform nonFinite = new RenderTransform(
            Double.NaN, Float.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, 0.0,
            Float.NaN, 0.0f, 0.0f, Float.NEGATIVE_INFINITY, 1, 1, 1
        );
        RenderTransform clampedNan = EditorParameters.clampTransform(nonFinite);
        assertEquals(EditorParameters.MIN_DISTANCE, clampedNan.distance(), EPS);
        assertEquals(EditorParameters.MIN_SCALE, clampedNan.scale(), FEPS);
        assertEquals(EditorParameters.MIN_OFFSET, clampedNan.offsetX(), EPS);
        assertEquals(EditorParameters.MIN_ANGLE, clampedNan.pitchOffset(), FEPS);
        assertEquals(EditorParameters.MIN_CURVATURE, clampedNan.curvature(), FEPS);
    }

    @Test
    void clampThirdPersonSettingsAndProfileEnforcesAllBounds() {
        ThirdPersonSettings extremeTp = new ThirdPersonSettings(
            50.0, -10.0, 12.0, -500.0f, 500.0f, 0.01, -3, 300.0f, true, false
        );
        ScreenProfile raw = new ScreenProfile(
            "chest",
            ScreenType.CHEST,
            ViewMode.AUTO,
            true,
            new RenderTransform(2.0, 1.0f, 0, 0, 0, 0, 0, 0, 0, 2, 2, 1),
            new RenderTransform(2.0, 1.0f, 0, 0, 0, 0, 0, 0, 0, 2, 2, 1),
            extremeTp,
            0,
            -2,
            2.5f,
            true,
            true,
            true
        );

        ScreenProfile clamped = EditorParameters.clampProfile(raw);
        assertEquals(1, clamped.columns());
        assertEquals(1, clamped.rows());
        assertEquals(EditorParameters.MAX_SLOT_SPACING, clamped.slotSpacing(), FEPS);
        assertEquals(EditorParameters.MAX_CAM_DISTANCE, clamped.thirdPersonSettings().cameraDistance(), EPS);
        assertEquals(EditorParameters.MIN_CAM_HEIGHT, clamped.thirdPersonSettings().cameraHeightOffset(), EPS);
        assertEquals(EditorParameters.MAX_CAM_SIDE, clamped.thirdPersonSettings().cameraSideOffset(), EPS);
        assertEquals(EditorParameters.MIN_ANGLE, clamped.thirdPersonSettings().cameraYawOffset(), FEPS);
        assertEquals(EditorParameters.MAX_CAM_PITCH, clamped.thirdPersonSettings().cameraPitchOffset(), FEPS);
        assertEquals(EditorParameters.MIN_CLEARANCE, clamped.thirdPersonSettings().minClearance(), EPS);
        assertEquals(0, clamped.thirdPersonSettings().cameraTeleportDurationTicks());
        assertEquals(EditorParameters.MAX_ANGLE, clamped.thirdPersonSettings().avatarBodyYawOffset(), FEPS);
        assertEquals(10, clamped.thirdPersonSettings().openTransitionTicks());
        assertEquals(8, clamped.thirdPersonSettings().closeTransitionTicks());

        ThirdPersonSettings extremeFront = new ThirdPersonSettings(
            3.0, 0.2, 0.0, 0.0f, -150.0f, 1.2, 4, 0.0f, true, true,
            -5, 99, 15.0, -9.0, 12.0, -400.0f, 120.0f
        );
        ThirdPersonSettings clampedFront = EditorParameters.clampThirdPersonSettings(extremeFront);
        assertEquals(EditorParameters.MIN_CAM_PITCH, clampedFront.cameraPitchOffset(), FEPS);
        assertEquals(EditorParameters.MIN_TRANSITION_TICKS, clampedFront.openTransitionTicks());
        assertEquals(EditorParameters.MAX_TRANSITION_TICKS, clampedFront.closeTransitionTicks());
        assertEquals(EditorParameters.MAX_CAM_DISTANCE, clampedFront.frontCameraDistance(), EPS);
        assertEquals(EditorParameters.MIN_CAM_HEIGHT, clampedFront.frontCameraHeightOffset(), EPS);
        assertEquals(EditorParameters.MAX_CAM_SIDE, clampedFront.frontCameraSideOffset(), EPS);
        assertEquals(EditorParameters.MIN_ANGLE, clampedFront.frontCameraYawOffset(), FEPS);
        assertEquals(EditorParameters.MAX_CAM_PITCH, clampedFront.frontCameraPitchOffset(), FEPS);
    }

    @Test
    void applyAndAdjustNamedParametersRespectBoundsAliasesAndModeIsolation() {
        ScreenProfile profile = BukkitTestSupport.defaultProfile(ScreenType.CHEST, true);

        ScreenProfile fpUpdated = EditorParameters.applyParameter(profile, ViewMode.FIRST_PERSON, "dist", 100.0);
        assertEquals(EditorParameters.MAX_DISTANCE, fpUpdated.firstPersonTransform().distance(), EPS);
        assertEquals(profile.thirdPersonTransform().distance(), fpUpdated.thirdPersonTransform().distance(), EPS);

        fpUpdated = EditorParameters.applyParameter(fpUpdated, ViewMode.FIRST_PERSON, "scale", 0.01);
        fpUpdated = EditorParameters.applyParameter(fpUpdated, ViewMode.FIRST_PERSON, "curve", 5.0);
        fpUpdated = EditorParameters.applyParameter(fpUpdated, ViewMode.FIRST_PERSON, "x", -8.0);
        fpUpdated = EditorParameters.applyParameter(fpUpdated, ViewMode.FIRST_PERSON, "y", 9.0);
        fpUpdated = EditorParameters.applyParameter(fpUpdated, ViewMode.FIRST_PERSON, "z", 1.25);
        fpUpdated = EditorParameters.applyParameter(fpUpdated, ViewMode.FIRST_PERSON, "yaw", 270.0);
        fpUpdated = EditorParameters.applyParameter(fpUpdated, ViewMode.FIRST_PERSON, "pitch", -270.0);
        fpUpdated = EditorParameters.applyParameter(fpUpdated, ViewMode.FIRST_PERSON, "roll", 45.0);
        fpUpdated = EditorParameters.applyParameter(fpUpdated, ViewMode.FIRST_PERSON, "slot-spacing", 0.05);

        assertEquals(EditorParameters.MIN_SCALE, fpUpdated.firstPersonTransform().scale(), FEPS);
        assertEquals(EditorParameters.MAX_CURVATURE, fpUpdated.firstPersonTransform().curvature(), FEPS);
        assertEquals(EditorParameters.MIN_OFFSET, fpUpdated.firstPersonTransform().offsetX(), EPS);
        assertEquals(EditorParameters.MAX_OFFSET, fpUpdated.firstPersonTransform().offsetY(), EPS);
        assertEquals(1.25, fpUpdated.firstPersonTransform().offsetZ(), EPS);
        assertEquals(EditorParameters.MAX_ANGLE, fpUpdated.firstPersonTransform().yawOffset(), FEPS);
        assertEquals(EditorParameters.MIN_ANGLE, fpUpdated.firstPersonTransform().pitchOffset(), FEPS);
        assertEquals(45.0f, fpUpdated.firstPersonTransform().rollOffset(), FEPS);
        assertEquals(EditorParameters.MIN_SLOT_SPACING, fpUpdated.slotSpacing(), FEPS);

        ScreenProfile tpUpdated = EditorParameters.applyParameter(fpUpdated, ViewMode.THIRD_PERSON, "camera-distance", 15.0);
        tpUpdated = EditorParameters.applyParameter(tpUpdated, ViewMode.THIRD_PERSON, "cam-height", 5.0);
        tpUpdated = EditorParameters.applyParameter(tpUpdated, ViewMode.THIRD_PERSON, "cam-side", -10.0);
        tpUpdated = EditorParameters.applyParameter(tpUpdated, ViewMode.THIRD_PERSON, "cam-pitch", 120.0);
        tpUpdated = EditorParameters.applyParameter(tpUpdated, ViewMode.THIRD_PERSON, "clearance", 0.1);
        tpUpdated = EditorParameters.applyParameter(tpUpdated, ViewMode.THIRD_PERSON, "open-ticks", 60.0);
        tpUpdated = EditorParameters.applyParameter(tpUpdated, ViewMode.THIRD_PERSON, "close-ticks", -4.0);
        tpUpdated = EditorParameters.applyParameter(tpUpdated, ViewMode.THIRD_PERSON_FRONT, "front-cam-distance", 12.0);
        tpUpdated = EditorParameters.applyParameter(tpUpdated, ViewMode.THIRD_PERSON_FRONT, "front-cam-height", -5.0);
        tpUpdated = EditorParameters.applyParameter(tpUpdated, ViewMode.THIRD_PERSON_FRONT, "front-cam-side", 8.0);
        tpUpdated = EditorParameters.applyParameter(tpUpdated, ViewMode.THIRD_PERSON_FRONT, "front-cam-yaw", -270.0);
        tpUpdated = EditorParameters.applyParameter(tpUpdated, ViewMode.THIRD_PERSON_FRONT, "front-cam-pitch", -120.0);
        tpUpdated = EditorParameters.adjustParameter(tpUpdated, ViewMode.THIRD_PERSON, "cam-distance", -2.5);

        assertEquals(EditorParameters.MAX_CAM_DISTANCE - 2.5, tpUpdated.thirdPersonSettings().cameraDistance(), EPS);
        assertEquals(EditorParameters.MAX_CAM_HEIGHT, tpUpdated.thirdPersonSettings().cameraHeightOffset(), EPS);
        assertEquals(EditorParameters.MIN_CAM_SIDE, tpUpdated.thirdPersonSettings().cameraSideOffset(), EPS);
        assertEquals(EditorParameters.MAX_CAM_PITCH, tpUpdated.thirdPersonSettings().cameraPitchOffset(), FEPS);
        assertEquals(EditorParameters.MIN_CLEARANCE, tpUpdated.thirdPersonSettings().minClearance(), EPS);
        assertEquals(EditorParameters.MAX_TRANSITION_TICKS, tpUpdated.thirdPersonSettings().openTransitionTicks());
        assertEquals(EditorParameters.MIN_TRANSITION_TICKS, tpUpdated.thirdPersonSettings().closeTransitionTicks());
        assertEquals(EditorParameters.MAX_CAM_DISTANCE, tpUpdated.thirdPersonSettings().frontCameraDistance(), EPS);
        assertEquals(EditorParameters.MIN_CAM_HEIGHT, tpUpdated.thirdPersonSettings().frontCameraHeightOffset(), EPS);
        assertEquals(EditorParameters.MAX_CAM_SIDE, tpUpdated.thirdPersonSettings().frontCameraSideOffset(), EPS);
        assertEquals(EditorParameters.MIN_ANGLE, tpUpdated.thirdPersonSettings().frontCameraYawOffset(), FEPS);
        assertEquals(EditorParameters.MIN_CAM_PITCH, tpUpdated.thirdPersonSettings().frontCameraPitchOffset(), FEPS);

        for (String param : EditorParameters.PARAMETER_NAMES) {
            assertTrue(EditorParameters.isValidParameter(param));
            double val = EditorParameters.getParameterValue(tpUpdated, ViewMode.THIRD_PERSON, param);
            assertTrue(val >= EditorParameters.getParameterMin(param) - EPS);
            assertTrue(val <= EditorParameters.getParameterMax(param) + EPS);
        }

        assertThrows(IllegalArgumentException.class, () ->
            EditorParameters.applyParameter(profile, ViewMode.FIRST_PERSON, "nonexistent", 1.0));
        assertThrows(IllegalArgumentException.class, () ->
            EditorParameters.applyParameter(profile, ViewMode.FIRST_PERSON, "distance", Double.NaN));
    }

    @Test
    void cycleViewModeIncludesThirdPersonFrontAndUsesThirdPersonTransform() {
        ScreenProfile p0 = BukkitTestSupport.defaultProfile(ScreenType.CHEST, true).withViewMode(ViewMode.AUTO);
        ScreenProfile p1 = EditorParameters.cycleViewMode(p0);
        ScreenProfile p2 = EditorParameters.cycleViewMode(p1);
        ScreenProfile p3 = EditorParameters.cycleViewMode(p2);
        ScreenProfile p4 = EditorParameters.cycleViewMode(p3);
        ScreenProfile p5 = EditorParameters.cycleViewMode(p4);

        assertEquals(ViewMode.FIRST_PERSON, p1.viewMode());
        assertEquals(ViewMode.THIRD_PERSON, p2.viewMode());
        assertEquals(ViewMode.THIRD_PERSON_FRONT, p3.viewMode());
        assertEquals(ViewMode.DISABLED, p4.viewMode());
        assertEquals(ViewMode.AUTO, p5.viewMode());

        assertEquals(p0.thirdPersonTransform(), EditorParameters.activeTransform(p0, ViewMode.THIRD_PERSON_FRONT));
        ScreenProfile updatedFront = EditorParameters.applyParameter(p0, ViewMode.THIRD_PERSON_FRONT, "distance", 3.4);
        assertEquals(3.4, updatedFront.thirdPersonTransform().distance(), EPS);
        assertEquals(p0.firstPersonTransform().distance(), updatedFront.firstPersonTransform().distance(), EPS);
    }
}
