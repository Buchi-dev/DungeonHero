# DungeonHero

Starting point for a dungeon adventure plugin built for Paper 26.1.2.

## Requirements

- Java 25
- Paper 26.1.2 (API build 72)

## Build

On Windows:

```powershell
.\gradlew.bat build
```

The plugin JAR is generated at `build/libs/DungeonHero-0.1.0-SNAPSHOT.jar`.

Copy that JAR into the server's `plugins` directory and restart the server. Once loaded, `/dungeonhero` (or `/dh`) confirms that the plugin is active.

