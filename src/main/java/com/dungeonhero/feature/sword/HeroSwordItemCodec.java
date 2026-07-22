package com.dungeonhero.feature.sword;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Bukkit persistence adapter for typed Hero Sword state. */
public final class HeroSwordItemCodec {

  private final NamespacedKey heroSwordKey;
  private final NamespacedKey damageBonusKey;
  private final NamespacedKey swordLevelKey;
  private final NamespacedKey swordXpKey;
  private final NamespacedKey swordTierKey;
  private final NamespacedKey prestigeKey;
  private final NamespacedKey fragmentTotalKey;
  private final NamespacedKey fragmentOverflowKey;
  private final NamespacedKey fragmentRankKey;
  private final FragmentCapPolicy fragmentCapPolicy;

  public HeroSwordItemCodec(JavaPlugin plugin, FragmentCapPolicy fragmentCapPolicy) {
    this.heroSwordKey = new NamespacedKey(plugin, "hero_sword");
    this.damageBonusKey = new NamespacedKey(plugin, "damage_bonus");
    this.swordLevelKey = new NamespacedKey(plugin, "sword_level");
    this.swordXpKey = new NamespacedKey(plugin, "sword_xp");
    this.swordTierKey = new NamespacedKey(plugin, "sword_tier");
    this.prestigeKey = new NamespacedKey(plugin, "prestige");
    this.fragmentTotalKey = new NamespacedKey(plugin, "fragment_damage_total");
    this.fragmentOverflowKey = new NamespacedKey(plugin, "fragment_damage_overflow");
    this.fragmentRankKey = new NamespacedKey(plugin, "fragment_damage_rank");
    this.fragmentCapPolicy = fragmentCapPolicy;
  }

  public ItemStack create(HeroSwordState state) {
    ItemStack sword = new ItemStack(SwordTier.WOOD.material());
    ItemMeta meta = sword.getItemMeta();
    meta.getPersistentDataContainer().set(heroSwordKey, PersistentDataType.BYTE, (byte) 1);
    sword.setItemMeta(meta);
    return write(sword, state);
  }

  public boolean isHeroSword(ItemStack item) {
    if (item == null || !item.hasItemMeta()) {
      return false;
    }
    Byte value =
        item.getItemMeta().getPersistentDataContainer().get(heroSwordKey, PersistentDataType.BYTE);
    return value != null && value == 1;
  }

  public HeroSwordState read(ItemStack sword) {
    if (!isHeroSword(sword)) {
      return HeroSwordState.defaults();
    }
    PersistentDataContainer data = sword.getItemMeta().getPersistentDataContainer();
    return new HeroSwordState(
        integer(data, swordLevelKey, 1),
        integer(data, swordXpKey, 0),
        storedDamage(data),
        integer(data, prestigeKey, 0),
        integer(data, fragmentRankKey, 1));
  }

  public ItemStack write(ItemStack sword, HeroSwordState state) {
    if (!isHeroSword(sword)) {
      return sword;
    }
    HeroSwordState safeState = state == null ? HeroSwordState.defaults() : state;
    ItemStack updatedSword = sword.clone();
    ItemMeta meta = updatedSword.getItemMeta();
    PersistentDataContainer data = meta.getPersistentDataContainer();
    double damage = fragmentCapPolicy.sanitizeTotal(safeState.damageBonus());
    SwordTier tier = SwordTier.fromLevel(safeState.level());
    data.set(heroSwordKey, PersistentDataType.BYTE, (byte) 1);
    data.set(swordLevelKey, PersistentDataType.INTEGER, safeState.level());
    data.set(swordXpKey, PersistentDataType.INTEGER, safeState.xp());
    data.set(swordTierKey, PersistentDataType.STRING, tier.name());
    data.set(damageBonusKey, PersistentDataType.DOUBLE, damage);
    data.set(prestigeKey, PersistentDataType.INTEGER, safeState.prestige());
    data.set(fragmentTotalKey, PersistentDataType.DOUBLE, damage);
    data.set(fragmentRankKey, PersistentDataType.INTEGER, safeState.fragmentRank());
    data.set(
        fragmentOverflowKey,
        PersistentDataType.DOUBLE,
        fragmentCapPolicy.overflow(damage, safeState.fragmentRank()));
    updatedSword.setType(tier.material());
    updatedSword.setItemMeta(meta);
    return updatedSword;
  }

  public NamespacedKey heroSwordKey() {
    return heroSwordKey;
  }

  public NamespacedKey damageBonusKey() {
    return damageBonusKey;
  }

  public NamespacedKey swordLevelKey() {
    return swordLevelKey;
  }

  public NamespacedKey swordXpKey() {
    return swordXpKey;
  }

  private double storedDamage(PersistentDataContainer data) {
    Double total = data.get(fragmentTotalKey, PersistentDataType.DOUBLE);
    if (total != null) return fragmentCapPolicy.sanitizeTotal(total);
    Double doubleBonus = data.get(damageBonusKey, PersistentDataType.DOUBLE);
    if (doubleBonus != null) return fragmentCapPolicy.sanitizeTotal(doubleBonus);
    Integer legacyBonus = data.get(damageBonusKey, PersistentDataType.INTEGER);
    return legacyBonus == null ? 0 : fragmentCapPolicy.sanitizeTotal(legacyBonus);
  }

  private int integer(PersistentDataContainer data, NamespacedKey key, int fallback) {
    Integer value = data.get(key, PersistentDataType.INTEGER);
    return value == null ? fallback : value;
  }
}
