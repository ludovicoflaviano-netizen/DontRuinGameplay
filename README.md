# DontRuinGameplay

Paper 1.21.11 plugin that protects player-shot projectiles from natural despawning while leaving dropped item entities completely alone.

## What it does

- Marks projectiles shot by players with persistent data.
- Keeps protected projectiles persistent through chunk saving/loading.
- Continuously resets `AbstractArrow` lifetime so arrows, spectral arrows, and tridents do not naturally age out.
- Detects existing player-shot projectiles when the plugin starts.
- Can recover a protected projectile if another plugin removes it with a `DESPAWN` or `PLUGIN` removal cause.
- Does not restore normal projectile lifecycle events such as `HIT`, `EXPLODE`, `PICKUP`, `OUT_OF_WORLD`, or chunk `UNLOAD`.
- Does not touch dropped `Item` entities.
- Wind Charges still explode normally when they hit something; the plugin does not cancel or change their normal hit behavior.

## Important API limitation

Paper exposes `EntityRemoveEvent` as a monitoring event rather than a cancellable event. DontRuinGameplay therefore restores a protected projectile after a `PLUGIN` or natural `DESPAWN` removal when recovery is enabled instead of pretending it can cancel another plugin's `remove()` call.

## Build

Requires Java 21 and Paper API 1.21.11.

```text
gradle build --no-daemon
```
