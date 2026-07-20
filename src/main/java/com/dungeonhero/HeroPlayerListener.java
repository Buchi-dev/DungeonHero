package com.dungeonhero;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HeroPlayerListener implements Listener {

    private final JavaPlugin plugin;
    private final HeroItemService heroItemService;
    private final Map<UUID, ItemStack> swordsToRestore = new HashMap<>();

    public HeroPlayerListener(JavaPlugin plugin, HeroItemService heroItemService) {
        this.plugin = plugin;
        this.heroItemService = heroItemService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ensureHeroSword(event.getPlayer(), null);
    }

    @EventHandler
    public void onDrop(org.bukkit.event.player.PlayerDropItemEvent event) {
        if (heroItemService.isHeroSword(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("The Hero Sword cannot be dropped.");
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        ItemStack bestSword = null;
        var drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            ItemStack drop = drops.next();
            if (!heroItemService.isHeroSword(drop)) {
                continue;
            }

            if (bestSword == null || isStronger(drop, bestSword)) {
                bestSword = drop.clone();
            }
            drops.remove();
        }

        if (bestSword != null) {
            swordsToRestore.put(event.getEntity().getUniqueId(), bestSword);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack sword = swordsToRestore.remove(event.getPlayer().getUniqueId());
            ensureHeroSword(event.getPlayer(), sword);
        });
    }

    private void ensureHeroSword(Player player, ItemStack preferredSword) {
        PlayerInventory inventory = player.getInventory();
        ItemStack bestSword = preferredSword == null ? null : preferredSword.clone();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!heroItemService.isHeroSword(item)) {
                continue;
            }

            if (bestSword == null || isStronger(item, bestSword)) {
                bestSword = item.clone();
            }
            inventory.setItem(slot, null);
        }

        if (bestSword == null) {
            bestSword = heroItemService.createHeroSword();
        } else {
            bestSword = heroItemService.normalizeSword(bestSword);
        }

        Map<Integer, ItemStack> leftovers = inventory.addItem(bestSword);
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    private boolean isStronger(ItemStack first, ItemStack second) {
        int firstLevel = heroItemService.getSwordLevel(first);
        int secondLevel = heroItemService.getSwordLevel(second);
        if (firstLevel != secondLevel) {
            return firstLevel > secondLevel;
        }
        int firstPrestige = heroItemService.getSwordPrestige(first);
        int secondPrestige = heroItemService.getSwordPrestige(second);
        if (firstPrestige != secondPrestige) {
            return firstPrestige > secondPrestige;
        }
        return heroItemService.getDamageBonus(first) > heroItemService.getDamageBonus(second);
    }
}
