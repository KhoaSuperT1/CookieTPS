package com.cookietps.camera;

import com.cookietps.BukkitTestSupport;
import com.cookietps.config.CrashRecoveryStore;
import com.cookietps.model.CameraSnapshot;
import com.cookietps.model.ScreenProfile;
import com.cookietps.model.ScreenType;
import com.cookietps.model.SpatialSession;
import com.cookietps.model.ViewMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeetVsEyeOriginRegressionTest {
    private static final double EPS = 1.0e-5;
    private static final Path BUNDLED_CONFIG = Path.of("src", "main", "resources", "config.yml");
    private static final Path BUNDLED_PROFILES = Path.of("src", "main", "resources", "profiles.yml");

    @Test
    void spatialSessionKeepsFeetEyeAndAnchorOriginsIndependent() {
        BukkitTestSupport.TestWorldState worldState = new BukkitTestSupport.TestWorldState();
        ScreenProfile profile = BukkitTestSupport.defaultProfile(ScreenType.CHEST, true);
        Location feet = new Location(worldState.world, 12.5, 64.0, -7.25, 35.0f, 0.0f);
        Location eye = new Location(worldState.world, 12.5, 65.62, -7.25, 35.0f, -12.0f);
        Location anchor = new Location(worldState.world, 12.5, 65.62, -7.25, 35.0f, 0.0f);

        SpatialSession session = new SpatialSession(
            UUID.randomUUID(),
            ScreenType.CHEST,
            profile,
            ViewMode.THIRD_PERSON,
            null,
            null,
            anchor,
            feet,
            eye,
            false
        );

        feet.setY(999.0);
        eye.setY(888.0);
        anchor.setY(777.0);

        assertEquals(64.0, session.getFeetOrigin().getY(), EPS);
        assertEquals(65.62, session.getEyeOrigin().getY(), EPS);
        assertEquals(1.62, session.getEyeOrigin().getY() - session.getFeetOrigin().getY(), EPS);
        assertEquals(0.0f, session.getAnchorOrigin().getPitch(), 1.0e-4f);
        assertEquals(-12.0f, session.getEyeOrigin().getPitch(), 1.0e-4f);

        Location nextEye = new Location(worldState.world, 12.5, 65.80, -7.25, 45.0f, -5.0f);
        session.setEyeOrigin(nextEye);
        assertEquals(64.0, session.getFeetOrigin().getY(), EPS);
        assertEquals(65.80, session.getEyeOrigin().getY(), EPS);

        SpatialSession legacyDelegated = new SpatialSession(
            UUID.randomUUID(),
            ScreenType.CHEST,
            profile,
            ViewMode.FIRST_PERSON,
            null,
            null,
            anchor,
            false
        );
        assertEquals(777.0, legacyDelegated.getFeetOrigin().getY(), EPS);
        assertEquals(777.0, legacyDelegated.getEyeOrigin().getY(), EPS);
    }

    @Test
    void crashRecoveryStoreRoundtripsFeetLocationAndVisibilityFlags(@TempDir Path tempDir) throws IOException {
        Plugin plugin = BukkitTestSupport.createConfigPluginProxy(tempDir, BUNDLED_CONFIG, BUNDLED_PROFILES);
        CrashRecoveryStore store = new CrashRecoveryStore(plugin);

        UUID playerUuid = UUID.randomUUID();
        UUID worldUid = UUID.randomUUID();
        long now = System.currentTimeMillis();
        CameraSnapshot snapshot = new CameraSnapshot(
            playerUuid,
            worldUid,
            104.25,
            64.0,
            -38.75,
            -135.5f,
            18.25f,
            GameMode.SURVIVAL,
            true,
            false,
            true,
            true,
            now
        );

        store.saveSnapshot(snapshot);
        Optional<CameraSnapshot> loadedOpt = store.getSnapshot(playerUuid);
        assertTrue(loadedOpt.isPresent());
        CameraSnapshot loaded = loadedOpt.get();

        assertEquals(playerUuid, loaded.playerUuid());
        assertEquals(worldUid, loaded.worldUid());
        assertEquals(104.25, loaded.x(), EPS);
        assertEquals(64.0, loaded.y(), EPS);
        assertEquals(-38.75, loaded.z(), EPS);
        assertEquals(-135.5f, loaded.yaw(), 1.0e-4f);
        assertEquals(18.25f, loaded.pitch(), 1.0e-4f);
        assertEquals(GameMode.SURVIVAL, loaded.gameMode());
        assertTrue(loaded.allowFlight());
        assertFalse(loaded.flying());
        assertTrue(loaded.invisible());
        assertTrue(loaded.invulnerable());
        assertEquals(now, loaded.capturedAtMillis());

        UUID legacyPlayer = UUID.randomUUID();
        Path legacyFile = tempDir.resolve("recovery").resolve(legacyPlayer + ".yml");
        YamlConfiguration legacyYaml = new YamlConfiguration();
        legacyYaml.set("player-uuid", legacyPlayer.toString());
        legacyYaml.set("world-uid", worldUid.toString());
        legacyYaml.set("x", 0.0);
        legacyYaml.set("y", 64.0);
        legacyYaml.set("z", 0.0);
        legacyYaml.set("yaw", 0.0);
        legacyYaml.set("pitch", 0.0);
        legacyYaml.set("game-mode", "ADVENTURE");
        legacyYaml.save(legacyFile.toFile());

        Optional<CameraSnapshot> legacyOpt = store.getSnapshot(legacyPlayer);
        assertTrue(legacyOpt.isPresent());
        assertFalse(legacyOpt.get().invisible());
        assertFalse(legacyOpt.get().invulnerable());
        assertEquals(GameMode.ADVENTURE, legacyOpt.get().gameMode());

        store.removeSnapshot(playerUuid);
        assertTrue(store.getSnapshot(playerUuid).isEmpty());
    }
}
