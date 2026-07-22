package com.dungeonhero.feature.sword;

import com.dungeonhero.integration.mythicmobs.MythicFragmentService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HeroItemService {

    private final NamespacedKey heroSwordKey;
    private final NamespacedKey damageBonusKey;
    private final NamespacedKey swordLevelKey;
    private final NamespacedKey swordXpKey;
    private final NamespacedKey swordTierKey;
    private final NamespacedKey prestigeKey;
    private final NamespacedKey fragmentTotalKey;
    private final NamespacedKey fragmentOverflowKey;
    private final NamespacedKey fragmentRankKey;
    private final JavaPlugin plugin;
    private final FragmentDamagePolicy fragmentDamagePolicy;

    public HeroItemService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.fragmentDamagePolicy = createFragmentDamagePolicy(plugin);
        heroSwordKey = new NamespacedKey(plugin, "hero_sword");
        damageBonusKey = new NamespacedKey(plugin, "damage_bonus");
        swordLevelKey = new NamespacedKey(plugin, "sword_level");
        swordXpKey = new NamespacedKey(plugin, "sword_xp");
        swordTierKey = new NamespacedKey(plugin, "sword_tier");
        prestigeKey = new NamespacedKey(plugin, "prestige");
        fragmentTotalKey = new NamespacedKey(plugin, "fragment_damage_total");
        fragmentOverflowKey = new NamespacedKey(plugin, "fragment_damage_overflow");
        fragmentRankKey = new NamespacedKey(plugin, "fragment_damage_rank");
    }

    public ItemStack createHeroSword() {
        ItemStack sword = new ItemStack(SwordTier.WOOD.material());
        ItemMeta meta = sword.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(heroSwordKey, PersistentDataType.BYTE, (byte) 1);
        data.set(damageBonusKey, PersistentDataType.DOUBLE, 0.0D);
        data.set(swordLevelKey, PersistentDataType.INTEGER, 1);
        data.set(swordXpKey, PersistentDataType.INTEGER, 0);
        data.set(swordTierKey, PersistentDataType.STRING, SwordTier.WOOD.name());
        data.set(prestigeKey, PersistentDataType.INTEGER, 0);
        data.set(fragmentTotalKey, PersistentDataType.DOUBLE, 0.0D);
        data.set(fragmentOverflowKey, PersistentDataType.DOUBLE, 0.0D);
        data.set(fragmentRankKey, PersistentDataType.INTEGER, 1);
        updateHeroSwordMeta(meta, 0.0D, SwordTier.WOOD, 0, 1);
        sword.setItemMeta(meta);
        return sword;
    }

    public ItemStack createHeroSwordFromState(int level, int xp, double damageBonus, int prestige) {
        return withSwordState(createHeroSword(), level, xp, damageBonus, prestige);
    }

    public boolean isHeroSword(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        Byte value = item.getItemMeta().getPersistentDataContainer().get(heroSwordKey, PersistentDataType.BYTE);
        return value != null && value == 1;
    }

    public double getDamageBonus(ItemStack sword) {
        return getStoredDamageBonus(sword);
    }

    /** Returns the complete, safely decoded fragment total, including inactive overflow. */
    public double getStoredDamageBonus(ItemStack sword) {
        if (!isHeroSword(sword)) {
            return 0;
        }

        PersistentDataContainer data = sword.getItemMeta().getPersistentDataContainer();
        Double total = data.get(fragmentTotalKey, PersistentDataType.DOUBLE);
        if (total != null) {
            return fragmentDamagePolicy.sanitizeTotal(total);
        }
        Double doubleBonus = data.get(damageBonusKey, PersistentDataType.DOUBLE);
        if (doubleBonus != null) {
            return fragmentDamagePolicy.sanitizeTotal(doubleBonus);
        }

        Integer legacyBonus = data.get(damageBonusKey, PersistentDataType.INTEGER);
        return legacyBonus == null ? 0 : fragmentDamagePolicy.sanitizeTotal(legacyBonus);
    }

    public double getEffectiveDamageBonus(ItemStack sword, int rank) {
        return fragmentDamagePolicy.effective(getStoredDamageBonus(sword), rank);
    }

    public double getInactiveDamageBonus(ItemStack sword, int rank) {
        return fragmentDamagePolicy.overflow(getStoredDamageBonus(sword), rank);
    }

    public int getFragmentRank(ItemStack sword) {
        if (!isHeroSword(sword)) {
            return 1;
        }
        Integer rank = sword.getItemMeta().getPersistentDataContainer()
                .get(fragmentRankKey, PersistentDataType.INTEGER);
        return rank == null ? 1 : Math.max(1, rank);
    }

    public int getSwordLevel(ItemStack sword) {
        if (!isHeroSword(sword)) {
            return 1;
        }
        Integer level = sword.getItemMeta().getPersistentDataContainer().get(swordLevelKey, PersistentDataType.INTEGER);
        return level == null ? 1 : Math.max(1, level);
    }

    public int getSwordXp(ItemStack sword) {
        if (!isHeroSword(sword)) {
            return 0;
        }
        Integer xp = sword.getItemMeta().getPersistentDataContainer().get(swordXpKey, PersistentDataType.INTEGER);
        return xp == null ? 0 : Math.max(0, xp);
    }

    public int getSwordPrestige(ItemStack sword) {
        if (!isHeroSword(sword)) {
            return 0;
        }
        Integer prestige = sword.getItemMeta().getPersistentDataContainer().get(prestigeKey, PersistentDataType.INTEGER);
        return prestige == null ? 0 : Math.max(0, prestige);
    }

    public SwordTier getSwordTier(ItemStack sword) {
        if (!isHeroSword(sword)) {
            return SwordTier.WOOD;
        }
        return SwordTier.fromLevel(getSwordLevel(sword));
    }

    public ItemStack withSwordProgression(ItemStack sword, int level, int xp) {
        if (!isHeroSword(sword)) {
            return sword;
        }

        return withSwordState(sword, level, xp, getStoredDamageBonus(sword), getSwordPrestige(sword));
    }

    private ItemStack withSwordState(ItemStack sword, int level, int xp, double damageBonus, int prestige) {
        if (!isHeroSword(sword)) {
            return sword;
        }

        ItemStack updatedSword = sword.clone();
        ItemMeta meta = updatedSword.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        int safeLevel = Math.max(1, level);
        double safeDamageBonus = safeDamage(damageBonus);
        int safePrestige = Math.max(0, prestige);
        SwordTier tier = SwordTier.fromLevel(safeLevel);
        data.set(swordLevelKey, PersistentDataType.INTEGER, safeLevel);
        data.set(swordXpKey, PersistentDataType.INTEGER, Math.max(0, xp));
        data.set(swordTierKey, PersistentDataType.STRING, tier.name());
        data.set(damageBonusKey, PersistentDataType.DOUBLE, safeDamageBonus);
        data.set(prestigeKey, PersistentDataType.INTEGER, safePrestige);
        data.set(fragmentTotalKey, PersistentDataType.DOUBLE, safeDamageBonus);
        int rank = getFragmentRank(sword);
        data.set(fragmentRankKey, PersistentDataType.INTEGER, rank);
        data.set(fragmentOverflowKey, PersistentDataType.DOUBLE, fragmentDamagePolicy.overflow(safeDamageBonus, rank));
        updatedSword.setType(tier.material());
        updateHeroSwordMeta(meta, safeDamageBonus, tier, safePrestige, rank);
        updatedSword.setItemMeta(meta);
        return updatedSword;
    }

    public ItemStack withPrestige(ItemStack sword) {
        if (!isHeroSword(sword)) {
            return sword;
        }

        int prestige = Math.min(5, getSwordPrestige(sword) + 1);
        return withSwordState(sword, 1, 0, getStoredDamageBonus(sword), prestige);
    }

    public ItemStack normalizeSword(ItemStack sword) {
        if (!isHeroSword(sword)) {
            return sword;
        }
        return withSwordProgression(sword, getSwordLevel(sword), getSwordXp(sword));
    }

    public ItemStack resetSword(ItemStack sword) {
        return withSwordState(sword, 1, 0, 0, 0);
    }

    /** Refreshes the rank-dependent active/overflow display and server attribute. */
    public ItemStack withFragmentRank(ItemStack sword, int rank) {
        if (!isHeroSword(sword)) {
            return sword;
        }
        ItemStack updated = sword.clone();
        ItemMeta meta = updated.getItemMeta();
        int safeRank = Math.max(1, rank);
        double total = getStoredDamageBonus(sword);
        meta.getPersistentDataContainer().set(fragmentTotalKey, PersistentDataType.DOUBLE, total);
        meta.getPersistentDataContainer().set(damageBonusKey, PersistentDataType.DOUBLE, total);
        meta.getPersistentDataContainer().set(fragmentRankKey, PersistentDataType.INTEGER, safeRank);
        meta.getPersistentDataContainer().set(fragmentOverflowKey, PersistentDataType.DOUBLE,
                getInactiveDamageBonus(sword, safeRank));
        updateHeroSwordMeta(meta, total, getSwordTier(sword), getSwordPrestige(sword), safeRank);
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

    public ItemStack forge(ItemStack sword, MythicFragmentService.FragmentUpgrade upgrade, int quantity) {
        if (!isHeroSword(sword) || upgrade == null || !upgrade.isSupported()) {
            throw new IllegalArgumentException("The Hero Forge requires a valid Hero Sword and MythicMobs fragment.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Forge quantity must be positive.");
        }

        double newDamageBonus = safeDamage(getStoredDamageBonus(sword) + (upgrade.amount() * quantity));
        ItemStack upgradedSword = sword.clone();
        ItemMeta meta = upgradedSword.getItemMeta();
        meta.getPersistentDataContainer().set(damageBonusKey, PersistentDataType.DOUBLE, newDamageBonus);
        meta.getPersistentDataContainer().set(fragmentTotalKey, PersistentDataType.DOUBLE, newDamageBonus);
        int rank = getFragmentRank(sword);
        meta.getPersistentDataContainer().set(fragmentRankKey, PersistentDataType.INTEGER, rank);
        meta.getPersistentDataContainer().set(fragmentOverflowKey, PersistentDataType.DOUBLE,
                Math.max(0, newDamageBonus - fragmentCap(rank)));
        updateHeroSwordMeta(meta, newDamageBonus, getSwordTier(upgradedSword), getSwordPrestige(upgradedSword), rank);
        upgradedSword.setItemMeta(meta);
        return upgradedSword;
    }

    public void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private void updateHeroSwordMeta(ItemMeta meta, double damageBonus, SwordTier tier, int prestige, int rank) {
        meta.displayName(Component.text(tier.displayName() + " Hero Sword", tier.color())
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("⚔ The first blade of every dungeon hero.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, true),
                Component.empty(),
                Component.text("◆ PROGRESSION", NamedTextColor.DARK_AQUA)
                        .decoration(TextDecoration.BOLD, true),
                loreLine("Tier", tier.displayName(), tier.color()),
                loreLine("Level", String.valueOf(getSwordLevelFromMeta(meta)), NamedTextColor.AQUA),
                loreLine("XP", String.valueOf(getSwordXpFromMeta(meta)), NamedTextColor.GREEN),
                Component.empty(),
                Component.text("◆ POWER", NamedTextColor.DARK_AQUA)
                        .decoration(TextDecoration.BOLD, true),
                loreLine("Fragment Damage", "+" + formatNumber(damageBonus), NamedTextColor.RED),
                loreLine("Effective Damage", "+" + formatNumber(Math.min(damageBonus, fragmentCap(rank))), NamedTextColor.GREEN),
                loreLine("Inactive Overflow", "+" + formatNumber(Math.max(0, damageBonus - fragmentCap(rank))), NamedTextColor.GRAY),
                loreLine("Prestige", String.valueOf(prestige), NamedTextColor.LIGHT_PURPLE),
                Component.empty(),
                Component.text("◆ FORGE", NamedTextColor.DARK_AQUA)
                        .decoration(TextDecoration.BOLD, true),
                Component.text("Insert MythicMobs fragments", NamedTextColor.GRAY),
                Component.text("into the Hero Forge to improve it.", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Bound to its rightful hero.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, true),
                Component.text("Unbreakable", NamedTextColor.BLUE)
        ));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
        double effectiveDamage = Math.min(damageBonus, fragmentCap(rank));
        if (effectiveDamage > 0) {
            meta.addAttributeModifier(
                    Attribute.ATTACK_DAMAGE,
                     new AttributeModifier(damageBonusKey, effectiveDamage, AttributeModifier.Operation.ADD_NUMBER,
                            EquipmentSlotGroup.MAINHAND)
            );
        }
    }

    private boolean isStronger(ItemStack first, ItemStack second) {
        int firstLevel = getSwordLevel(first);
        int secondLevel = getSwordLevel(second);
        if (firstLevel != secondLevel) {
            return firstLevel > secondLevel;
        }
        int firstPrestige = getSwordPrestige(first);
        int secondPrestige = getSwordPrestige(second);
        if (firstPrestige != secondPrestige) {
            return firstPrestige > secondPrestige;
        }
        return getDamageBonus(first) > getDamageBonus(second);
    }

    private int getSwordLevelFromMeta(ItemMeta meta) {
        Integer level = meta.getPersistentDataContainer().get(swordLevelKey, PersistentDataType.INTEGER);
        return level == null ? 1 : Math.max(1, level);
    }

    private int getSwordXpFromMeta(ItemMeta meta) {
        Integer xp = meta.getPersistentDataContainer().get(swordXpKey, PersistentDataType.INTEGER);
        return xp == null ? 0 : Math.max(0, xp);
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
        int safeRank = Math.max(1, rank);
        double configured = plugin.getConfig().getDouble(
                "DungeonHero.FragmentCaps.RankCaps." + safeRank, Double.NaN);
        if (!Double.isFinite(configured)) {
            configured = plugin.getConfig().getDouble("DungeonHero.Fragments.Caps." + safeRank, Double.NaN);
        }
        if (!Double.isFinite(configured)) {
            configured = switch (safeRank) {
                case 1 -> 10; case 2 -> 20; case 3 -> 35; case 4 -> 55; case 5 -> 80;
                case 6 -> 110; case 7 -> 145; case 8 -> 185; case 9 -> 230; default -> 280;
            };
        }
        return Math.max(0, configured);
    }

    private double safeDamage(double value) {
        double max = Math.max(280, plugin.getConfig().getDouble(
                "DungeonHero.FragmentCaps.MaximumStoredDamage", 100000));
        return Math.max(0, Math.min(max, Double.isFinite(value) ? value : 0));
    }

    private FragmentDamagePolicy createFragmentDamagePolicy(JavaPlugin plugin) {
        double[] caps = new double[11];
        for (int rank = 1; rank <= 10; rank++) {
            caps[rank] = plugin.getConfig().getDouble("DungeonHero.FragmentCaps.RankCaps." + rank,
                    Double.NaN);
        }
        return new FragmentDamagePolicy(caps, plugin.getConfig().getDouble(
                "DungeonHero.FragmentCaps.MaximumStoredDamage", 100000));
    }
}
