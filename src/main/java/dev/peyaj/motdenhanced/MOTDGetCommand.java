package dev.peyaj.motdenhanced;

import dev.peyaj.motdenhanced.util.ServerPinger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class MOTDGetCommand implements CommandExecutor {
    protected final MOTDCommand parent;

    public MOTDGetCommand(MOTDCommand parent) {
        this.parent = parent;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("motdenhanced.apply") && !sender.hasPermission("motdenhanced")) {
            parent.plugin.sendMessage(sender, Component
                    .text("You don't have permission to execute this command.")
                    .color(NamedTextColor.RED)
            );
            return true;
        }

        if (args.length == 0) {
            parent.plugin.sendMessage(sender, Component
                    .text("Usage: /" + label + " get <server-ip>")
                    .color(NamedTextColor.YELLOW)
            );
            return true;
        }

        String target = args[0];
        parent.plugin.sendMessage(sender, Component
                .text("Fetching MOTD and icon from " + target + "...")
                .color(NamedTextColor.GRAY)
        );

        Bukkit.getScheduler().runTaskAsynchronously(parent.plugin, () -> {
            ServerPinger.PingResult result = ServerPinger.ping(target);
            if (!result.success) {
                parent.plugin.sendMessage(sender, Component
                        .text("Failed to fetch from " + target + ": " + (result.error != null ? result.error : "Server unreachable"))
                        .color(NamedTextColor.RED)
                );
                return;
            }

            boolean applied = parent.plugin.applyMotd(result.motd, result.favicon);
            if (applied) {
                parent.plugin.sendMessage(sender, Component
                        .text("Successfully copied and applied MOTD and icon from " + target)
                        .color(NamedTextColor.GREEN)
                );
            } else {
                parent.plugin.sendMessage(sender, Component
                        .text("Fetched MOTD but failed to apply to server.")
                        .color(NamedTextColor.RED)
                );
            }
        });

        return true;
    }
}
