package com.dungeonhero.feature.armor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ArmorTierTest {

  @Test
  void usesSwordTierBoundariesAndArmorMaterials() {
    assertEquals(ArmorTier.WOOD, ArmorTier.fromLevel(1));
    assertEquals(ArmorTier.STONE, ArmorTier.fromLevel(11));
    assertEquals(ArmorTier.HERO, ArmorTier.fromLevel(100));
    assertEquals(
        "NETHERITE_CHESTPLATE", ArmorTier.MYTHIC.material(ArmorTier.ArmorSlot.CHESTPLATE).name());
  }
}
