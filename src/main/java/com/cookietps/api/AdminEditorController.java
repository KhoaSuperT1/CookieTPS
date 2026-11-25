package com.cookietps.api;

import com.cookietps.model.ScreenProfile;
import com.cookietps.model.ScreenType;
import com.cookietps.model.ViewMode;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface AdminEditorController {
    boolean startEditor(Player admin, ScreenType screenType);

    void applyTransformDelta(Player admin, ScreenProfile updatedProfile);

    void switchPreviewMode(Player admin, ViewMode mode);

    void openQuickSettingsMenu(Player admin);

    void saveActiveProfile(Player admin);

    void resetActiveProfile(Player admin);

    void exitEditor(Player admin, boolean saveChanges);

    boolean isEditing(UUID playerUuid);

    void shutdownAll();
}
