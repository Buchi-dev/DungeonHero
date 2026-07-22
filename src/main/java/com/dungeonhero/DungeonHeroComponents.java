package com.dungeonhero;

import com.dungeonhero.command.DungeonHeroCommand;
import com.dungeonhero.common.BukkitPlayerResolver;
import com.dungeonhero.common.ItemDeliveryService;
import com.dungeonhero.common.MythicDeathDeduplicator;
import com.dungeonhero.common.PlayerResolver;
import com.dungeonhero.config.DungeonHeroConfiguration;
import com.dungeonhero.feature.coins.DungeonCoinService;
import com.dungeonhero.feature.forge.ForgeGui;
import com.dungeonhero.feature.mobregistry.MobRegistryService;
import com.dungeonhero.feature.openworlddungeon.OpenWorldDungeonFeature;
import com.dungeonhero.feature.party.PartyService;
import com.dungeonhero.feature.quest.DungeonRushListener;
import com.dungeonhero.feature.quest.DungeonRushService;
import com.dungeonhero.feature.rank.DungeonRankService;
import com.dungeonhero.feature.sword.HeroAscensionService;
import com.dungeonhero.feature.sword.HeroItemService;
import com.dungeonhero.feature.sword.HeroPlayerListener;
import com.dungeonhero.feature.sword.HeroSwordStorage;
import com.dungeonhero.feature.sword.SwordHudScheduler;
import com.dungeonhero.feature.sword.SwordHudService;
import com.dungeonhero.feature.sword.SwordProgressionListener;
import com.dungeonhero.feature.sword.SwordProgressionService;
import com.dungeonhero.feature.sword.SwordXpItemService;
import com.dungeonhero.feature.trainingdummy.TrainingDummyService;
import com.dungeonhero.framework.GameplayFramework;
import com.dungeonhero.framework.objective.DefeatMobsObjective;
import com.dungeonhero.framework.reward.ItemReward;
import com.dungeonhero.gui.GuiManager;
import com.dungeonhero.integration.mythicmobs.HeroDamageProtectionListener;
import com.dungeonhero.integration.mythicmobs.HeroRareDropBonusListener;
import com.dungeonhero.integration.mythicmobs.HeroSwordMobScaler;
import com.dungeonhero.integration.mythicmobs.MythicFragmentService;
import com.dungeonhero.lifecycle.PluginModule;
import com.dungeonhero.lifecycle.PluginModuleRegistry;
import com.dungeonhero.messaging.MessageService;
import org.bukkit.plugin.java.JavaPlugin;

/** Composition root for all DungeonHero services, listeners, and lifecycle ordering. */
public final class DungeonHeroComponents {

  private final JavaPlugin plugin;
  private final PluginModuleRegistry modules;
  private final GameplayFramework gameplayFramework;
  private final MobRegistryService mobRegistryService;
  private final ItemDeliveryService itemDeliveryService;
  private final MythicDeathDeduplicator mythicDeathDeduplicator;
  private final PlayerResolver playerResolver;
  private final HeroItemService heroItemService;
  private final HeroSwordStorage heroSwordStorage;
  private final MythicFragmentService mythicFragmentService;
  private final DungeonCoinService dungeonCoinService;
  private final DungeonRankService dungeonRankService;
  private final HeroAscensionService heroAscensionService;
  private final GuiManager guiManager;
  private final ForgeGui forgeGui;
  private final PartyService partyService;
  private final TrainingDummyService trainingDummyService;
  private final SwordXpItemService swordXpItemService;
  private final SwordProgressionService swordProgressionService;
  private final DungeonRushService dungeonRushService;
  private final DungeonRushListener dungeonRushListener;
  private final HeroSwordMobScaler heroSwordMobScaler;
  private final SwordHudService swordHudService;
  private final SwordHudScheduler swordHudScheduler;
  private final HeroPlayerListener heroPlayerListener;
  private final MessageService messageService;
  private final HeroDamageProtectionListener damageProtectionListener;
  private final HeroRareDropBonusListener rareDropBonusListener;
  private final SwordProgressionListener swordProgressionListener;
  private final DungeonHeroCommand command;
  private DungeonHeroConfiguration configuration;

