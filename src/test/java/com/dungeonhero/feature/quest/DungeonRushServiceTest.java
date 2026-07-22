package com.dungeonhero.feature.quest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dungeonhero.TestFixtures;
import com.dungeonhero.config.DungeonHeroConfiguration;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DungeonRushServiceTest {

  @Test
  void disabledQuestReportsItsConfiguredStateWithoutStartingAScheduler() throws Exception {
    var dataFolder = Files.createTempDirectory("dungeonhero-quest-disabled");
    JavaPlugin plugin = TestFixtures.plugin(dataFolder);
    plugin.getConfig().set("DungeonHero.DungeonRush.Enabled", false);
    Player player = TestFixtures.player(UUID.randomUUID(), "QuestHero");

    DungeonRushService service = new DungeonRushService(plugin, null, null);
    service.sendStatus(player);

    verify(player).sendMessage(any(Component.class));
  }

  @Test
  void activeQuestCountsEligibleVanillaKillsOnTheLeaderboard() throws Exception {
    var dataFolder = Files.createTempDirectory("dungeonhero-quest-active");
    Server server = mock(Server.class);
    BukkitScheduler scheduler = mock(BukkitScheduler.class);
    BukkitTask task = mock(BukkitTask.class);
    when(server.getScheduler()).thenReturn(scheduler);
    when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);
    JavaPlugin configuredPlugin = TestFixtures.plugin(dataFolder, server);
    configuredPlugin
        .getConfig()
        .set("DungeonHero.DungeonRush.QuestTypes", java.util.List.of("MOST_DUNGEON_MOBS_KILLED"));
    DungeonRushService service = new DungeonRushService(configuredPlugin, null, null);
    service.start();
    ArgumentCaptor<Runnable> tickCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduler).runTaskTimer(any(), tickCaptor.capture(), anyLong(), anyLong());
    tickCaptor.getValue();

    Player killer = TestFixtures.player(UUID.randomUUID(), "QuestHero");
    World world = mock(World.class);
    Location location = mock(Location.class);
    when(location.getWorld()).thenReturn(world);
    when(world.getName()).thenReturn("dungeon_world");

    DungeonRushRoundState state = roundState(service);
    state.start(
        System.currentTimeMillis(),
        DungeonHeroConfiguration.load(configuredPlugin).dungeonRush(),
        new java.util.Random(0));
    service.recordKill(killer, location, QuestScoringPolicy.KillType.DUNGEON);
    service.sendStatus(killer);

    ArgumentCaptor<Component> messageCaptor = ArgumentCaptor.forClass(Component.class);
    verify(killer, atLeastOnce()).sendMessage(messageCaptor.capture());
    String messages =
        messageCaptor.getAllValues().stream()
            .map(PlainTextComponentSerializer.plainText()::serialize)
            .reduce("", (left, right) -> left + "\n" + right);
    assertTrue(messages.contains("QuestHero - 1 kills"));
  }

  private static DungeonRushRoundState roundState(DungeonRushService service) throws Exception {
    Field field = service.getClass().getDeclaredField("roundState");
    field.setAccessible(true);
    return (DungeonRushRoundState) field.get(service);
  }
}
