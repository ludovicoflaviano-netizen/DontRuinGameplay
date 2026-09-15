package dev.dontruingameplay;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DontRuinGameplayPlugin extends JavaPlugin implements Listener {
    private NamespacedKey protectedKey;
    private NamespacedKey shooterKey;
    private final Set<EntityType> protectedTypes = new HashSet<>();
    private final Set<UUID> recentHits = ConcurrentHashMap.newKeySet();

    private boolean protectNaturalDespawn;
    private boolean recoverPluginRemoval;
    private boolean adoptExistingPlayerProjectiles;
    private int scanInterval;

    @Override
    public void onEnable() {
        protectedKey = new NamespacedKey(this, "protected_projectile");
        shooterKey = new NamespacedKey(this, "player_shooter");
        saveDefaultConfig();
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);
        startProtectionTask();
        getLogger().info("DontRuinGameplay enabled. Protecting " + protectedTypes.size() + " projectile types.");
    }

    private void loadSettings() {
        scanInterval = Math.max(1, getConfig().getInt("scan-interval-ticks", 1));
        protectNaturalDespawn = getConfig().getBoolean("prevent-natural-despawn", true);
        recoverPluginRemoval = getConfig().getBoolean("recover-plugin-removal", true);
        adoptExistingPlayerProjectiles = getConfig().getBoolean("adopt-existing-player-projectiles", true);

        protectedTypes.clear();
        for (String raw : getConfig().getStringList("protected-projectiles")) {
            try {
                EntityType type = EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
                if (type.getEntityClass() != null && Projectile.class.isAssignableFrom(type.getEntityClass())) {
                    protectedTypes.add(type);
                } else {
                    getLogger().warning("Ignoring non-projectile entity type: " + raw);
                }
            } catch (IllegalArgumentException ex) {
                getLogger().warning("Ignoring unknown entity type in config: " + raw);
            }
        }
    }

    private void startProtectionTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (!(entity instanceof Projectile projectile) || !protectedTypes.contains(projectile.getType())) {
                        continue;
                    }
                    if (!isProtected(projectile) && adoptExistingPlayerProjectiles && projectile.getShooter() instanceof Player player) {
                        markProtected(projectile, player);
                    }
                    if (isProtected(projectile)) {
                        maintain(projectile);
                    }
                }
            }
            recentHits.clear();
        }, 1L, scanInterval);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (protectedTypes.contains(projectile.getType()) && projectile.getShooter() instanceof Player player) {
            markProtected(projectile, player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (isProtected(event.getEntity())) {
            recentHits.add(event.getEntity().getUniqueId());
            Bukkit.getScheduler().runTaskLater(this, () -> recentHits.remove(event.getEntity().getUniqueId()), 5L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRemove(EntityRemoveEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Projectile projectile) || !isProtected(projectile) || !protectedTypes.contains(projectile.getType())) {
            return;
        }
        if (recentHits.remove(projectile.getUniqueId())) {
            return;
        }

        EntityRemoveEvent.Cause cause = event.getCause();
        if ((cause == EntityRemoveEvent.Cause.DESPAWN && protectNaturalDespawn)
                || (cause == EntityRemoveEvent.Cause.PLUGIN && recoverPluginRemoval)) {
            recover(projectile);
        }
    }

    private void maintain(Projectile projectile) {
        projectile.setPersistent(true);
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setLifetimeTicks(0);
        }
    }

    private void markProtected(Projectile projectile, Player player) {
        projectile.setPersistent(true);
        projectile.getPersistentDataContainer().set(protectedKey, PersistentDataType.BYTE, (byte) 1);
        projectile.getPersistentDataContainer().set(shooterKey, PersistentDataType.STRING, player.getUniqueId().toString());
        maintain(projectile);
    }

    private boolean isProtected(Projectile projectile) {
        Byte value = projectile.getPersistentDataContainer().get(protectedKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private void recover(Projectile oldProjectile) {
        var location = oldProjectile.getLocation().clone();
        Vector velocity = oldProjectile.getVelocity().clone();
        EntityType type = oldProjectile.getType();
        UUID shooterId = readShooter(oldProjectile);

        Bukkit.getScheduler().runTask(this, () -> {
            if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4) || !type.isSpawnable()) {
                return;
            }
            try {
                Entity replacement = location.getWorld().spawnEntity(location, type);
                if (!(replacement instanceof Projectile projectile)) {
                    replacement.remove();
                    return;
                }
                projectile.setPersistent(true);
                projectile.setVelocity(velocity);
                projectile.getPersistentDataContainer().set(protectedKey, PersistentDataType.BYTE, (byte) 1);
                if (shooterId != null) {
                    projectile.getPersistentDataContainer().set(shooterKey, PersistentDataType.STRING, shooterId.toString());
                    Player player = Bukkit.getPlayer(shooterId);
                    if (player != null) {
                        projectile.setShooter(player);
                    }
                }
                maintain(projectile);
            } catch (RuntimeException ex) {
                getLogger().fine("Could not recover projectile " + type + ": " + ex.getMessage());
            }
        });
    }

    private UUID readShooter(Projectile projectile) {
        String value = projectile.getPersistentDataContainer().get(shooterKey, PersistentDataType.STRING);
        if (value == null) {
            return projectile.getShooter() instanceof Player player ? player.getUniqueId() : null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
