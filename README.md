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

The plugin JAR is generated at `build/libs/DungeonHero-2.0.1.jar`.

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
- `/dh quest` shows the automatic five-minute Dungeon Rush.
- `/dh quest top` shows the current or most recent Dungeon Rush leaderboard.
- `/dh rankup` spends Dungeon Coins to increase Dungeon Rank.
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

## Automatic Dungeon Rush quests

DungeonHero can run automatic five-minute leaderboard quests. Players do not
need to join manually: a player is registered only after their first
qualifying kill during the active round. Players who do not kill anything do
not appear on the leaderboard.

The default quest pool randomly selects between killing the most Dungeon mobs
and killing the most Mythic mobs. Only kills in the configured
`DungeonHero.DungeonRush.Worlds` count. The top three players receive their
configured rewards when the round ends. Use `/dh quest` to see the timer and
`/dh quest top` to see the leaderboard. Configure the round schedule, minimum
kills, quest pool, and rewards under `DungeonHero.DungeonRush` in `config.yml`.

Each placement accepts a list of reward definitions. Supported types are
`coins`, `sword_xp`, `item`, and `command`:

```yaml
Rewards:
  First:
    - Type: coins
      Amount: 100
    - Type: item
      Material: DIAMOND
      Amount: 3
    - Type: command
      Command: "give %player% golden_apple 2"
```

Command rewards run as console and support these placeholders:

| Placeholder | Value |
|---|---|
| `%player%` | Winner's Minecraft name |
| `%uuid%` | Winner's UUID |
| `%place%` | Winner's place: 1, 2, or 3 |
| `%kills%` | Winner's kill count |
| `%quest_name%` / `%objective%` | Human-readable quest name |
| `%quest_type%` | Quest type enum name |
| `%quest_time_left%` / `%time_left%` | Remaining time, such as `4m 32s` |
| `%quest_time_left_seconds%` | Remaining time in seconds |
| `%quest_duration%` / `%duration%` | Full quest duration |
| `%quest_duration_seconds%` | Full duration in seconds |
| `%quest_biome%` / `%biome%` | Selected biome or `All Biomes` |
| `%quest_world%` / `%world%` | Configured quest worlds |
| `%quest_goal%` | `Top 3 players with the most kills` |
| `%quest_round%` | Current round number |

Set `DungeonRush.Biomes` to a list such as `PLAINS` or `DEEP_DARK` to restrict
each round to a randomly selected biome. Leave it empty to allow all biomes.
These placeholders allow server developers to grant ranks, keys, MythicMobs
items, or rewards from other plugins.

## Modular gameplay framework

DungeonHero now exposes a reusable, configuration-driven gameplay framework.
The framework is intentionally separate from the existing DungeonHero services
and provides:

- `GameplayFeature` and `FeatureRegistry` for registration and lifecycle.
- Typed event dispatching through `FeatureEventBus`.
- Feature-scoped state and clock-based timers/cooldowns.
- Registries and extension interfaces for objectives, conditions, actions,
  rewards, and triggers.
- `PlayerContext` and `TeamContext` for portable gameplay context.
- Configuration version checks and safe feature reloads.

The Open-World Dungeon module is configured under
`DungeonHero.Gameplay.Features.open-world-dungeon`. It listens to player mob
defeats and supports the built-in `defeat_mobs` objective and `item` reward.
DungeonCore remains the authoritative spawner and dungeon completion owner;
the module does not pay DungeonCore rewards a second time.

### Adding a feature

Implement `GameplayFeature`, register it from the plugin composition root, and
use `FeatureContext` to subscribe to framework events and access the registries.
Keep Bukkit listeners and third-party API calls at the feature/integration edge.
Feature configuration is validated before `load` and a failed feature is
isolated from the other modules.

### Adding an objective or reward

Implement `GameplayObjective` or `GameplayReward`, give it a stable lowercase
type, register it with `getGameplayFramework().objectives()` or `.rewards()`,
and reference that type in a feature's YAML definitions. This avoids adding a
new hardcoded branch to the framework for each future quest, boss, event, or
progression module.

The framework configuration uses the existing PascalCase convention and is
safe to reload with `/dh reload`. Unsupported configuration versions and
malformed definition lists are reported with their feature path and prevent
only the affected feature from starting.

## Source organization

The Java source is organized by ownership boundary:

- `feature/sword`, `feature/forge`, `feature/rank`, `feature/party`, `feature/coins`, `feature/quest`, and `feature/trainingdummy` contain gameplay features.
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
    PartyScalingMode: HIGHEST
    MaxPlayers: 5
```

Use `/dh party help` for all commands. When a MythicMob spawns near a party member, DungeonHero scales it from the highest active Sword Level in that party, so a low-level member cannot reduce encounter difficulty. Sword XP and Dungeon Coin balances remain personal; shared dungeon-completion rewards can be added once a dungeon completion event is connected.

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
    MobLevelSource: EFFECTIVE_SWORD_LEVEL
    PartyScalingMode: HIGHEST
    MaxPlayers: 5
    MaximumMobLevel: 104
    LevelOffsets:
      Normal: {Min: -2, Max: 4}
      Elite: {Min: 0, Max: 4}
      Miniboss: {Min: 1, Max: 4}
      RareBoss: {Min: 2, Max: 4}
  MobHp:
    NormalBase: 400
    NormalHpPerLevel: 40
    ProfileMultipliers: {Normal: 1.0, Elite: 3.0, Miniboss: 8.0, RareBoss: 18.0}
    MinimumAttacks: {Normal: 6, Elite: 12, Miniboss: 25, RareBoss: 50}
    MaximumAmplifierCompensation: 0.50
```

