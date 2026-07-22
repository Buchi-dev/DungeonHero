package com.dungeonhero.integration.mythicmobs;

import com.dungeonhero.feature.mobregistry.MobRegistryService;
import com.dungeonhero.feature.sword.HeroAscensionService;
import com.dungeonhero.feature.sword.HeroItemService;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Applies configured Ascension bonuses only to explicitly eligible mob drops. */
public final class HeroRareDropBonusListener implements Listener {

    private final HeroAscensionService ascensionService;
    private final HeroItemService heroItemService;
    private final MythicFragmentService fragmentService;
    private final MobRegistryService mobRegistryService;
    private final JavaPlugin plugin;
    private Set<Material> eligibleMaterials = Set.of();
    private Set<String> eligibleMythicItems = Set.of();

    public HeroRareDropBonusListener(JavaPlugin plugin, HeroAscensionService ascensionService,
                                     HeroItemService heroItemService, MythicFragmentService fragmentService,
                                     MobRegistryService mobRegistryService) {
        this.plugin = plugin;
        this.ascensionService = ascensionService;
        this.heroItemService = heroItemService;
        this.fragmentService = fragmentService;
        this.mobRegistryService = mobRegistryService;
        reload();
    }

    public void reload() {
        Set<Material> materials = new HashSet<>();
        for (String value : plugin.getConfig().getStringList(
                "DungeonHero.HeroAscension.RareDropEligibleMaterials")) {
            try {
                materials.add(Material.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring unknown rare-drop material: " + value);
            }
        }
        eligibleMaterials = Set.copyOf(materials);
        eligibleMythicItems = plugin.getConfig().getStringList(
                        "DungeonHero.HeroAscension.RareDropEligibleMythicItems").stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT)).filter(value -> !value.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        if (!(event.getKiller() instanceof Player player)) {
            return;
        }
        ItemStack sword = heroItemService.findStrongestHeroSword(player);
        if (!heroItemService.isHeroSword(sword)) {
            return;
        }
        double bonus = ascensionService.rareDropBonus(heroItemService.getSwordPrestige(sword));
        if (bonus <= 0 || !isRareProfile(event)) {
            return;
        }

        var drops = new ArrayList<>(event.getDrops());
        var additions = new ArrayList<ItemStack>();
        for (ItemStack drop : drops) {
            if (isEligible(drop) && ThreadLocalRandom.current().nextDouble() < bonus) {
                additions.add(drop.clone());
            }
        }
        drops.addAll(additions);
        event.setDrops(drops);
    }

    private boolean isRareProfile(MythicMobDeathEvent event) {
        String id = event.getMobType() == null ? "" : event.getMobType().getInternalName();
        return mobRegistryService.profileOrDefault(id).kind() == MobCombatBalance.MobKind.RARE_BOSS;
    }

    private boolean isEligible(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (eligibleMaterials.contains(item.getType())) {
            return true;
        }
        try {
            var inspection = fragmentService.inspect(item);
            return inspection.isValid() && eligibleMythicItems.contains(inspection.upgrade().id().toLowerCase(Locale.ROOT));
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
