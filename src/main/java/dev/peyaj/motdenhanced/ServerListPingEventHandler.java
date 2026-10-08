package dev.peyaj.motdenhanced;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

public class ServerListPingEventHandler implements Listener {
    protected final MOTDEnhancedPlugin plugin;

    ServerListPingEventHandler(MOTDEnhancedPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void handleServerPing(ServerListPingEvent event) {
        // Maintenance Mode Handler
        if (this.plugin.getConfig().getBoolean("maintenance.enabled", false)) {
            String maintenanceMotd = this.plugin.getConfig().getString("maintenance.motd",
                    "&c&lMAINTENANCE &8» &7Server is currently undergoing maintenance.\n&eFollow updates on our Discord: &b/discord");
            event.setMotd(ChatColor.translateAlternateColorCodes('&', maintenanceMotd));

            if (this.plugin.icon != null) {
                event.setServerIcon(this.plugin.icon);
            }

            try {
                if (event instanceof com.destroystokyo.paper.event.server.PaperServerListPingEvent paperEvent) {
                    String ver = this.plugin.getConfig().getString("maintenance.version-string", "&cMaintenance");
                    paperEvent.setVersion(ChatColor.translateAlternateColorCodes('&', ver));
                    paperEvent.setProtocolVersion(-1);
                }
            } catch (Throwable ignored) {
            }

            applyHoverSample(event);
            return;
        }

        // Player Count Customization
        if (this.plugin.getConfig().getBoolean("player-count.modify-max", false)) {
            int customMax = this.plugin.getConfig().getInt("player-count.max-players", 100);
            event.setMaxPlayers(customMax);
        }

        try {
            if (event instanceof com.destroystokyo.paper.event.server.PaperServerListPingEvent paperEvent) {
                if (this.plugin.getConfig().getBoolean("player-count.plus-one", false)) {
                    paperEvent.setNumPlayers(event.getNumPlayers() + 1);
                }

                if (this.plugin.getConfig().getBoolean("version-string.enabled", false)) {
                    String verText = this.plugin.getConfig().getString("version-string.text", "&b1.20 - 1.21.x");
                    paperEvent.setVersion(ChatColor.translateAlternateColorCodes('&', verText));
                }
            }
        } catch (Throwable ignored) {
        }

        String motdText = null;

        if (this.plugin.getConfig().getBoolean("rotation.enabled", false)) {
            java.util.List<String> list = this.plugin.getConfig().getStringList("rotation.motds");
            if (list != null && !list.isEmpty()) {
                int index = java.util.concurrent.ThreadLocalRandom.current().nextInt(list.size());
                motdText = list.get(index);
            }
        }

        if (motdText == null) {
            String active = this.plugin.motdText;
            motdText = active != null ? active : this.plugin.getServer().getMotd();
        }

        if (motdText != null) {
            event.setMotd(ChatColor.translateAlternateColorCodes('&', motdText));
        }

        if (this.plugin.icon != null) {
            event.setServerIcon(this.plugin.icon);
        }

        applyHoverSample(event);
    }

    private void applyHoverSample(ServerListPingEvent event) {
        java.util.List<String> hoverLines = this.plugin.getConfig().getStringList("hover-text");
        if (hoverLines == null || hoverLines.isEmpty()) {
            return;
        }

        try {
            if (event instanceof com.destroystokyo.paper.event.server.PaperServerListPingEvent paperEvent) {
                java.util.List<com.destroystokyo.paper.event.server.PaperServerListPingEvent.ListedPlayerInfo> listed = paperEvent.getListedPlayers();
                listed.clear();
                for (String line : hoverLines) {
                    String resolved = line.replace("%online%", String.valueOf(event.getNumPlayers()))
                                          .replace("%max%", String.valueOf(event.getMaxPlayers()));
                    String formatted = ChatColor.translateAlternateColorCodes('&', resolved);
                    listed.add(new com.destroystokyo.paper.event.server.PaperServerListPingEvent.ListedPlayerInfo(formatted, java.util.UUID.randomUUID()));
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