  public DungeonHeroComponents(JavaPlugin plugin) {
    this.plugin = plugin;
    this.modules = new PluginModuleRegistry(plugin);
    this.configuration = DungeonHeroConfiguration.load(plugin);

    gameplayFramework = new GameplayFramework(plugin);
    gameplayFramework.objectives().register(new DefeatMobsObjective());
    gameplayFramework.rewards().register(new ItemReward());
    gameplayFramework.features().register(new OpenWorldDungeonFeature(plugin));

    mobRegistryService = new MobRegistryService(plugin, configuration);
    itemDeliveryService = new ItemDeliveryService();
    mythicDeathDeduplicator = new MythicDeathDeduplicator();
    playerResolver = new BukkitPlayerResolver();
    heroItemService =
        new HeroItemService(plugin, itemDeliveryService, configuration.fragmentCaps());
    heroSwordStorage = new HeroSwordStorage(plugin, heroItemService);
    mythicFragmentService = new MythicFragmentService(plugin);
    dungeonCoinService = new DungeonCoinService(plugin);
    dungeonRankService =
        new DungeonRankService(plugin, heroItemService, dungeonCoinService, configuration);
    heroAscensionService =
        new HeroAscensionService(
            plugin,
            heroItemService,
            heroSwordStorage,
            dungeonRankService,
            configuration.ascension());
    guiManager = new GuiManager();
    forgeGui =
        new ForgeGui(plugin, guiManager, heroItemService, mythicFragmentService, heroSwordStorage);
    partyService = new PartyService(plugin, playerResolver, configuration.party());
    trainingDummyService =
        new TrainingDummyService(plugin, heroItemService, configuration.trainingDummy());
    swordXpItemService = new SwordXpItemService(plugin, configuration.swordXpItem());
    swordProgressionService =
        new SwordProgressionService(
            plugin,
            heroItemService,
            swordXpItemService,
            dungeonRankService,
            heroSwordStorage,
            mobRegistryService,
            configuration);
    dungeonRushService =
        new DungeonRushService(
            plugin,
            dungeonCoinService,
            swordProgressionService,
            playerResolver,
            itemDeliveryService,
            configuration.dungeonRush());
    dungeonRushListener =
        new DungeonRushListener(plugin, dungeonRushService, mythicDeathDeduplicator);
    heroSwordMobScaler =
        new HeroSwordMobScaler(
            plugin,
            heroItemService,
            partyService,
            dungeonRankService,
            mobRegistryService,
            configuration.mobScaling());
    swordHudService =
        new SwordHudService(plugin, heroItemService, swordProgressionService, configuration.hud());
    swordHudScheduler = new SwordHudScheduler(plugin, swordHudService);
    heroPlayerListener =
        new HeroPlayerListener(plugin, heroItemService, heroSwordStorage, dungeonRankService);
    messageService = new MessageService(plugin);
    damageProtectionListener =
        new HeroDamageProtectionListener(
            plugin, heroItemService, dungeonRankService, configuration.damageProtection());
    rareDropBonusListener =
        new HeroRareDropBonusListener(
            plugin,
            heroAscensionService,
            heroItemService,
            mythicFragmentService,
            mobRegistryService,
            configuration.ascension());
    swordProgressionListener =
        new SwordProgressionListener(plugin, swordProgressionService, mythicDeathDeduplicator);

    command =
        new DungeonHeroCommand(
            plugin,
            heroItemService,
            heroSwordStorage,
            heroPlayerListener,
            mythicFragmentService,
            heroSwordMobScaler,
            swordXpItemService,
            swordHudService,
            swordProgressionService,
            dungeonRankService,
            partyService,
            trainingDummyService,
            forgeGui,
            messageService,
            dungeonCoinService,
            mobRegistryService,
            gameplayFramework,
            dungeonRushService,
            heroAscensionService,
            rareDropBonusListener,
            this::reload);

    registerModules();
  }

