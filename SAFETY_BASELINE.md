# DungeonHero Safety Baseline

This document records the behavior and contracts protected before the source cleanup. The baseline is intentionally behavior-focused: feature internals may move later, but these external commands, permissions, APIs, and configuration paths must remain compatible unless a migration is documented.

## Verification command

```powershell
.\gradlew.bat clean test
```

The baseline currently contains 41 tests covering sword progression, rank caps, coin transfers, parties, forge arithmetic and validation, Dungeon Rush behavior and reload state, MythicMobs registry/scaling behavior, configuration anchors, configuration migration, command support, and framework lifecycle behavior.

The forge tests currently protect the pure transaction arithmetic. Full GUI inventory-consumption and item-return tests are intentionally deferred until a Paper/MockBukkit server test runtime is added; the current `paper-api` dependency alone cannot construct server-backed `ItemStack` metadata safely.

## Public plugin API

The following are the supported integration points exposed by the plugin:

| API | Contract |
|---|---|
| `DungeonHeroPlugin#getMobRegistry()` | Returns the shared MythicMobs registry used by integrations. |
| `DungeonHeroPlugin#getGameplayFramework()` | Returns the extension root for gameplay features, objectives, conditions, actions, rewards, and triggers. |
| `MobRegistryService#find(String)` | Finds an explicitly registered MythicMob profile. |
| `MobRegistryService#profileOrDefault(String)` | Returns a registered profile or the safe normal profile. |
| `MobRegistryService#getRegisteredMobs()` | Returns the current immutable registered-mob map. |
| `MobRegistryService#getProfiles()` | Returns the current immutable profile map. |
| `MobRegistryService#register(String, MobProfile)` | Adds a runtime MythicMob mapping. |
| `MobRegistryService#unregister(String)` | Removes a runtime MythicMob mapping. |
| `GameplayFeature` | Extension interface for configuration-driven gameplay modules. |
| `GameplayObjective` | Extension interface for objective evaluation. |
| `GameplayCondition` | Extension interface for feature conditions. |
| `GameplayAction` | Extension interface for feature actions. |
| `GameplayReward` | Extension interface for feature rewards. |
| `GameplayTrigger` | Extension interface for feature triggers. |
| `GameplayFramework` registries | Registration and lookup surface for the framework extension interfaces. |

The `/dungeonhero` command and `/dh` alias are also public server-facing contracts.

## Command and permission contract

Commands currently supported:

`help`, `reload`, `forge`, `give`, `give-xp`, `sword`, `rank`, `rankup`, `balance`, `transfer`, `quest`, `party`, `prestige`, `dummy`, `admin`, and `version`.

Permission nodes currently supported:

- `dungeonhero.admin`
- `dungeonhero.admin.reload`
- `dungeonhero.admin.give`
- `dungeonhero.admin.coins`
- `dungeonhero.admin.dummy.remove`
- `dungeonhero.admin.dummy.remove-all`
- `dungeonhero.admin.resetsword`
- `dungeonhero.coins.balance`
- `dungeonhero.coins.balance.others`
- `dungeonhero.coins.transfer`

## Configuration contract

The canonical root is `DungeonHero`. These sections and keys are currently consumed by production code:

- `Locale`
- `Gameplay.ConfigVersion`, `Gameplay.Debug`, `Gameplay.Features.*`
- `Fragments.*`
- `Progression.SwordXPItem.*`, `Progression.AutoMobKillXP`, `Progression.HostileMobKillXPOnly`, `Progression.XPPerMobKill`, `Progression.MythicMobXP`, `Progression.EliteXP`, `Progression.MinibossXP`, `Progression.RareBossXP`, `Progression.BaseXPRequired`, `Progression.XPRequiredMultiplier`, `Progression.MaxSwordLevel`, `Progression.Prestige.*`
- `HeroAscension.Enabled`, `HeroAscension.RequiredSwordLevel`, `HeroAscension.MaxPrestige`, `HeroAscension.ConfirmationSeconds`, `HeroAscension.XpMultiplier`, `HeroAscension.RareDropBonuses.*`, `HeroAscension.RareDropEligibleMaterials`, `HeroAscension.RareDropEligibleMythicItems`
- `FragmentCaps.MaximumStoredDamage`, `FragmentCaps.RankCaps.*`
- Legacy fragment-cap fallback: `Fragments.Caps.*`
- `Ranks.CoinName`, `Ranks.List.*`
- `Hud.UseVanillaXpBar`, `Hud.UpdateTicks`
- `MobScaling.Enabled`, `MobScaling.Worlds`, `MobScaling.SearchRadius`, `MobScaling.MobLevelSource`, `MobScaling.PartyScalingMode`, `MobScaling.PartyMode`, `MobScaling.MaxPlayers`, `MobScaling.MaximumMobLevel`, `MobScaling.MaxLevel`, `MobScaling.LevelOffsets.*`, `MobScaling.Debug`
- `MobHp.NormalBase`, `MobHp.NormalHpPerLevel`, `MobHp.ProfileMultipliers.*`, `MobHp.MinimumAttacks.*`, `MobHp.MaximumAmplifierCompensation`, `MobHp.DamageAmplifierCompensationPerDamage`
- `DamageProtection.CriticalDamageMultiplier`
- `DamageAmplifiers.ApprovedPotionEffects`, `DamageAmplifiers.PotionCompensationPerLevel`
- `Admin.ResetSwordPermission`
- `Party.Enabled`, `Party.MaxSize`, `Party.RequireSameWorld`, `Party.InvitationSeconds`
- `DungeonRush.Enabled`, `DungeonRush.Worlds`, `DungeonRush.Biomes`, `DungeonRush.DurationMinutes`, `DungeonRush.IntervalMinutes`, `DungeonRush.FirstDelayMinutes`, `DungeonRush.MinimumKills`, `DungeonRush.QuestTypes`, `DungeonRush.Rewards.*`
- `TargetDummy.Enabled`, `TargetDummy.Health`, `TargetDummy.SearchRadius`, `TargetDummy.SpawnDistance`, `TargetDummy.DamageWindowSeconds`, `TargetDummy.Hologram.Enabled`, `TargetDummy.Hologram.Height`

`MobScaling.MaxLevel`, `MobScaling.PartyMode`, `Progression.Prestige.*`, and
`Fragments.Caps.*` remain compatibility paths in the configuration reader.
The canonical shipped names are `MobScaling.MaximumMobLevel`,
`MobScaling.PartyScalingMode`, `HeroAscension.*`, and `FragmentCaps.*`.
Using a legacy key emits a migration warning; canonical values win when both
forms are present.

## Cleanup rules

1. Do not remove a public API or configuration key without first checking this document and all repository references.
2. Preserve command behavior, permission behavior, item metadata, persistence keys, and reward semantics.
3. Add a regression test before changing behavior-sensitive code.
4. Remove dead code only after compiler, repository search, tests, and configuration references agree that it is unused.

## Domain extraction baseline

Pure gameplay rules are documented in `ARCHITECTURE.md` and tested without a Bukkit runtime.
Bukkit/Paper and MythicMobs remain adapter concerns. The extracted calculators
and policies are now the sole internal implementations; `MobCombatBalance`
remains only because the runtime MythicMobs adapter still exposes it.
