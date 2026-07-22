package com.dungeonhero.framework;

import com.dungeonhero.framework.config.ConfigurationValidator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationValidationTest {

    @Test
    void acceptsValidDefinitions() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("Enabled", true);
        config.set("ConfigVersion", 1);
        config.set("Objectives", java.util.List.of(java.util.Map.of("Type", "defeat_mobs")));

        assertTrue(ConfigurationValidator.validate(new FeatureConfig("demo", config)).valid());
    }

    @Test
    void rejectsInvalidVersionAndDefinitionShape() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("Enabled", "yes");
        config.set("ConfigVersion", 99);
        config.set("Rewards", "not-a-list");

        var result = ConfigurationValidator.validate(new FeatureConfig("demo", config));
        assertFalse(result.valid());
        assertTrue(result.messages().stream().anyMatch(message -> message.contains("Enabled")));
        assertTrue(result.messages().stream().anyMatch(message -> message.contains("ConfigVersion")));
        assertTrue(result.messages().stream().anyMatch(message -> message.contains("Rewards")));
    }
}
