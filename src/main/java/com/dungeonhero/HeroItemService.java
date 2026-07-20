package com.dungeonhero;

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

    public HeroItemService(JavaPlugin plugin) {
        heroSwordKey = new NamespacedKey(plugin, "hero_sword");
        damageBonusKey = new NamespacedKey(plugin, "damage_bonus");
        swordLevelKey = new NamespacedKey(plugin, "sword_level");
        swordXpKey = new NamespacedKey(plugin, "sword_xp");
        swordTierKey = new NamespacedKey(plugin, "sword_tier");
        prestigeKey = new NamespacedKey(plugin, "prestige");
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
        updateHeroSwordMeta(meta, 0.0D, SwordTier.WOOD, 0);
        sword.setItemMeta(meta);
        return sword;
    }

    public boolean isHeroSword(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        Byte value = item.getItemMeta().getPersistentDataContainer().get(heroSwordKey, PersistentDataType.BYTE);
        return value != null && value == 1;
    }

    public double getDamageBonus(ItemStack sword) {
        if (!isHeroSword(sword)) {
            return 0;
        }

        PersistentDataContainer data = sword.getItemMeta().getPersistentDataContainer();
        Double doubleBonus = data.get(damageBonusKey, PersistentDataType.DOUBLE);
        if (doubleBonus != null) {
            return Math.max(0, doubleBonus);
        }

        Integer legacyBonus = data.get(damageBonusKey, PersistentDataType.INTEGER);
        return legacyBonus == null ? 0 : Math.max(0, legacyBonus);
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

        ItemStack updatedSword = sword.clone();
        ItemMeta meta = updatedSword.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        int safeLevel = Math.max(1, level);
        SwordTier tier = SwordTier.fromLevel(safeLevel);
        data.set(swordLevelKey, PersistentDataType.INTEGER, safeLevel);
        data.set(swordXpKey, PersistentDataType.INTEGER, Math.max(0, xp));
        data.set(swordTierKey, PersistentDataType.STRING, tier.name());
        updatedSword.setType(tier.material());
        updateHeroSwordMeta(meta, getDamageBonus(updatedSword), tier, getSwordPrestige(updatedSword));
        updatedSword.setItemMeta(meta);
        return updatedSword;
    }

    public ItemStack withPrestige(ItemStack sword) {
        if (!isHeroSword(sword)) {
            return sword;
        }

        ItemStack updatedSword = sword.clone();
        ItemMeta meta = updatedSword.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        int prestige = getSwordPrestige(sword) + 1;
        data.set(swordLevelKey, PersistentDataType.INTEGER, 1);
        data.set(swordXpKey, PersistentDataType.INTEGER, 0);
        data.set(swordTierKey, PersistentDataType.STRING, SwordTier.WOOD.name());
        data.set(prestigeKey, PersistentDataType.INTEGER, prestige);
        updatedSword.setType(SwordTier.WOOD.material());
        updateHeroSwordMeta(meta, getDamageBonus(updatedSword), SwordTier.WOOD, prestige);
        updatedSword.setItemMeta(meta);
        return updatedSword;
    }

    public ItemStack normalizeSword(ItemStack sword) {
        if (!isHeroSword(sword)) {
            return sword;
        }
        return withSwordProgression(sword, getSwordLevel(sword), getSwordXp(sword));
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
        if (!isHeroSword(sword) || upgrade == null || !upgrade.isSupported()) {
            throw new IllegalArgumentException("The Hero Forge requires a valid Hero Sword and MythicMobs fragment.");
        }

        double newDamageBonus = getDamageBonus(sword) + upgrade.amount();
        ItemStack upgradedSword = sword.clone();
        ItemMeta meta = upgradedSword.getItemMeta();
        meta.getPersistentDataContainer().set(damageBonusKey, PersistentDataType.DOUBLE, newDamageBonus);
        updateHeroSwordMeta(meta, newDamageBonus, getSwordTier(upgradedSword), getSwordPrestige(upgradedSword));
        upgradedSword.setItemMeta(meta);
        return upgradedSword;
    }

    public void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private void updateHeroSwordMeta(ItemMeta meta, double damageBonus, SwordTier tier, int prestige) {
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
                loreLine("Damage Bonus", "+" + formatNumber(damageBonus), NamedTextColor.RED),
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
        if (damageBonus > 0) {
            meta.addAttributeModifier(
                    Attribute.ATTACK_DAMAGE,
                    new AttributeModifier(damageBonusKey, damageBonus, AttributeModifier.Operation.ADD_NUMBER,
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
}
