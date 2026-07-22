package com.dungeonhero.feature.sword;

import com.dungeonhero.config.DungeonHeroConfiguration;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Creates and identifies native DungeonHero Sword XP items. */
public final class SwordXpItemService {

  private final JavaPlugin plugin;
  private final NamespacedKey xpKey;
  private Material material;
  private String displayName;
  private List<String> lore;
  private int configuredXp;

  public SwordXpItemService(JavaPlugin plugin) {
    this(plugin, DungeonHeroConfiguration.load(plugin).swordXpItem());
  }

  public SwordXpItemService(JavaPlugin plugin, DungeonHeroConfiguration.SwordXpItem configuration) {
    this.plugin = plugin;
    this.xpKey = new NamespacedKey(plugin, "sword_xp_item");
    reload(configuration);
  }

  public void reload() {
    reload(DungeonHeroConfiguration.load(plugin).swordXpItem());
  }

  public void reload(DungeonHeroConfiguration.SwordXpItem configuration) {
    String materialName = configuration.material();
    material = Material.matchMaterial(materialName == null ? "EXPERIENCE_BOTTLE" : materialName);
    if (material == null || !material.isItem()) {
      plugin.getLogger().warning("Invalid SwordXPItem material; using EXPERIENCE_BOTTLE.");
      material = Material.EXPERIENCE_BOTTLE;
    }
    displayName = configuration.name();
    lore = configuration.lore();
    configuredXp = configuration.xp();
  }

  public ItemStack createItem() {
    return createItem(configuredXp);
  }

  public ItemStack createItem(int xpAmount) {
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(deserialize(displayName));
    if (!lore.isEmpty()) {
      meta.lore(lore.stream().map(this::deserialize).toList());
    }
    meta.getPersistentDataContainer().set(xpKey, PersistentDataType.INTEGER, Math.max(1, xpAmount));
    item.setItemMeta(meta);
    return item;
  }

  public boolean isSwordXpItem(ItemStack item) {
    if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
      return false;
    }
    return item.getItemMeta().getPersistentDataContainer().has(xpKey, PersistentDataType.INTEGER);
  }

  public int getXpAmount(ItemStack item) {
    if (!isSwordXpItem(item)) {
      return 0;
    }
    Integer amount =
        item.getItemMeta().getPersistentDataContainer().get(xpKey, PersistentDataType.INTEGER);
    return Math.max(0, amount == null ? configuredXp : amount);
  }

  public int getConfiguredXp() {
    return configuredXp;
  }

  private Component deserialize(String value) {
    return LegacyComponentSerializer.legacyAmpersand().deserialize(value == null ? "" : value);
  }
}
