package com.cookietps.camera;

import com.cookietps.BukkitTestSupport;
import com.cookietps.config.ProfileManager;
import com.cookietps.model.RenderTransform;
import com.cookietps.model.ScreenProfile;
import com.cookietps.model.ScreenType;
import com.cookietps.model.ThirdPersonSettings;
import com.cookietps.model.ViewMode;
import com.cookietps.render.CurvedPanelMath;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MouseHeadTrackingTest {
    private static final Path BUNDLED_CONFIG = Path.of("src", "main", "resources", "config.yml");
    private static final Path BUNDLED_PROFILES = Path.of("src", "main", "resources", "profiles.yml");

    @Test
    void thirdPersonBackTracksContinuousMousePlaneIntersections(@TempDir Path tempDir) {
        Plugin plugin = BukkitTestSupport.createConfigPluginProxy(tempDir, BUNDLED_CONFIG, BUNDLED_PROFILES);
        ScreenProfile chest = new ProfileManager(plugin).getProfile(ScreenType.CHEST);
        ThirdPersonSettings settings = chest.thirdPersonSettings();
        RenderTransform transform = chest.thirdPersonTransform();

        Location avatarEye = new Location(null, 0.0, 65.62, 0.0, 0.0f, 0.0f);
        Location camLoc = WallOcclusionCalculator.computeDesiredCameraLocation(avatarEye, settings, ViewMode.THIRD_PERSON);
        CurvedPanelMath.PanelFrame frame = CurvedPanelMath.computePanelFrame(avatarEye, transform);

        Vector leftPanelPoint = frame.center().clone().add(frame.right().clone().multiply(-0.8));
        Vector rightPanelPoint = frame.center().clone().add(frame.right().clone().multiply(0.8));
        Vector upPanelPoint = frame.center().clone().add(frame.up().clone().multiply(0.7));
        Vector downPanelPoint = frame.center().clone().add(frame.up().clone().multiply(-0.7));

        Vector leftHit = CurvedPanelMath.intersectRayWithPanelPlane(
            camLoc.toVector(), leftPanelPoint.clone().subtract(camLoc.toVector()).normalize(), frame, 12.0
        );
        Vector rightHit = CurvedPanelMath.intersectRayWithPanelPlane(
            camLoc.toVector(), rightPanelPoint.clone().subtract(camLoc.toVector()).normalize(), frame, 12.0
        );
        Vector upHit = CurvedPanelMath.intersectRayWithPanelPlane(
            camLoc.toVector(), upPanelPoint.clone().subtract(camLoc.toVector()).normalize(), frame, 12.0
        );
        Vector downHit = CurvedPanelMath.intersectRayWithPanelPlane(
            camLoc.toVector(), downPanelPoint.clone().subtract(camLoc.toVector()).normalize(), frame, 12.0
        );

        assertNotNull(leftHit);
        assertNotNull(rightHit);
        assertNotNull(upHit);
        assertNotNull(downHit);

        float bodyYaw = avatarEye.getYaw() + settings.avatarBodyYawOffset();
        EulerAngle poseLeft = WallOcclusionCalculator.computeHeadPose(avatarEye, toLoc(leftHit), bodyYaw);
        EulerAngle poseRight = WallOcclusionCalculator.computeHeadPose(avatarEye, toLoc(rightHit), bodyYaw);
        EulerAngle poseUp = WallOcclusionCalculator.computeHeadPose(avatarEye, toLoc(upHit), bodyYaw);
        EulerAngle poseDown = WallOcclusionCalculator.computeHeadPose(avatarEye, toLoc(downHit), bodyYaw);

        assertTrue(poseLeft.getY() < poseRight.getY());
        assertTrue(poseUp.getX() < 0.0);
        assertTrue(poseDown.getX() > 0.0);
        assertTrue(poseUp.getX() < poseDown.getX());
    }

    @Test
    void thirdPersonFrontTracksContinuousMouseAnglesLeftRightUpDown(@TempDir Path tempDir) {
        Plugin plugin = BukkitTestSupport.createConfigPluginProxy(tempDir, BUNDLED_CONFIG, BUNDLED_PROFILES);
        ScreenProfile chest = new ProfileManager(plugin).getProfile(ScreenType.CHEST);
        ThirdPersonSettings settings = chest.thirdPersonSettings();
        RenderTransform transform = chest.thirdPersonTransform();

        Location avatarEye = new Location(null, 0.0, 65.62, 0.0, 0.0f, 0.0f);
        Location frontCam = WallOcclusionCalculator.computeDesiredCameraLocation(avatarEye, settings, ViewMode.THIRD_PERSON_FRONT);
        Location frontOrigin = avatarEye.clone();
        frontOrigin.setPitch(0.0f);
        frontOrigin.setYaw(WallOcclusionCalculator.normalizeYawDegrees(
            frontOrigin.getYaw() + settings.avatarBodyYawOffset() + settings.frontCameraYawOffset()
        ));
        CurvedPanelMath.PanelFrame frontFrame = CurvedPanelMath.computePanelFrame(frontOrigin, transform);

        Location avatarLeftTarget = new Location(null, 1.5, 65.62, 1.5);
        Location avatarRightTarget = new Location(null, -1.5, 65.62, 1.5);
        Location avatarUpTarget = new Location(null, 0.0, 67.2, 2.0);
        Location avatarDownTarget = new Location(null, 0.0, 64.0, 2.0);

        float bodyYaw = avatarEye.getYaw() + settings.avatarBodyYawOffset();
        EulerAngle headLeft = WallOcclusionCalculator.computeHeadPose(avatarEye, avatarLeftTarget, bodyYaw);
        EulerAngle headRight = WallOcclusionCalculator.computeHeadPose(avatarEye, avatarRightTarget, bodyYaw);
        EulerAngle headUp = WallOcclusionCalculator.computeHeadPose(avatarEye, avatarUpTarget, bodyYaw);
        EulerAngle headDown = WallOcclusionCalculator.computeHeadPose(avatarEye, avatarDownTarget, bodyYaw);

        assertTrue(headLeft.getY() < 0.0);
        assertTrue(headRight.getY() > 0.0);
        assertTrue(headUp.getX() < 0.0);
        assertTrue(headDown.getX() > 0.0);

        Vector panelCenterHit = CurvedPanelMath.intersectRayWithPanelPlane(
            frontCam.toVector(), frontFrame.center().clone().subtract(frontCam.toVector()).normalize(), frontFrame, 14.0
        );
        Vector panelLeftEdgeHit = CurvedPanelMath.intersectRayWithPanelPlane(
            frontCam.toVector(),
            frontFrame.center().clone().add(frontFrame.right().clone().multiply(-1.2)).subtract(frontCam.toVector()).normalize(),
            frontFrame,
            14.0
        );
        Vector panelUpHit = CurvedPanelMath.intersectRayWithPanelPlane(
            frontCam.toVector(),
            frontFrame.center().clone().add(frontFrame.up().clone().multiply(0.8)).subtract(frontCam.toVector()).normalize(),
            frontFrame,
            14.0
        );
        Vector panelDownHit = CurvedPanelMath.intersectRayWithPanelPlane(
            frontCam.toVector(),
            frontFrame.center().clone().add(frontFrame.up().clone().multiply(-0.8)).subtract(frontCam.toVector()).normalize(),
            frontFrame,
            14.0
        );

        assertNotNull(panelCenterHit);
        assertNotNull(panelLeftEdgeHit);
        assertNotNull(panelUpHit);
        assertNotNull(panelDownHit);

        EulerAngle lookPanelCenter = WallOcclusionCalculator.computeHeadPose(avatarEye, toLoc(panelCenterHit), bodyYaw);
        EulerAngle lookScreenLeft = WallOcclusionCalculator.computeHeadPose(avatarEye, new Location(null, -1.2, 65.62, 0.8), bodyYaw);
        EulerAngle lookScreenCenter = WallOcclusionCalculator.computeHeadPose(avatarEye, new Location(null, 0.0, 65.62, 1.5), bodyYaw);
        EulerAngle lookPanelUp = WallOcclusionCalculator.computeHeadPose(avatarEye, toLoc(panelUpHit), bodyYaw);
        EulerAngle lookPanelDown = WallOcclusionCalculator.computeHeadPose(avatarEye, toLoc(panelDownHit), bodyYaw);

        assertTrue(lookScreenLeft.getY() > lookScreenCenter.getY());
        assertTrue(lookScreenCenter.getY() > lookPanelCenter.getY());
        assertTrue(lookPanelUp.getX() < lookPanelDown.getX());
    }

    private static Location toLoc(Vector vec) {
        return new Location(null, vec.getX(), vec.getY(), vec.getZ());
    }
}
