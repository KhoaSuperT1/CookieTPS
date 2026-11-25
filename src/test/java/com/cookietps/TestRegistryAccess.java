package com.cookietps;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.BlockType;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class TestRegistryAccess implements RegistryAccess {
    private static final Map<Object, Registry<?>> REGISTRIES = new HashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Keyed> Registry<T> getRegistry(Class<T> type) {
        return (Registry<T>) REGISTRIES.computeIfAbsent(type, k -> createRegistryProxy(type == BlockType.class));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> registryKey) {
        boolean isBlock = registryKey == RegistryKey.BLOCK;
        return (Registry<T>) REGISTRIES.computeIfAbsent(registryKey, k -> createRegistryProxy(isBlock));
    }

    private static Registry<?> createRegistryProxy(boolean blockRegistry) {
        return (Registry<?>) Proxy.newProxyInstance(
            TestRegistryAccess.class.getClassLoader(),
            new Class<?>[]{Registry.class},
            (proxy, method, args) -> {
                String name = method.getName();
                if ("get".equals(name) && args != null && args.length == 1 && args[0] instanceof NamespacedKey key) {
                    if (blockRegistry) {
                        return createBlockTypeProxy(key);
                    }
                    return null;
                }
                if ("iterator".equals(name)) {
                    return Collections.emptyIterator();
                }
                if ("stream".equals(name)) {
                    return Collections.emptyList().stream();
                }
                return null;
            }
        );
    }

    private static BlockType createBlockTypeProxy(NamespacedKey key) {
        String keyName = key.getKey();
        boolean air = "air".equals(keyName) || "cave_air".equals(keyName) || "void_air".equals(keyName);
        return (BlockType) Proxy.newProxyInstance(
            TestRegistryAccess.class.getClassLoader(),
            new Class<?>[]{BlockType.class},
            (proxy, method, args) -> {
                String name = method.getName();
                if ("isAir".equals(name)) {
                    return air;
                }
                if ("getKey".equals(name) || "key".equals(name)) {
                    return key;
                }
                if ("toString".equals(name)) {
                    return key.toString();
                }
                return false;
            }
        );
    }
}
