package com.dungeonhero.command;

import com.dungeonhero.feature.coins.DungeonCoinService;
import com.dungeonhero.feature.forge.ForgeGui;
import com.dungeonhero.feature.mobregistry.MobRegistryService;
import com.dungeonhero.feature.party.PartyService;
import com.dungeonhero.feature.quest.DungeonRushService;
import com.dungeonhero.feature.rank.DungeonRankService;
import com.dungeonhero.feature.sword.HeroAscensionService;
import com.dungeonhero.feature.sword.HeroItemService;
import com.dungeonhero.feature.sword.HeroPlayerListener;
import com.dungeonhero.feature.sword.HeroSwordStorage;
import com.dungeonhero.feature.sword.SwordHudService;
import com.dungeonhero.feature.sword.SwordProgressionService;
import com.dungeonhero.feature.sword.SwordXpItemService;
import com.dungeonhero.feature.trainingdummy.TrainingDummyService;
import com.dungeonhero.framework.GameplayFramework;
import com.dungeonhero.integration.mythicmobs.HeroRareDropBonusListener;
import com.dungeonhero.integration.mythicmobs.HeroSwordMobScaler;
import com.dungeonhero.integration.mythicmobs.MythicFragmentService;
import com.dungeonhero.messaging.MessageService;
import org.bukkit.plugin.java.JavaPlugin;

/** Immutable dependency bundle shared by command handlers. */
public record DungeonHeroCommandContext(
    JavaPlugin plugin,
    HeroItemService heroItemService,
    HeroSwordStorage heroSwordStorage,
    HeroPlayerListener heroPlayerListener,
    MythicFragmentService mythicFragmentService,
    HeroSwordMobScaler heroSwordMobScaler,
    SwordXpItemService swordXpItemService,
    SwordHudService swordHudService,
    SwordProgressionService swordProgressionService,
    DungeonRankService dungeonRankService,
    PartyService partyService,
    TrainingDummyService trainingDummyService,
    ForgeGui forgeGui,
    MessageService messageService,
    DungeonCoinService dungeonCoinService,
    MobRegistryService mobRegistryService,
    GameplayFramework gameplayFramework,
    DungeonRushService dungeonRushService,
    HeroAscensionService heroAscensionService,
    HeroRareDropBonusListener rareDropBonusListener,
    Runnable reloadModules) {}
