package com.cookietps.api;

import com.cookietps.model.ScreenProfile;
import com.cookietps.model.SpatialSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface ThirdPersonCameraController {
    boolean enterThirdPerson(Player player, SpatialSession session);

    void updateCameraAndAvatar(Player player, SpatialSession session, Location hoveredWorldTarget);

    boolean checkClearance(Location avatarEyeLocation, ScreenProfile profile);

    void exitThirdPerson(Player player, boolean restoreState);

    void exitThirdPersonAnimated(Player player, Runnable onFinished);

    boolean isInThirdPerson(UUID playerUuid);

    void recoverOnJoin(Player player);

    void recoverAllPending();

    void shutdownAll();
}
