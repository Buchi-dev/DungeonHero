package com.dungeonhero.feature.armor;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Stores one canonical shared Hero Armor progression on the player. */
public final class HeroArmorStorage {

  private final NamespacedKey levelKey;
  private final NamespacedKey xpKey;
  private final NamespacedKey bonusKey;
  private final NamespacedKey rankKey;

  public HeroArmorStorage(JavaPlugin plugin) {
    levelKey = new NamespacedKey(plugin, "saved_armor_level");
    xpKey = new NamespacedKey(plugin, "saved_armor_xp");
    bonusKey = new NamespacedKey(plugin, "saved_armor_bonus");
    rankKey = new NamespacedKey(plugin, "saved_armor_fragment_rank");
  }

  public HeroArmorState load(Player player) {
    if (player == null) return null;
    PersistentDataContainer data = player.getPersistentDataContainer();
    Integer level = data.get(levelKey, PersistentDataType.INTEGER);
    if (level == null) return null;
    Integer xp = data.get(xpKey, PersistentDataType.INTEGER);
    Double bonus = data.get(bonusKey, PersistentDataType.DOUBLE);
    Integer rank = data.get(rankKey, PersistentDataType.INTEGER);
    return new HeroArmorState(
        level, xp == null ? 0 : xp, bonus == null ? 0 : bonus, rank == null ? 1 : rank);
  }

  public HeroArmorState loadOrDefault(Player player) {
    HeroArmorState state = load(player);
    return state == null ? HeroArmorState.defaults() : state;
  }

  public void save(Player player, HeroArmorState state) {
    if (player == null) return;
    HeroArmorState safe = state == null ? HeroArmorState.defaults() : state;
    PersistentDataContainer data = player.getPersistentDataContainer();
    data.set(levelKey, PersistentDataType.INTEGER, safe.level());
    data.set(xpKey, PersistentDataType.INTEGER, safe.xp());
    data.set(bonusKey, PersistentDataType.DOUBLE, safe.armorBonus());
    data.set(rankKey, PersistentDataType.INTEGER, safe.fragmentRank());
  }
}
