package com.dungeonhero.feature.quest;

import java.util.List;
import java.util.Map;

/** Bukkit configuration adapter for Dungeon Rush settings. */
public record DungeonRushConfiguration(
    boolean enabled,
    List<String> worlds,
    List<String> biomes,
    List<QuestScoringPolicy.QuestType> questTypes,
    int durationMinutes,
    int intervalMinutes,
    int firstDelayMinutes,
    int minimumKills,
    Map<Integer, List<RewardPolicy.Reward>> rewardsByPlace) {}
