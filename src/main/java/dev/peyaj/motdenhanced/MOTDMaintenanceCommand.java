package dev.peyaj.motdenhanced;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class MOTDMaintenanceCommand implements CommandExecutor {
    protected final MOTDCommand parent;

    public MOTDMaintenanceCommand(MOTDCommand parent) {
        this.parent = parent;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("motdenhanced.maintenance") && !sender.hasPermission("motdenhanced")) {
            parent.plugin.sendMessage(sender, Component
                    .text("You don't have permission to manage maintenance mode.")
                    .color(NamedTextColor.RED)
            );
            return true;
        }

        boolean current = parent.plugin.getConfig().getBoolean("maintenance.enabled", false);

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            NamedTextColor statusColor = current ? NamedTextColor.RED : NamedTextColor.GREEN;
            String statusText = current ? "ENABLED" : "DISABLED";
            parent.plugin.sendMessage(sender, Component
                    .text("Maintenance mode is currently ")
                    .color(NamedTextColor.GOLD)
                    .append(Component.text(statusText, statusColor))
                    .append(Component.text(". Use /" + label + " maintenance <on|off|toggle> to change.", NamedTextColor.GRAY))
            );
            return true;
        }

        boolean newState;
        if (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("true")) {
            newState = true;
        } else if (args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("disable") || args[0].equalsIgnoreCase("false")) {
            newState = false;
        } else if (args[0].equalsIgnoreCase("toggle")) {
            newState = !current;
        } else {
            parent.plugin.sendMessage(sender, Component
                    .text("Usage: /" + label + " maintenance <on|off|toggle|status>")
                    .color(NamedTextColor.RED)
            );
            return true;
        }

        parent.plugin.getConfig().set("maintenance.enabled", newState);
        parent.plugin.saveConfig();

        if (newState) {
            parent.plugin.sendMessage(sender, Component
                    .text("✔ Maintenance mode has been ")
                    .color(NamedTextColor.GOLD)
                    .append(Component.text("ENABLED", NamedTextColor.RED))
                    .append(Component.text(". Players will now see the maintenance MOTD and version indicator.", NamedTextColor.GRAY))
            );
        } else {
            parent.plugin.sendMessage(sender, Component
                    .text("✔ Maintenance mode has been ")
                    .color(NamedTextColor.GOLD)
                    .append(Component.text("DISABLED", NamedTextColor.GREEN))
                    .append(Component.text(". Regular server MOTD and ping response restored.", NamedTextColor.GRAY))
            );
        }

        return true;
    }
}
