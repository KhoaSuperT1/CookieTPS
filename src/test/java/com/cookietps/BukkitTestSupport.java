package com.cookietps;

import com.cookietps.model.RenderTransform;
import com.cookietps.model.ScreenProfile;
import com.cookietps.model.ScreenType;
import com.cookietps.model.ThirdPersonSettings;
import com.cookietps.model.ViewMode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

public final class BukkitTestSupport {
    private static boolean serverInitialized = false;

    private BukkitTestSupport() {
    }

    public static synchronized void ensureServer() {
        if (serverInitialized || Bukkit.getServer() != null) {
            serverInitialized = true;
            return;
        }
        Logger logger = Logger.getLogger("CookieTPS-Test");
        Server server = (Server) Proxy.newProxyInstance(
            BukkitTestSupport.class.getClassLoader(),
            new Class<?>[]{Server.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getLogger" -> logger;
                case "getName" -> "CookieTPS-TestServer";
                case "getVersion" -> "1.21.1-Test";
                case "getBukkitVersion" -> "1.21.1-R0.1-SNAPSHOT";
                case "getCraftingRecipe" -> null;
                default -> defaultReturnValue(method.getReturnType());
            }
        );
        Bukkit.setServer(server);
        serverInitialized = true;
    }

    public static ScreenProfile defaultProfile(ScreenType type, boolean includePlayerInv) {
        RenderTransform fp = new RenderTransform(2.1, 1.0f, 0.0, 0.0, 0.0, 0.0f, 0.0f, 0.0f, 0.0f, 4, 3, 2);
        RenderTransform tp = new RenderTransform(2.3, 1.0f, 0.0, 0.0, 0.0, 0.0f, 0.0f, 0.0f, 0.0f, 5, 4, 2);
        ThirdPersonSettings tps = new ThirdPersonSettings(2.8, 0.55, -0.65, 14.0f, 8.0f, 1.25, 4, 0.0f, true, true);
        int cols = switch (type) {
            case CRAFTING -> 5;
            case FURNACE -> 3;
            default -> 9;
        };
        int rows = switch (type) {
            case DOUBLE_CHEST -> 6;
            case PLAYER_INVENTORY -> 4;
            default -> 3;
        };
        return new ScreenProfile(
            type.name().toLowerCase(),
            type,
            ViewMode.AUTO,
            true,
            fp,
            tp,
            tps,
            cols,
            rows,
            0.24f,
            true,
            true,
            includePlayerInv
        );
    }

    public static TestItemStack item(Material material, int amount) {
        ensureServer();
        return new TestItemStack(material, amount);
    }

    public static final class TestItemStack extends ItemStack {
        private Material type;
        private int amount;

        public TestItemStack(Material type, int amount) {
            super();
            this.type = Objects.requireNonNull(type);
            this.amount = amount;
        }

        @Override
        public Material getType() {
            return type;
        }

        @Override
        public void setType(Material type) {
            this.type = Objects.requireNonNull(type);
        }

        @Override
        public int getAmount() {
            return amount;
        }

        @Override
        public void setAmount(int amount) {
            this.amount = amount;
        }

        @Override
        public int getMaxStackSize() {
            return type.getMaxStackSize();
        }

        @Override
        public boolean isSimilar(ItemStack stack) {
            if (stack == null) {
                return false;
            }
            return stack.getType() == this.type;
        }

