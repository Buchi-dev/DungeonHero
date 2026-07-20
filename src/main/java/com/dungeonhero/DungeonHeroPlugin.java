package com.dungeonhero;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class DungeonHeroPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("DungeonHero enabled. Dungeon systems are ready to be built.");
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!command.getName().equalsIgnoreCase("dungeonhero")) {
            return false;
        }

        sender.sendMessage(Component.text("DungeonHero is online.", NamedTextColor.GOLD));
        return true;
    }
}

