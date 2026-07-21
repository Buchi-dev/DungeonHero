# DungeonHero Architecture

## Boundaries

`DungeonHeroPlugin` is the composition root. It creates services, registers listeners, starts scheduled tasks, and attaches the command executor. Gameplay behavior belongs in feature packages.

```text
com.dungeonhero
├── command                 /dh routing and admin permissions
├── feature                 gameplay modules
│   ├── sword
│   ├── forge
│   ├── rank
│   ├── party
│   ├── coins
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
