package com.dungeonhero;

import com.dungeonhero.command.DungeonHeroCommand;
import com.dungeonhero.feature.forge.ForgeGui;
import com.dungeonhero.feature.coins.DungeonCoinService;
import com.dungeonhero.feature.party.PartyService;
import com.dungeonhero.feature.rank.DungeonRankService;
import com.dungeonhero.feature.mobregistry.MobRegistryService;
import com.dungeonhero.feature.sword.HeroItemService;
import com.dungeonhero.feature.sword.HeroPlayerListener;
import com.dungeonhero.feature.sword.HeroSwordStorage;
import com.dungeonhero.feature.sword.HeroAscensionService;
import com.dungeonhero.feature.sword.SwordHudService;
import com.dungeonhero.feature.sword.SwordProgressionService;
import com.dungeonhero.feature.sword.SwordXpItemService;
import com.dungeonhero.feature.trainingdummy.TrainingDummyService;
import com.dungeonhero.feature.quest.DungeonRushService;
import com.dungeonhero.integration.mythicmobs.HeroSwordMobScaler;
import com.dungeonhero.integration.mythicmobs.MythicFragmentService;
import com.dungeonhero.integration.mythicmobs.HeroDamageProtectionListener;
import com.dungeonhero.integration.mythicmobs.HeroRareDropBonusListener;
import com.dungeonhero.gui.GuiManager;
import com.dungeonhero.messaging.MessageService;
import com.dungeonhero.feature.openworlddungeon.OpenWorldDungeonFeature;
import com.dungeonhero.framework.GameplayFramework;
import com.dungeonhero.framework.objective.DefeatMobsObjective;
import com.dungeonhero.framework.reward.ItemReward;
import org.bukkit.plugin.java.JavaPlugin;

/** Plugin bootstrap and dependency composition root. */
public final class DungeonHeroPlugin extends JavaPlugin {

    private GuiManager guiManager;
    private DungeonRushService dungeonRushService;
    private MobRegistryService mobRegistryService;
    private GameplayFramework gameplayFramework;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("mob-registry.yml", false);

        gameplayFramework = new GameplayFramework(this);
        gameplayFramework.objectives().register(new DefeatMobsObjective());
        gameplayFramework.rewards().register(new ItemReward());
        gameplayFramework.features().register(new OpenWorldDungeonFeature(this));
        gameplayFramework.reload(getConfig().getConfigurationSection("DungeonHero.Gameplay.Features"));

        mobRegistryService = new MobRegistryService(this);

        HeroItemService heroItemService = new HeroItemService(this);
        HeroSwordStorage heroSwordStorage = new HeroSwordStorage(this, heroItemService);
        MythicFragmentService mythicFragmentService = new MythicFragmentService(this);
        DungeonCoinService dungeonCoinService = new DungeonCoinService(this);
        DungeonRankService dungeonRankService = new DungeonRankService(this, heroItemService, dungeonCoinService);
        HeroAscensionService heroAscensionService = new HeroAscensionService(this, heroItemService,
                heroSwordStorage, dungeonRankService);
        guiManager = new GuiManager();
        ForgeGui forgeGui = new ForgeGui(this, guiManager, heroItemService,
                mythicFragmentService, heroSwordStorage);
        PartyService partyService = new PartyService(this);
        TrainingDummyService trainingDummyService = new TrainingDummyService(this, heroItemService);
        SwordXpItemService swordXpItemService = new SwordXpItemService(this);
        SwordProgressionService swordProgressionService = new SwordProgressionService(this, heroItemService,
                swordXpItemService, dungeonRankService, heroSwordStorage, mobRegistryService);
        dungeonRushService = new DungeonRushService(this, dungeonCoinService, swordProgressionService);
        HeroSwordMobScaler heroSwordMobScaler = new HeroSwordMobScaler(this, heroItemService, partyService,
                dungeonRankService, mobRegistryService);
        SwordHudService swordHudService = new SwordHudService(this, heroItemService, swordProgressionService);
        HeroPlayerListener heroPlayerListener = new HeroPlayerListener(this, heroItemService, heroSwordStorage,
                dungeonRankService);
        MessageService messageService = new MessageService(this);

        getServer().getPluginManager().registerEvents(heroPlayerListener, this);
        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(swordProgressionService, this);
        getServer().getPluginManager().registerEvents(heroSwordMobScaler, this);
        getServer().getPluginManager().registerEvents(
                new HeroDamageProtectionListener(this, heroItemService, dungeonRankService), this);
        HeroRareDropBonusListener rareDropBonusListener = new HeroRareDropBonusListener(this, heroAscensionService,
                heroItemService, mythicFragmentService, mobRegistryService);
        getServer().getPluginManager().registerEvents(rareDropBonusListener, this);
        getServer().getPluginManager().registerEvents(swordHudService, this);
        getServer().getPluginManager().registerEvents(partyService, this);
        getServer().getPluginManager().registerEvents(dungeonRushService, this);
        getServer().getPluginManager().registerEvents(trainingDummyService, this);
        dungeonRushService.start();

        long hudUpdateTicks = Math.max(1, getConfig().getLong("DungeonHero.Hud.UpdateTicks", 10));
        getServer().getScheduler().runTaskTimer(this, swordHudService::syncOnlinePlayers,
                hudUpdateTicks, hudUpdateTicks);
        DungeonHeroCommand command = new DungeonHeroCommand(this, heroItemService, heroSwordStorage,
                heroPlayerListener, mythicFragmentService, heroSwordMobScaler,
                swordXpItemService, swordHudService, swordProgressionService, dungeonRankService, partyService,
                trainingDummyService, forgeGui,
                messageService, dungeonCoinService, mobRegistryService,
                gameplayFramework, dungeonRushService, heroAscensionService, rareDropBonusListener);
        if (getCommand("dungeonhero") != null) {
            getCommand("dungeonhero").setExecutor(command);
            getCommand("dungeonhero").setTabCompleter(command);
        }

        getLogger().info("DungeonHero enabled. Hero Sword, Forge, party, rank, Dungeon Rush, and MythicMobs systems are ready.");
    }

    /** Public integration point for other plugins that create MythicMobs. */
    public MobRegistryService getMobRegistry() {
        return mobRegistryService;
    }

    /** Public API root for registering future objectives, actions, rewards, and features. */
    public GameplayFramework getGameplayFramework() {
        return gameplayFramework;
    }

    @Override
    public void onDisable() {
        if (gameplayFramework != null) {
            gameplayFramework.close();
        }
        if (dungeonRushService != null) {
            dungeonRushService.close();
        }
        if (guiManager != null) {
            guiManager.closeAll();
        }
    }
}
