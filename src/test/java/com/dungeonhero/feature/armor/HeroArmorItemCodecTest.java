package com.dungeonhero.feature.armor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dungeonhero.TestFixtures;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

class HeroArmorItemCodecTest {

  @Test
  void roundTripsSharedStateAndSlotMetadata() throws Exception {
    var plugin = TestFixtures.plugin(Files.createTempDirectory("dungeonhero-armor-item"));
    HeroArmorItemCodec codec = new HeroArmorItemCodec(plugin, ArmorCapPolicy.defaults());
    HeroArmorState expected = new HeroArmorState(72, 18, 125.5, 2);
    ItemStack item = mock(ItemStack.class);
    ItemMeta meta = mock(ItemMeta.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    Map<NamespacedKey, Object> values = new HashMap<>();
    values.put(new NamespacedKey(plugin, "hero_armor"), (byte) 1);
    values.put(new NamespacedKey(plugin, "armor_slot"), "CHESTPLATE");
    when(item.hasItemMeta()).thenReturn(true);
    when(item.getItemMeta()).thenReturn(meta);
    when(item.clone()).thenReturn(item);
    when(meta.getPersistentDataContainer()).thenReturn(data);
    doAnswer(
            invocation -> {
              values.put(invocation.getArgument(0), invocation.getArgument(2));
              return null;
            })
        .when(data)
        .set(any(), any(), any());
    when(data.get(any(), any())).thenAnswer(invocation -> values.get(invocation.getArgument(0)));

    item = codec.write(item, ArmorTier.ArmorSlot.CHESTPLATE, expected);

    assertTrue(codec.isHeroArmor(item));
    assertEquals(ArmorTier.ArmorSlot.CHESTPLATE, codec.slot(item));
    assertEquals(expected, codec.read(item));
  }
}