        @Override
        public ItemStack clone() {
            return new TestItemStack(type, amount);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ItemStack other)) {
                return false;
            }
            return other.getType() == this.type && other.getAmount() == this.amount;
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, amount);
        }

        @Override
        public String toString() {
            return "TestItemStack{" + type.name() + " x" + amount + "}";
        }
    }

    public static final class TestWorldState {
        public final UUID uid = UUID.randomUUID();
        public final NamespacedKey key = NamespacedKey.minecraft("overworld");
        public final List<ItemStack> droppedItems = new ArrayList<>();
        public final World world;

        public TestWorldState() {
            ensureServer();
            this.world = (World) Proxy.newProxyInstance(
                BukkitTestSupport.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUID" -> uid;
                    case "getKey" -> key;
                    case "dropItem", "dropItemNaturally" -> {
                        ItemStack dropped = (ItemStack) args[1];
                        droppedItems.add(dropped != null ? dropped.clone() : null);
                        yield createDroppedItemProxy(dropped);
                    }
                    case "equals" -> args[0] == proxy;
                    case "hashCode" -> uid.hashCode();
                    default -> defaultReturnValue(method.getReturnType());
                }
            );
        }

        private static Item createDroppedItemProxy(ItemStack stack) {
            return (Item) Proxy.newProxyInstance(
                BukkitTestSupport.class.getClassLoader(),
                new Class<?>[]{Item.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getItemStack" -> stack;
                    case "setVelocity", "setPickupDelay" -> null;
                    default -> defaultReturnValue(method.getReturnType());
                }
            );
        }
    }

    public static final class TestInventoryState {
        public final InventoryType type;
        public final ItemStack[] contents;
        public final InventoryHolder holder;
        public final Inventory inventory;

        public TestInventoryState(InventoryType type, int size, InventoryHolder holder) {
            ensureServer();
            this.type = type;
            this.contents = new ItemStack[size];
            this.holder = holder;
            this.inventory = (Inventory) Proxy.newProxyInstance(
                BukkitTestSupport.class.getClassLoader(),
                new Class<?>[]{Inventory.class},
                (proxy, method, args) -> handleInventoryCall(method.getName(), args, proxy)
            );
        }

        Object handleInventoryCall(String name, Object[] args, Object proxy) {
            return switch (name) {
                case "getSize" -> contents.length;
                case "getType" -> type;
                case "getHolder" -> holder;
                case "getItem" -> {
                    int idx = (int) args[0];
                    yield idx >= 0 && idx < contents.length ? contents[idx] : null;
                }
                case "setItem" -> {
                    int idx = (int) args[0];
                    ItemStack item = (ItemStack) args[1];
                    if (idx >= 0 && idx < contents.length) {
                        contents[idx] = item;
                    }
                    yield null;
                }
                case "getContents" -> contents.clone();
                case "setContents" -> {
                    ItemStack[] next = (ItemStack[]) args[0];
                    for (int i = 0; i < contents.length; i++) {
                        contents[i] = (next != null && i < next.length) ? next[i] : null;
                    }
                    yield null;
                }
                case "addItem" -> addItems((ItemStack[]) args[0]);
                case "equals" -> args[0] == proxy;
                case "hashCode" -> System.identityHashCode(this);
                default -> defaultReturnValue(returnTypeFor(name));
            };
        }

        HashMap<Integer, ItemStack> addItems(ItemStack[] items) {
            HashMap<Integer, ItemStack> overflow = new HashMap<>();
            if (items == null) {
                return overflow;
            }
            for (int idx = 0; idx < items.length; idx++) {
                ItemStack input = items[idx];
                if (input == null || input.getType().isAir() || input.getAmount() <= 0) {
                    continue;
                }
                ItemStack remaining = input.clone();
                int maxStack = remaining.getMaxStackSize();
                for (int i = 0; i < Math.min(36, contents.length) && remaining.getAmount() > 0; i++) {
                    ItemStack existing = contents[i];
                    if (existing != null && !existing.getType().isAir() && existing.isSimilar(remaining) && existing.getAmount() < maxStack) {
                        int move = Math.min(maxStack - existing.getAmount(), remaining.getAmount());
                        existing.setAmount(existing.getAmount() + move);
                        remaining.setAmount(remaining.getAmount() - move);
                    }
                }
                for (int i = 0; i < Math.min(36, contents.length) && remaining.getAmount() > 0; i++) {
                    ItemStack existing = contents[i];
                    if (existing == null || existing.getType().isAir()) {
                        int move = Math.min(maxStack, remaining.getAmount());
                        ItemStack placed = remaining.clone();
                        placed.setAmount(move);
                        contents[i] = placed;
                        remaining.setAmount(remaining.getAmount() - move);
                    }
                }
                if (remaining.getAmount() > 0) {
                    overflow.put(idx, remaining);
                }
            }
            return overflow;
        }

        private static Class<?> returnTypeFor(String name) {
            return Void.TYPE;
        }

        public int totalAmount() {
            int sum = 0;
            for (ItemStack item : contents) {
                if (item != null && !item.getType().isAir()) {
                    sum += item.getAmount();
                }
            }
            return sum;
        }
    }

    public static final class TestPlayerInventoryState {
        public final TestInventoryState base = new TestInventoryState(InventoryType.PLAYER, 41, null);
        public final PlayerInventory inventory;

        public TestPlayerInventoryState() {
            this.inventory = (PlayerInventory) Proxy.newProxyInstance(
                BukkitTestSupport.class.getClassLoader(),
                new Class<?>[]{PlayerInventory.class},
                (proxy, method, args) -> base.handleInventoryCall(method.getName(), args, proxy)
            );
        }

        public int totalAmount() {
            return base.totalAmount();
        }
    }

    public static Player createPlayerProxy(UUID uuid, TestWorldState worldState, TestPlayerInventoryState playerInv) {
        ensureServer();
        Location eyeLoc = new Location(worldState.world, 0.0, 65.62, 0.0, 0.0f, 0.0f);
        Location loc = new Location(worldState.world, 0.0, 64.0, 0.0, 0.0f, 0.0f);
        return (Player) Proxy.newProxyInstance(
            BukkitTestSupport.class.getClassLoader(),
            new Class<?>[]{Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> uuid;
                case "getWorld" -> worldState.world;
                case "getLocation" -> loc.clone();
                case "getEyeLocation" -> eyeLoc.clone();
                case "getInventory" -> playerInv.inventory;
                case "equals" -> args[0] == proxy;
                case "hashCode" -> uuid.hashCode();
                default -> defaultReturnValue(method.getReturnType());
            }
        );
    }

    public static Plugin createConfigPluginProxy(Path dataFolder, Path bundledConfigSource, Path bundledProfilesSource) {
        ensureServer();
        Logger logger = Logger.getLogger("CookieTPS-PluginTest");
        File dataDir = dataFolder.toFile();
        final YamlConfiguration[] liveConfig = {new YamlConfiguration()};
        return (Plugin) Proxy.newProxyInstance(
            BukkitTestSupport.class.getClassLoader(),
            new Class<?>[]{Plugin.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getDataFolder" -> dataDir;
                case "getLogger" -> logger;
                case "saveDefaultConfig" -> {
                    Files.createDirectories(dataFolder);
                    Path target = dataFolder.resolve("config.yml");
                    if (!Files.exists(target) && bundledConfigSource != null && Files.exists(bundledConfigSource)) {
                        Files.copy(bundledConfigSource, target);
                    }
                    yield null;
                }
                case "reloadConfig" -> {
                    Path target = dataFolder.resolve("config.yml");
                    liveConfig[0] = Files.exists(target)
                        ? YamlConfiguration.loadConfiguration(target.toFile())
                        : new YamlConfiguration();
                    yield null;
                }
                case "getConfig" -> liveConfig[0];
                case "saveConfig" -> {
                    Files.createDirectories(dataFolder);
                    liveConfig[0].save(dataFolder.resolve("config.yml").toFile());
                    yield null;
                }
                case "getResource" -> {
                    String resourceName = (String) args[0];
                    Path src = "profiles.yml".equals(resourceName) ? bundledProfilesSource : bundledConfigSource;
                    if (src != null && Files.exists(src)) {
                        yield new ByteArrayInputStream(Files.readAllBytes(src));
                    }
                    yield null;
                }
                case "saveResource" -> {
                    String resourceName = (String) args[0];
                    boolean replace = (boolean) args[1];
                    Path src = "profiles.yml".equals(resourceName) ? bundledProfilesSource : bundledConfigSource;
                    Path dest = dataFolder.resolve(resourceName);
                    if (src != null && Files.exists(src) && (replace || !Files.exists(dest))) {
                        Files.createDirectories(dest.getParent());
                        Files.copy(src, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    yield null;
                }
                case "equals" -> args[0] == proxy;
                case "hashCode" -> System.identityHashCode(proxy);
                default -> defaultReturnValue(method.getReturnType());
            }
        );
    }

    public static InputStream utf8Stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    public static void assertClose(double expected, double actual, double eps) {
        if (Math.abs(expected - actual) > eps) {
            throw new AssertionError("Expected " + expected + " +/- " + eps + " but got " + actual);
        }
    }

    private static Object defaultReturnValue(Class<?> returnType) {
        if (returnType == null || returnType == Void.TYPE) {
            return null;
        }
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Double.TYPE) {
            return 0.0;
        }
        if (returnType == Float.TYPE) {
            return 0.0f;
        }
        if (returnType == Short.TYPE) {
            return (short) 0;
        }
        if (returnType == Byte.TYPE) {
            return (byte) 0;
        }
        if (returnType == Character.TYPE) {
            return '\0';
        }
        return null;
    }
}
