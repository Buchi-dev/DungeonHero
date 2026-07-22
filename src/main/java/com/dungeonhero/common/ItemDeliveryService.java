package com.dungeonhero.common;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Shared Bukkit-edge item delivery, including inventory overflow drops. */
public final class ItemDeliveryService {

  public void giveOrDrop(Player player, ItemStack item) {
    if (player == null || item == null || item.getType().isAir() || item.getAmount() <= 0) {
      return;
    }
    Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
    leftovers
        .values()
        .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
  }

  public void giveStacked(Player player, Material material, long amount) {
    if (material == null || material.isAir() || amount <= 0) {
      return;
    }
    long remaining = amount;
    while (remaining > 0) {
      int stackAmount = (int) Math.min(material.getMaxStackSize(), remaining);
      giveOrDrop(player, new ItemStack(material, stackAmount));
      remaining -= stackAmount;
    }
  }
}
