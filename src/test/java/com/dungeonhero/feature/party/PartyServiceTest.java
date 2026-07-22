package com.dungeonhero.feature.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dungeonhero.TestFixtures;
import java.nio.file.Files;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

class PartyServiceTest {

  @Test
  void leaderCanInviteAndAcceptMembersUpToTheConfiguredPartySize() throws Exception {
    JavaPlugin plugin = TestFixtures.plugin(Files.createTempDirectory("dungeonhero-party"));
    PartyService parties = new PartyService(plugin);
    Player leader = TestFixtures.player(UUID.randomUUID(), "Leader");
    Player member = TestFixtures.player(UUID.randomUUID(), "Member");

    assertEquals(PartyService.ActionStatus.SUCCESS, parties.create(leader).status());
    assertEquals(PartyService.ActionStatus.SUCCESS, parties.invite(leader, member).status());
    PartyService.ActionResult accepted = parties.accept(member);

    assertEquals(PartyService.ActionStatus.SUCCESS, accepted.status());
    assertEquals(2, accepted.party().members().size());
    assertEquals(leader.getUniqueId(), accepted.party().leader());
  }

  @Test
  void leavingTransfersLeadershipAndDisbandingRemovesTheParty() throws Exception {
    JavaPlugin plugin = TestFixtures.plugin(Files.createTempDirectory("dungeonhero-party-leave"));
    PartyService parties = new PartyService(plugin);
    Player leader = TestFixtures.player(UUID.randomUUID(), "Leader");
    Player member = TestFixtures.player(UUID.randomUUID(), "Member");

    parties.create(leader);
    parties.invite(leader, member);
    parties.accept(member);

    PartyService.ActionResult left = parties.leave(leader);
    assertEquals(PartyService.ActionStatus.SUCCESS, left.status());
    assertEquals(member.getUniqueId(), parties.getParty(member).leader());

    assertEquals(PartyService.ActionStatus.SUCCESS, parties.disband(member).status());
    assertTrue(parties.getParty(member) == null);
  }

  @Test
  void rejectsSelfInvitesAndInvitesWithoutAnExistingParty() throws Exception {
    JavaPlugin plugin = TestFixtures.plugin(Files.createTempDirectory("dungeonhero-party-failure"));
    PartyService parties = new PartyService(plugin);
    Player leader = TestFixtures.player(UUID.randomUUID(), "Leader");
    Player member = TestFixtures.player(UUID.randomUUID(), "Member");

    assertEquals(PartyService.ActionStatus.NO_PARTY, parties.invite(leader, member).status());
    parties.create(leader);
    assertEquals(
        PartyService.ActionStatus.CANNOT_INVITE_SELF, parties.invite(leader, leader).status());
  }
}
