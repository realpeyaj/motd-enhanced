package dev.peyaj.motdenhanced;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class MOTDEditorCommand implements CommandExecutor {
    protected final MOTDCommand parent;

    public MOTDEditorCommand(MOTDCommand parent) {
        this.parent = parent;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!sender.hasPermission("motdenhanced.editor") && !sender.hasPermission("motdenhanced")) {
            parent.plugin.sendMessage(sender, Component
                    .text("You don't have permission to use the MOTD editor.")
                    .color(NamedTextColor.RED)
            );
            return true;
        }

        if (parent.plugin.getWebServer() == null) {
            parent.plugin.sendMessage(sender, Component
                    .text("The local web editor is not running. Check your server logs or config.yml.")
                    .color(NamedTextColor.RED)
            );
            return true;
        }

        String token = parent.plugin.getSessionManager().createSession(sender.getName());
        int port = parent.plugin.getConfig().getInt("web.port", 25585);
        String host = parent.plugin.getConfig().getString("web.public-host", "").trim();

        if (host.equals("0.0.0.0") || host.equals("::") || host.equals("[::]")) {
            host = "";
        }

        if (host.isEmpty() && sender instanceof org.bukkit.entity.Player player) {
            try {
                java.net.InetSocketAddress vHost = player.getVirtualHost();
                if (vHost != null && vHost.getHostString() != null) {
                    String vHostStr = vHost.getHostString().trim();
                    if (!vHostStr.isEmpty() && !vHostStr.equals("0.0.0.0")) {
                        host = vHostStr;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        if (host.isEmpty() || host.contains("0.0.0.0") || host.equals("::") || host.equals("[::]")) {
            String serverIp = parent.plugin.getServer().getIp();
            if (serverIp != null && !serverIp.trim().isEmpty() && !serverIp.contains("0.0.0.0") && !serverIp.equals("::")) {
                host = serverIp.trim();
            } else {
                host = "localhost";
            }
        }

        if (host.contains(":") && !host.startsWith("[")) {
            host = "[" + host + "]";
        }

        String url = "http://" + host + ":" + port + "/?token=" + token;
        int lifetimeMinutes = parent.plugin.getConfig().getInt("web.token-lifetime-minutes", 30);

        Component message = Component.empty()
                .append(Component.text("MOTD Editor: ").color(NamedTextColor.GOLD))
                .append(Component.text(url)
                        .color(NamedTextColor.AQUA)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(url))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to open editor in browser"))))
                .append(Component.text(" (Valid for " + lifetimeMinutes + " minutes)").color(NamedTextColor.GRAY));

        parent.plugin.sendMessage(sender, message);
        parent.plugin.getLogger().info("Generated MOTD editor URL for " + sender.getName() + ": " + url);
        return true;
    }
}