  public void load() {
    modules.load();
  }

  public void reload() {
    plugin.reloadConfig();
    configuration = DungeonHeroConfiguration.load(plugin);
    modules.reload();
  }

  public void start() {
    modules.start();
  }

  public void close() {
    modules.close();
  }

  public DungeonHeroCommand command() {
    return command;
  }

  public MobRegistryService mobRegistry() {
    return mobRegistryService;
  }

  public GameplayFramework gameplayFramework() {
    return gameplayFramework;
  }

  private void registerModules() {
    modules.register(
        new PluginModule(
            "framework",
            null,
            null,
            () -> gameplayFramework.reload(configuration.gameplayFeatures()),
            null,
            gameplayFramework::close));
    modules.register(
        new PluginModule(
            "mob-registry",
            null,
            null,
            () -> mobRegistryService.reload(configuration),
            null,
            null));
    modules.register(new PluginModule("coins", null, null, dungeonCoinService::reload, null, null));
    modules.register(
        new PluginModule(
            "messages",
            null,
            null,
            () -> messageService.reload(configuration.locale()),
            null,
            null));
    modules.register(
        new PluginModule(
            "mythic-fragments",
            null,
            null,
            () -> mythicFragmentService.reload(configuration.fragments()),
            null,
            null));
    modules.register(
        new PluginModule(
            "sword-xp-items",
            null,
            null,
            () -> swordXpItemService.reload(configuration.swordXpItem()),
            null,
            null));
    modules.register(
        new PluginModule(
            "rank", null, null, () -> dungeonRankService.reload(configuration), null, null));
    modules.register(
        new PluginModule(
            "party",
            partyService,
            null,
            () -> partyService.reload(configuration.party()),
            null,
            null));
    modules.register(
        new PluginModule(
            "sword-progression",
            swordProgressionListener,
            null,
            () -> swordProgressionService.reload(configuration),
            null,
            null));
    modules.register(
        new PluginModule(
            "ascension",
            null,
            null,
            () -> heroAscensionService.reload(configuration.ascension()),
            null,
            null));
    modules.register(
        new PluginModule(
            "mob-scaling",
            heroSwordMobScaler,
            null,
            () -> heroSwordMobScaler.reload(configuration.mobScaling()),
            null,
            null));
    modules.register(
        new PluginModule(
            "sword-hud",
            swordHudService,
            null,
            () -> swordHudService.reload(configuration.hud()),
            null,
            null));
    modules.register(
        new PluginModule(
            "sword-hud-scheduler",
            null,
            null,
            () -> swordHudScheduler.reload(configuration.hud()),
            swordHudScheduler::start,
            swordHudScheduler::close));
    modules.register(
        new PluginModule(
            "damage-protection",
            damageProtectionListener,
            null,
            () -> damageProtectionListener.reload(configuration.damageProtection()),
            null,
            null));
    modules.register(
        new PluginModule(
            "rare-drop-bonus",
            rareDropBonusListener,
            null,
            () -> rareDropBonusListener.reload(configuration.ascension()),
            null,
            null));
    modules.register(
        new PluginModule(
            "dungeon-rush",
            dungeonRushListener,
            null,
            () -> dungeonRushService.reload(configuration.dungeonRush()),
            dungeonRushService::start,
            dungeonRushService::close));
    modules.register(
        new PluginModule(
            "training-dummy",
            trainingDummyService,
            null,
            () -> trainingDummyService.reload(configuration.trainingDummy()),
            null,
            null));
    modules.register(new PluginModule("hero-player", heroPlayerListener, null, null, null, null));
    modules.register(new PluginModule("gui", guiManager, null, null, null, guiManager::closeAll));
  }
}
