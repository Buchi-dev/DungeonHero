# DungeonHero Architecture

## Boundaries

`DungeonHeroPlugin` is the composition root. It creates services, registers listeners, starts scheduled tasks, and attaches the command executor. Gameplay behavior belongs in feature packages.

## Gameplay framework

`com.dungeonhero.framework` is the reusable gameplay layer. `GameplayFramework`
owns the event bus, state store, timer/cooldown service, and registries for
features, objectives, conditions, actions, rewards, and triggers.
`FeatureRegistry` validates each feature's YAML section before loading it and
coordinates idempotent stop/load/start transitions during `/dh reload`.

The existing Open-World Dungeon loop is represented by
`feature/openworlddungeon/OpenWorldDungeonFeature`. It does not replace
DungeonCore or duplicate the existing sword, party, or MythicMobs
services. It converts player mob defeats into framework events and composes
configured objectives and rewards around them. DungeonCore remains the
authoritative world spawner and completion owner.

To create a module, implement `GameplayFeature`, register it with
`plugin.getGameplayFramework().features()`, and use the injected
`FeatureContext` in `load`/`start`. Custom objectives implement
`GameplayObjective`; custom rewards implement `GameplayReward`. Register their
types once at bootstrap, then reference those types from the feature YAML.

Framework configuration lives under `DungeonHero.Gameplay.Features` so the
project's existing PascalCase YAML convention remains intact:

```yaml
DungeonHero:
  Gameplay:
    Features:
      open-world-dungeon:
        Enabled: true
        ConfigVersion: 1
        Objectives:
          - Type: defeat_mobs
            Mob: ZOMBIE
            Amount: 20
        Rewards:
          - Type: item
            Material: DIAMOND
            Amount: 2
```

Invalid feature configuration disables only that feature and logs the exact
path and reason. Existing services still use their original configuration
paths and reload behavior, so this framework addition is backward compatible.

```text
com.dungeonhero
├── command                 /dh routing and admin permissions
├── feature                 gameplay modules
│   ├── sword
│   ├── forge
│   ├── rank
│   ├── party
│   ├── coins
│   ├── quest
│   └── trainingdummy
├── integration             external plugin adapters
│   └── mythicmobs
└── messaging               Adventure message panels
```

## Admin control

`dungeonhero.admin` grants all administrator capabilities through permission inheritance:

- `dungeonhero.admin.reload`
- `dungeonhero.admin.give`
- `dungeonhero.admin.coins`
- `dungeonhero.admin.dummy.remove`
- `dungeonhero.admin.dummy.remove-all`

The root permission is intended for server operators and developers. Individual permissions can be assigned when a server needs narrower access.

## Training dummy lifecycle

Training Dummies and their text displays are identified with DungeonHero persistent data keys. The remove commands only target those marked entities:

- `/dh dummy remove` removes targets in the sender's current world.
- `/dh dummy remove-all` removes targets from all loaded worlds and can be run from console.

The complete training dummy feature is isolated under `feature/trainingdummy`, so it can be removed later by deleting that module, its listener registration, and its command registration without affecting sword progression or external integrations.

## Change rules

1. Keep Bukkit/Paper event handling at feature or integration edges.
2. Keep external-plugin API calls inside `integration` packages.
3. Inject dependencies through constructors.
4. Add new admin actions under `command` with a dedicated permission node.
5. Do not use broad entity removal; always check DungeonHero persistent data keys.
6. Keep forge transactions in `feature/forge`; GUI input and output slots must be explicitly protected and revalidated before consuming inputs.

## Pure domain extraction

The current cleanup introduces a framework-free domain layer:

- `feature.sword.HeroSwordState`, `SwordComparator`, `SwordProgressionCalculator`, and `FragmentCapPolicy`.
- `feature.rank.RankPolicy`.
- `feature.quest.QuestScoringPolicy` and `RewardPolicy`.
- `integration.mythicmobs.MobScalingPolicy`.

`HeroItemService`, `DungeonRankService`, `SwordProgressionService`, `DungeonRushService`, and
`HeroSwordMobScaler` translate Bukkit or MythicMobs objects at the edge, invoke these policies, and
apply the results. `MobCombatBalance` remains as the runtime MythicMobs adapter
used by `HeroSwordMobScaler`; the duplicate progression, fragment-cap, and
rank-cap compatibility facades were removed after repository-wide reference
verification.

## Service boundaries

- `DungeonRushService` owns lifecycle orchestration and presentation.
- `DungeonRushConfiguration` owns Dungeon Rush configuration parsing.
- `DungeonRushRoundState` owns round transitions and score state.
- `DungeonRushLeaderboard` owns leaderboard ordering.
- `DungeonRushRewardDistributor` owns Bukkit reward delivery.
- `DungeonRushListener` and `SwordProgressionListener` are event-only adapters.
- `HeroSwordItemCodec` owns Hero Sword PDC encoding/decoding; `HeroItemService` owns sword rules.
- `common.ItemDeliveryService`, `PlayerResolver`, `ConfigValues`, and `MythicDeathDeduplicator` are shared edge utilities.

## Command boundaries

`DungeonHeroCommand` is only the `/dh` router. It handles command identity,
the help/version fallbacks, handler lookup, and delegation. Command behavior is
split into focused handlers:

- `SwordCommand`: sword status, forge, and prestige.
- `RankCommand`: rank status and rank-up.
- `QuestCommand`: Dungeon Rush status and leaderboard.
- `PartyCommand`: party actions and broadcasts.
- `EconomyCommand`: balances and coin transfers.
- `AdminCommand`: reload, item grants, coin administration, sword resets, and dummies.

`CommandSupport` centralizes permission inheritance, player resolution, numeric
argument parsing, usage output, and case-insensitive completion matching.
`CommandCompletionService` owns the completion catalog and route-specific
argument suggestions. This keeps Bukkit command plumbing at the edge while
making each handler independently replaceable and reviewable.

## Bootstrap lifecycle

`DungeonHeroPlugin` is intentionally limited to resource setup, component startup, command binding,
and public API delegation. `DungeonHeroComponents` owns dependency creation. `PluginModuleRegistry`
invokes modules in dependency order for `load`, `reload`, and `start`, registers listeners centrally,
and closes modules in reverse order. The framework feature registry remains responsible for its own
feature listener because it is a reusable framework lifecycle boundary.

## Configuration boundary

`config.DungeonHeroConfiguration` is the only reader of the main `config.yml`.
It converts YAML into immutable records for progression, swords, ascension,
fragments, ranks, HUD, MythicMob scaling, damage protection, parties, Dungeon
Rush, training dummies, and administration. Feature services receive those
snapshots and do not depend on Bukkit configuration APIs.

Canonical duplicate-key names are `HeroAscension.*` over
`Progression.Prestige.*`, `FragmentCaps.*` over `Fragments.Caps.*`,
`MobScaling.MaximumMobLevel` over `MobScaling.MaxLevel`, and
`MobScaling.PartyScalingMode` over `MobScaling.PartyMode`. Legacy keys remain
read-only compatibility aliases and emit a migration warning whenever they
are present. The shipped config contains canonical keys only.
