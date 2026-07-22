package com.dungeonhero.feature.armor;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Bukkit persistence and presentation adapter for one shared Hero Armor state. */
public final class HeroArmorItemCodec {

  private final NamespacedKey markerKey;
  private final NamespacedKey slotKey;
  private final NamespacedKey levelKey;
  private final NamespacedKey xpKey;
  private final NamespacedKey bonusKey;
  private final NamespacedKey tierKey;
  private final NamespacedKey rankKey;
  private final ArmorCapPolicy capPolicy;

  public HeroArmorItemCodec(JavaPlugin plugin, ArmorCapPolicy capPolicy) {
    markerKey = new NamespacedKey(plugin, "hero_armor");
    slotKey = new NamespacedKey(plugin, "armor_slot");
    levelKey = new NamespacedKey(plugin, "armor_level");
    xpKey = new NamespacedKey(plugin, "armor_xp");
    bonusKey = new NamespacedKey(plugin, "armor_bonus");
    tierKey = new NamespacedKey(plugin, "armor_tier");
    rankKey = new NamespacedKey(plugin, "armor_fragment_rank");
    this.capPolicy = capPolicy == null ? ArmorCapPolicy.defaults() : capPolicy;
  }

  public ItemStack create(ArmorTier.ArmorSlot slot, HeroArmorState state) {
    ItemStack item =
        new ItemStack(ArmorTier.fromLevel(state == null ? 1 : state.level()).material(slot));
    ItemMeta meta = item.getItemMeta();
    meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
    meta.getPersistentDataContainer().set(slotKey, PersistentDataType.STRING, slot.name());
    item.setItemMeta(meta);
    return write(item, slot, state);
  }

  public boolean isHeroArmor(ItemStack item) {
    if (item == null || !item.hasItemMeta()) return false;
    Byte value =
        item.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BYTE);
    return value != null && value == 1 && slot(item) != null;
  }

  public ArmorTier.ArmorSlot slot(ItemStack item) {
    if (item == null || !item.hasItemMeta()) return null;
    String value =
        item.getItemMeta().getPersistentDataContainer().get(slotKey, PersistentDataType.STRING);
    if (value == null) return null;
    try {
      return ArmorTier.ArmorSlot.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  public HeroArmorState read(ItemStack item) {
    if (!isHeroArmor(item)) return HeroArmorState.defaults();
    PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
    return new HeroArmorState(
        integer(data, levelKey, 1),
        integer(data, xpKey, 0),
        number(data, bonusKey),
        integer(data, rankKey, 1));
  }

  public ItemStack write(ItemStack item, ArmorTier.ArmorSlot slot, HeroArmorState state) {
    if (!isHeroArmor(item) || slot == null) return item;
    HeroArmorState safe = state == null ? HeroArmorState.defaults() : state;
    ItemStack updated = item.clone();
    ItemMeta meta = updated.getItemMeta();
    PersistentDataContainer data = meta.getPersistentDataContainer();
    ArmorTier tier = ArmorTier.fromLevel(safe.level());
    double bonus = capPolicy.sanitizeTotal(safe.armorBonus());
    data.set(markerKey, PersistentDataType.BYTE, (byte) 1);
    data.set(slotKey, PersistentDataType.STRING, slot.name());
    data.set(levelKey, PersistentDataType.INTEGER, safe.level());
    data.set(xpKey, PersistentDataType.INTEGER, safe.xp());
    data.set(bonusKey, PersistentDataType.DOUBLE, bonus);
    data.set(rankKey, PersistentDataType.INTEGER, safe.fragmentRank());
    data.set(tierKey, PersistentDataType.STRING, tier.name());
    meta.displayName(
        Component.text(tier.displayName() + " Hero " + title(slot), tier.color())
            .decoration(TextDecoration.ITALIC, false));
    meta.lore(
        List.of(
            Component.text("Aegis of the Fallen", NamedTextColor.GRAY),
            Component.text("Bound to its rightful hero.", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, true),
            Component.text("Unbreakable", NamedTextColor.BLUE),
            Component.empty(),
            section("PROGRESSION"),
            line("Tier", tier.displayName(), tier.color()),
            line("Armor Level", String.valueOf(safe.level()), NamedTextColor.AQUA),
            line("Armor XP", String.valueOf(safe.xp()), NamedTextColor.GREEN),
            Component.empty(),
            section("DEFENSE"),
            line("Armor Bonus", "+" + format(bonus), NamedTextColor.BLUE),
            line(
                "Effective Bonus",
                "+" + format(capPolicy.effective(bonus, safe.fragmentRank())),
                NamedTextColor.GREEN),
            line(
                "Inactive Overflow",
                "+" + format(capPolicy.overflow(bonus, safe.fragmentRank())),
                NamedTextColor.GRAY),
            Component.empty(),
            section("SET BONUSES"),
            Component.text("2 pieces: 2% damage reduction", NamedTextColor.GRAY),
            Component.text("3 pieces: 5% damage reduction", NamedTextColor.GRAY),
            Component.text("4 pieces: Last Stand", NamedTextColor.GRAY),
            Component.empty(),
            section("FORGE"),
            Component.text("Insert an ARMOR fragment in the Hero Forge.", NamedTextColor.GRAY)));
    meta.setUnbreakable(true);
    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    updated.setType(tier.material(slot));
    updated.setItemMeta(meta);
    return updated;
  }

  private String title(ArmorTier.ArmorSlot slot) {
    return slot.name().charAt(0) + slot.name().substring(1).toLowerCase(Locale.ROOT);
  }

  private Component section(String text) {
    return Component.text("◆ " + text, NamedTextColor.DARK_AQUA)
        .decoration(TextDecoration.BOLD, true);
  }

  private Component line(String label, String value, NamedTextColor color) {
    return Component.text("» " + label + ": ", NamedTextColor.GRAY)
        .append(Component.text(value, color))
        .decoration(TextDecoration.ITALIC, false);
  }

  private String format(double value) {
    return value == Math.rint(value)
        ? String.format(Locale.ROOT, "%.0f", value)
        : String.format(Locale.ROOT, "%.2f", value);
  }

  private int integer(PersistentDataContainer data, NamespacedKey key, int fallback) {
    Integer value = data.get(key, PersistentDataType.INTEGER);
    return value == null ? fallback : value;
  }

  private double number(PersistentDataContainer data, NamespacedKey key) {
    Double value = data.get(key, PersistentDataType.DOUBLE);
    if (value != null) return value;
    Integer legacy = data.get(key, PersistentDataType.INTEGER);
    return legacy == null ? 0 : legacy;
  }
}
