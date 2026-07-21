# DungeonHero

Starting point for a dungeon adventure plugin built for Paper 26.1.2.

## Requirements

- Java 25
- Paper 26.1.2 (API build 72)
- MythicMobs 5.12.1

## Build

On Windows:

```powershell
.\gradlew.bat build
```

The plugin JAR is generated at `build/libs/DungeonHero-1.7.5.jar`.

Copy that JAR into the server's `plugins` directory and restart the server. Once loaded, `/dungeonhero` (or `/dh`) confirms that the plugin is active.

## Install the Crypt of the Fallen content pack

The repository includes a ready-to-install `Dungeon_world` content pack. It
adds Hero Sword XP and Damage Fragment items, four Crypt encounters, drop
tables, and the boss skills used by the `dungeon_world` run. Build first, then
run this from the project root:

```powershell
.\gradlew.bat build
.\tools\Install-DungeonHero.ps1
```

The installer targets `D:\Minecraft FIles (Server)\New` by default, installs
MythicMobs 5.12.1 from the supplied parent folder when it is
not already present, and copies only DungeonHero-owned MythicMobs files. It
does not overwrite DungeonCore content. Restart the server and run `/mm reload`
after installation.

### Dungeon_world gameplay loop

Cryptlings and Bone Archers are the normal wave, Soul Reavers are the elite
encounter, and the Crypt Lord is the boss. Player-killed mobs automatically
grant Sword XP to the killer's strongest Hero Sword. `HeroDamageFragment` is the forge
material and adds +2 damage per infusion. The boss enters a soul phase below
55% health and summons reinforcements.

DungeonCore should remain the authoritative world spawner and reward owner.
Use the MythicMobs spawn command above for a smoke test, or register the `DW_HERO_`
IDs in a DungeonCore pool if your DungeonCore build supports Mythic provider
entries. This keeps DungeonCore rewards and DungeonHero progression from being
paid twice.

## Current features

- Players receive one unique Hero Sword when they join.
- The Hero Sword cannot be dropped and is restored after death.
- `/dungeonhero forge` (or `/dh forge`) opens the Hero Forge.
- `/dh help` lists the available commands.
- `/dh reload` reloads the plugin configuration (admin permission required).
- `/dh give <player> mm:<item-id>` gives a configured MythicMobs fragment (admin permission required).
- `/dh dummy remove` removes DungeonHero Training Dummies in the current world (admin permission required).
- `/dh dummy remove-all` removes all DungeonHero Training Dummies in loaded worlds (admin permission required; console-safe).
- `/dh sword` shows the Hero Sword level and XP.
- `/dh prestige` resets a capped Hero Sword to Level 1 and increases its permanent Prestige.
- `/dh rank` shows Dungeon Rank, Sword Level cap, and the Dungeon Coin balance.
- `/dh balance` shows the sender's independent Dungeon Coin balance.
- `/dh transfer <player> <amount>` transfers Dungeon Coins to another player.
- `/dh rankup` spends Dungeon Coins to increase Dungeon Rank.
- `/dh reputation` shows shared Dungeon Reputation, rank, and public event status.
- `/dh reputation contract` shows the player's daily biome contract.
- `/dh reputation top` shows the weekly Dungeon contributor leaderboard.
- `/dh party` opens the party command help for parties of up to 5 players.
- Player-facing rank, sword, help, and rank-up messages use formatted Adventure panels.
- Hero Sword lore is grouped into progression, power, and forge sections.
- The vanilla Minecraft XP level number and XP bar display the Hero Sword level and XP.
- `/dh version` shows the installed plugin version.
- `/dh dummy` creates or locates a Training Dummy and displays hit damage, total damage, and short-window DPS.
- MythicMobs custom items can be configured as fragments and dropped by MythicMobs mobs.
- Player-killed mobs automatically grant Sword XP to the strongest Hero Sword, with no XP item required.
- The legacy Hero Sword XP pickup flow remains available when automatic mob XP is disabled.
- Forging a Damage fragment updates the sword's actual main-hand attack damage.
- Hero Sword tiers progress from Wood to Hero as the sword levels.
- MythicMob levels can scale from nearby players' Sword Level, fragment Damage Bonus, and Prestige.
- Dungeon Rank controls the player's maximum Sword Level until the next rank-up.
- Dungeon worlds keep the player's normal Minecraft inventory. The Hero Forge
  is optional, and MythicMobs fragments remain physical inventory items.

## Dungeon Reputation

Version 1.7.5 adds a server-wide Dungeon Reputation system for `dungeon_world`.
Vanilla hostile mobs, DungeonHero Mythic variants, elites, five-minute
minibosses, and rare biome bosses contribute to capped daily biome activity.
Passive mobs and Mythic reinforcements do not generate reputation. Minibosses
remain valuable after their daily reputation credit through their XP and loot.

The system stores its progress separately in `plugins/DungeonHero/reputation.yml`.
Players receive a rotating daily contract, personal weekly Contribution, and
credit when fighting with nearby party members. Routine activity is capped so
one mob farm cannot rush the shared ranks. Public events start on a timer,
summon the matching rare biome boss when a player enters the target biome, and
award a larger reputation bonus when the community completes both objectives.

Reputation ranks are content and prestige milestones rather than raw combat
stat bonuses: Uncharted, Recognized, Dangerous, Notorious, Renowned, and
Legendary. The defaults can be tuned under `DungeonHero.Reputation` in
`config.yml`. Use `/dh admin reputation set <amount>` or `add <amount>` for
controlled testing; the command requires `dungeonhero.admin.reputation`.

## Source organization

The Java source is organized by ownership boundary:

