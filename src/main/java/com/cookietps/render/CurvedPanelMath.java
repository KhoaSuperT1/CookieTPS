package com.cookietps.render;

import com.cookietps.model.PanelSlotLayout;
import com.cookietps.model.RenderTransform;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public final class CurvedPanelMath {
    private static final float FLAT_CURVATURE_EPSILON = 0.001f;
    private static final float MAX_ARC_ANGLE_RAD = 1.25f;

    public record CurvedPoint(float x, float y, float z, float localYawRad) {}

    public record PanelFrame(
        Vector center,
        Vector forward,
        Vector right,
        Vector up,
        Vector normal,
        Quaternionf orientation
    ) {}

    public record SlotPose(
        Vector worldPosition,
        Quaternionf orientation,
        Vector right,
        Vector up,
        Vector normal,
        float localYawRad
    ) {}

    public record RaycastHit(int spatialIndex, double distance) {}

    private CurvedPanelMath() {}

    public static CurvedPoint project(float localX, float localY, float curvature) {
        if (Math.abs(curvature) < FLAT_CURVATURE_EPSILON) {
            return new CurvedPoint(localX, localY, 0.0f, 0.0f);
        }
        float radius = 1.0f / curvature;
        float rawTheta = localX / radius;
        float theta = Math.max(-MAX_ARC_ANGLE_RAD, Math.min(MAX_ARC_ANGLE_RAD, rawTheta));
        float px = (float) (radius * Math.sin(theta));
        float pz = (float) (radius * (1.0 - Math.cos(theta)));
        return new CurvedPoint(px, localY, pz, -theta);
    }

    public static PanelFrame computePanelFrame(Location origin, RenderTransform transform) {
        if (origin == null || transform == null) {
            Vector zero = new Vector(0.0, 0.0, 0.0);
            Vector fwd = new Vector(0.0, 0.0, -1.0);
            Vector rgt = new Vector(1.0, 0.0, 0.0);
            Vector up = new Vector(0.0, 1.0, 0.0);
            Vector nrm = new Vector(0.0, 0.0, 1.0);
            return new PanelFrame(zero, fwd, rgt, up, nrm, new Quaternionf());
        }

        double yawRad = Math.toRadians(origin.getYaw());
        double clampedPitchDeg = Math.max(-85.0, Math.min(85.0, origin.getPitch()));
        double pitchRad = Math.toRadians(clampedPitchDeg);

        Vector baseForward = new Vector(
            -Math.sin(yawRad) * Math.cos(pitchRad),
            -Math.sin(pitchRad),
            Math.cos(yawRad) * Math.cos(pitchRad)
        );
        if (baseForward.lengthSquared() < 1e-8) {
            baseForward = new Vector(0.0, 0.0, 1.0);
        } else {
            baseForward.normalize();
        }

        Vector worldUp = new Vector(0.0, 1.0, 0.0);
        Vector baseRight = baseForward.clone().crossProduct(worldUp);
        if (baseRight.lengthSquared() < 1e-6) {
            baseRight = new Vector(-Math.cos(yawRad), 0.0, -Math.sin(yawRad));
        }
        baseRight.normalize();

        Vector baseUp = baseRight.clone().crossProduct(baseForward).normalize();
        Vector baseNormal = baseForward.clone().multiply(-1.0);

        Vector center = origin.toVector()
            .add(baseForward.clone().multiply(transform.distance() + transform.offsetZ()))
            .add(baseRight.clone().multiply(transform.offsetX()))
            .add(baseUp.clone().multiply(transform.offsetY()));

        Matrix3f baseMatrix = new Matrix3f(
            toVec3f(baseRight),
            toVec3f(baseUp),
            toVec3f(baseNormal)
        );
        Quaternionf baseQuat = new Quaternionf().setFromNormalized(baseMatrix);
        Quaternionf offsetQuat = new Quaternionf()
            .rotateY((float) Math.toRadians(transform.yawOffset()))
            .rotateX((float) Math.toRadians(transform.pitchOffset()))
            .rotateZ((float) Math.toRadians(transform.rollOffset()));
        Quaternionf panelQuat = baseQuat.mul(offsetQuat, new Quaternionf()).normalize();

        Vector3f right3 = panelQuat.transform(new Vector3f(1.0f, 0.0f, 0.0f));
        Vector3f up3 = panelQuat.transform(new Vector3f(0.0f, 1.0f, 0.0f));
        Vector3f normal3 = panelQuat.transform(new Vector3f(0.0f, 0.0f, 1.0f));

        Vector panelRight = new Vector(right3.x, right3.y, right3.z).normalize();
        Vector panelUp = new Vector(up3.x, up3.y, up3.z).normalize();
        Vector panelNormal = new Vector(normal3.x, normal3.y, normal3.z).normalize();
        Vector panelForward = panelNormal.clone().multiply(-1.0);

        return new PanelFrame(center, panelForward, panelRight, panelUp, panelNormal, panelQuat);
    }

    public static SlotPose computeSlotPose(
        PanelFrame frame,
        float localX,
        float localY,
        float localZOffset,
        RenderTransform transform
    ) {
        float scale = Math.max(0.05f, transform.scale());
        CurvedPoint curved = project(localX, localY, transform.curvature());

        Quaternionf slotQuat = new Quaternionf(frame.orientation())
            .rotateY(curved.localYawRad())
            .normalize();

        Vector3f right3 = slotQuat.transform(new Vector3f(1.0f, 0.0f, 0.0f));
        Vector3f up3 = slotQuat.transform(new Vector3f(0.0f, 1.0f, 0.0f));
        Vector3f normal3 = slotQuat.transform(new Vector3f(0.0f, 0.0f, 1.0f));

        Vector slotRight = new Vector(right3.x, right3.y, right3.z).normalize();
        Vector slotUp = new Vector(up3.x, up3.y, up3.z).normalize();
        Vector slotNormal = new Vector(normal3.x, normal3.y, normal3.z).normalize();

        Vector worldPos = frame.center().clone()
            .add(frame.right().clone().multiply(curved.x() * scale))
            .add(frame.up().clone().multiply(curved.y() * scale))
            .add(frame.normal().clone().multiply(curved.z() * scale))
            .add(slotNormal.clone().multiply(localZOffset * scale));

        return new SlotPose(worldPos, slotQuat, slotRight, slotUp, slotNormal, curved.localYawRad());
    }

    public static Vector slotWorldPosition(PanelFrame frame, PanelSlotLayout layout, RenderTransform transform) {
        if (frame == null || layout == null || transform == null) {
            return new Vector(0.0, 0.0, 0.0);
        }
        return computeSlotPose(frame, layout.localX(), layout.localY(), layout.localZ(), transform).worldPosition();
    }

    public static int raycastSlot(
        Vector rayOrigin,
        Vector rayDirection,
        double maxDistance,
        PanelFrame frame,
        List<PanelSlotLayout> layouts,
        RenderTransform transform,
        float slotSpacing
    ) {
        RaycastHit hit = raycastSlotDetail(rayOrigin, rayDirection, maxDistance, frame, layouts, transform, slotSpacing);
        return hit != null ? hit.spatialIndex() : -1;
    }

    public static RaycastHit raycastSlotDetail(
        Vector rayOrigin,
        Vector rayDirection,
        double maxDistance,
        PanelFrame frame,
        List<PanelSlotLayout> layouts,
        RenderTransform transform,
        float slotSpacing
    ) {
        if (rayOrigin == null || rayDirection == null || frame == null || layouts == null || layouts.isEmpty() || transform == null) {
            return null;
        }
        if (rayDirection.lengthSquared() < 1e-10) {
            return null;
        }
        Vector dir = rayDirection.clone().normalize();
        float scale = Math.max(0.05f, transform.scale());
        float effectiveSpacing = Math.max(0.12f, slotSpacing);
        double baseHalfSize = (effectiveSpacing * 0.48) * scale;

        int bestIndex = -1;
        double bestT = Double.MAX_VALUE;
        double bestCenterDistSq = Double.MAX_VALUE;

        for (PanelSlotLayout layout : layouts) {
            SlotPose pose = computeSlotPose(frame, layout.localX(), layout.localY(), layout.localZ(), transform);
            double denom = dir.dot(pose.normal());
            if (Math.abs(denom) < 1e-5) {
                continue;
            }
            Vector toSlot = pose.worldPosition().clone().subtract(rayOrigin);
            double t = toSlot.dot(pose.normal()) / denom;
            if (t <= 0.0 || t > maxDistance) {
                continue;
            }

            Vector hitPoint = rayOrigin.clone().add(dir.clone().multiply(t));
            Vector delta = hitPoint.subtract(pose.worldPosition());
            double dx = delta.dot(pose.right());
            double dy = delta.dot(pose.up());

            double halfSize = layout.closeButton() ? baseHalfSize * 1.15 : baseHalfSize;
            if (Math.abs(dx) <= halfSize && Math.abs(dy) <= halfSize) {
                double centerDistSq = (dx * dx) + (dy * dy);
                if (t < bestT - 1e-3 || (Math.abs(t - bestT) <= 1e-3 && centerDistSq < bestCenterDistSq)) {
                    bestT = t;
                    bestCenterDistSq = centerDistSq;
                    bestIndex = layout.spatialIndex();
                }
            }
        }

        return bestIndex >= 0 ? new RaycastHit(bestIndex, bestT) : null;
    }

    public static Vector intersectRayWithPanelPlane(
        Vector rayOrigin,
        Vector rayDirection,
        PanelFrame frame,
        double maxDistance
    ) {
        if (rayOrigin == null || rayDirection == null || frame == null || rayDirection.lengthSquared() < 1e-10) {
            return null;
        }
        Vector dir = rayDirection.clone().normalize();
        double denom = dir.dot(frame.normal());
        if (Math.abs(denom) < 1e-5) {
            return null;
        }
        double t = frame.center().clone().subtract(rayOrigin).dot(frame.normal()) / denom;
        if (t <= 0.0 || t > maxDistance) {
            return null;
        }
        return rayOrigin.clone().add(dir.multiply(t));
    }

    public static Location toLocation(World world, Vector position, Vector lookDirection) {
        if (position == null) {
            return null;
        }
        float yaw = 0.0f;
        float pitch = 0.0f;
        if (lookDirection != null && lookDirection.lengthSquared() > 1e-8) {
            Vector dir = lookDirection.clone().normalize();
            yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
            double clampedY = Math.max(-1.0, Math.min(1.0, dir.getY()));
            pitch = (float) Math.toDegrees(-Math.asin(clampedY));
        }
        return new Location(world, position.getX(), position.getY(), position.getZ(), yaw, pitch);
    }

    private static Vector3f toVec3f(Vector v) {
        return new Vector3f((float) v.getX(), (float) v.getY(), (float) v.getZ());
    }
}
