package com.dungeonhero.integration.vault;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class VaultEconomyService {

    private final JavaPlugin plugin;

    public VaultEconomyService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Economy getEconomy() {
        RegisteredServiceProvider<Economy> registration =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }

    public boolean isAvailable() {
        return getEconomy() != null;
    }

    public double getBalance(Player player) {
        Economy economy = getEconomy();
        return economy == null ? 0 : economy.getBalance(player);
    }

    public boolean withdraw(Player player, double amount) {
        Economy economy = getEconomy();
        if (economy == null || amount < 0 || !economy.has(player, amount)) {
            return false;
        }
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public String format(double amount) {
        if (amount == Math.rint(amount)) {
            return String.format(java.util.Locale.ROOT, "%,.0f", amount);
        }
        return String.format(java.util.Locale.ROOT, "%,.2f", amount);
    }
}
