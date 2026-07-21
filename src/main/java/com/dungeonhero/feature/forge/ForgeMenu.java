package com.dungeonhero.feature.forge;

import com.dungeonhero.feature.dungeoninventory.DungeonInventoryService;
import com.dungeonhero.feature.sword.HeroItemService;
import com.dungeonhero.feature.sword.HeroSwordStorage;
import com.dungeonhero.integration.mythicmobs.MythicFragmentService;

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
    private static final int MINUS_TEN_SLOT = 18;
    private static final int MINUS_ONE_SLOT = 19;
    private static final int PLUS_ONE_SLOT = 20;
    private static final int PLUS_TEN_SLOT = 21;
    private static final int MAX_SLOT = 22;
    private static final int FORGE_BUTTON_SLOT = 24;

    private final HeroItemService heroItemService;
    private final MythicFragmentService mythicFragmentService;
    private final HeroSwordStorage heroSwordStorage;
    private final DungeonInventoryService dungeonInventoryService;
    private final boolean dungeonForge;
    private Inventory inventory;
    private Player forgePlayer;
    private int batchAmount = 1;

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
        boolean dungeonForge = dungeonInventoryService != null
                && dungeonInventoryService.isDungeonWorld(player)
                && dungeonInventoryService.isFragmentVaultEnabled();
        ForgeMenu menu = new ForgeMenu(heroItemService, mythicFragmentService, heroSwordStorage,
                dungeonInventoryService, dungeonForge);
        menu.forgePlayer = player;
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
            if (slot != SWORD_SLOT && slot != OUTPUT_SLOT && slot != FRAGMENT_SLOT && !isControlSlot(slot)) {
                inventory.setItem(slot, namedItem(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY));
            }
        }

        if (dungeonForge) {
            Player player = (Player) inventory.getViewers().stream().findFirst().orElse(forgePlayer);
            if (player != null) {
                inventory.setItem(FRAGMENT_SLOT, dungeonInventoryService.getAvailableForgeFragment(player));
            }
        }

        ItemStack sword = inventory.getItem(SWORD_SLOT);
        ItemStack fragment = inventory.getItem(FRAGMENT_SLOT);
        MythicFragmentService.Inspection inspection = mythicFragmentService.inspect(fragment);
        int available = availableFragments(inspection, fragment);
        boolean canForge = sword != null && sword.getAmount() == 1
                && heroItemService.isHeroSword(sword)
                && inspection.isValid()
                && available > 0;

        if (canForge) {
            MythicFragmentService.FragmentUpgrade upgrade = inspection.upgrade();
            batchAmount = Math.max(1, Math.min(batchAmount, available));
            inventory.setItem(OUTPUT_SLOT, heroItemService.forge(sword, upgrade, batchAmount));
            inventory.setItem(FORGE_BUTTON_SLOT,
                    namedItem(Material.ANVIL, "Forge x" + batchAmount, NamedTextColor.GREEN,
                            "Apply " + upgrade.stat() + " +" + formatNumber(upgrade.amount() * batchAmount),
                            "Cost: " + batchAmount + " fragment" + (batchAmount == 1 ? "" : "s"),
                            "Click to forge this batch."));
        } else {
            inventory.setItem(OUTPUT_SLOT,
                    namedItem(Material.BARRIER, "Place a Hero Sword and Fragment", NamedTextColor.RED));
            inventory.setItem(FORGE_BUTTON_SLOT,
                    namedItem(Material.ANVIL, "Forge", NamedTextColor.DARK_GRAY,
                            available <= 0 && inspection.isValid()
                                    ? (dungeonForge ? "Your Fragment Vault is empty."
                                    : "Place a stack of fragments.")
                                    : inspection.error()));
        }
        refreshBatchControls(available, canForge);
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
        int available = availableFragments(inspection, fragment);
        int quantity = Math.max(1, Math.min(batchAmount, available));
        if (available <= 0) {
            player.sendMessage(Component.text(dungeonForge
                    ? "You do not have that fragment in your Fragment Vault."
                    : "Place a stack of fragments in the forge.", NamedTextColor.RED));
            refresh();
            return;
        }
        if (dungeonForge && !dungeonInventoryService.consumeForgeFragments(player, upgrade, quantity)) {
            player.sendMessage(Component.text("You do not have enough of that fragment in your Fragment Vault.",
                    NamedTextColor.RED));
            refresh();
            return;
        }

        ItemStack upgradedSword = heroItemService.forge(sword, upgrade, quantity);
        inventory.setItem(SWORD_SLOT, upgradedSword);
        heroSwordStorage.save(player, upgradedSword);
        if (!dungeonForge && fragment.getAmount() > quantity) {
            fragment.setAmount(fragment.getAmount() - quantity);
            inventory.setItem(FRAGMENT_SLOT, fragment);
        } else if (!dungeonForge) {
            inventory.setItem(FRAGMENT_SLOT, null);
        }
        batchAmount = 1;
        refresh();
        player.sendMessage(Component.text("The Hero Sword grew stronger: x" + quantity + " forge, +"
                + formatNumber(upgrade.amount() * quantity) + " " + upgrade.stat() + ".", NamedTextColor.GREEN));
        player.playSound(player.getLocation(), "block.anvil.use", 1.0f, 1.0f);
    }

    private int availableFragments(MythicFragmentService.Inspection inspection, ItemStack fragment) {
        if (!inspection.isValid()) {
            return 0;
        }
        if (dungeonForge) {
            Player player = (Player) inventory.getViewers().stream().findFirst().orElse(forgePlayer);
            return player == null ? 0 : dungeonInventoryService.getFragmentCount(player, inspection.upgrade().id());
        }
        return fragment == null ? 0 : Math.max(0, fragment.getAmount());
    }

    private void refreshBatchControls(int available, boolean enabled) {
        inventory.setItem(MINUS_TEN_SLOT, namedItem(Material.REDSTONE,
                "-10", enabled ? NamedTextColor.RED : NamedTextColor.DARK_GRAY,
                "Decrease the forge batch."));
        inventory.setItem(MINUS_ONE_SLOT, namedItem(Material.REDSTONE_TORCH,
                "-1", enabled ? NamedTextColor.RED : NamedTextColor.DARK_GRAY,
                "Decrease the forge batch."));
        inventory.setItem(PLUS_ONE_SLOT, namedItem(Material.GLOWSTONE_DUST,
                "+1", enabled ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY,
                "Increase the forge batch."));
        inventory.setItem(PLUS_TEN_SLOT, namedItem(Material.GLOWSTONE,
                "+10", enabled ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY,
                "Increase the forge batch."));
        inventory.setItem(MAX_SLOT, namedItem(Material.NETHER_STAR,
                "MAX (" + Math.max(0, available) + ")", enabled ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY,
                "Use the largest available batch."));
    }

    private void adjustBatch(int slot) {
        if (slot == MINUS_TEN_SLOT) {
            batchAmount = Math.max(1, batchAmount - 10);
        } else if (slot == MINUS_ONE_SLOT) {
            batchAmount = Math.max(1, batchAmount - 1);
        } else if (slot == PLUS_ONE_SLOT) {
            batchAmount++;
        } else if (slot == PLUS_TEN_SLOT) {
            batchAmount += 10;
        } else if (slot == MAX_SLOT) {
            MythicFragmentService.Inspection inspection = mythicFragmentService.inspect(inventory.getItem(FRAGMENT_SLOT));
            batchAmount = Math.max(1, availableFragments(inspection, inventory.getItem(FRAGMENT_SLOT)));
        }
        refresh();
    }

    private boolean isControlSlot(int slot) {
        return slot == MINUS_TEN_SLOT || slot == MINUS_ONE_SLOT || slot == PLUS_ONE_SLOT
                || slot == PLUS_TEN_SLOT || slot == MAX_SLOT || slot == FORGE_BUTTON_SLOT;
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
            if (rawSlot == MINUS_TEN_SLOT || rawSlot == MINUS_ONE_SLOT || rawSlot == PLUS_ONE_SLOT
                    || rawSlot == PLUS_TEN_SLOT || rawSlot == MAX_SLOT) {
                event.setCancelled(true);
                menu.adjustBatch(rawSlot);
                return;
            }
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
            for (int slot : new int[] {MINUS_TEN_SLOT, MINUS_ONE_SLOT, PLUS_ONE_SLOT, PLUS_TEN_SLOT, MAX_SLOT}) {
                event.getInventory().setItem(slot, null);
            }
        }

        private static void returnItem(Player player, ItemStack item) {
            if (item == null || item.getType().isAir()) {
                return;
            }

            player.getInventory().addItem(item).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }
}
