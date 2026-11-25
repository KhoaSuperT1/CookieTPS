package com.cookietps.camera;

import com.cookietps.BukkitTestSupport;
import org.bukkit.Location;
import org.bukkit.util.EulerAngle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraTransitionTest {
    private static final double EPS = 1.0e-5;

    @Test
    void cubicEasingCurvesRespectEndpointsClampingAndMonotonicity() {
        assertEquals(0.0, CameraTransitionMath.easeOutCubic(-0.5), EPS);
        assertEquals(0.0, CameraTransitionMath.easeOutCubic(0.0), EPS);
        assertEquals(0.875, CameraTransitionMath.easeOutCubic(0.5), EPS);
        assertEquals(1.0, CameraTransitionMath.easeOutCubic(1.0), EPS);
        assertEquals(1.0, CameraTransitionMath.easeOutCubic(1.5), EPS);
        assertEquals(0.0, CameraTransitionMath.easeOutCubic(Double.NaN), EPS);

        assertEquals(0.0, CameraTransitionMath.easeInOutCubic(-0.2), EPS);
        assertEquals(0.0, CameraTransitionMath.easeInOutCubic(0.0), EPS);
        assertEquals(0.5, CameraTransitionMath.easeInOutCubic(0.5), EPS);
        assertEquals(1.0, CameraTransitionMath.easeInOutCubic(1.0), EPS);
        assertEquals(1.0, CameraTransitionMath.easeInOutCubic(2.0), EPS);
        assertEquals(0.0, CameraTransitionMath.easeInOutCubic(Double.NaN), EPS);

        double prevOut = -1.0;
        double prevInOut = -1.0;
        for (int i = 0; i <= 20; i++) {
            double t = i / 20.0;
            double outVal = CameraTransitionMath.easeOutCubic(t);
            double inOutVal = CameraTransitionMath.easeInOutCubic(t);
            assertTrue(outVal >= prevOut - EPS);
            assertTrue(inOutVal >= prevInOut - EPS);
            assertEquals(1.0 - CameraTransitionMath.easeInOutCubic(1.0 - t), inOutVal, EPS);
            prevOut = outVal;
            prevInOut = inOutVal;
        }
    }

    @Test
    void interpolateLocationWrapsYawAcrossBoundaryAndSupportsReverseGlide() {
        BukkitTestSupport.TestWorldState worldState = new BukkitTestSupport.TestWorldState();

        Location fromPositive = new Location(worldState.world, 0.0, 65.62, 0.0, 170.0f, 10.0f);
        Location toNegative = new Location(worldState.world, 2.0, 66.62, -2.0, -170.0f, -10.0f);

        Location midCross = CameraTransitionMath.interpolateLocation(fromPositive, toNegative, 0.5);
        assertNotNull(midCross);
        assertEquals(1.0, midCross.getX(), EPS);
        assertEquals(66.12, midCross.getY(), EPS);
        assertEquals(-1.0, midCross.getZ(), EPS);
        assertEquals(180.0, Math.abs(midCross.getYaw()), 1.0e-3);
        assertEquals(0.0f, midCross.getPitch(), 1.0e-3f);

        Location reverseCross = CameraTransitionMath.interpolateLocation(toNegative, fromPositive, 0.25);
        assertEquals(-175.0f, reverseCross.getYaw(), 1.0e-3f);

        Location eyeOrigin = new Location(worldState.world, 10.0, 70.62, -4.0, 15.0f, 5.0f);
        Location tpCamera = new Location(worldState.world, 8.2, 70.57, -6.1, 41.0f, 7.0f);

        Location openStart = CameraTransitionMath.interpolateLocation(eyeOrigin, tpCamera, CameraTransitionMath.easeInOutCubic(0.0));
        Location openEnd = CameraTransitionMath.interpolateLocation(eyeOrigin, tpCamera, CameraTransitionMath.easeInOutCubic(1.0));
        assertEquals(eyeOrigin.getX(), openStart.getX(), EPS);
        assertEquals(eyeOrigin.getY(), openStart.getY(), EPS);
        assertEquals(eyeOrigin.getZ(), openStart.getZ(), EPS);
        assertEquals(tpCamera.getX(), openEnd.getX(), EPS);
        assertEquals(tpCamera.getY(), openEnd.getY(), EPS);
        assertEquals(tpCamera.getZ(), openEnd.getZ(), EPS);

        Location closeEnd = CameraTransitionMath.interpolateLocation(tpCamera, eyeOrigin, CameraTransitionMath.easeInOutCubic(1.0));
        assertEquals(eyeOrigin.getX(), closeEnd.getX(), EPS);
        assertEquals(eyeOrigin.getY(), closeEnd.getY(), EPS);
        assertEquals(eyeOrigin.getZ(), closeEnd.getZ(), EPS);
        assertEquals(eyeOrigin.getYaw(), closeEnd.getYaw(), 1.0e-3f);
        assertEquals(eyeOrigin.getPitch(), closeEnd.getPitch(), 1.0e-3f);

        assertNull(CameraTransitionMath.interpolateLocation(null, null, 0.5));
        assertEquals(eyeOrigin.getX(), CameraTransitionMath.interpolateLocation(eyeOrigin, null, 0.5).getX(), EPS);
        assertEquals(tpCamera.getX(), CameraTransitionMath.interpolateLocation(null, tpCamera, 0.5).getX(), EPS);
    }

    @Test
    void lerpHeadPoseInterpolatesAnglesAndHandlesNulls() {
        EulerAngle a = new EulerAngle(-0.4, -0.6, 0.0);
        EulerAngle b = new EulerAngle(0.2, 0.8, 0.0);

        EulerAngle mid = CameraTransitionMath.lerpHeadPose(a, b, 0.5);
        assertEquals(-0.1, mid.getX(), EPS);
        assertEquals(0.1, mid.getY(), EPS);
        assertEquals(0.0, mid.getZ(), EPS);

        EulerAngle start = CameraTransitionMath.lerpHeadPose(a, b, -1.0);
        assertEquals(a.getX(), start.getX(), EPS);
        assertEquals(a.getY(), start.getY(), EPS);

        EulerAngle end = CameraTransitionMath.lerpHeadPose(a, b, 2.0);
        assertEquals(b.getX(), end.getX(), EPS);
        assertEquals(b.getY(), end.getY(), EPS);

        assertEquals(b, CameraTransitionMath.lerpHeadPose(null, b, 0.4));
        assertEquals(a, CameraTransitionMath.lerpHeadPose(a, null, 0.4));
        assertEquals(EulerAngle.ZERO, CameraTransitionMath.lerpHeadPose(null, null, 0.4));
    }
}
