package dev.projectileguard;

import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class ProjectileGuardPlugin extends JavaPlugin implements Listener {
    private NamespacedKey protectedKey;
    private NamespacedKey shooterKey;
    private final Set<EntityType> protectedTypes = EnumSet.noneOf(EntityType.class);
    private int checkInterval;
    private boolean protectNaturalDespawn;
    private boolean protectPluginRemoval;
    private boolean adoptExisting;

    @Override
    public void onEnable() {
        protectedKey = new NamespacedKey(this, "protected_projectile");
        shooterKey = new NamespacedKey(this, "player_shooter_uuid");
        saveDefaultConfig();
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("projectileguard") != null) {
            getCommand("projectileguard").setExecutor(new ProjectileGuardCommand(this));
        }
        startProtectionTask();
        getLogger().info("ProjectileGuard enabled. Protected projectile types: " + protectedTypes.size());
    }

    private void loadSettings() {
        checkInterval = Math.max(1, getConfig().getInt("check-interval-ticks", 1));
        protectNaturalDespawn = getConfig().getBoolean("prevent-natural-despawn", true);
        protectPluginRemoval = getConfig().getBoolean("prevent-plugin-removal", true);
        adoptExisting = getConfig().getBoolean("adopt-existing-player-projectiles", true);
        protectedTypes.clear();
        for (String raw : getConfig().getStringList("protected-projectiles")) {
            try {
                protectedTypes.add(EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                getLogger().warning("Unknown projectile EntityType in config: " + raw);
            }
        }
    }

    private void startProtectionTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (!(entity instanceof Projectile projectile)) continue;
                    if (!protectedTypes.contains(projectile.getType())) continue;
                    if (!isProtected(projectile)) {
                        if (adoptExisting && projectile.getShooter() instanceof Player player) {
                            markProtected(projectile, player);
                        } else {
                            continue;
                        }
                    }
                    entity.setPersistent(true);
                    if (projectile instanceof AbstractArrow arrow) {
                        arrow.setLifetimeTicks(0);
                    }
                }
            }
        }, 1L, checkInterval);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!protectedTypes.contains(projectile.getType())) return;
        if (!(projectile.getShooter() instanceof Player player)) return;
        markProtected(projectile, player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityRemove(EntityRemoveEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Projectile projectile)) return;
        if (!protectedTypes.contains(entity.getType()) || !isProtected(projectile)) return;

        EntityRemoveEvent.Cause cause = event.getCause();
        if (cause == EntityRemoveEvent.Cause.DESPAWN && protectNaturalDespawn) {
            restoreProjectile(projectile);
        } else if (cause == EntityRemoveEvent.Cause.PLUGIN && protectPluginRemoval) {
            restoreProjectile(projectile);
        }
    }

    private void restoreProjectile(Projectile oldProjectile) {
        Location location = oldProjectile.getLocation().clone();
        Vector velocity = oldProjectile.getVelocity().clone();
        World world = oldProjectile.getWorld();
        EntityType type = oldProjectile.getType();
        UUID shooterUuid = getShooterUuid(oldProjectile);

        Bukkit.getScheduler().runTask(this, () -> {
            if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return;
            try {
                Entity replacement = world.spawnEntity(location, type);
                if (!(replacement instanceof Projectile projectile)) {
                    replacement.remove();
                    return;
                }
                replacement.setPersistent(true);
                projectile.setVelocity(velocity);
                projectile.getPersistentDataContainer().set(protectedKey, PersistentDataType.BYTE, (byte) 1);
                if (shooterUuid != null) {
                    projectile.getPersistentDataContainer().set(shooterKey, PersistentDataType.STRING, shooterUuid.toString());
                    Player player = Bukkit.getPlayer(shooterUuid);
                    if (player != null) projectile.setShooter(player);
                }
                if (projectile instanceof AbstractArrow arrow) arrow.setLifetimeTicks(0);
            } catch (IllegalArgumentException | IllegalStateException ex) {
                getLogger().fine("Could not restore protected projectile " + type + ": " + ex.getMessage());
            }
        });
    }

    private void markProtected(Projectile projectile, Player player) {
        projectile.setPersistent(true);
        projectile.getPersistentDataContainer().set(protectedKey, PersistentDataType.BYTE, (byte) 1);
        projectile.getPersistentDataContainer().set(shooterKey, PersistentDataType.STRING, player.getUniqueId().toString());
        if (projectile instanceof AbstractArrow arrow) arrow.setLifetimeTicks(0);
    }

    private boolean isProtected(Projectile projectile) {
        Byte value = projectile.getPersistentDataContainer().get(protectedKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private UUID getShooterUuid(Projectile projectile) {
        String value = projectile.getPersistentDataContainer().get(shooterKey, PersistentDataType.STRING);
        if (value == null) return null;
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
