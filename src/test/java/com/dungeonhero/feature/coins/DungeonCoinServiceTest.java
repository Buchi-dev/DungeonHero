package com.dungeonhero.feature.coins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DungeonCoinServiceTest {

  @Test
  void transfersPersistAcrossServiceReloads() throws Exception {
    var directory = Files.createTempDirectory("dungeonhero-coins");
    UUID source = UUID.randomUUID();
    UUID target = UUID.randomUUID();
    DungeonCoinService service = new DungeonCoinService(directory.toFile(), ignored -> {});

    assertTrue(service.setBalance(source, 500));
    DungeonCoinService.TransferResult result = service.transfer(source, target, 125);

    assertEquals(DungeonCoinService.TransferStatus.SUCCESS, result.status());
    assertEquals(375, service.getBalance(source));
    assertEquals(125, service.getBalance(target));

    DungeonCoinService reloaded = new DungeonCoinService(directory.toFile(), ignored -> {});
    assertEquals(375, reloaded.getBalance(source));
    assertEquals(125, reloaded.getBalance(target));
  }

  @Test
  void invalidTransfersDoNotChangeBalances() throws Exception {
    var directory = Files.createTempDirectory("dungeonhero-coins");
    UUID source = UUID.randomUUID();
    UUID target = UUID.randomUUID();
    DungeonCoinService service = new DungeonCoinService(directory.toFile(), ignored -> {});
    service.setBalance(source, 100);
    service.setBalance(target, 50);

    assertEquals(
        DungeonCoinService.TransferStatus.INSUFFICIENT_FUNDS,
        service.transfer(source, target, 101).status());
    assertEquals(
        DungeonCoinService.TransferStatus.SELF_TRANSFER,
        service.transfer(source, source, 1).status());
    assertEquals(100, service.getBalance(source));
    assertEquals(50, service.getBalance(target));
  }
}
