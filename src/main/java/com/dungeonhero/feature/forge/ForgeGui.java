package com.dungeonhero.feature.forge;

import com.dungeonhero.feature.sword.HeroItemService;
import com.dungeonhero.feature.sword.HeroSwordStorage;
import com.dungeonhero.gui.GuiManager;
import com.dungeonhero.integration.mythicmobs.MythicFragmentService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Locale;

/** Player-friendly custom Forge GUI. */
public final class ForgeGui implements GuiManager.Screen {

    private static final int GUI_ROWS = 3;
    private static final int GUI_SIZE = GUI_ROWS * 9;
    private static final int SWORD_SLOT = 11;
    private static final int OUTPUT_SLOT = 13;
    private static final int FRAGMENT_SLOT = 15;
    private static final int MINUS_TEN_SLOT = 18;
    private static final int MINUS_ONE_SLOT = 19;
    private static final int PLUS_ONE_SLOT = 20;
    private static final int PLUS_TEN_SLOT = 21;
    private static final int MAX_SLOT = 22;
    private static final int FORGE_BUTTON_SLOT = 24;

    private final JavaPlugin plugin;
    private final GuiManager guiManager;
    private final HeroItemService heroItemService;
    private final MythicFragmentService mythicFragmentService;
    private final HeroSwordStorage heroSwordStorage;
    private Player player;
    private Inventory inventory;
    private int batchAmount = 1;
    private boolean closed;

