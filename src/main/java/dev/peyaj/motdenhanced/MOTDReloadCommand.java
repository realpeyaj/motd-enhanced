package dev.peyaj.motdenhanced;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class MOTDReloadCommand implements CommandExecutor {
    protected final MOTDCommand parent;

    public MOTDReloadCommand(MOTDCommand parent) {
        this.parent = parent;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("motdenhanced.reload") && !sender.hasPermission("motdenhanced")) {
            parent.plugin.sendMessage(sender, Component
                    .text("You don't have permission to reload the MOTD configuration.")
                    .color(NamedTextColor.RED)
            );
            return true;
        }

        parent.plugin.reloadPluginConfig();
        parent.plugin.sendMessage(sender, Component
                .text("MOTD configuration reloaded successfully.")
                .color(NamedTextColor.GREEN)
        );
        return true;
    }
}
