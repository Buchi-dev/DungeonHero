package com.dungeonhero.command;

import org.bukkit.command.CommandSender;

/** Focused execution unit for one or more DungeonHero command roots. */
public interface CommandHandler {

  void execute(CommandSender sender, String[] args);
}
