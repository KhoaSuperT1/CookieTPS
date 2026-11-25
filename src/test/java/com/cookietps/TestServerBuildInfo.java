package com.cookietps;

import io.papermc.paper.ServerBuildInfo;
import net.kyori.adventure.key.Key;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

public final class TestServerBuildInfo implements ServerBuildInfo {
    @Override
    public Key brandId() {
        return BRAND_PAPER_ID;
    }

    @Override
    public boolean isBrandCompatible(Key brandId) {
        return BRAND_PAPER_ID.equals(brandId);
    }

    @Override
    public String brandName() {
        return "Paper";
    }

    @Override
    public String minecraftVersionId() {
        return "1.21.1";
    }

    @Override
    public String minecraftVersionName() {
        return "1.21.1";
    }

    @Override
    public OptionalInt buildNumber() {
        return OptionalInt.of(1);
    }

    @Override
    public Instant buildTime() {
        return Instant.EPOCH;
    }

    @Override
    public Optional<String> gitBranch() {
        return Optional.of("main");
    }

    @Override
    public Optional<String> gitCommit() {
        return Optional.of("test");
    }

    @Override
    public String asString(StringRepresentation representation) {
        return "Paper 1.21.1-Test";
    }
}
