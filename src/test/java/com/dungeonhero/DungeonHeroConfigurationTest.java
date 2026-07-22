package com.dungeonhero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dungeonhero.config.DungeonHeroConfiguration;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DungeonHeroConfigurationTest {

  @Test
  void canonicalKeysWinAndLegacyAliasesEmitMigrationWarnings() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("DungeonHero.MobScaling.MaximumMobLevel", 90);
    config.set("DungeonHero.MobScaling.MaxLevel", 80);
    config.set("DungeonHero.MobScaling.PartyMode", "HIGHEST");
    config.set("DungeonHero.Progression.Prestige.MaxPrestige", 7);
    config.set("DungeonHero.Fragments.Caps.1", 42);
    Logger logger = mock(Logger.class);

    DungeonHeroConfiguration loaded = DungeonHeroConfiguration.load(config, logger);

    assertEquals(90, loaded.mobScaling().maxMobLevel());
    assertEquals("HIGHEST", loaded.mobScaling().partyScalingMode());
    assertEquals(7, loaded.ascension().maxPrestige());
    assertEquals(42D, loaded.fragmentCaps().rankCaps().get(1));
    verify(logger, org.mockito.Mockito.atLeast(3))
        .warning(org.mockito.ArgumentMatchers.contains("deprecated"));
  }
}
