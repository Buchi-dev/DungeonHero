package com.dungeonhero.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dungeonhero.TestFixtures;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class ArmorConfigurationTest {

  @Test
  void suppliesArmorDefaultsAndFallsBackToSwordCap() throws Exception {
    var config = TestFixtures.defaultConfig();
    config.set("DungeonHero.Ranks.List.1.ArmorLevelCap", null);

    DungeonHeroConfiguration loaded =
        DungeonHeroConfiguration.load(config, Logger.getLogger("DungeonHeroConfigTest"));

    assertTrue(loaded.armor().enabled());
    assertEquals(100, loaded.armor().maxLevel());
    assertEquals(
        10,
        loaded.ranks().ranks().stream()
            .filter(rank -> rank.number() == 1)
            .findFirst()
            .orElseThrow()
            .armorLevelCap());
  }
}
