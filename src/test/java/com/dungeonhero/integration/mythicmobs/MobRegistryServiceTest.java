package com.dungeonhero.integration.mythicmobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dungeonhero.TestFixtures;
import com.dungeonhero.feature.mobregistry.MobRegistryService;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class MobRegistryServiceTest {

  @Test
  void reloadLoadsConfiguredMythicMobProfilesAndPreservesTheirXpValues() throws Exception {
    var dataFolder = Files.createTempDirectory("dungeonhero-mobs");
    Files.writeString(
        dataFolder.resolve("mob-registry.yml"),
        """
                Profiles:
                  elite:
                    LevelOffset: 7
                    SwordXP: 175
                    MobType: elite
                Mobs:
                  TEST_CRYPT_ELITE:
                    Profile: elite
                """);

    MobRegistryService registry = new MobRegistryService(TestFixtures.plugin(dataFolder));

    assertTrue(registry.find("test_crypt_elite").isPresent());
    MobRegistryService.MobProfile profile = registry.profileOrDefault("TEST_CRYPT_ELITE");
    assertEquals(7, profile.levelOffset());
    assertEquals(175, profile.swordXp());
    assertEquals(MobCombatBalance.MobKind.ELITE, profile.kind());
  }

  @Test
  void unknownMythicMobIdsUseTheSafeNormalProfile() throws Exception {
    var dataFolder = Files.createTempDirectory("dungeonhero-mobs-default");
    MobRegistryService registry = new MobRegistryService(TestFixtures.plugin(dataFolder));

    MobRegistryService.MobProfile profile = registry.profileOrDefault("UNKNOWN_MOB");

    assertEquals("normal", profile.name());
    assertEquals(25, profile.swordXp());
  }
}
