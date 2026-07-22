package com.dungeonhero.command;

/** Central command usage text so handlers do not duplicate syntax strings. */
public final class CommandUsages {

  public static final String GIVE = "Usage: /dh give <player> mm:<item-id>";
  public static final String GIVE_XP = "Usage: /dh give-xp <player> [xp]";
  public static final String BALANCE_PLAYER = "Usage: /dh balance <player>";
  public static final String TRANSFER = "Usage: /dh transfer <player> <amount>";
  public static final String ADMIN_COINS =
      "Usage: /dh admin coins [set|add|take] <player> <amount>";
  public static final String RESET_SWORD = "Usage: /dh admin resetsword <player> [preview|confirm]";
  public static final String DUMMY = "Usage: /dh dummy [spawn|stats|remove]";

  private CommandUsages() {}
}
