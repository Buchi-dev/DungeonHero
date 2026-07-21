package com.dungeonhero;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Locale;

public final class ForgeMenu implements InventoryHolder {

    private static final int INVENTORY_SIZE = 27;
    private static final int SWORD_SLOT = 11;
    private static final int OUTPUT_SLOT = 13;
    private static final int FRAGMENT_SLOT = 15;
    private static final int FORGE_BUTTON_SLOT = 22;

    private final HeroItemService heroItemService;
    private final MythicFragmentService mythicFragmentService;
    private final HeroSwordStorage heroSwordStorage;
    private final DungeonInventoryService dungeonInventoryService;
    private final boolean dungeonForge;
    private Inventory inventory;

    private ForgeMenu(HeroItemService heroItemService, MythicFragmentService mythicFragmentService,
                      HeroSwordStorage heroSwordStorage, DungeonInventoryService dungeonInventoryService,
                      boolean dungeonForge) {
        this.heroItemService = heroItemService;
        this.mythicFragmentService = mythicFragmentService;
        this.heroSwordStorage = heroSwordStorage;
        this.dungeonInventoryService = dungeonInventoryService;
        this.dungeonForge = dungeonForge;
    }

    public static void open(Player player, HeroItemService heroItemService,
                            MythicFragmentService mythicFragmentService,
                            HeroSwordStorage heroSwordStorage,
                            DungeonInventoryService dungeonInventoryService) {
        boolean dungeonForge = dungeonInventoryService != null && dungeonInventoryService.isDungeonWorld(player);
        ForgeMenu menu = new ForgeMenu(heroItemService, mythicFragmentService, heroSwordStorage,
                dungeonInventoryService, dungeonForge);
        menu.inventory = Bukkit.createInventory(menu, INVENTORY_SIZE,
                Component.text("Hero Forge", NamedTextColor.DARK_PURPLE));
        if (dungeonForge) {
            menu.inventory.setItem(SWORD_SLOT, dungeonInventoryService.takeHeroSwordForForge(player));
            menu.inventory.setItem(FRAGMENT_SLOT, dungeonInventoryService.getAvailableForgeFragment(player));
        }
        menu.refresh();
        player.openInventory(menu.inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void refresh() {
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            if (slot != SWORD_SLOT && slot != OUTPUT_SLOT && slot != FRAGMENT_SLOT && slot != FORGE_BUTTON_SLOT) {
                inventory.setItem(slot, namedItem(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY));
            }
        }

        if (dungeonForge) {
            Player player = (Player) inventory.getViewers().stream().findFirst().orElse(null);
            if (player != null) {
                inventory.setItem(FRAGMENT_SLOT, dungeonInventoryService.getAvailableForgeFragment(player));
            }
        }

        ItemStack sword = inventory.getItem(SWORD_SLOT);
        ItemStack fragment = inventory.getItem(FRAGMENT_SLOT);
        MythicFragmentService.Inspection inspection = mythicFragmentService.inspect(fragment);
        boolean canForge = sword != null && sword.getAmount() == 1
                && heroItemService.isHeroSword(sword)
                && inspection.isValid();

        if (canForge) {
            MythicFragmentService.FragmentUpgrade upgrade = inspection.upgrade();
            inventory.setItem(OUTPUT_SLOT, heroItemService.forge(sword, upgrade));
            inventory.setItem(FORGE_BUTTON_SLOT,
                    namedItem(Material.ANVIL, "Forge Hero Sword", NamedTextColor.GREEN,
                            "Apply " + upgrade.stat() + " +" + formatNumber(upgrade.amount()),
                            upgrade.id(), "The fragment will be consumed."));
        } else {
            inventory.setItem(OUTPUT_SLOT,
                    namedItem(Material.BARRIER, "Place a Hero Sword and Fragment", NamedTextColor.RED));
            inventory.setItem(FORGE_BUTTON_SLOT,
                    namedItem(Material.ANVIL, "Forge", NamedTextColor.DARK_GRAY,
                            inspection.error()));
        }
    }

    private void forge(Player player) {
        ItemStack sword = inventory.getItem(SWORD_SLOT);
        ItemStack fragment = inventory.getItem(FRAGMENT_SLOT);
        MythicFragmentService.Inspection inspection = mythicFragmentService.inspect(fragment);
        if (sword == null || sword.getAmount() != 1 || !heroItemService.isHeroSword(sword)) {
            player.sendMessage(Component.text("The forge requires one Hero Sword.", NamedTextColor.RED));
            return;
        }
        if (!inspection.isValid()) {
            player.sendMessage(Component.text(inspection.error(), NamedTextColor.RED));
            return;
        }

        MythicFragmentService.FragmentUpgrade upgrade = inspection.upgrade();
        if (dungeonForge && !dungeonInventoryService.consumeForgeFragment(player, upgrade)) {
            player.sendMessage(Component.text("You do not have that fragment in your Fragment Vault.",
                    NamedTextColor.RED));
            refresh();
            return;
        }
        ItemStack upgradedSword = heroItemService.forge(sword, upgrade);
        inventory.setItem(SWORD_SLOT, upgradedSword);
        heroSwordStorage.save(player, upgradedSword);
        if (!dungeonForge && fragment.getAmount() > 1) {
            fragment.setAmount(fragment.getAmount() - 1);
            inventory.setItem(FRAGMENT_SLOT, fragment);
        } else if (!dungeonForge) {
            inventory.setItem(FRAGMENT_SLOT, null);
        }
        refresh();
        player.sendMessage(Component.text("The Hero Sword grew stronger: +" + formatNumber(upgrade.amount())
                + " " + upgrade.stat() + ".", NamedTextColor.GREEN));
        player.playSound(player.getLocation(), "block.anvil.use", 1.0f, 1.0f);
    }

    private ItemStack namedItem(Material material, String name, NamedTextColor color, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        if (loreLines.length > 0) {
            meta.lore(Arrays.stream(loreLines)
                    .map(line -> Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                    .toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    private String formatNumber(double value) {
        return value == Math.rint(value)
                ? String.format(Locale.ROOT, "%.0f", value)
                : String.format(Locale.ROOT, "%.2f", value);
    }

    public static final class Listener implements org.bukkit.event.Listener {

        private final JavaPlugin plugin;

        public Listener(JavaPlugin plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (!(event.getView().getTopInventory().getHolder() instanceof ForgeMenu menu)) {
                return;
            }

            int rawSlot = event.getRawSlot();
            if (rawSlot == FORGE_BUTTON_SLOT) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    menu.forge(player);
                }
                return;
            }

            if (rawSlot == OUTPUT_SLOT || (rawSlot >= 0 && rawSlot < INVENTORY_SIZE
                    && rawSlot != SWORD_SLOT && rawSlot != FRAGMENT_SLOT)) {
                event.setCancelled(true);
                return;
            }

            if (menu.dungeonForge && rawSlot >= INVENTORY_SIZE) {
                event.setCancelled(true);
                return;
            }

            if (menu.dungeonForge && (rawSlot == SWORD_SLOT || rawSlot == FRAGMENT_SLOT)) {
                event.setCancelled(true);
                return;
            }

            if (rawSlot == SWORD_SLOT || rawSlot == FRAGMENT_SLOT) {
                Bukkit.getScheduler().runTask(plugin, menu::refresh);
                return;
            }

            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            if (!(event.getView().getTopInventory().getHolder() instanceof ForgeMenu)) {
                return;
            }

            if (event.getRawSlots().stream().anyMatch(slot -> slot < INVENTORY_SIZE)) {
                event.setCancelled(true);
            }
        }

        @EventHandler
        public void onClose(InventoryCloseEvent event) {
            if (!(event.getInventory().getHolder() instanceof ForgeMenu menu)
                    || !(event.getPlayer() instanceof Player player)) {
                return;
            }

            ItemStack sword = event.getInventory().getItem(SWORD_SLOT);
            ItemStack fragment = event.getInventory().getItem(FRAGMENT_SLOT);
            if (menu.dungeonForge) {
                menu.dungeonInventoryService.returnHeroSwordFromForge(player, sword);
            } else {
                returnItem(player, sword);
                returnItem(player, fragment);
            }
            event.getInventory().setItem(SWORD_SLOT, null);
            event.getInventory().setItem(FRAGMENT_SLOT, null);
            event.getInventory().setItem(OUTPUT_SLOT, null);
            event.getInventory().setItem(FORGE_BUTTON_SLOT, null);
        }

        private void returnItem(Player player, ItemStack item) {
            if (item == null || item.getType().isAir()) {
                return;
            }

            player.getInventory().addItem(item).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }
}