- `feature/sword`, `feature/forge`, `feature/rank`, `feature/party`, `feature/coins`, and `feature/trainingdummy` contain gameplay features.
- `integration/mythicmobs` contains the remaining external-plugin adapter.
- `command` contains `/dh` routing and administrative command behavior.
- `messaging` contains player-facing Adventure panels.
- `DungeonHeroPlugin` is intentionally limited to dependency construction, listener registration, and scheduler setup.

The `dungeonhero.admin` permission remains the full administrator permission and inherits the granular reload, give, and dummy cleanup permissions. Dummy cleanup only removes entities marked with DungeonHero's persistent data keys.

## Inventory and forge

DungeonHero uses the player's normal Minecraft inventory. It does not replace,
lock, snapshot, or vault items. `/dh forge` opens a protected custom Forge GUI. Place
one Hero Sword in the left slot and a configured Damage Fragment in the right
slot. Use the batch controls and click the Forge button; the center slot is a
preview and cannot be removed directly. One fragment is consumed per forge
operation, and closing the GUI safely returns both input items.

## Five-player parties

DungeonHero supports parties of up to five players. Party membership is session-based and is cleared when the party disbands or all members leave. Invitations expire after the configured time.

```yaml
DungeonHero:
  Party:
    Enabled: true
    MaxSize: 5
    RequireSameWorld: true
    InvitationSeconds: 60
  MobScaling:
    PartyMode: AVERAGE
    MaxPlayers: 5
```

Use `/dh party help` for all commands. When a MythicMob spawns near a party member, DungeonHero scales it using the nearby members of that party. `AVERAGE` is recommended so one highly progressed player does not make the dungeon unfair for newer friends. Sword XP and Dungeon Coin balances remain personal; shared dungeon-completion rewards can be added once a dungeon completion event is connected.

## Dungeon Coins and ranks

DungeonHero stores Dungeon Coins independently in `plugins/DungeonHero/coins.yml`, keyed by player UUID. No economy plugin is required.

The default progression has 10 ranks. A player must reach the current rank's Sword Level cap, then pay the next rank's configured cost:

```yaml
DungeonHero:
  Ranks:
    CoinName: "Dungeon Coins"
    List:
      "2":
        Name: Apprentice
        RequiredSwordLevel: 10
        SwordLevelCap: 20
        Cost: 100
```

Rank progress is stored in the player's persistent data, while Sword Level, XP, tiers, Prestige, and forged Damage Bonus remain stored on the Hero Sword.

## MythicMobs fragments

DungeonHero uses MythicMobs' internal item ID as `mm:<item-id>`. MythicMobs only defines and drops the item. Configure the Forge behavior in `plugins/DungeonHero/config.yml`:

```yaml
DungeonHero:
  Fragments:
    "mm:HeroDamageFragment":
      Type: fragment
      Stat: DAMAGE
      Amount: 2
```

Then configure the MythicMob to drop `HeroDamageFragment` using MythicMobs' normal drop configuration. After changing DungeonHero's fragment mapping, run `/dh reload`; after changing the MythicMobs item, run `/mm reload`.

## Hero Sword mob scaling

DungeonHero finds nearby players within the configured radius when a MythicMob spawns and sets that mob's level from their strongest Hero Swords:

```yaml
DungeonHero:
  MobScaling:
    Enabled: true
    Worlds:
      - dungeon_world
    SearchRadius: 32
    PartyMode: AVERAGE
    MaxPlayers: 5
    BaseLevel: 1
    SwordLevelsPerMobLevel: 2
    RankPowerBonus: 2.0
    DamageBonusWeight: 0.5
    PrestigeLevelBonus: 5
    MaxLevel: 100
```

`PartyMode` can be `NEAREST`, `AVERAGE`, or `MAX`. With the default configuration, every 2 points of party combat power adds one MythicMob level. Combat power is Sword Level plus half of the sword's Damage Bonus plus 5 per Prestige. MythicMobs then applies the mob's own `LevelModifiers` for health, damage, armor, and other stats.

When `MobScaling.Worlds` is configured, only MythicMobs spawning in those worlds
is scaled. `RankPowerBonus` adds direct Dungeon Rank power using
`(Dungeon Rank - 1) * RankPowerBonus`, so Rank affects mobs even before the sword
reaches its new rank cap. The default setup enables scaling only in
`dungeon_world` with a `RankPowerBonus` of 2.

Prestige is available when the sword reaches `MaxSwordLevel`. It preserves fragment Damage Bonus, increases Prestige by one, and resets the sword to Level 1 and the Wood tier.

## Sword XP

By default, player-killed mobs automatically grant Sword XP directly to the killer's strongest Hero Sword. No XP item is created or dropped by mob kills. Native Sword XP items remain available for manual or administrative use.

```yaml
DungeonHero:
  Progression:
    SwordXPItem:
      Material: EXPERIENCE_BOTTLE
      Name: "&aHero Sword XP"
      Lore:
        - "&7Use this to increase Sword XP."
      XP: 25
    AutoMobKillXP: true
    XPPerMobKill: 25
    BaseXPRequired: 100
    XPRequiredMultiplier: 1.25
    MaxSwordLevel: 100
```

The sword stores its level and XP as persistent item data. XP requirements increase each level, and XP collection stops at the configured maximum level. `XPPerMobKill` controls the direct mob-kill reward. Native XP items store their own XP amount in DungeonHero persistent item data and can be given with `/dh give-xp <player> [xp]`.

## Sword XP HUD

The plugin reuses Minecraft's native XP HUD so the green number displays Sword Level and the XP bar displays progress toward the next Sword Level:

```yaml
DungeonHero:
  Hud:
    UseVanillaXpBar: true
    UpdateTicks: 10
```

When enabled, normal Minecraft XP gain is blocked so the HUD remains dedicated to Sword XP. Set `UseVanillaXpBar` to `false` if the server needs to use normal Minecraft experience.
