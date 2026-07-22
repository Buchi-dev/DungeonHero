package com.dungeonhero.feature.party;

import com.dungeonhero.common.BukkitPlayerResolver;
import com.dungeonhero.common.PlayerResolver;
import com.dungeonhero.config.DungeonHeroConfiguration;
import com.dungeonhero.feature.sword.HeroItemService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PartyService implements Listener {

  private final JavaPlugin plugin;
  private final PlayerResolver playerResolver;
  private final Map<UUID, Party> parties = new HashMap<>();
  private final Map<UUID, Invitation> invitations = new HashMap<>();
  private boolean enabled;
  private int maxSize;
  private boolean requireSameWorld;
  private long invitationDurationMillis;

  public PartyService(JavaPlugin plugin) {
    this(plugin, new BukkitPlayerResolver());
  }

  public PartyService(JavaPlugin plugin, PlayerResolver playerResolver) {
    this(plugin, playerResolver, DungeonHeroConfiguration.load(plugin).party());
  }

  public PartyService(
      JavaPlugin plugin,
      PlayerResolver playerResolver,
      DungeonHeroConfiguration.Party configuration) {
    this.plugin = plugin;
    this.playerResolver = playerResolver;
    reload(configuration);
  }

  public void reload() {
    reload(DungeonHeroConfiguration.load(plugin).party());
  }

  public void reload(DungeonHeroConfiguration.Party configuration) {
    enabled = configuration.enabled();
    maxSize = configuration.maxSize();
    requireSameWorld = configuration.requireSameWorld();
    invitationDurationMillis = configuration.invitationSeconds() * 1000L;
    if (!enabled) {
      parties.clear();
      invitations.clear();
    }
  }

  public ActionResult create(Player player) {
    if (!enabled) {
      return failure(ActionStatus.PARTY_DISABLED, null);
    }
    if (getParty(player) != null) {
      return failure(ActionStatus.ALREADY_IN_PARTY, getParty(player));
    }
    Party party = new Party(player.getUniqueId());
    party.members().add(player.getUniqueId());
    parties.put(party.id(), party);
    return success(party);
  }

  public ActionResult invite(Player inviter, Player target) {
    if (!enabled) {
      return failure(ActionStatus.PARTY_DISABLED, null);
    }
    Party party = getParty(inviter);
    if (party == null) {
      return failure(ActionStatus.NO_PARTY, null);
    }
    if (!party.leader().equals(inviter.getUniqueId())) {
      return failure(ActionStatus.NOT_LEADER, party);
    }
    if (target == null) {
      return failure(ActionStatus.TARGET_NOT_FOUND, party);
    }
    if (target.getUniqueId().equals(inviter.getUniqueId())) {
      return failure(ActionStatus.CANNOT_INVITE_SELF, party);
    }
    if (getParty(target) != null) {
      return failure(ActionStatus.TARGET_IN_PARTY, party);
    }
    if (party.members().size() >= maxSize) {
      return failure(ActionStatus.PARTY_FULL, party);
    }
    invitations.put(
        target.getUniqueId(),
        new Invitation(
            party.id(),
            inviter.getUniqueId(),
            System.currentTimeMillis() + invitationDurationMillis));
    return new ActionResult(ActionStatus.SUCCESS, party, target, inviter.getName());
  }

  public ActionResult accept(Player player) {
    if (!enabled) {
      return failure(ActionStatus.PARTY_DISABLED, null);
    }
    Invitation invitation = invitations.remove(player.getUniqueId());
    if (invitation == null) {
      return failure(ActionStatus.NO_INVITATION, null);
    }
    if (invitation.expiresAt() < System.currentTimeMillis()) {
      return failure(ActionStatus.INVITATION_EXPIRED, null);
    }
    if (getParty(player) != null) {
      return failure(ActionStatus.ALREADY_IN_PARTY, getParty(player));
    }
    Party party = parties.get(invitation.partyId());
    if (party == null) {
      return failure(ActionStatus.PARTY_CLOSED, null);
    }
    if (party.members().size() >= maxSize) {
      return failure(ActionStatus.PARTY_FULL, party);
    }
    party.members().add(player.getUniqueId());
    return new ActionResult(ActionStatus.SUCCESS, party, player, null);
  }

  public ActionResult leave(Player player) {
    Party party = getParty(player);
    if (party == null) {
      return failure(ActionStatus.NO_PARTY, null);
    }
    party.members().remove(player.getUniqueId());
    if (party.members().isEmpty()) {
      parties.remove(party.id());
    } else if (party.leader().equals(player.getUniqueId())) {
      party.setLeader(party.members().iterator().next());
    }
    return new ActionResult(ActionStatus.SUCCESS, party, player, null);
  }

  public ActionResult kick(Player leader, Player target) {
    if (!enabled) {
      return failure(ActionStatus.PARTY_DISABLED, null);
    }
    Party party = getParty(leader);
    if (party == null) {
      return failure(ActionStatus.NO_PARTY, null);
    }
    if (!party.leader().equals(leader.getUniqueId())) {
      return failure(ActionStatus.NOT_LEADER, party);
    }
    if (target == null || !party.members().contains(target.getUniqueId())) {
      return failure(ActionStatus.TARGET_NOT_IN_PARTY, party);
    }
    if (target.getUniqueId().equals(leader.getUniqueId())) {
      return failure(ActionStatus.CANNOT_KICK_LEADER, party);
    }
    party.members().remove(target.getUniqueId());
    return new ActionResult(ActionStatus.SUCCESS, party, target, null);
  }

  public ActionResult disband(Player leader) {
    if (!enabled) {
      return failure(ActionStatus.PARTY_DISABLED, null);
    }
    Party party = getParty(leader);
    if (party == null) {
      return failure(ActionStatus.NO_PARTY, null);
    }
    if (!party.leader().equals(leader.getUniqueId())) {
      return failure(ActionStatus.NOT_LEADER, party);
    }
    parties.remove(party.id());
    return new ActionResult(ActionStatus.SUCCESS, party, leader, null);
  }

  public Party getParty(Player player) {
    for (Party party : parties.values()) {
      if (party.members().contains(player.getUniqueId())) {
        return party;
      }
    }
    return null;
  }

  public List<Player> getMembers(Party party) {
    if (party == null) {
      return List.of();
    }
    List<Player> members = new ArrayList<>();
    for (UUID memberId : party.members()) {
      Player member = playerResolver.resolve(memberId);
      if (member != null) {
        members.add(member);
      }
    }
    return members;
  }

  public List<Player> findScalingPlayers(
      Location location, double radius, HeroItemService heroItemService) {
    World world = location.getWorld();
    if (world == null) {
      return List.of();
    }

    double radiusSquared = radius * radius;
    Player nearest = null;
    double nearestDistance = Double.MAX_VALUE;
    for (Player player : world.getPlayers()) {
      if (!heroItemService.isHeroSword(heroItemService.findStrongestHeroSword(player))) {
        continue;
      }
      double distance = player.getLocation().distanceSquared(location);
      if (distance <= radiusSquared && distance < nearestDistance) {
        nearest = player;
        nearestDistance = distance;
      }
    }
    if (nearest == null) {
      return List.of();
    }

    Party party = enabled ? getParty(nearest) : null;
    if (party == null) {
      return List.of(nearest);
    }

    List<Player> scalingPlayers = new ArrayList<>();
    for (Player member : getMembers(party)) {
      if (requireSameWorld && !member.getWorld().equals(world)) {
        continue;
      }
      if (member.getLocation().distanceSquared(location) > radiusSquared) {
        continue;
      }
      if (heroItemService.isHeroSword(heroItemService.findStrongestHeroSword(member))) {
        scalingPlayers.add(member);
      }
    }
    return scalingPlayers;
  }

  public int getMaxSize() {
    return maxSize;
  }

  public void broadcast(Party party, Component message) {
    for (Player member : getMembers(party)) {
      member.sendMessage(message);
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    invitations.remove(event.getPlayer().getUniqueId());
    leave(event.getPlayer());
  }

  private ActionResult success(Party party) {
    return new ActionResult(ActionStatus.SUCCESS, party, null, null);
  }

  private ActionResult failure(ActionStatus status, Party party) {
    return new ActionResult(status, party, null, null);
  }

  public static final class Party {
    private final UUID id;
    private final LinkedHashSet<UUID> members;
    private UUID leader;

    public Party(UUID leader) {
      this.id = UUID.randomUUID();
      this.members = new LinkedHashSet<>();
      this.leader = leader;
    }

    public UUID id() {
      return id;
    }

    public LinkedHashSet<UUID> members() {
      return members;
    }

    public UUID leader() {
      return leader;
    }

    public void setLeader(UUID newLeader) {
      this.leader = newLeader;
    }
  }

  public enum ActionStatus {
    SUCCESS,
    PARTY_DISABLED,
    ALREADY_IN_PARTY,
    NO_PARTY,
    NOT_LEADER,
    TARGET_NOT_FOUND,
    TARGET_IN_PARTY,
    PARTY_FULL,
    CANNOT_INVITE_SELF,
    NO_INVITATION,
    INVITATION_EXPIRED,
    PARTY_CLOSED,
    TARGET_NOT_IN_PARTY,
    CANNOT_KICK_LEADER
  }

  public record ActionResult(ActionStatus status, Party party, Player target, String inviterName) {}

  private record Invitation(UUID partyId, UUID inviterId, long expiresAt) {}
}
