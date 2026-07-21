# DungeonHero

Starting point for a dungeon adventure plugin built for Paper 26.1.2.

## Requirements

- Java 25
- Paper 26.1.2 (API build 72)
- MythicMobs 5.12.1
- Vault with an economy provider

## Build

On Windows:

```powershell
.\gradlew.bat build
```

The plugin JAR is generated at `build/libs/DungeonHero-1.6.2.jar`.

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

The installer targets `D:\Minecraft FIles (Server)\New` by default, verifies
Vault, installs MythicMobs 5.12.1 from the supplied parent folder when it is
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
- `/dh sword` shows the Hero Sword level and XP.
- `/dh prestige` resets a capped Hero Sword to Level 1 and increases its permanent Prestige.
- `/dh rank` shows Dungeon Rank, Sword Level cap, and the Vault economy balance used as Dungeon Coins.
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
- Dungeon worlds use a separate RPG loadout with a fixed Hero Sword, Dungeon Menu,
  Fragment Vault, and five-unique-item Supply Loadout.

## Dungeon loadout inventory

Version 1.6.2 adds a dungeon-only inventory profile. Configure the world names in
`plugins/DungeonHero/config.yml`:

```yaml
DungeonHero:
  DungeonInventory:
    Enabled: true
    Worlds:
      - dungeon_world
    HeroSwordSlot: 5
    FragmentBagSlot: 1
    SupplyBagSlot: 2
    SupplyHotbarSlots: [3, 4, 6, 7, 8]
    ReservedSlot: 9
    MaxUniqueSupplyItems: 5
    FragmentVaultSlots: 27
    FragmentVaultStackSize: 64
    LoseSuppliesOnDeath: false
```

When a player enters a configured dungeon world, DungeonHero stores their normal
inventory and loads this layout:

```text
[Dungeon Menu] [Reserved] [Item 1] [Item 2] [HERO SWORD] [Item 3] [Item 4] [Item 5] [Reserved]
      1              2        3        4          5           6        7        8         9
```

Use `/dh menu` or right-click the Dungeon Menu item. The menu opens the Fragment
Vault, Supply Loadout, and Hero Forge. Fragments are stored as numeric balances
per configured MythicMobs fragment type, but the vault deliberately behaves like
a 27-slot inventory with 64 fragments per slot. This gives a clear, finite RPG
capacity while still allowing automatic pickup. If the vault is full, the
unstored remainder stays on the ground. `/dh vault` shows the stored counts.
`/dh loadout` opens the preparation GUI; the top five slots are the
selected loadout, and the lower staging area contains allowed items from the
player's stored normal inventory. Only configured supply materials are accepted,
and the selected loadout is limited to five unique item types.

The Hero Sword and Dungeon Menu cannot be moved or dropped in the dungeon.
Ordinary item pickups are blocked so the five-item loadout cannot be bypassed.
Leaving the world restores the player's normal inventory and saves the dungeon
loadout for the next run. The Hero Sword is retained through death; set
`LoseSuppliesOnDeath` to `true` if dungeon supplies should be cleared on death.
`/dh forge` consumes fragments directly from the Fragment Vault. The Forge now
supports batch forging: use `-10`, `-1`, `+1`, `+10`, or `MAX`, then click the
Forge button. The preview shows the total damage bonus and fragment cost before
the batch is committed.

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

Use `/dh party help` for all commands. When a MythicMob spawns near a party member, DungeonHero scales it using the nearby members of that party. `AVERAGE` is recommended so one highly progressed player does not make the dungeon unfair for newer friends. Sword XP and Vault balances remain personal; shared dungeon-completion rewards can be added once a dungeon completion event is connected.

## Vault Dungeon Coins and ranks

DungeonHero uses Vault's Economy service, so it works with the economy plugin registered through Vault instead of storing a second currency. The Vault balance is displayed as Dungeon Coins using the configured label. Vault and an economy provider must be installed on the server.

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
    SearchRadius: 32
    PartyMode: AVERAGE
    MaxPlayers: 5
    BaseLevel: 1
    SwordLevelsPerMobLevel: 2
    DamageBonusWeight: 0.5
    PrestigeLevelBonus: 5
    MaxLevel: 100
```

`PartyMode` can be `NEAREST`, `AVERAGE`, or `MAX`. With the default configuration, every 2 points of party combat power adds one MythicMob level. Combat power is Sword Level plus half of the sword's Damage Bonus plus 5 per Prestige. MythicMobs then applies the mob's own `LevelModifiers` for health, damage, armor, and other stats.

Prestige is available when the sword reaches `MaxSwordLevel`. It preserves fragment Damage Bonus, increases Prestige by one, and resets the sword to Level 1 and the Wood tier.

## Sword XP

By default, player-killed mobs automatically grant Sword XP to the killer's strongest Hero Sword. No physical XP item is created or required. The legacy `HeroSwordXP` pickup flow can still be used by disabling automatic mob XP.

```yaml
DungeonHero:
  Progression:
    SwordXPItem: "mm:HeroSwordXP"
    AutoMobKillXP: true
    XPPerItem: 25
    XPPerMobKill: 25
    BaseXPRequired: 100
    XPRequiredMultiplier: 1.25
    MaxSwordLevel: 100
```

The sword stores its level and XP as persistent item data. XP requirements increase each level, and XP collection stops at the configured maximum level. `XPPerMobKill` controls the automatic reward; `XPPerItem` controls the legacy pickup reward.

## Sword XP HUD

The plugin reuses Minecraft's native XP HUD so the green number displays Sword Level and the XP bar displays progress toward the next Sword Level:

```yaml
DungeonHero:
  Hud:
    UseVanillaXpBar: true
    UpdateTicks: 10
```

When enabled, normal Minecraft XP gain is blocked so the HUD remains dedicated to Sword XP. Set `UseVanillaXpBar` to `false` if the server needs to use normal Minecraft experience.
