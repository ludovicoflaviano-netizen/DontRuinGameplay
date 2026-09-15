# ProjectileGuard

Paper 1.21.11 plugin that protects player-shot projectiles from natural despawning and recovers them if another plugin removes them.

Dropped Item entities are not protected.

## Protection

Only projectiles whose shooter is a Player are marked. The marker is stored in the projectile's PersistentDataContainer, so it survives chunk saving and server restarts when the entity itself is saved.

Arrows, spectral arrows, and tridents use Paper's AbstractArrow lifetime API and are continuously reset to lifetime 0. Other configured projectile types are made persistent and are recovered when Paper reports a DESPAWN or PLUGIN removal.

Normal projectile lifecycle is preserved: HIT, EXPLODE, PICKUP, and other intentional consumption/removal causes are not restored. Wind Charges therefore still explode normally after collision.

## Important API limitation

Paper's EntityRemoveEvent exposes a PLUGIN cause, but the event is not cancellable. A Bukkit/Paper plugin therefore cannot stop another plugin's remove() call before it happens using that event. ProjectileGuard immediately recreates protected projectiles after DESPAWN/PLUGIN removal instead. Fully preventing arbitrary NMS/server-level removal would require version-specific server internals.

## Commands

`/projectileguard reload`

Permission: `projectileguard.admin`

## Build

Java 21 + Paper API 1.21.11.
