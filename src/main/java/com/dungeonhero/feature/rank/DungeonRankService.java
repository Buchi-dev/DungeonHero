package com.dungeonhero.feature.rank;

import com.dungeonhero.feature.sword.HeroItemService;
import com.dungeonhero.feature.coins.DungeonCoinService;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DungeonRankService {

    private final JavaPlugin plugin;
    private final HeroItemService heroItemService;
    private final DungeonCoinService coinService;
    private final NamespacedKey rankKey;
    private final List<RankDefinition> ranks = new ArrayList<>();
    private String coinName;

    public DungeonRankService(JavaPlugin plugin, HeroItemService heroItemService, DungeonCoinService coinService) {
        this.plugin = plugin;
        this.heroItemService = heroItemService;
        this.coinService = coinService;
        this.rankKey = new NamespacedKey(plugin, "dungeon_rank");
        reload();
    }

    public void reload() {
        ranks.clear();
        coinName = plugin.getConfig().getString("DungeonHero.Ranks.CoinName", "Dungeon Coins");
        ConfigurationSection configuredRanks = plugin.getConfig().getConfigurationSection("DungeonHero.Ranks.List");
        if (configuredRanks != null) {
            for (String key : configuredRanks.getKeys(false)) {
                ConfigurationSection rank = configuredRanks.getConfigurationSection(key);
                if (rank == null) {
                    continue;
                }
                try {
                    int number = Integer.parseInt(key);
                    ranks.add(new RankDefinition(
                            number,
                            rank.getString("Name", "Rank " + number),
                            Math.max(1, rank.getInt("RequiredSwordLevel", number == 1 ? 1 : 10)),
                            Math.max(1, rank.getInt("SwordLevelCap", 100)),
                            Math.max(0, rank.getLong("Cost", 0))));
                } catch (NumberFormatException ignored) {
                    plugin.getLogger().warning("Ignoring invalid DungeonHero rank key: " + key);
                }
            }
        }
        if (ranks.isEmpty()) {
            ranks.addAll(defaultRanks());
        }
        ranks.sort(Comparator.comparingInt(RankDefinition::number));
    }

    public int getRank(Player player) {
        Integer storedRank = player.getPersistentDataContainer().get(rankKey, PersistentDataType.INTEGER);
        int rank = storedRank == null ? 1 : storedRank;
        return Math.max(1, Math.min(getHighestRank(), rank));
    }

    public RankDefinition getCurrentRank(Player player) {
        return getRankDefinition(getRank(player));
    }

    public RankDefinition getNextRank(Player player) {
        int current = getRank(player);
        return current >= getHighestRank() ? null : getRankDefinition(current + 1);
    }

    public int getSwordLevelCap(Player player) {
        int configuredCap = plugin.getConfig().getInt("DungeonHero.Progression.MaxSwordLevel", 100);
        return Math.min(configuredCap, getCurrentRank(player).swordLevelCap());
    }

    /** Revalidates permanent unlocked access without downgrading a player's stored rank. */
    public int recalculateRank(Player player) {
        int current = getRank(player);
        player.getPersistentDataContainer().set(rankKey, PersistentDataType.INTEGER, current);
        return current;
    }

    public long getBalance(Player player) {
        return coinService.getBalance(player.getUniqueId());
    }

    public String getCoinName() {
        return coinName;
    }

    public String formatCoins(long amount) {
        return coinService.format(amount);
    }

    public RankUpResult rankUp(Player player) {
        RankDefinition current = getCurrentRank(player);
        RankDefinition next = getNextRank(player);
        if (next == null) {
            return new RankUpResult(RankUpStatus.MAX_RANK, current, null, 0, 0, 0, getBalance(player));
        }
        ItemStack sword = heroItemService.findStrongestHeroSword(player);
        if (!heroItemService.isHeroSword(sword)) {
            return new RankUpResult(RankUpStatus.NO_SWORD, current, next,
                    next.requiredSwordLevel(), 0, next.cost(), getBalance(player));
        }
        int swordLevel = heroItemService.getSwordLevel(sword);
        if (swordLevel < next.requiredSwordLevel()) {
            return new RankUpResult(RankUpStatus.SWORD_LEVEL, current, next,
                    next.requiredSwordLevel(), swordLevel, next.cost(), getBalance(player));
        }
        long balance = getBalance(player);
        if (balance < next.cost()) {
            return new RankUpResult(RankUpStatus.INSUFFICIENT_FUNDS, current, next,
                    next.requiredSwordLevel(), swordLevel, next.cost(), balance);
        }
        if (!coinService.withdraw(player.getUniqueId(), next.cost())) {
            return new RankUpResult(RankUpStatus.PAYMENT_FAILED, current, next,
                    next.requiredSwordLevel(), swordLevel, next.cost(), getBalance(player));
        }

        player.getPersistentDataContainer().set(rankKey, PersistentDataType.INTEGER, next.number());
        return new RankUpResult(RankUpStatus.SUCCESS, current, next,
                next.requiredSwordLevel(), swordLevel, next.cost(), getBalance(player));
    }

    private int getHighestRank() {
        return ranks.stream().mapToInt(RankDefinition::number).max().orElse(1);
    }

    private RankDefinition getRankDefinition(int number) {
        return ranks.stream().filter(rank -> rank.number() == number).findFirst().orElse(ranks.getFirst());
    }

    private List<RankDefinition> defaultRanks() {
        List<RankDefinition> defaults = new ArrayList<>();
        String[] names = {"Novice", "Apprentice", "Adventurer", "Warrior", "Elite",
                "Champion", "Master", "Grandmaster", "Legend", "Hero"};
        for (int number = 1; number <= 10; number++) {
            defaults.add(new RankDefinition(number, names[number - 1],
                    number == 1 ? 1 : (number - 1) * 10,
                    number * 10,
                    number == 1 ? 0 : (long) Math.pow(2, number + 5)));
        }
        return defaults;
    }

    public record RankDefinition(int number, String name, int requiredSwordLevel,
                                 int swordLevelCap, long cost) {
    }

    public enum RankUpStatus {
        SUCCESS,
        MAX_RANK,
        NO_SWORD,
        SWORD_LEVEL,
        INSUFFICIENT_FUNDS,
        PAYMENT_FAILED
    }

    public record RankUpResult(RankUpStatus status, RankDefinition current, RankDefinition next,
                               int requiredSwordLevel, int actualSwordLevel,
                               long cost, long balance) {
    }
}
