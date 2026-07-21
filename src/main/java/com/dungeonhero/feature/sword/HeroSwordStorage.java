package com.dungeonhero.feature.sword;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Stores the canonical Hero Sword progression on the player. */
public final class HeroSwordStorage {

    private final HeroItemService heroItemService;
    private final NamespacedKey levelKey;
    private final NamespacedKey xpKey;
    private final NamespacedKey damageKey;
    private final NamespacedKey prestigeKey;

    public HeroSwordStorage(JavaPlugin plugin, HeroItemService heroItemService) {
        this.heroItemService = heroItemService;
        levelKey = new NamespacedKey(plugin, "saved_sword_level");
        xpKey = new NamespacedKey(plugin, "saved_sword_xp");
        damageKey = new NamespacedKey(plugin, "saved_sword_damage");
        prestigeKey = new NamespacedKey(plugin, "saved_sword_prestige");
    }

    public ItemStack load(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        Integer level = data.get(levelKey, PersistentDataType.INTEGER);
        if (level == null) {
            return null;
        }

        Integer xp = data.get(xpKey, PersistentDataType.INTEGER);
        Double damage = data.get(damageKey, PersistentDataType.DOUBLE);
        Integer prestige = data.get(prestigeKey, PersistentDataType.INTEGER);
        return heroItemService.createHeroSwordFromState(
                level,
                xp == null ? 0 : xp,
                damage == null ? 0.0D : damage,
                prestige == null ? 0 : prestige);
    }

    public void save(Player player, ItemStack sword) {
        if (!heroItemService.isHeroSword(sword)) {
            return;
        }

        PersistentDataContainer data = player.getPersistentDataContainer();
        data.set(levelKey, PersistentDataType.INTEGER, heroItemService.getSwordLevel(sword));
        data.set(xpKey, PersistentDataType.INTEGER, heroItemService.getSwordXp(sword));
        data.set(damageKey, PersistentDataType.DOUBLE, heroItemService.getDamageBonus(sword));
        data.set(prestigeKey, PersistentDataType.INTEGER, heroItemService.getSwordPrestige(sword));
    }
}