    public ForgeGui(JavaPlugin plugin, GuiManager guiManager, HeroItemService heroItemService,
                    MythicFragmentService mythicFragmentService, HeroSwordStorage heroSwordStorage) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.heroItemService = heroItemService;
        this.mythicFragmentService = mythicFragmentService;
        this.heroSwordStorage = heroSwordStorage;
    }

    public void open(Player player) {
        ForgeGui session = new ForgeGui(plugin, guiManager, heroItemService,
                mythicFragmentService, heroSwordStorage);
        guiManager.open(player, GUI_ROWS,
                Component.text("Hero Forge", NamedTextColor.DARK_PURPLE), session);
    }

    @Override
    public void onOpen(Player player, Inventory inventory) {
        this.player = player;
        this.inventory = inventory;
        this.closed = false;
        this.batchAmount = 1;
        refresh();
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player clickedPlayer) || clickedPlayer != player) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot == FORGE_BUTTON_SLOT) {
            event.setCancelled(true);
            forge();
            return;
        }
        if (isBatchControl(rawSlot)) {
            event.setCancelled(true);
            adjustBatch(rawSlot);
            return;
        }
        if (rawSlot == OUTPUT_SLOT) {
            event.setCancelled(true);
            return;
        }
        if (rawSlot >= 0 && rawSlot < GUI_SIZE
                && rawSlot != SWORD_SLOT && rawSlot != FRAGMENT_SLOT) {
            event.setCancelled(true);
            return;
        }

        if (rawSlot == SWORD_SLOT || rawSlot == FRAGMENT_SLOT || event.isShiftClick()) {
            scheduleRefresh();
        }
    }

    @Override
    public void onDrag(InventoryDragEvent event) {
        if (event.getRawSlots().stream().noneMatch(slot -> slot >= 0 && slot < GUI_SIZE)) {
            return;
        }

        boolean onlyInputSlots = event.getRawSlots().stream()
                .filter(slot -> slot >= 0 && slot < GUI_SIZE)
                .allMatch(this::isInputSlot);
        if (!onlyInputSlots) {
            event.setCancelled(true);
            return;
        }
        scheduleRefresh();
    }

    @Override
    public void onClose(Player player, Inventory inventory) {
        if (closed) {
            return;
        }
        closed = true;

        returnItem(player, inventory.getItem(SWORD_SLOT));
        returnItem(player, inventory.getItem(FRAGMENT_SLOT));
        inventory.clear();
    }

    private void forge() {
        ItemStack sword = inventory.getItem(SWORD_SLOT);
        ItemStack fragment = inventory.getItem(FRAGMENT_SLOT);
        MythicFragmentService.Inspection inspection = inspect(sword, fragment);
        if (!inspection.isValid()) {
            player.sendMessage(Component.text(inspection.error(), NamedTextColor.RED));
            refresh();
            return;
        }

        int available = fragment.getAmount();
        int quantity = Math.max(1, Math.min(batchAmount, available));
        ItemStack upgradedSword = heroItemService.forge(sword, inspection.upgrade(), quantity);
        inventory.setItem(SWORD_SLOT, upgradedSword);
        if (available > quantity) {
            fragment.setAmount(available - quantity);
            inventory.setItem(FRAGMENT_SLOT, fragment);
        } else {
            inventory.setItem(FRAGMENT_SLOT, null);
        }
        heroSwordStorage.save(player, upgradedSword);
        batchAmount = 1;
        refresh();

        player.sendMessage(Component.text("The Hero Sword grew stronger: x" + quantity + " forge, +"
                + formatNumber(inspection.upgrade().amount() * quantity) + " "
                + inspection.upgrade().stat() + ".", NamedTextColor.GREEN));
        player.playSound(player.getLocation(), "block.anvil.use", 1.0f, 1.0f);
    }

    private MythicFragmentService.Inspection inspect(ItemStack sword, ItemStack fragment) {
        if (sword == null || sword.getAmount() != 1 || !heroItemService.isHeroSword(sword)) {
            return MythicFragmentService.Inspection.invalid("Place one Hero Sword in the sword slot.");
        }
        if (fragment == null || fragment.getAmount() < 1) {
            return MythicFragmentService.Inspection.invalid("Place a configured Damage Fragment in the fragment slot.");
        }
        return mythicFragmentService.inspect(fragment);
    }

    private void refresh() {
        if (closed || inventory == null) {
            return;
        }

        for (int slot = 0; slot < GUI_SIZE; slot++) {
            if (!isInputSlot(slot) && slot != OUTPUT_SLOT && !isBatchControl(slot)
                    && slot != FORGE_BUTTON_SLOT) {
                inventory.setItem(slot, namedItem(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY));
            }
        }

        ItemStack sword = inventory.getItem(SWORD_SLOT);
        ItemStack fragment = inventory.getItem(FRAGMENT_SLOT);
        MythicFragmentService.Inspection inspection = mythicFragmentService.inspect(fragment);
        int available = availableFragments(inspection, fragment);
        boolean canForge = sword != null && sword.getAmount() == 1
                && heroItemService.isHeroSword(sword)
                && inspection.isValid() && available > 0;

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
                                    ? "Place a stack of fragments."
                                    : inspection.error()));
        }
        refreshBatchControls(available, canForge);
    }

    private int availableFragments(MythicFragmentService.Inspection inspection, ItemStack fragment) {
        return inspection.isValid() && fragment != null ? Math.max(0, fragment.getAmount()) : 0;
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
            ItemStack fragment = inventory.getItem(FRAGMENT_SLOT);
            batchAmount = Math.max(1, availableFragments(mythicFragmentService.inspect(fragment), fragment));
        }
        refresh();
    }

    private void refreshBatchControls(int available, boolean enabled) {
        inventory.setItem(MINUS_TEN_SLOT, namedItem(Material.REDSTONE, "-10",
                enabled ? NamedTextColor.RED : NamedTextColor.DARK_GRAY, "Decrease the forge batch."));
        inventory.setItem(MINUS_ONE_SLOT, namedItem(Material.REDSTONE_TORCH, "-1",
                enabled ? NamedTextColor.RED : NamedTextColor.DARK_GRAY, "Decrease the forge batch."));
        inventory.setItem(PLUS_ONE_SLOT, namedItem(Material.GLOWSTONE_DUST, "+1",
                enabled ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY, "Increase the forge batch."));
        inventory.setItem(PLUS_TEN_SLOT, namedItem(Material.GLOWSTONE, "+10",
                enabled ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY, "Increase the forge batch."));
        inventory.setItem(MAX_SLOT, namedItem(Material.NETHER_STAR, "MAX (" + Math.max(0, available) + ")",
                enabled ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY, "Use the largest available batch."));
    }

    private boolean isInputSlot(int slot) {
        return slot == SWORD_SLOT || slot == FRAGMENT_SLOT;
    }

    private boolean isBatchControl(int slot) {
        return slot == MINUS_TEN_SLOT || slot == MINUS_ONE_SLOT || slot == PLUS_ONE_SLOT
                || slot == PLUS_TEN_SLOT || slot == MAX_SLOT;
    }

    private void scheduleRefresh() {
        Bukkit.getScheduler().runTask(plugin, this::refresh);
    }

    private ItemStack namedItem(Material material, String name, NamedTextColor color, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        if (loreLines.length > 0) {
            meta.lore(Arrays.stream(loreLines)
                    .map(line -> Component.text(line, NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false))
                    .toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    private void returnItem(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        if (player.isOnline()) {
            player.getInventory().addItem(item).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }

    private String formatNumber(double value) {
        return value == Math.rint(value)
                ? String.format(Locale.ROOT, "%.0f", value)
                : String.format(Locale.ROOT, "%.2f", value);
    }
}
