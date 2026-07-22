package com.dungeonhero;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ConfigurationContractTest {

  @Test
  void baselineConfigurationContainsAllFeatureSectionsAndCompatibilityKeys() throws Exception {
    FileConfiguration config = loadConfig();
    List<String> requiredPaths =
        List.of(
            "DungeonHero.Locale",
            "DungeonHero.Gameplay.ConfigVersion",
            "DungeonHero.Gameplay.Features.open-world-dungeon",
            "DungeonHero.Gameplay.Features.blood-arena",
            "DungeonHero.Fragments.mm:HeroDamageFragment",
            "DungeonHero.Progression.SwordXPItem.Material",
            "DungeonHero.Progression.MaxSwordLevel",
            "DungeonHero.HeroAscension.Enabled",
            "DungeonHero.FragmentCaps.MaximumStoredDamage",
            "DungeonHero.FragmentCaps.RankCaps.1",
            "DungeonHero.Ranks.List.1.SwordLevelCap",
            "DungeonHero.Hud.UpdateTicks",
            "DungeonHero.MobScaling.PartyScalingMode",
            "DungeonHero.MobScaling.MaximumMobLevel",
            "DungeonHero.MobHp.ProfileMultipliers.RareBoss",
            "DungeonHero.DamageProtection.CriticalDamageMultiplier",
            "DungeonHero.DamageAmplifiers.ApprovedPotionEffects",
            "DungeonHero.Admin.ResetSwordPermission",
            "DungeonHero.Party.MaxSize",
            "DungeonHero.DungeonRush.QuestTypes",
            "DungeonHero.DungeonRush.Rewards.First",
            "DungeonHero.TargetDummy.Hologram.Height");

    for (String path : requiredPaths) {
      assertTrue(config.contains(path), "Missing baseline configuration path: " + path);
    }
    assertFalse(config.contains("DungeonHero.Progression.Prestige"));
    assertFalse(config.contains("DungeonHero.Fragments.Caps"));
    assertFalse(config.contains("DungeonHero.MobScaling.PartyMode"));
    assertFalse(config.contains("DungeonHero.MobScaling.MaxLevel"));
  }

  private static FileConfiguration loadConfig() throws IOException {
    try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources/config.yml"))) {
      return YamlConfiguration.loadConfiguration(reader);
    }
  }
}
