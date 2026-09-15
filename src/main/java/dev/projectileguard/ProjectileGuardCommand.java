package dev.projectileguard;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class ProjectileGuardCommand implements CommandExecutor {
    private final ProjectileGuardPlugin plugin;

    public ProjectileGuardCommand(ProjectileGuardPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("projectileguard.admin")) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPluginConfig();
            sender.sendMessage("ProjectileGuard configuration reloaded.");
            return true;
        }

        sender.sendMessage("Usage: /projectileguard reload");
        return true;
    }
}
