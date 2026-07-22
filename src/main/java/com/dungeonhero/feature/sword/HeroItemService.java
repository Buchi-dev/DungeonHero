package com.dungeonhero.feature.sword;

import com.dungeonhero.common.ItemDeliveryService;
import com.dungeonhero.config.DungeonHeroConfiguration;
import com.dungeonhero.integration.mythicmobs.MythicFragmentService;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class HeroItemService {

  private final FragmentCapPolicy fragmentCapPolicy;
  private final HeroSwordItemCodec itemCodec;
  private final ItemDeliveryService itemDeliveryService;
  private final SwordComparator swordComparator = new SwordComparator();

  public HeroItemService(JavaPlugin plugin) {
    this(plugin, new ItemDeliveryService());
  }

  public HeroItemService(JavaPlugin plugin, ItemDeliveryService itemDeliveryService) {
    this(plugin, itemDeliveryService, DungeonHeroConfiguration.load(plugin).fragmentCaps());
  }

  public HeroItemService(
      JavaPlugin plugin,
      ItemDeliveryService itemDeliveryService,
      DungeonHeroConfiguration.FragmentCaps fragmentCaps) {
    this.itemDeliveryService = itemDeliveryService;
    this.fragmentCapPolicy = createFragmentCapPolicy(fragmentCaps);
    this.itemCodec = new HeroSwordItemCodec(plugin, fragmentCapPolicy);
  }

  public ItemStack createHeroSword() {
    HeroSwordState state = HeroSwordState.defaults();
    ItemStack sword = itemCodec.create(state);
    ItemMeta meta = sword.getItemMeta();
    updateHeroSwordMeta(
        meta,
        state.damageBonus(),
        SwordTier.WOOD,
        state.prestige(),
        state.fragmentRank(),
        state.level(),
        state.xp());
    sword.setItemMeta(meta);
    return sword;
  }

  public ItemStack createHeroSwordFromState(int level, int xp, double damageBonus, int prestige) {
    return withSwordState(
        createHeroSword(), new HeroSwordState(level, xp, damageBonus, prestige, 1));
  }

  public boolean isHeroSword(ItemStack item) {
    return itemCodec.isHeroSword(item);
  }

  public double getDamageBonus(ItemStack sword) {
    return getStoredDamageBonus(sword);
  }

  /** Returns the complete, safely decoded fragment total, including inactive overflow. */
  public double getStoredDamageBonus(ItemStack sword) {
    return itemCodec.read(sword).damageBonus();
  }

  public double getEffectiveDamageBonus(ItemStack sword, int rank) {
    return fragmentCapPolicy.effective(getStoredDamageBonus(sword), rank);
  }

  public double getInactiveDamageBonus(ItemStack sword, int rank) {
    return fragmentCapPolicy.overflow(getStoredDamageBonus(sword), rank);
  }

  public int getFragmentRank(ItemStack sword) {
    return itemCodec.read(sword).fragmentRank();
  }

  public int getSwordLevel(ItemStack sword) {
    return itemCodec.read(sword).level();
  }

  public int getSwordXp(ItemStack sword) {
    return itemCodec.read(sword).xp();
  }

  public int getSwordPrestige(ItemStack sword) {
    return itemCodec.read(sword).prestige();
  }

  public SwordTier getSwordTier(ItemStack sword) {
    if (!isHeroSword(sword)) {
      return SwordTier.WOOD;
    }
    return SwordTier.fromLevel(getSwordLevel(sword));
  }

  /** Decodes the Bukkit item boundary into a typed, framework-free domain value. */
  public HeroSwordState getSwordState(ItemStack sword) {
    return itemCodec.read(sword);
  }

  public ItemStack withSwordProgression(ItemStack sword, int level, int xp) {
    if (!isHeroSword(sword)) {
      return sword;
    }

    return withSwordState(sword, getSwordState(sword).withProgression(level, xp));
  }

  /** Encodes a typed domain state at the Bukkit item boundary. */
  public ItemStack withSwordState(ItemStack sword, HeroSwordState state) {
    if (!isHeroSword(sword)) {
      return sword;
    }

    HeroSwordState safeState = state == null ? HeroSwordState.defaults() : state;
    ItemStack updatedSword = itemCodec.write(sword, safeState);
    ItemMeta meta = updatedSword.getItemMeta();
    int safeLevel = safeState.level();
    double safeDamageBonus = safeDamage(safeState.damageBonus());
    int safePrestige = safeState.prestige();
    SwordTier tier = SwordTier.fromLevel(safeLevel);
    int rank = safeState.fragmentRank();
    updateHeroSwordMeta(meta, safeDamageBonus, tier, safePrestige, rank, safeLevel, safeState.xp());
    updatedSword.setItemMeta(meta);
    return updatedSword;
  }

  public ItemStack withPrestige(ItemStack sword) {
    if (!isHeroSword(sword)) {
      return sword;
    }

    int prestige = Math.min(5, getSwordPrestige(sword) + 1);
    return withSwordState(
        sword,
        new HeroSwordState(1, 0, getStoredDamageBonus(sword), prestige, getFragmentRank(sword)));
  }

  public ItemStack normalizeSword(ItemStack sword) {
    if (!isHeroSword(sword)) {
      return sword;
    }
    return withSwordProgression(sword, getSwordLevel(sword), getSwordXp(sword));
  }

  public ItemStack resetSword(ItemStack sword) {
    return withSwordState(sword, new HeroSwordState(1, 0, 0, 0, getFragmentRank(sword)));
  }

  /** Refreshes the rank-dependent active/overflow display and server attribute. */
  public ItemStack withFragmentRank(ItemStack sword, int rank) {
    if (!isHeroSword(sword)) {
      return sword;
    }
    HeroSwordState state = getSwordState(sword);
    ItemStack updated =
        itemCodec.write(
            sword,
            new HeroSwordState(
                state.level(),
                state.xp(),
                state.damageBonus(),
                state.prestige(),
                Math.max(1, rank)));
    ItemMeta meta = updated.getItemMeta();
    HeroSwordState updatedState = itemCodec.read(updated);
    updateHeroSwordMeta(
        meta,
        updatedState.damageBonus(),
        getSwordTier(updated),
        updatedState.prestige(),
        updatedState.fragmentRank(),
        updatedState.level(),
        updatedState.xp());
    updated.setItemMeta(meta);
    return updated;
  }

  public ItemStack findStrongestHeroSword(Player player) {
    ItemStack strongest = null;
    for (ItemStack item : player.getInventory().getContents()) {
      if (!isHeroSword(item)) {
        continue;
      }
      if (strongest == null || isStronger(item, strongest)) {
        strongest = item;
      }
    }
    return strongest;
  }

  public ItemStack forge(ItemStack sword, MythicFragmentService.FragmentUpgrade upgrade) {
    return forge(sword, upgrade, 1);
  }

  public ItemStack forge(
      ItemStack sword, MythicFragmentService.FragmentUpgrade upgrade, int quantity) {
    if (!isHeroSword(sword) || upgrade == null || !upgrade.isDamageSupported()) {
      throw new IllegalArgumentException(
          "The Hero Forge requires a valid Hero Sword and MythicMobs fragment.");
    }
    if (quantity <= 0) {
      throw new IllegalArgumentException("Forge quantity must be positive.");
    }

    double newDamageBonus =
        ForgePolicy.apply(
                getStoredDamageBonus(sword),
                upgrade.amount(),
                quantity,
                fragmentCapPolicy.maximumStoredDamage())
            .totalDamage();
    HeroSwordState state = getSwordState(sword);
    return withSwordState(
        sword,
        new HeroSwordState(
            state.level(), state.xp(), newDamageBonus, state.prestige(), state.fragmentRank()));
  }

  public void giveOrDrop(Player player, ItemStack item) {
    itemDeliveryService.giveOrDrop(player, item);
  }

  private void updateHeroSwordMeta(
      ItemMeta meta,
      double damageBonus,
      SwordTier tier,
      int prestige,
      int rank,
      int level,
      int xp) {
    meta.displayName(
        Component.text(tier.displayName() + " Hero Sword", tier.color())
            .decoration(TextDecoration.ITALIC, false));
    meta.lore(
        List.of(
            Component.text("⚔ The first blade of every dungeon hero.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, true),
            Component.empty(),
            Component.text("◆ PROGRESSION", NamedTextColor.DARK_AQUA)
                .decoration(TextDecoration.BOLD, true),
            loreLine("Tier", tier.displayName(), tier.color()),
            loreLine("Level", String.valueOf(level), NamedTextColor.AQUA),
            loreLine("XP", String.valueOf(xp), NamedTextColor.GREEN),
            Component.empty(),
            Component.text("◆ POWER", NamedTextColor.DARK_AQUA)
                .decoration(TextDecoration.BOLD, true),
            loreLine("Fragment Damage", "+" + formatNumber(damageBonus), NamedTextColor.RED),
            loreLine(
                "Effective Damage",
                "+" + formatNumber(Math.min(damageBonus, fragmentCap(rank))),
                NamedTextColor.GREEN),
            loreLine(
                "Inactive Overflow",
                "+" + formatNumber(Math.max(0, damageBonus - fragmentCap(rank))),
                NamedTextColor.GRAY),
            loreLine("Prestige", String.valueOf(prestige), NamedTextColor.LIGHT_PURPLE),
            Component.empty(),
            Component.text("◆ FORGE", NamedTextColor.DARK_AQUA)
                .decoration(TextDecoration.BOLD, true),
            Component.text("Insert MythicMobs fragments", NamedTextColor.GRAY),
            Component.text("into the Hero Forge to improve it.", NamedTextColor.GRAY),
            Component.empty(),
            Component.text("Bound to its rightful hero.", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, true),
            Component.text("Unbreakable", NamedTextColor.BLUE)));
    meta.setUnbreakable(true);
    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
    double effectiveDamage = Math.min(damageBonus, fragmentCap(rank));
    if (effectiveDamage > 0) {
      meta.addAttributeModifier(
          Attribute.ATTACK_DAMAGE,
          new AttributeModifier(
              itemCodec.damageBonusKey(),
              effectiveDamage,
              AttributeModifier.Operation.ADD_NUMBER,
              EquipmentSlotGroup.MAINHAND));
    }
  }

  private boolean isStronger(ItemStack first, ItemStack second) {
    return swordComparator.isStronger(getSwordState(first), getSwordState(second));
  }

  private Component loreLine(String label, String value, NamedTextColor valueColor) {
    return Component.text("» " + label + ": ", NamedTextColor.GRAY)
        .append(Component.text(value, valueColor))
        .decoration(TextDecoration.ITALIC, false);
  }

  private String formatNumber(double value) {
    if (value == Math.rint(value)) {
      return String.format(Locale.ROOT, "%.0f", value);
    }
    return String.format(Locale.ROOT, "%.2f", value);
  }

  private double fragmentCap(int rank) {
    return fragmentCapPolicy.cap(rank);
  }

  private double safeDamage(double value) {
    return fragmentCapPolicy.sanitizeTotal(value);
  }

  private FragmentCapPolicy createFragmentCapPolicy(
      DungeonHeroConfiguration.FragmentCaps configuration) {
    double[] caps = new double[11];
    for (int rank = 1; rank <= 10; rank++) {
      caps[rank] = configuration.rankCaps().getOrDefault(rank, Double.NaN);
    }
    return new FragmentCapPolicy(caps, configuration.maximumStoredDamage());
  }
}
