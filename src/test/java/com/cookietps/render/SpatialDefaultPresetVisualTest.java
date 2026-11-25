package com.cookietps.render;

import com.cookietps.BukkitTestSupport;
import com.cookietps.bridge.ContainerBridge;
import com.cookietps.camera.WallOcclusionCalculator;
import com.cookietps.config.ProfileManager;
import com.cookietps.model.PanelSlotLayout;
import com.cookietps.model.RenderTransform;
import com.cookietps.model.ScreenProfile;
import com.cookietps.model.ScreenType;
import com.cookietps.model.ThirdPersonSettings;
import com.cookietps.model.ViewMode;
import org.bukkit.Location;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpatialDefaultPresetVisualTest {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final double VFOV_DEG = 70.0;
    private static final Path BUNDLED_CONFIG = Path.of("src", "main", "resources", "config.yml");
    private static final Path BUNDLED_PROFILES = Path.of("src", "main", "resources", "profiles.yml");
    private static final Path PREVIEW_DIR = Path.of("docs", "previews");

    private record CameraBasis(Vector pos, Vector forward, Vector right, Vector up, double tanHalfV, double tanHalfH) {}

    private record ProjectedPoint(double sx, double sy, double depth, boolean inFront) {}

    private record FramingMetrics(
        double minX,
        double maxX,
        double minY,
        double maxY,
        double centerX,
        double centerY,
        double leftDepth,
        double rightDepth
    ) {}

    @Test
    void firstPersonDefaultMatchesModScreenshotFraming(@TempDir Path tempDir) throws IOException {
        Plugin plugin = BukkitTestSupport.createConfigPluginProxy(tempDir, BUNDLED_CONFIG, BUNDLED_PROFILES);
        ProfileManager manager = new ProfileManager(plugin);
        ScreenProfile crafting = manager.getProfile(ScreenType.CRAFTING);
        ScreenProfile chest = manager.getProfile(ScreenType.CHEST);

        assertEquals(0.0f, crafting.firstPersonTransform().curvature(), 1.0e-5f);
        assertEquals(0.0f, chest.firstPersonTransform().curvature(), 1.0e-5f);
        assertTrue(crafting.firstPersonTransform().offsetX() < 0.0);
        assertTrue(crafting.firstPersonTransform().yawOffset() > 0.0f);
        assertTrue(crafting.firstPersonTransform().rollOffset() > 0.0f);

        Location eye = new Location(null, 0.0, 65.62, 0.0, 0.0f, 0.0f);
        Inventory topInv = new BukkitTestSupport.TestInventoryState(InventoryType.WORKBENCH, 10, null).inventory;
        Inventory bottomInv = new BukkitTestSupport.TestPlayerInventoryState().inventory;

        ContainerBridge bridge = new ContainerBridge();
        List<PanelSlotLayout> layouts = bridge.buildSlotLayouts(crafting, topInv, bottomInv);
        RenderTransform transform = crafting.firstPersonTransform();
        CurvedPanelMath.PanelFrame frame = CurvedPanelMath.computePanelFrame(eye, transform);
        CameraBasis cam = buildCameraBasis(eye);

        FramingMetrics metrics = measurePanel(cam, frame, layouts, transform, crafting.slotSpacing());
        assertTrue(metrics.centerX >= 0.28 && metrics.centerX <= 0.49);
        assertTrue(metrics.minX >= 0.08);
        assertTrue(metrics.maxY < 0.88);
        assertTrue(metrics.leftDepth < metrics.rightDepth);

        PanelSlotLayout sampleSlot = layouts.get(0);
        Vector slotWorld = CurvedPanelMath.slotWorldPosition(frame, sampleSlot, transform);
        Vector rayDir = slotWorld.clone().subtract(eye.toVector()).normalize();
        int hit = CurvedPanelMath.raycastSlot(eye.toVector(), rayDir, 6.0, frame, layouts, transform, crafting.slotSpacing());
        assertEquals(sampleSlot.spatialIndex(), hit);

        BufferedImage img = renderFirstPersonPreview(cam, frame, layouts, crafting, transform, metrics);
        Files.createDirectories(PREVIEW_DIR);
        Path out = PREVIEW_DIR.resolve("cookietps-first-person-preview.png");
        ImageIO.write(img, "png", out.toFile());
        assertTrue(Files.exists(out) && Files.size(out) > 1024);
    }

    @Test
    void thirdPersonDefaultMatchesModScreenshotFraming(@TempDir Path tempDir) throws IOException {
        Plugin plugin = BukkitTestSupport.createConfigPluginProxy(tempDir, BUNDLED_CONFIG, BUNDLED_PROFILES);
        ProfileManager manager = new ProfileManager(plugin);
        ScreenProfile chest = manager.getProfile(ScreenType.CHEST);

        assertEquals(0.0f, chest.thirdPersonTransform().curvature(), 1.0e-5f);
        assertTrue(chest.thirdPersonTransform().offsetX() > 1.0);
        assertTrue(chest.thirdPersonTransform().yawOffset() < -15.0f);

        Location avatarEye = new Location(null, 0.0, 65.62, 0.0, 0.0f, 0.0f);
        RenderTransform transform = chest.thirdPersonTransform();
        ThirdPersonSettings settings = chest.thirdPersonSettings();
        Location camLoc = WallOcclusionCalculator.computeDesiredCameraLocation(avatarEye, settings);
        CurvedPanelMath.PanelFrame frame = CurvedPanelMath.computePanelFrame(avatarEye, transform);
        CameraBasis cam = buildCameraBasis(camLoc);

        Inventory topInv = new BukkitTestSupport.TestInventoryState(InventoryType.CHEST, 27, null).inventory;
        Inventory bottomInv = new BukkitTestSupport.TestPlayerInventoryState().inventory;
        ContainerBridge bridge = new ContainerBridge();
        List<PanelSlotLayout> layouts = bridge.buildSlotLayouts(chest, topInv, bottomInv);

        FramingMetrics panelMetrics = measurePanel(cam, frame, layouts, transform, chest.slotSpacing());
        FramingMetrics avatarMetrics = measureAvatarBox(cam, avatarEye);

        assertTrue(avatarMetrics.centerX >= 0.12 && avatarMetrics.centerX <= 0.36);
        assertTrue(panelMetrics.centerX >= 0.48 && panelMetrics.centerX <= 0.76);
        assertTrue(avatarMetrics.maxX < panelMetrics.minX);
        assertTrue(panelMetrics.rightDepth < panelMetrics.leftDepth);

        PanelSlotLayout hoveredLayout = layouts.get(13);
        Vector hoveredPos = CurvedPanelMath.slotWorldPosition(frame, hoveredLayout, transform);
        Location hoveredWorld = new Location(null, hoveredPos.getX(), hoveredPos.getY(), hoveredPos.getZ());
        EulerAngle headPose = WallOcclusionCalculator.computeHeadPose(avatarEye, hoveredWorld, settings.avatarBodyYawOffset());
        assertTrue(headPose.getY() > 0.15);

        BufferedImage img = renderThirdPersonPreview(cam, avatarEye, headPose, frame, layouts, chest, transform, settings, panelMetrics, avatarMetrics);
        Files.createDirectories(PREVIEW_DIR);
        Path out = PREVIEW_DIR.resolve("cookietps-third-person-preview.png");
        ImageIO.write(img, "png", out.toFile());
        assertTrue(Files.exists(out) && Files.size(out) > 1024);
    }

    @Test
    void thirdPersonFrontDefaultFramesAvatarAndPanelFacingCamera(@TempDir Path tempDir) throws IOException {
        Plugin plugin = BukkitTestSupport.createConfigPluginProxy(tempDir, BUNDLED_CONFIG, BUNDLED_PROFILES);
        ProfileManager manager = new ProfileManager(plugin);
        ScreenProfile chest = manager.getProfile(ScreenType.CHEST);

        Location avatarEye = new Location(null, 0.0, 65.62, 0.0, 0.0f, 0.0f);
        RenderTransform transform = chest.thirdPersonTransform();
        ThirdPersonSettings settings = chest.thirdPersonSettings();
        Location camLoc = WallOcclusionCalculator.computeDesiredCameraLocation(
            avatarEye, settings, ViewMode.THIRD_PERSON_FRONT
        );
        assertTrue(camLoc.getZ() > avatarEye.getZ());

        Location frontOrigin = avatarEye.clone();
        frontOrigin.setPitch(0.0f);
        frontOrigin.setYaw(WallOcclusionCalculator.normalizeYawDegrees(
            frontOrigin.getYaw() + settings.avatarBodyYawOffset() + settings.frontCameraYawOffset()
        ));
        CurvedPanelMath.PanelFrame frame = CurvedPanelMath.computePanelFrame(frontOrigin, transform);
        CameraBasis cam = buildCameraBasis(camLoc);
        assertTrue(frame.normal().dot(cam.forward()) < -0.5);

        Inventory topInv = new BukkitTestSupport.TestInventoryState(InventoryType.CHEST, 27, null).inventory;
        Inventory bottomInv = new BukkitTestSupport.TestPlayerInventoryState().inventory;
        ContainerBridge bridge = new ContainerBridge();
        List<PanelSlotLayout> layouts = bridge.buildSlotLayouts(chest, topInv, bottomInv);

        FramingMetrics panelMetrics = measurePanel(cam, frame, layouts, transform, chest.slotSpacing());
        FramingMetrics avatarMetrics = measureAvatarBox(cam, avatarEye);

        assertTrue(avatarMetrics.centerX >= 0.30 && avatarMetrics.centerX <= 0.58);
        assertTrue(panelMetrics.centerX >= 0.58 && panelMetrics.centerX <= 0.90);
        assertTrue(avatarMetrics.centerX < panelMetrics.centerX);

        PanelSlotLayout hoveredLayout = layouts.get(13);
        Vector hoveredPos = CurvedPanelMath.slotWorldPosition(frame, hoveredLayout, transform);
        Location hoveredWorld = new Location(null, hoveredPos.getX(), hoveredPos.getY(), hoveredPos.getZ());
        EulerAngle headPose = WallOcclusionCalculator.computeHeadPose(avatarEye, hoveredWorld, settings.avatarBodyYawOffset());
        assertTrue(Math.abs(headPose.getY()) > 0.15);

        BufferedImage img = renderThirdPersonFrontPreview(
            cam, avatarEye, headPose, frame, layouts, chest, transform, settings, panelMetrics, avatarMetrics
        );
        Files.createDirectories(PREVIEW_DIR);
        Path out = PREVIEW_DIR.resolve("cookietps-third-person-front-preview.png");
        ImageIO.write(img, "png", out.toFile());
        assertTrue(Files.exists(out) && Files.size(out) > 1024);
    }

    @Test
    void generatesCombinedComparisonOverviewSheet(@TempDir Path tempDir) throws IOException {
        firstPersonDefaultMatchesModScreenshotFraming(tempDir);
        thirdPersonDefaultMatchesModScreenshotFraming(tempDir);
        thirdPersonFrontDefaultFramesAvatarAndPanelFacingCamera(tempDir);

        BufferedImage fp = ImageIO.read(PREVIEW_DIR.resolve("cookietps-first-person-preview.png").toFile());
        BufferedImage tp = ImageIO.read(PREVIEW_DIR.resolve("cookietps-third-person-preview.png").toFile());
        BufferedImage tpFront = ImageIO.read(PREVIEW_DIR.resolve("cookietps-third-person-front-preview.png").toFile());

        int outW = 1880;
        int outH = 445;
        BufferedImage sheet = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(new Color(20, 22, 30));
        g.fillRect(0, 0, outW, outH);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.drawString("CookieTPS 2.0 - Default Spatial GUI Modes (FP / TP-Back / TP-Front)", 28, 36);

        g.setColor(new Color(175, 190, 215));
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.drawString("Pure Server-Side Paper 1.21 - 26.3 | CurvedPanelMath + WallOcclusionCalculator + Interaction Mount", 28, 58);

        int cardW = 592;
        int cardH = 333;
        g.drawImage(fp, 28, 82, cardW, cardH, null);
        g.drawImage(tp, 644, 82, cardW, cardH, null);
        g.drawImage(tpFront, 1260, 82, cardW, cardH, null);

        g.setColor(new Color(88, 180, 255));
        g.setStroke(new BasicStroke(2f));
        g.drawRect(28, 82, cardW, cardH);
        g.setColor(new Color(190, 120, 255));
        g.drawRect(644, 82, cardW, cardH);
        g.setColor(new Color(64, 215, 190));
        g.drawRect(1260, 82, cardW, cardH);

        g.dispose();

        Path overviewOut = PREVIEW_DIR.resolve("cookietps-default-overview.png");
        ImageIO.write(sheet, "png", overviewOut.toFile());
        assertTrue(Files.exists(overviewOut) && Files.size(overviewOut) > 2048);
        assertFalse(Files.isDirectory(overviewOut));
    }

    private static CameraBasis buildCameraBasis(Location loc) {
        double yawRad = Math.toRadians(loc.getYaw());
        double pitchRad = Math.toRadians(loc.getPitch());
        Vector forward = new Vector(
            -Math.sin(yawRad) * Math.cos(pitchRad),
            -Math.sin(pitchRad),
            Math.cos(yawRad) * Math.cos(pitchRad)
        ).normalize();
        Vector worldUp = new Vector(0.0, 1.0, 0.0);
        Vector right = forward.clone().crossProduct(worldUp).normalize();
        Vector up = right.clone().crossProduct(forward).normalize();
        double tanHalfV = Math.tan(Math.toRadians(VFOV_DEG * 0.5));
        double aspect = (double) WIDTH / (double) HEIGHT;
        double tanHalfH = tanHalfV * aspect;
        return new CameraBasis(loc.toVector(), forward, right, up, tanHalfV, tanHalfH);
    }

    private static ProjectedPoint projectPoint(CameraBasis cam, Vector worldPoint) {
        Vector toPoint = worldPoint.clone().subtract(cam.pos());
        double depth = toPoint.dot(cam.forward());
        if (depth <= 0.05) {
            return new ProjectedPoint(0.5, 0.5, depth, false);
        }
        double xCam = toPoint.dot(cam.right());
        double yCam = toPoint.dot(cam.up());
        double nx = 0.5 + (xCam / (depth * cam.tanHalfH())) * 0.5;
        double ny = 0.5 - (yCam / (depth * cam.tanHalfV())) * 0.5;
        return new ProjectedPoint(nx, ny, depth, true);
    }

    private static FramingMetrics measurePanel(
        CameraBasis cam,
        CurvedPanelMath.PanelFrame frame,
        List<PanelSlotLayout> layouts,
        RenderTransform transform,
        float spacing
    ) {
        float minLx = Float.MAX_VALUE;
        float maxLx = -Float.MAX_VALUE;
        float minLy = Float.MAX_VALUE;
        float maxLy = -Float.MAX_VALUE;
        for (PanelSlotLayout layout : layouts) {
            if (!layout.closeButton()) {
                minLx = Math.min(minLx, layout.localX());
                maxLx = Math.max(maxLx, layout.localX());
                minLy = Math.min(minLy, layout.localY());
                maxLy = Math.max(maxLy, layout.localY());
            }
        }
        float pad = spacing * 0.72f;
        float[][] corners = {
            {minLx - pad, maxLy + spacing * 1.15f},
            {maxLx + pad, maxLy + spacing * 1.15f},
            {maxLx + pad, minLy - pad},
            {minLx - pad, minLy - pad}
        };

        double minX = 1.0;
        double maxX = 0.0;
        double minY = 1.0;
        double maxY = 0.0;
        double leftDepth = 0.0;
        double rightDepth = 0.0;

        for (int i = 0; i < corners.length; i++) {
            Vector wp = CurvedPanelMath.computeSlotPose(frame, corners[i][0], corners[i][1], 0.0f, transform).worldPosition();
            ProjectedPoint pp = projectPoint(cam, wp);
            assertTrue(pp.inFront());
            minX = Math.min(minX, pp.sx());
            maxX = Math.max(maxX, pp.sx());
            minY = Math.min(minY, pp.sy());
            maxY = Math.max(maxY, pp.sy());
            if (i == 0 || i == 3) {
                leftDepth += pp.depth() * 0.5;
            } else {
                rightDepth += pp.depth() * 0.5;
            }
        }

        ProjectedPoint centerP = projectPoint(cam, frame.center());
        return new FramingMetrics(minX, maxX, minY, maxY, centerP.sx(), centerP.sy(), leftDepth, rightDepth);
    }

    private static FramingMetrics measureAvatarBox(CameraBasis cam, Location avatarEye) {
        double baseX = avatarEye.getX();
        double feetY = avatarEye.getY() - 1.62;
        double headTopY = avatarEye.getY() + 0.18;
        double baseZ = avatarEye.getZ();
        double minX = 1.0;
        double maxX = 0.0;
        double minY = 1.0;
        double maxY = 0.0;
        double[] xs = {-0.3, 0.3};
        double[] ys = {feetY, headTopY};
        double[] zs = {-0.2, 0.2};
        for (double dx : xs) {
            for (double dy : ys) {
                for (double dz : zs) {
                    ProjectedPoint pp = projectPoint(cam, new Vector(baseX + dx, dy, baseZ + dz));
                    minX = Math.min(minX, pp.sx());
                    maxX = Math.max(maxX, pp.sx());
                    minY = Math.min(minY, pp.sy());
                    maxY = Math.max(maxY, pp.sy());
                }
            }
        }
        ProjectedPoint center = projectPoint(cam, new Vector(baseX, feetY + 0.9, baseZ));
        return new FramingMetrics(minX, maxX, minY, maxY, center.sx(), center.sy(), center.depth(), center.depth());
    }

    private static BufferedImage renderFirstPersonPreview(
        CameraBasis cam,
        CurvedPanelMath.PanelFrame frame,
        List<PanelSlotLayout> layouts,
        ScreenProfile profile,
        RenderTransform transform,
        FramingMetrics metrics
    ) {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        enableQuality(g);

        GradientPaint caveBg = new GradientPaint(0, 0, new Color(72, 76, 80), 0, HEIGHT, new Color(45, 48, 52));
        g.setPaint(caveBg);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        drawStoneGridBackdrop(g);

        drawPanel3D(g, cam, frame, layouts, profile, transform, "[FP] Crafting", 12);

        g.setColor(new Color(185, 134, 108));
        int[] armX = {960, 1180, 1280, 1085};
        int[] armY = {720, 485, 580, 720};
        g.fillPolygon(armX, armY, 4);
        g.setColor(new Color(145, 98, 76));
        g.setStroke(new BasicStroke(3f));
        g.drawPolygon(armX, armY, 4);

        drawHotbarHud(g);

        drawHudBanner(
            g,
            "FIRST-PERSON DEFAULT (profiles.yml -> CRAFTING)",
            String.format(
                "dist=%.2f scale=%.2f offset=(%.2f, %.2f) rot=(P%.1f, Y%.1f, R%.1f) curve=%.2f | centerX=%.2f",
                transform.distance(), transform.scale(), transform.offsetX(), transform.offsetY(),
                transform.pitchOffset(), transform.yawOffset(), transform.rollOffset(), transform.curvature(),
                metrics.centerX()
            ),
            new Color(65, 195, 255)
        );
        g.dispose();
        return img;
    }

    private static BufferedImage renderThirdPersonPreview(
        CameraBasis cam,
        Location avatarEye,
        EulerAngle headPose,
        CurvedPanelMath.PanelFrame frame,
        List<PanelSlotLayout> layouts,
        ScreenProfile profile,
        RenderTransform transform,
        ThirdPersonSettings settings,
        FramingMetrics panelMetrics,
        FramingMetrics avatarMetrics
    ) {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        enableQuality(g);

        GradientPaint sky = new GradientPaint(0, 0, new Color(120, 174, 245), 0, 460, new Color(186, 222, 255));
        g.setPaint(sky);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        g.setColor(new Color(235, 245, 255, 180));
        g.fillRect(140, 75, 190, 32);
        g.fillRect(580, 52, 240, 36);
        g.fillRect(920, 96, 175, 28);

        GradientPaint meadow = new GradientPaint(0, 360, new Color(102, 160, 68), 0, HEIGHT, new Color(76, 128, 48));
        g.setPaint(meadow);
        g.fillRect(0, 360, WIDTH, HEIGHT - 360);

        drawSteve3D(g, cam, avatarEye, headPose);
        drawPanel3D(g, cam, frame, layouts, profile, transform, "[TP] Chest", 13);

        drawHudBanner(
            g,
            "THIRD-PERSON DEFAULT (profiles.yml -> CHEST)",
            String.format(
                "SteveX=%.2f PanelX=%.2f | camDist=%.2f side=%.2f yaw=%.1f | panelOffX=%.2f panelYaw=%.1f | headYaw=%.1f deg",
                avatarMetrics.centerX(), panelMetrics.centerX(),
                settings.cameraDistance(), settings.cameraSideOffset(), settings.cameraYawOffset(),
                transform.offsetX(), transform.yawOffset(), Math.toDegrees(headPose.getY())
            ),
            new Color(195, 130, 255)
        );
        g.dispose();
        return img;
    }

    private static BufferedImage renderThirdPersonFrontPreview(
        CameraBasis cam,
        Location avatarEye,
        EulerAngle headPose,
        CurvedPanelMath.PanelFrame frame,
        List<PanelSlotLayout> layouts,
        ScreenProfile profile,
        RenderTransform transform,
        ThirdPersonSettings settings,
        FramingMetrics panelMetrics,
        FramingMetrics avatarMetrics
    ) {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        enableQuality(g);

        GradientPaint sky = new GradientPaint(0, 0, new Color(96, 158, 228), 0, 460, new Color(172, 214, 250));
        g.setPaint(sky);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        g.setColor(new Color(235, 245, 255, 175));
        g.fillRect(110, 82, 200, 30);
        g.fillRect(520, 56, 230, 34);
        g.fillRect(940, 92, 185, 28);

        GradientPaint meadow = new GradientPaint(0, 360, new Color(92, 152, 62), 0, HEIGHT, new Color(68, 118, 44));
        g.setPaint(meadow);
        g.fillRect(0, 360, WIDTH, HEIGHT - 360);

        drawPanel3D(g, cam, frame, layouts, profile, transform, "[TP-F] Chest", 13);
        drawSteve3D(g, cam, avatarEye, headPose);

        drawHudBanner(
            g,
            "THIRD-PERSON FRONT DEFAULT (Mode 3 -> CHEST)",
            String.format(
                "SteveX=%.2f PanelX=%.2f | frontDist=%.2f side=%.2f yaw=%.1f | headYaw=%.1f deg",
                avatarMetrics.centerX(), panelMetrics.centerX(),
                settings.frontCameraDistance(), settings.frontCameraSideOffset(), settings.frontCameraYawOffset(),
                Math.toDegrees(headPose.getY())
            ),
            new Color(64, 215, 190)
        );
        g.dispose();
        return img;
    }

    private static void drawPanel3D(
        Graphics2D g,
        CameraBasis cam,
        CurvedPanelMath.PanelFrame frame,
        List<PanelSlotLayout> layouts,
        ScreenProfile profile,
        RenderTransform transform,
        String title,
        int hoveredIndex
    ) {
        float spacing = profile.slotSpacing();
        float minLx = Float.MAX_VALUE;
        float maxLx = -Float.MAX_VALUE;
        float minLy = Float.MAX_VALUE;
        float maxLy = -Float.MAX_VALUE;
        for (PanelSlotLayout layout : layouts) {
            if (!layout.closeButton()) {
                minLx = Math.min(minLx, layout.localX());
                maxLx = Math.max(maxLx, layout.localX());
                minLy = Math.min(minLy, layout.localY());
                maxLy = Math.max(maxLy, layout.localY());
            }
        }
        float padX = spacing * 0.72f;
        float padTop = spacing * 1.45f;
        float padBottom = spacing * 0.68f;

        Polygon backdropPoly = quadToPolygon(
            cam, frame, transform,
            minLx - padX, maxLx + padX,
            minLy - padBottom, maxLy + padTop,
            -0.03f
        );
        g.setColor(new Color(198, 198, 198, 240));
        g.fillPolygon(backdropPoly);
        g.setColor(new Color(85, 85, 85));
        g.setStroke(new BasicStroke(3f));
        g.drawPolygon(backdropPoly);

        ProjectedPoint titlePos = projectPoint(
            cam,
            CurvedPanelMath.computeSlotPose(frame, (minLx + maxLx) * 0.5f, maxLy + spacing * 0.96f, 0.0f, transform).worldPosition()
        );
        g.setColor(new Color(48, 48, 48));
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString(title, (int) (titlePos.sx() * WIDTH) - 48, (int) (titlePos.sy() * HEIGHT));

        Color[] itemPalette = {
            new Color(90, 220, 210),
            new Color(150, 80, 220),
            new Color(230, 185, 65),
            new Color(210, 75, 75),
            new Color(95, 195, 85),
            new Color(220, 220, 225)
        };

        float halfTile = spacing * 0.43f;
        for (PanelSlotLayout layout : layouts) {
            if (layout.closeButton()) {
                Polygon closePoly = quadToPolygon(
                    cam, frame, transform,
                    layout.localX() - halfTile, layout.localX() + halfTile,
                    layout.localY() - halfTile, layout.localY() + halfTile,
                    0.01f
                );
                g.setColor(new Color(175, 45, 45));
                g.fillPolygon(closePoly);
                ProjectedPoint cp = projectPoint(cam, CurvedPanelMath.slotWorldPosition(frame, layout, transform));
                g.setColor(Color.WHITE);
                g.setFont(new Font("SansSerif", Font.BOLD, 14));
                g.drawString("X", (int) (cp.sx() * WIDTH) - 4, (int) (cp.sy() * HEIGHT) + 5);
                continue;
            }

            Polygon tilePoly = quadToPolygon(
                cam, frame, transform,
                layout.localX() - halfTile, layout.localX() + halfTile,
                layout.localY() - halfTile, layout.localY() + halfTile,
                0.0f
            );
            boolean hovered = layout.spatialIndex() == hoveredIndex;
            g.setColor(hovered ? new Color(235, 235, 235) : new Color(139, 139, 139, 230));
            g.fillPolygon(tilePoly);
            g.setColor(hovered ? new Color(255, 220, 90) : new Color(78, 78, 78));
            g.setStroke(new BasicStroke(hovered ? 2.5f : 1.2f));
            g.drawPolygon(tilePoly);

            if (layout.spatialIndex() % 3 == 0 || hovered) {
                float iconHalf = halfTile * (hovered ? 0.72f : 0.62f);
                Polygon iconPoly = quadToPolygon(
                    cam, frame, transform,
                    layout.localX() - iconHalf, layout.localX() + iconHalf,
                    layout.localY() - iconHalf, layout.localY() + iconHalf,
                    0.02f
                );
                g.setColor(itemPalette[layout.spatialIndex() % itemPalette.length]);
                g.fillPolygon(iconPoly);
            }
        }

        if (hoveredIndex >= 0 && hoveredIndex < layouts.size()) {
            ProjectedPoint hp = projectPoint(cam, CurvedPanelMath.slotWorldPosition(frame, layouts.get(hoveredIndex), transform));
            int tx = (int) (hp.sx() * WIDTH) + 14;
            int ty = (int) (hp.sy() * HEIGHT) - 18;
            g.setColor(new Color(16, 0, 16, 235));
            g.fillRoundRect(tx, ty, 118, 28, 6, 6);
            g.setColor(new Color(80, 0, 255, 190));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(tx, ty, 118, 28, 6, 6);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.drawString("Diamond x16", tx + 10, ty + 19);
        }
    }

    private static Polygon quadToPolygon(
        CameraBasis cam,
        CurvedPanelMath.PanelFrame frame,
        RenderTransform transform,
        float x0,
        float x1,
        float y0,
        float y1,
        float zOff
    ) {
        int[] xs = new int[4];
        int[] ys = new int[4];
        float[][] pts = {{x0, y1}, {x1, y1}, {x1, y0}, {x0, y0}};
        for (int i = 0; i < 4; i++) {
            Vector wp = CurvedPanelMath.computeSlotPose(frame, pts[i][0], pts[i][1], zOff, transform).worldPosition();
            ProjectedPoint pp = projectPoint(cam, wp);
            xs[i] = (int) Math.round(pp.sx() * WIDTH);
            ys[i] = (int) Math.round(pp.sy() * HEIGHT);
        }
        return new Polygon(xs, ys, 4);
    }

    private static void drawSteve3D(Graphics2D g, CameraBasis cam, Location avatarEye, EulerAngle headPose) {
        double bx = avatarEye.getX();
        double feetY = avatarEye.getY() - 1.62;
        double bz = avatarEye.getZ();

        List<BoxFace> faces = new ArrayList<>();
        addBoxFaces(faces, cam, bx, feetY + 0.36, bz, 0.24, 0.72, 0.24, new Color(46, 46, 168));
        addBoxFaces(faces, cam, bx, feetY + 1.08, bz, 0.48, 0.72, 0.24, new Color(0, 168, 178));
        addBoxFaces(faces, cam, bx - 0.36, feetY + 1.08, bz, 0.22, 0.68, 0.22, new Color(185, 138, 108));
        addBoxFaces(faces, cam, bx + 0.36, feetY + 1.08, bz, 0.22, 0.68, 0.22, new Color(185, 138, 108));
        addBoxFaces(faces, cam, bx, feetY + 1.66, bz, 0.44, 0.44, 0.44, new Color(110, 70, 45));

        faces.sort(Comparator.comparingDouble(BoxFace::avgDepth).reversed());
        for (BoxFace face : faces) {
            g.setColor(face.color());
            g.fillPolygon(face.poly());
            g.setColor(new Color(20, 20, 25, 90));
            g.setStroke(new BasicStroke(1.2f));
            g.drawPolygon(face.poly());
        }

        Vector headCenter = new Vector(bx, feetY + 1.66, bz);
        double lookYaw = headPose.getY();
        Vector eyeRightLook = new Vector(-Math.sin(lookYaw), 0.0, Math.cos(lookYaw)).normalize();
        ProjectedPoint eyeDot = projectPoint(cam, headCenter.clone().add(eyeRightLook.multiply(0.24)));
        g.setColor(Color.WHITE);
        g.fillOval((int) (eyeDot.sx() * WIDTH) - 5, (int) (eyeDot.sy() * HEIGHT) - 3, 10, 6);
        g.setColor(new Color(65, 65, 200));
        g.fillOval((int) (eyeDot.sx() * WIDTH) - 2, (int) (eyeDot.sy() * HEIGHT) - 3, 5, 6);
    }

    private record BoxFace(Polygon poly, Color color, double avgDepth) {}

    private static void addBoxFaces(
        List<BoxFace> out,
        CameraBasis cam,
        double cx,
        double cy,
        double cz,
        double w,
        double h,
        double d,
        Color baseColor
    ) {
        double hw = w * 0.5;
        double hh = h * 0.5;
        double hd = d * 0.5;
        Vector[] v = {
            new Vector(cx - hw, cy - hh, cz - hd),
            new Vector(cx + hw, cy - hh, cz - hd),
            new Vector(cx + hw, cy + hh, cz - hd),
            new Vector(cx - hw, cy + hh, cz - hd),
            new Vector(cx - hw, cy - hh, cz + hd),
            new Vector(cx + hw, cy - hh, cz + hd),
            new Vector(cx + hw, cy + hh, cz + hd),
            new Vector(cx - hw, cy + hh, cz + hd)
        };
        int[][] idx = {
            {0, 1, 2, 3},
            {4, 5, 6, 7},
            {0, 4, 7, 3},
            {1, 5, 6, 2},
            {3, 2, 6, 7}
        };
        float[] shade = {0.82f, 1.0f, 0.90f, 0.72f, 1.08f};
        for (int f = 0; f < idx.length; f++) {
            int[] xs = new int[4];
            int[] ys = new int[4];
            double depthSum = 0.0;
            for (int k = 0; k < 4; k++) {
                ProjectedPoint pp = projectPoint(cam, v[idx[f][k]]);
                xs[k] = (int) Math.round(pp.sx() * WIDTH);
                ys[k] = (int) Math.round(pp.sy() * HEIGHT);
                depthSum += pp.depth();
            }
            int r = Math.min(255, Math.round(baseColor.getRed() * shade[f]));
            int gr = Math.min(255, Math.round(baseColor.getGreen() * shade[f]));
            int b = Math.min(255, Math.round(baseColor.getBlue() * shade[f]));
            out.add(new BoxFace(new Polygon(xs, ys, 4), new Color(r, gr, b), depthSum * 0.25));
        }
    }

    private static void drawStoneGridBackdrop(Graphics2D g) {
        g.setColor(new Color(255, 255, 255, 16));
        for (int x = 0; x < WIDTH; x += 64) {
            g.drawLine(x, 0, x, HEIGHT);
        }
        for (int y = 0; y < HEIGHT; y += 64) {
            g.drawLine(0, y, WIDTH, y);
        }
        g.setColor(new Color(42, 66, 130, 210));
        g.fillPolygon(new int[]{0, 430, 320, 0}, new int[]{535, 585, 720, 720}, 4);
    }

    private static void drawHotbarHud(Graphics2D g) {
        int barW = 396;
        int barH = 44;
        int bx = (WIDTH - barW) / 2;
        int by = HEIGHT - 54;
        g.setColor(new Color(28, 28, 32, 215));
        g.fillRect(bx, by, barW, barH);
        g.setColor(new Color(140, 140, 150));
        g.setStroke(new BasicStroke(2f));
        g.drawRect(bx, by, barW, barH);
        for (int i = 0; i < 9; i++) {
            g.drawRect(bx + 4 + i * 43, by + 4, 38, 36);
        }
        g.setColor(Color.WHITE);
        g.drawRect(bx + 4 + 4 * 43, by + 4, 38, 36);
    }

    private static void drawHudBanner(Graphics2D g, String title, String subtitle, Color accent) {
        g.setColor(new Color(14, 16, 22, 200));
        g.fillRoundRect(18, 16, 860, 56, 10, 10);
        g.setColor(accent);
        g.fillRect(18, 16, 5, 56);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString(title, 34, 38);
        g.setColor(new Color(215, 222, 235));
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.drawString(subtitle, 34, 60);
    }

    private static void enableQuality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
}
