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
