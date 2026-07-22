package com.dungeonhero.framework.reward;

import com.dungeonhero.framework.GameplayDefinition;
import com.dungeonhero.framework.context.GameplayContext;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Default Bukkit item reward; custom modules can register their own reward types. */
public final class ItemReward implements GameplayReward {
  @Override
  public String type() {
    return "item";
  }

  @Override
  public RewardResult grant(GameplayContext context, GameplayDefinition definition) {
    if (context.player().isEmpty()) {
      return new RewardResult(false, "Item rewards require a player context.");
    }
    Player player = Bukkit.getPlayer(context.player().get().id());
    if (player == null || !player.isOnline()) {
      return new RewardResult(false, "The reward player is no longer online.");
    }
    String materialName = String.valueOf(definition.parameters().getOrDefault("material", ""));
    Material material = Material.matchMaterial(materialName);
    int amount = number(definition.parameters().get("amount"));
    if (material == null || material.isAir() || amount < 1) {
      return new RewardResult(false, "Item reward requires a valid material and positive amount.");
    }
    ItemStack item = new ItemStack(material, amount);
    player
        .getInventory()
        .addItem(item)
        .values()
        .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    return new RewardResult(true, "Granted " + amount + " " + material.name() + ".");
  }

  private int number(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return value == null ? 0 : Integer.parseInt(value.toString());
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }
}
