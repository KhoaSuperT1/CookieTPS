package com.cookietps;

import com.cookietps.api.AdminEditorController;
import com.cookietps.api.InputHandler;
import com.cookietps.api.SpatialRenderer;
import com.cookietps.api.ThirdPersonCameraController;
import com.cookietps.bridge.ContainerBridge;
import com.cookietps.config.ConfigManager;
import com.cookietps.config.CrashRecoveryStore;
import com.cookietps.config.PlayerPreferencesStore;
import com.cookietps.config.ProfileManager;
import com.cookietps.model.ScreenProfile;
import com.cookietps.model.ScreenType;
import com.cookietps.model.SlotClickAction;
import com.cookietps.model.SpatialSession;
import com.cookietps.model.ViewMode;
import org.bukkit.Location;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

public final class CookieTPSPlugin extends JavaPlugin {
    private static CookieTPSPlugin instance;

    private ConfigManager configManager;
    private ProfileManager profileManager;
    private PlayerPreferencesStore preferencesStore;
    private CrashRecoveryStore crashRecoveryStore;
    private ContainerBridge containerBridge;

    private SpatialRenderer spatialRenderer;
    private ThirdPersonCameraController cameraController;
    private InputHandler inputHandler;
    private AdminEditorController editorController;

    @Override
    public void onEnable() {
        instance = this;

        this.configManager = new ConfigManager(this);
        this.profileManager = new ProfileManager(this);
        this.preferencesStore = new PlayerPreferencesStore(this);
        this.crashRecoveryStore = new CrashRecoveryStore(this);
        this.containerBridge = new ContainerBridge();

        this.spatialRenderer = resolveModule(
            "com.cookietps.render.DisplaySpatialRenderer",
            SpatialRenderer.class,
            new NoOpSpatialRenderer()
        );
        this.cameraController = resolveModule(
            "com.cookietps.camera.SpectatorCameraController",
            ThirdPersonCameraController.class,
            new NoOpCameraController()
        );
        this.inputHandler = resolveModule(
            "com.cookietps.input.SpatialInputListener",
            InputHandler.class,
            new NoOpInputHandler()
        );
        this.editorController = resolveModule(
            "com.cookietps.editor.VisualEditorManager",
            AdminEditorController.class,
            new NoOpAdminEditorController()
        );

        registerIfListener(spatialRenderer);
        registerIfListener(cameraController);
        registerIfListener(inputHandler);
        registerIfListener(editorController);

        Object commandModule = resolveRawModule("com.cookietps.command.CookieTPSCommand");
        PluginCommand pluginCommand = getCommand("cookietps");
        if (pluginCommand != null && commandModule instanceof CommandExecutor executor) {
            pluginCommand.setExecutor(executor);
            if (commandModule instanceof TabCompleter completer) {
                pluginCommand.setTabCompleter(completer);
            }
        }

        cameraController.recoverAllPending();
        getLogger().info("CookieTPS enabled.");
    }

    @Override
    public void onDisable() {
        safeShutdown(editorController != null ? editorController::shutdownAll : null);
        safeShutdown(inputHandler != null ? inputHandler::shutdownAll : null);
        safeShutdown(cameraController != null ? cameraController::shutdownAll : null);
        safeShutdown(spatialRenderer != null ? spatialRenderer::shutdownAll : null);
        instance = null;
    }

    public static CookieTPSPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ProfileManager getProfileManager() {
        return profileManager;
    }

    public PlayerPreferencesStore getPreferencesStore() {
        return preferencesStore;
    }

    public CrashRecoveryStore getCrashRecoveryStore() {
        return crashRecoveryStore;
    }

    public ContainerBridge getContainerBridge() {
        return containerBridge;
    }

    public SpatialRenderer getSpatialRenderer() {
        return spatialRenderer;
    }

    public InputHandler getInputHandler() {
        return inputHandler;
    }

    public ThirdPersonCameraController getCameraController() {
        return cameraController;
    }

    public AdminEditorController getEditorController() {
        return editorController;
    }

    private void registerIfListener(Object module) {
        if (module instanceof Listener listener) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }

