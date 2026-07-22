package com.dungeonhero.feature.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class ArenaSessionTest {

  @Test
  void tracksParticipantsExpiryAndCompletionWithoutExposingMutableLocations() {
    UUID playerId = UUID.randomUUID();
    Location original = new Location(null, 10, 64, 10);
    ArenaSession session =
        new ArenaSession(
            "crypt_colosseum",
            UUID.randomUUID(),
            new Location(null, 0, 64, 0),
            Map.of(playerId, original),
            1_000,
            2_000);

    assertTrue(session.contains(playerId));
    assertTrue(session.isExpired(2_000));
    assertFalse(session.isExpired(1_999));
    assertEquals(10, session.originalPlayerLocations().get(playerId).getBlockX());

    original.setX(99);
    assertEquals(10, session.originalPlayerLocations().get(playerId).getBlockX());
    assertTrue(session.markComplete());
    assertFalse(session.markComplete());
  }
}
