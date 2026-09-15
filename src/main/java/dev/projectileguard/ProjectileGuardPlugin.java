package dev.projectileguard;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class ProjectileGuardPlugin extends JavaPlugin implements Listener {
    private NamespacedKey protectedKey;
    private NamespacedKey shooterKey;
    private final Set<EntityType> protectedTypes = EnumSet.noneOf(EntityType.class);
    private int checkInterval;
    private boolean preventNaturalDespawn;

    @Override
    public void onEnable() {
        protectedKey = new NamespacedKey(this, "protected_projectile");
        shooterKey = new NamespacedKey(this, "shooter_uuid");
        saveDefaultConfig();
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);
        startProtectionTask();
        getLogger().info("ProjectileGuard enabled for " + protectedTypes.size() + " projectile types.");
    }

    @Override
    public void onDisable() {
        getLogger().info("ProjectileGuard disabled.");
    }

    private void loadSettings() {
        checkInterval = Math.max(1, getConfig().getInt("check-interval-ticks", 10));
        preventNaturalDespawn = getConfig().getBoolean("prevent-natural-despawn", true);
        protectedTypes.clear();

        for (String raw : getConfig().getStringList("protected-projectiles")) {
            try {
                protectedTypes.add(EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                getLogger().warning("Unknown projectile EntityType in config: " + raw);
            }
        }
    }

    private void startProtectionTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!preventNaturalDespawn) {
                return;
            }

            for (var world : Bukkit.getWorlds()) {
                for (var entity : world.getEntities()) {
                    if (!isProtected(entity)) {
                        continue;
                    }

                    // Paper exposes lifetime control for arrows/tridents. Resetting it
                    // prevents the vanilla arrow-age despawn while keeping normal physics.
                    if (entity instanceof AbstractArrow arrow) {
                        arrow.setLifetimeTicks(0);
                    }
                }
            }
        }, checkInterval, checkInterval);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player)) {
            return;
        }

        if (!protectedTypes.contains(projectile.getType())) {
            return;
        }

        projectile.getPersistentDataContainer().set(protectedKey, PersistentDataType.BYTE, (byte) 1);
        projectile.getPersistentDataContainer().set(shooterKey, PersistentDataType.STRING, player.getUniqueId().toString());

        if (projectile instanceof AbstractArrow arrow) {
            arrow.setLifetimeTicks(0);
        }
    }

    /**
     * Reassert protection after an entity is loaded from a chunk.
     * The marker is stored on the projectile itself, so no in-memory list is required.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        if (!preventNaturalDespawn || !isProtected(event.getEntity())) {
            return;
        }

        // EntityRemoveEvent is monitoring-only in Paper. We intentionally do not try to
        // modify/cancel it. A plugin explicitly removing an entity cannot be overridden
        // safely from this event. Natural despawn is prevented by lifetime management.
        if (event.getCause() == EntityRemoveEvent.Cause.PLUGIN) {
            getLogger().fine("Another plugin removed a protected projectile: " + event.getEntity().getUniqueId());
        }
    }

    private boolean isProtected(Entity entity) {
        if (!(entity instanceof Projectile projectile)) {
            return false;
        }
        if (!protectedTypes.contains(projectile.getType())) {
            return false;
        }
        return projectile.getPersistentDataContainer().has(protectedKey, PersistentDataType.BYTE);
    }

    @SuppressWarnings("unused")
    private UUID getShooterUuid(Projectile projectile) {
        String value = projectile.getPersistentDataContainer().get(shooterKey, PersistentDataType.STRING);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public void reloadPluginConfig() {
        reloadConfig();
        loadSettings();
    }
}