    private <T> T resolveModule(String className, Class<T> contract, T fallback) {
        Object resolved = resolveRawModule(className);
        if (contract.isInstance(resolved)) {
            return contract.cast(resolved);
        }
        return fallback;
    }

    private Object resolveRawModule(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Constructor<?> constructor = clazz.getConstructor(CookieTPSPlugin.class);
            return constructor.newInstance(this);
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (ReflectiveOperationException e) {
            getLogger().warning("Failed to initialize module " + className + ": " + e.getMessage());
            return null;
        }
    }

    private void safeShutdown(Runnable shutdownTask) {
        if (shutdownTask == null) {
            return;
        }
        try {
            shutdownTask.run();
        } catch (RuntimeException e) {
            getLogger().warning("Error during shutdown: " + e.getMessage());
        }
    }

    private static final class NoOpSpatialRenderer implements SpatialRenderer {
        @Override
        public void openPanel(Player player, SpatialSession session) {}

        @Override
        public void updatePanel(Player player, SpatialSession session) {}

        @Override
        public void syncContents(Player player, SpatialSession session) {}

        @Override
        public void updateHover(Player player, SpatialSession session, int newHoveredSlot) {}

        @Override
        public void updateCursorVisual(Player player, SpatialSession session) {}

        @Override
        public void refreshTransform(Player player, SpatialSession session, ScreenProfile updatedProfile) {}

        @Override
        public void closePanel(Player player, SpatialSession session, boolean immediate) {}

        @Override
        public int raycastHoveredSlot(Player player, SpatialSession session) {
            return -1;
        }

        @Override
        public Location raycastMouseWorldTarget(Player player, SpatialSession session) {
            return null;
        }

        @Override
        public Location getSlotWorldLocation(SpatialSession session, int spatialSlotIndex) {
            return null;
        }

        @Override
        public void shutdownAll() {}
    }

    private static final class NoOpCameraController implements ThirdPersonCameraController {
        @Override
        public boolean enterThirdPerson(Player player, SpatialSession session) {
            return false;
        }

        @Override
        public void updateCameraAndAvatar(Player player, SpatialSession session, Location hoveredWorldTarget) {}

        @Override
        public boolean checkClearance(Location avatarEyeLocation, ScreenProfile profile) {
            return true;
        }

        @Override
        public void exitThirdPerson(Player player, boolean restoreState) {}

        @Override
        public void exitThirdPersonAnimated(Player player, Runnable onFinished) {
            if (onFinished != null) {
                onFinished.run();
            }
        }

        @Override
        public boolean isInThirdPerson(UUID playerUuid) {
            return false;
        }

        @Override
        public void recoverOnJoin(Player player) {}

        @Override
        public void recoverAllPending() {}

        @Override
        public void shutdownAll() {}
    }

    private static final class NoOpInputHandler implements InputHandler {
        @Override
        public boolean hasActiveSession(UUID playerUuid) {
            return false;
        }

        @Override
        public SpatialSession getSession(UUID playerUuid) {
            return null;
        }

        @Override
        public Collection<SpatialSession> getActiveSessions() {
            return Collections.emptyList();
        }

        @Override
        public void startSession(Player player, Inventory topInventory, ScreenType screenType, boolean editorPreview) {}

        @Override
        public void handleSlotAction(Player player, SpatialSession session, SlotClickAction action) {}

        @Override
        public void stepHoveredSlot(Player player, SpatialSession session, int delta) {}

        @Override
        public void closeSession(Player player, boolean immediate) {}

        @Override
        public void shutdownAll() {}
    }

    private static final class NoOpAdminEditorController implements AdminEditorController {
        @Override
        public boolean startEditor(Player admin, ScreenType screenType) {
            return false;
        }

        @Override
        public void applyTransformDelta(Player admin, ScreenProfile updatedProfile) {}

        @Override
        public void switchPreviewMode(Player admin, ViewMode mode) {}

        @Override
        public void openQuickSettingsMenu(Player admin) {}

        @Override
        public void saveActiveProfile(Player admin) {}

        @Override
        public void resetActiveProfile(Player admin) {}

        @Override
        public void exitEditor(Player admin, boolean saveChanges) {}

        @Override
        public boolean isEditing(UUID playerUuid) {
            return false;
        }

        @Override
        public void shutdownAll() {}
    }
}
