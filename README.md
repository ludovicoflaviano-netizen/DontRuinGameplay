# ProjectileGuard

ProjectileGuard is a lightweight Paper 1.21.11 plugin that marks projectiles launched by players and prevents natural projectile despawn where the Bukkit/Paper API exposes lifetime control.

## Features

- Player-shot projectiles only.
- PersistentDataContainer marker survives chunk unload/reload and entity persistence.
- Arrow and trident lifetime is continuously reset so vanilla age-based despawn is prevented.
- Wind Charge and other projectile types can be tracked/configured.
- Configurable projectile list and check interval.
- No NMS or server jar modification.
- `/projectileguard reload` with the `projectileguard.admin` permission.

## Important behavior

ProjectileGuard does not cancel normal projectile behavior. A projectile can still be removed because it hits something, is picked up, explodes, goes out of the world, or is intentionally removed by another plugin. Paper's `EntityRemoveEvent` is monitoring-only, so a plugin cannot safely cancel another plugin's explicit entity removal from that event.

For arrows/tridents, the plugin resets the Paper `AbstractArrow` lifetime. Paper 1.21.11 documents `EntityRemoveEvent.Cause.DESPAWN` as including arrows that have stayed around too long, which is the despawn this plugin targets.

Wind Charges are intentionally allowed to perform their normal hit/explosion behavior. Keeping a Wind Charge entity alive after its hit would require changing vanilla behavior rather than simply preventing despawn.

## Build

Requires Java 21 and Gradle.

```bash
gradle build
```

The resulting jar is in `build/libs/`.

## Install

1. Build the project.
2. Copy `ProjectileGuard-1.0.0.jar` into the server's `plugins/` folder.
3. Restart the server.
4. Edit `plugins/ProjectileGuard/config.yml` if needed.
5. Use `/projectileguard reload` after configuration changes.

## Compatibility

Target API: Paper 1.21.11. The project uses the Paper API because Paper exposes the projectile lifetime controls needed for reliable arrow/trident anti-despawn behavior.
