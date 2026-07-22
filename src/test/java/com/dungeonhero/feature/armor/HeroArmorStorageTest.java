package com.dungeonhero.feature.armor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dungeonhero.TestFixtures;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class HeroArmorStorageTest {

  @Test
  void savesAndLoadsCanonicalState() throws Exception {
    var plugin = TestFixtures.plugin(Files.createTempDirectory("dungeonhero-armor-storage"));
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    Player player = mock(Player.class);
    Map<NamespacedKey, Object> values = new HashMap<>();
    when(player.getPersistentDataContainer()).thenReturn(data);
    doAnswer(
            invocation -> {
              values.put(invocation.getArgument(0), invocation.getArgument(2));
              return null;
            })
        .when(data)
        .set(any(), any(), any());
    when(data.get(any(), eq(PersistentDataType.INTEGER)))
        .thenAnswer(invocation -> (Integer) values.get(invocation.getArgument(0)));
    when(data.get(any(), eq(PersistentDataType.DOUBLE)))
        .thenAnswer(invocation -> (Double) values.get(invocation.getArgument(0)));

    HeroArmorStorage storage = new HeroArmorStorage(plugin);
    HeroArmorState expected = new HeroArmorState(12, 4, 17.5, 2);
    storage.save(player, expected);

    assertEquals(expected, storage.load(player));
  }
}
