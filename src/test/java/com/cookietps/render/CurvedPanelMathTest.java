package com.cookietps.render;

import com.cookietps.model.PanelSlotLayout;
import com.cookietps.model.RenderTransform;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurvedPanelMathTest {
    private static final float EPS = 1.0e-4f;

    @Test
    void flatProjectionWhenCurvatureBelowThreshold() {
        CurvedPanelMath.CurvedPoint zero = CurvedPanelMath.project(0.75f, -0.25f, 0.0f);
        assertEquals(0.75f, zero.x(), EPS);
        assertEquals(-0.25f, zero.y(), EPS);
        assertEquals(0.0f, zero.z(), EPS);
        assertEquals(0.0f, zero.localYawRad(), EPS);

        CurvedPanelMath.CurvedPoint tinyPos = CurvedPanelMath.project(-0.5f, 0.3f, 0.0005f);
        assertEquals(-0.5f, tinyPos.x(), EPS);
        assertEquals(0.3f, tinyPos.y(), EPS);
        assertEquals(0.0f, tinyPos.z(), EPS);
        assertEquals(0.0f, tinyPos.localYawRad(), EPS);

        CurvedPanelMath.CurvedPoint tinyNeg = CurvedPanelMath.project(0.4f, 0.1f, -0.0009f);
        assertEquals(0.4f, tinyNeg.x(), EPS);
        assertEquals(0.1f, tinyNeg.y(), EPS);
        assertEquals(0.0f, tinyNeg.z(), EPS);
        assertEquals(0.0f, tinyNeg.localYawRad(), EPS);
    }

    @Test
    void concaveAndConvexArcProjectionAndSymmetry() {
        CurvedPanelMath.CurvedPoint center = CurvedPanelMath.project(0.0f, 0.2f, 0.5f);
        assertEquals(0.0f, center.x(), EPS);
        assertEquals(0.2f, center.y(), EPS);
        assertEquals(0.0f, center.z(), EPS);
        assertEquals(0.0f, center.localYawRad(), EPS);

        CurvedPanelMath.CurvedPoint right = CurvedPanelMath.project(0.8f, 0.1f, 0.5f);
        CurvedPanelMath.CurvedPoint left = CurvedPanelMath.project(-0.8f, 0.1f, 0.5f);
        assertEquals(-right.x(), left.x(), EPS);
        assertEquals(right.z(), left.z(), EPS);
        assertEquals(-right.localYawRad(), left.localYawRad(), EPS);
        assertTrue(right.z() > 0.0f);
        assertTrue(right.localYawRad() < 0.0f);
        assertTrue(left.localYawRad() > 0.0f);

        CurvedPanelMath.CurvedPoint convexRight = CurvedPanelMath.project(0.8f, 0.1f, -0.5f);
        CurvedPanelMath.CurvedPoint convexLeft = CurvedPanelMath.project(-0.8f, 0.1f, -0.5f);
        assertEquals(-convexRight.x(), convexLeft.x(), EPS);
        assertEquals(convexRight.z(), convexLeft.z(), EPS);
        assertTrue(convexRight.z() < 0.0f);
        assertTrue(convexRight.localYawRad() > 0.0f);
    }

    @Test
    void largeLocalXClampsArcAngleToMaxRadians() {
        CurvedPanelMath.CurvedPoint clampedPos = CurvedPanelMath.project(10.0f, 0.0f, 1.5f);
        CurvedPanelMath.CurvedPoint clampedNeg = CurvedPanelMath.project(-10.0f, 0.0f, 1.5f);
        assertEquals(-1.25f, clampedPos.localYawRad(), EPS);
        assertEquals(1.25f, clampedNeg.localYawRad(), EPS);
    }

    @Test
    void raycastHitsEveryFlatSlotAccurately() {
        RenderTransform flat = new RenderTransform(2.2, 1.0f, 0.0, 0.0, 0.0, 0.0f, 0.0f, 0.0f, 0.0f, 4, 3, 2);
        Location origin = new Location(null, 10.0, 65.62, -5.0, 35.0f, 10.0f);
        CurvedPanelMath.PanelFrame frame = CurvedPanelMath.computePanelFrame(origin, flat);
        List<PanelSlotLayout> grid = build3x3Grid(0.24f);

        Vector rayOrigin = origin.toVector();
        for (PanelSlotLayout slot : grid) {
            Vector target = CurvedPanelMath.slotWorldPosition(frame, slot, flat);
            Vector dir = target.clone().subtract(rayOrigin).normalize();
            CurvedPanelMath.RaycastHit hit = CurvedPanelMath.raycastSlotDetail(
                rayOrigin, dir, 6.0, frame, grid, flat, 0.24f
            );
            assertNotNull(hit);
            assertEquals(slot.spatialIndex(), hit.spatialIndex());
            assertTrue(hit.distance() > 0.0 && hit.distance() <= 6.0);
        }
    }

    @Test
    void raycastHitsEveryCurvedSlotAccuratelyConcaveAndConvex() {
        float[] curvatures = {0.5f, -0.4f};
        for (float curvature : curvatures) {
            RenderTransform curved = new RenderTransform(2.3, 1.1f, 0.1, -0.05, 0.0, 5.0f, -15.0f, 0.0f, curvature, 4, 3, 2);
            Location origin = new Location(null, 0.0, 70.0, 0.0, -90.0f, -5.0f);
            CurvedPanelMath.PanelFrame frame = CurvedPanelMath.computePanelFrame(origin, curved);
            List<PanelSlotLayout> grid = build3x3Grid(0.26f);
            Vector rayOrigin = origin.toVector();

            for (PanelSlotLayout slot : grid) {
                Vector target = CurvedPanelMath.slotWorldPosition(frame, slot, curved);
                Vector dir = target.clone().subtract(rayOrigin).normalize();
                int hitIndex = CurvedPanelMath.raycastSlot(
                    rayOrigin, dir, 6.0, frame, grid, curved, 0.26f
                );
                assertEquals(slot.spatialIndex(), hitIndex);
            }
        }
    }

    @Test
    void raycastMissesOutsideBoundsBehindOrOutOfRange() {
        RenderTransform flat = new RenderTransform(2.5, 1.0f, 0.0, 0.0, 0.0, 0.0f, 0.0f, 0.0f, 0.35f, 4, 3, 2);
        Location origin = new Location(null, 0.0, 65.0, 0.0, 0.0f, 0.0f);
        CurvedPanelMath.PanelFrame frame = CurvedPanelMath.computePanelFrame(origin, flat);
        List<PanelSlotLayout> grid = build3x3Grid(0.24f);
        Vector rayOrigin = origin.toVector();

        Vector farRightPoint = frame.center().clone().add(frame.right().clone().multiply(3.0));
        Vector outsideDir = farRightPoint.subtract(rayOrigin).normalize();
        assertEquals(-1, CurvedPanelMath.raycastSlot(rayOrigin, outsideDir, 6.0, frame, grid, flat, 0.24f));
        assertNull(CurvedPanelMath.raycastSlotDetail(rayOrigin, outsideDir, 6.0, frame, grid, flat, 0.24f));

        Vector reverseDir = frame.forward().clone().multiply(-1.0);
        assertEquals(-1, CurvedPanelMath.raycastSlot(rayOrigin, reverseDir, 6.0, frame, grid, flat, 0.24f));

        Vector centerDir = frame.center().clone().subtract(rayOrigin).normalize();
        assertEquals(-1, CurvedPanelMath.raycastSlot(rayOrigin, centerDir, 1.0, frame, grid, flat, 0.24f));
    }

    private static List<PanelSlotLayout> build3x3Grid(float spacing) {
        List<PanelSlotLayout> list = new ArrayList<>();
        int idx = 0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                float lx = (col - 1) * spacing;
                float ly = (1 - row) * spacing;
                list.add(new PanelSlotLayout(idx++, idx - 1, true, false, lx, ly, 0.0f));
            }
        }
        return list;
    }
}
