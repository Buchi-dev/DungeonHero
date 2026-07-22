package com.dungeonhero.framework;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeatureRegistryTest {

    @Test
    void registersLoadsStartsAndStopsFeatures() {
        GameplayFramework framework = new GameplayFramework(null);
        TestFeature feature = new TestFeature("test-feature");
        framework.features().register(feature);

        YamlConfiguration config = new YamlConfiguration();
        config.set("test-feature.Enabled", true);
        framework.reload(config);

        assertEquals(FeatureLifecycle.STARTED, framework.features().lifecycle("test-feature"));
        assertEquals(1, feature.loads.get());
        assertEquals(1, feature.starts.get());

        framework.reload(config);
        assertEquals(1, feature.stops.get());
        assertEquals(2, feature.loads.get());
        assertEquals(2, feature.starts.get());
    }

    @Test
    void disabledFeatureDoesNotStart() {
        GameplayFramework framework = new GameplayFramework(null);
        TestFeature feature = new TestFeature("disabled");
        framework.features().register(feature);
        YamlConfiguration config = new YamlConfiguration();
        config.set("disabled.Enabled", false);

        framework.reload(config);

        assertEquals(FeatureLifecycle.DISABLED, framework.features().lifecycle("disabled"));
        assertEquals(0, feature.starts.get());
    }

    private static final class TestFeature implements GameplayFeature {
        private final String id;
        private final AtomicInteger loads = new AtomicInteger();
        private final AtomicInteger starts = new AtomicInteger();
        private final AtomicInteger stops = new AtomicInteger();

        private TestFeature(String id) { this.id = id; }
        @Override public String id() { return id; }
        @Override public void load(FeatureContext context, FeatureConfig config) { loads.incrementAndGet(); }
        @Override public void start() { starts.incrementAndGet(); }
        @Override public void stop() { stops.incrementAndGet(); }
    }
}
