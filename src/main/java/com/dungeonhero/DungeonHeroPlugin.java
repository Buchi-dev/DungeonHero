package com.dungeonhero;

import com.dungeonhero.command.DungeonHeroCommand;
import com.dungeonhero.feature.forge.ForgeMenu;
import com.dungeonhero.feature.coins.DungeonCoinService;
import com.dungeonhero.feature.party.PartyService;
import com.dungeonhero.feature.rank.DungeonRankService;
import com.dungeonhero.feature.sword.HeroItemService;
import com.dungeonhero.feature.sword.HeroPlayerListener;
import com.dungeonhero.feature.sword.HeroSwordStorage;
import com.dungeonhero.feature.sword.SwordHudService;
import com.dungeonhero.feature.sword.SwordProgressionService;
import com.dungeonhero.feature.sword.SwordXpItemService;
import com.dungeonhero.feature.trainingdummy.TrainingDummyService;
import com.dungeonhero.integration.mythicmobs.HeroSwordMobScaler;
import com.dungeonhero.integration.mythicmobs.MythicFragmentService;
import com.dungeonhero.messaging.MessageService;
import org.bukkit.plugin.java.JavaPlugin;

/** Plugin bootstrap and dependency composition root. */
public final class DungeonHeroPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        HeroItemService heroItemService = new HeroItemService(this);
        HeroSwordStorage heroSwordStorage = new HeroSwordStorage(this, heroItemService);
        MythicFragmentService mythicFragmentService = new MythicFragmentService(this);
        DungeonCoinService dungeonCoinService = new DungeonCoinService(this);
        DungeonRankService dungeonRankService = new DungeonRankService(this, heroItemService, dungeonCoinService);
        PartyService partyService = new PartyService(this);
        TrainingDummyService trainingDummyService = new TrainingDummyService(this, heroItemService);
        SwordXpItemService swordXpItemService = new SwordXpItemService(this);
        SwordProgressionService swordProgressionService = new SwordProgressionService(this, heroItemService,
                swordXpItemService, dungeonRankService, heroSwordStorage);
        HeroSwordMobScaler heroSwordMobScaler = new HeroSwordMobScaler(this, heroItemService, partyService,
                dungeonRankService);
        SwordHudService swordHudService = new SwordHudService(this, heroItemService, swordProgressionService);
        HeroPlayerListener heroPlayerListener = new HeroPlayerListener(this, heroItemService, heroSwordStorage);
        MessageService messageService = new MessageService(this);

        getServer().getPluginManager().registerEvents(heroPlayerListener, this);
        getServer().getPluginManager().registerEvents(new ForgeMenu.Listener(this), this);
        getServer().getPluginManager().registerEvents(swordProgressionService, this);
        getServer().getPluginManager().registerEvents(heroSwordMobScaler, this);
        getServer().getPluginManager().registerEvents(swordHudService, this);
        getServer().getPluginManager().registerEvents(partyService, this);
        getServer().getPluginManager().registerEvents(trainingDummyService, this);

        long hudUpdateTicks = Math.max(1, getConfig().getLong("DungeonHero.Hud.UpdateTicks", 10));
        getServer().getScheduler().runTaskTimer(this, swordHudService::syncOnlinePlayers,
                hudUpdateTicks, hudUpdateTicks);
        DungeonHeroCommand command = new DungeonHeroCommand(this, heroItemService, heroSwordStorage,
                heroPlayerListener, mythicFragmentService, heroSwordMobScaler,
                swordXpItemService, swordHudService, swordProgressionService, dungeonRankService, partyService,
                trainingDummyService,
                messageService, dungeonCoinService);
        if (getCommand("dungeonhero") != null) {
            getCommand("dungeonhero").setExecutor(command);
            getCommand("dungeonhero").setTabCompleter(command);
        }

        getLogger().info("DungeonHero enabled. Hero Sword, Forge, party, rank, and MythicMobs systems are ready.");
    }
}
