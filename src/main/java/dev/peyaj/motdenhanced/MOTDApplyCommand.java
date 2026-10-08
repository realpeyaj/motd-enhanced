package dev.peyaj.motdenhanced;

import dev.peyaj.motdenhanced.util.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Level;

public class MOTDApplyCommand implements CommandExecutor {
    protected final MOTDCommand parent;

    public MOTDApplyCommand(MOTDCommand parent) {
        this.parent = parent;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("motdenhanced.apply") && !sender.hasPermission("motdenhanced")) {
            parent.plugin.sendMessage(sender, Component
                    .text("You don't have permission to apply MOTD changes.")
                    .color(NamedTextColor.RED)
            );
            return true;
        }

        // Disk I/O stays off the main thread
        SchedulerUtil.runAsync(parent.plugin, () -> applyFromDisk(sender));
        return true;
    }

    private void applyFromDisk(CommandSender sender) {
        String motdText = "";
        Path propertiesPath = Paths.get("server.properties");
        if (Files.exists(propertiesPath)) {
            try (InputStream in = Files.newInputStream(propertiesPath)) {
                Properties properties = new Properties();
                properties.load(in);
                motdText = properties.getProperty("motd", "");
            } catch (IOException e) {
                parent.plugin.getLogger().log(Level.SEVERE, "Failed to read server.properties", e);
                parent.plugin.sendMessage(sender, Component
                        .text("Failed to read server.properties. Check console log.")
                        .color(NamedTextColor.RED)
                );
                return;
            }
        }

        String faviconBase64 = parent.plugin.getServerIconBase64();
        boolean success = parent.plugin.applyMotd(motdText, faviconBase64);

        if (success) {
            parent.plugin.sendMessage(sender, Component
                    .text("Applied current MOTD and server icon from disk.")
                    .color(NamedTextColor.GREEN)
            );
        } else {
            parent.plugin.sendMessage(sender, Component
                    .text("Failed to apply MOTD. Check console log.")
                    .color(NamedTextColor.RED)
            );
        }
    }
}
