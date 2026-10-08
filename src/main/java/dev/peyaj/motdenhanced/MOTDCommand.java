package dev.peyaj.motdenhanced;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class MOTDCommand implements CommandExecutor, TabExecutor {
    protected final MOTDEnhancedPlugin plugin;
    public final HashMap<String, CommandExecutor> subCommands = new HashMap<>();

    public MOTDCommand(MOTDEnhancedPlugin plugin) {
        this.plugin = plugin;
        this.registerSubCommands();
    }

    private void registerSubCommands() {
        this.subCommands.put("editor", new MOTDEditorCommand(this));
        this.subCommands.put("apply", new MOTDApplyCommand(this));
        this.subCommands.put("reload", new MOTDReloadCommand(this));
        this.subCommands.put("get", new MOTDGetCommand(this));
        this.subCommands.put("maintenance", new MOTDMaintenanceCommand(this));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!sender.hasPermission("motdenhanced")) {
            plugin.sendMessage(sender, Component
                    .text("You don't have the permissions required to execute this command.")
                    .color(NamedTextColor.RED)
            );
            return true;
        }

        if (args.length == 0) {
            plugin.sendMessage(sender, Component
                    .text("Usage: /" + alias + " <editor|apply|reload|get|maintenance>")
                    .color(NamedTextColor.YELLOW)
            );
            return true;
        }

        CommandExecutor subCommand = subCommands.get(args[0].toLowerCase());
        if (subCommand == null) {
            plugin.sendMessage(sender, Component
                    .text("Unknown subcommand. Usage: /" + alias + " <editor|apply|reload|get|maintenance>")
                    .color(NamedTextColor.RED)
            );
            return true;
        }

        return subCommand.onCommand(sender, command, alias, Arrays.copyOfRange(args, 1, args.length));
    }

    public void log(Level level, String message) {
        plugin.getLogger().log(level, message);
    }

    public MOTDEnhancedPlugin getPlugin() {
        return plugin;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return subCommands.keySet()
                    .stream()
                    .filter(arg -> arg.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("maintenance")) {
            return Arrays.asList("on", "off", "toggle", "status")
                    .stream()
                    .filter(arg -> arg.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