Mob levels are selected once at spawn from the effective Sword Level (including
the prestige floor `1 + Prestige * 20`) and the profile offset range. MythicMobs
then applies the mob's own `LevelModifiers` for health, damage, armor, and other
stats. DungeonHero additionally applies the configured HP formula and bounded
damage-based attack floor once at creation.

### MythicMob registry

Mob classification is data-driven in `plugins/DungeonHero/mob-registry.yml`.
Profiles define level offsets and Sword XP; the `Mobs` section maps MythicMobs
internal IDs to profiles. Add a new mob
without rebuilding DungeonHero:

```yaml
Profiles:
  cursed_elite:
    LevelOffset: 5
    SwordXP: 175

Mobs:
  MY_CUSTOM_KNIGHT:
    Profile: cursed_elite
```

Run `/dh reload` after editing the registry. Other plugins can register a mob
at runtime through the public API:

```java
DungeonHeroPlugin dungeonHero = /* Bukkit plugin instance */;
dungeonHero.getMobRegistry().register("MY_CUSTOM_KNIGHT",
        new MobRegistryService.MobProfile("cursed_elite", 5, 175));
```

Runtime registrations survive DungeonHero reloads and take precedence over the
YAML mapping. Unknown `DH_` and `DW_` IDs safely use the normal Mythic profile
when prefix tracking is enabled.

When `MobScaling.Worlds` is configured, only MythicMobs spawning in those worlds
is scaled. The selected level is also written to DungeonHero persistent metadata
on the spawned entity, so it is not rerolled during combat. The default setup
enables scaling only in `dungeon_world` and clamps levels to 104.

Hero Ascension is available at Sword Level 100, up to Prestige 5. `/dh prestige`
opens a confirmation flow; `/dh prestige confirm` performs the atomic reset to
Level 1 while preserving fragments, rank, dungeon access, and Dungeon Coins.
Each Prestige player receives a single configured 2x Sword XP multiplier (not
2^Prestige), and rare-drop bonuses follow the configured Prestige table.

Administrators can inspect and reset a player's physical sword without deleting
it:

```text
/dh admin resetsword <player> preview
/dh admin resetsword <player> confirm
```

The command requires `dungeonhero.admin.resetsword` and writes an audit entry.

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
    XPPerMobKill: 10
    MythicMobXP: 25
    EliteXP: 100
    MinibossXP: 400
    RareBossXP: 1000
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

## Hero Armor — Aegis of the Fallen

Hero Armor is one shared progression system represented by a Helmet, Chestplate,
Leggings, and Boots. All four pieces share the player’s Armor Level, Armor XP,
and Armor Bonus. The canonical state is stored on the player; each physical
piece carries mirrored PDC metadata so it can be identified and normalized.

Armor receives the same XP awards as the sword for mob kills, MythicMob kills,
Dungeon Rush Sword XP rewards, and manual Sword XP item pickup. Armor uses the
same XP curve and rank-controlled cap, with `ArmorLevelCap` falling back to
`SwordLevelCap` when omitted.

```text
/dh armor
/dh armor forge
```

The Forge accepts either a Hero Sword with a `DAMAGE` fragment or one Hero
Armor piece with an `ARMOR` fragment. Batch quantities consume only the selected
fragment count. Closing the GUI returns both inputs through the normal safe
delivery path.

Armor protection scales from Armor Level and effective, rank-capped Armor
Bonus. Stored overflow is preserved and becomes active as the player unlocks
higher ranks. Two equipped pieces grant 2% damage reduction, three grant 5%,
and a complete set activates Last Stand: once every 30 seconds, lethal damage
at or below 30% maximum health is cancelled and the player is restored to 30%.

```yaml
DungeonHero:
  Armor:
    Enabled: true
    MaxLevel: 100
    LevelReductionPerLevel: 0.0015
    MaxLevelReduction: 0.15
    FragmentReductionPerPoint: 0.01
    MaxFragmentReduction: 0.20
    MaxTotalReduction: 0.40
    LastStandHealthThreshold: 0.30
    LastStandCooldownSeconds: 30
    MaximumStoredArmor: 100000
    RankCaps:
      1: 10
      2: 20
      3: 35
      10: 280
  Ranks:
    List:
      "1":
        Name: Novice
        RequiredSwordLevel: 1
        SwordLevelCap: 10
        ArmorLevelCap: 10
        Cost: 0
```

Hero Armor is unbreakable and cannot be dropped. On join, respawn, and reload,
duplicates are removed, missing pieces are rebuilt from canonical state, and
normal armor or inventory items are never overwritten. If a normal armor item
occupies a required slot, the Hero Armor piece is delivered to the inventory or
dropped safely when the inventory is full.
