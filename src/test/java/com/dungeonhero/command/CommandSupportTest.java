package com.dungeonhero.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommandSupportTest {

  private final CommandSupport support = new CommandSupport(null);

  @Test
  void completionIsCaseInsensitiveAndSorted() {
    assertEquals(
        List.of("alpha", "alpine"), support.complete("AL", List.of("alpine", "Beta", "alpha")));
  }

  @Test
  void amountParsingAcceptsZeroOnlyWhenRequested() {
    assertEquals(0L, support.parseAmount(null, "0", true));
    assertEquals(25L, support.parseAmount(null, "25", false));
  }
}
