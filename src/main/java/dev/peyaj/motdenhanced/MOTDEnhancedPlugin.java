package dev.peyaj.motdenhanced;

import dev.peyaj.motdenhanced.util.SchedulerUtil;
import dev.peyaj.motdenhanced.web.LocalWebServer;
import dev.peyaj.motdenhanced.web.SessionManager;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.CachedServerIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Properties;
import java.util.logging.Level;

public class MOTDEnhancedPlugin extends JavaPlugin {

    protected CachedServerIcon icon = null;
    protected volatile String motdText = null;
    protected BukkitAudiences adventure;

    protected LocalWebServer webServer;
    protected SessionManager sessionManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        int lifetimeMinutes = getConfig().getInt("web.token-lifetime-minutes", 30);
        this.sessionManager = new SessionManager(lifetimeMinutes);

        startWebServer();

        org.bukkit.command.PluginCommand cmd = this.getCommand("motd-enhanced");
        if (cmd != null) {
            cmd.setExecutor(new MOTDCommand(this));
        }

        try {
            this.adventure = BukkitAudiences.create(this);
        } catch (Throwable t) {
            this.adventure = null;
        }

        loadInitialIcon();
        this.motdText = getServer().getMotd();

        getServer().getPluginManager().registerEvents(new ServerListPingEventHandler(this), this);

        int pluginId = 34571;
        Metrics metrics = new Metrics(this, pluginId);
        metrics.addCustomChart(new SimplePie("web_editor_enabled", () ->
                getConfig().getBoolean("web.enabled", true) ? "Enabled" : "Disabled"));
        metrics.addCustomChart(new SimplePie("rotation_enabled", () ->
                getConfig().getBoolean("rotation.enabled", false) ? "Enabled" : "Disabled"));
        metrics.addCustomChart(new SimplePie("maintenance_mode", () ->
                getConfig().getBoolean("maintenance.enabled", false) ? "Enabled" : "Disabled"));
        metrics.addCustomChart(new SimplePie("custom_version_string", () ->
                getConfig().getBoolean("version-string.enabled", false) ? "Enabled" : "Disabled"));
    }

    @Override
    public void onDisable() {
        stopWebServer();

        if (this.sessionManager != null) {
            this.sessionManager.clear();
            this.sessionManager = null;
        }

        if (this.adventure != null) {
            try {
                this.adventure.close();
            } catch (Throwable ignored) {
            }
            this.adventure = null;
        }

        this.motdText = null;
        this.icon = null;
    }

    public synchronized void startWebServer() {
        if (!getConfig().getBoolean("web.enabled", true)) {
            return;
        }

        String bindAddress = getConfig().getString("web.bind-address", "0.0.0.0");
        int port = getConfig().getInt("web.port", 25585);

        try {
            this.webServer = new LocalWebServer(this, bindAddress, port);
            this.webServer.start();
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to start local MOTD web editor on " + bindAddress + ":" + port, e);
            this.webServer = null;
        }
    }

    public synchronized void stopWebServer() {
        if (this.webServer != null) {
            this.webServer.stop();
            this.webServer = null;
        }
    }

    public synchronized void reloadPluginConfig() {
        reloadConfig();
        stopWebServer();
        int lifetimeMinutes = getConfig().getInt("web.token-lifetime-minutes", 30);
        this.sessionManager = new SessionManager(lifetimeMinutes);
        startWebServer();
    }

    private void loadInitialIcon() {
        File iconFile = new File("server-icon.png");
        if (iconFile.exists()) {
            try {
                this.icon = getServer().loadServerIcon(iconFile);
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to load initial server-icon.png", e);
            }
        }
    }

    public String getMotdText() {
        String text = this.motdText != null ? this.motdText : getServer().getMotd();
        if (text != null && text.contains("§")) {
            text = text.replace('§', '&');
        }
        return text != null ? text : "";
    }

    public @Nullable String getServerIconBase64() {
        File iconFile = new File("server-icon.png");
        if (!iconFile.exists()) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(iconFile.toPath());
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Failed to read server-icon.png", e);
            return null;
        }
    }

    public boolean applyMotd(String text, @Nullable String faviconBase64) {
        boolean success = true;

        this.motdText = text;

        // Update server.properties
        try {
            Path propertiesPath = Paths.get("server.properties");
            Properties properties = new Properties();
            if (Files.exists(propertiesPath)) {
                try (InputStream in = Files.newInputStream(propertiesPath)) {
                    properties.load(in);
                }
            }
            String propertiesMotd = org.bukkit.ChatColor.translateAlternateColorCodes('&', text != null ? text : "");
            properties.setProperty("motd", propertiesMotd);
            try (OutputStream out = Files.newOutputStream(propertiesPath)) {
                properties.store(out, null);
            }
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to write server.properties", e);
            success = false;
        }

        // Update server-icon.png
        if (faviconBase64 != null && faviconBase64.contains(",")) {
            try {
                String b64 = faviconBase64.split(",")[1];
                byte[] imageBytes = Base64.getDecoder().decode(b64);
                try (ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes)) {
                    BufferedImage image = ImageIO.read(bis);
                    if (image != null) {
                        File iconFile = new File("server-icon.png");
                        ImageIO.write(image, "png", iconFile);

                        // Load server icon safely on primary thread
                        SchedulerUtil.runSync(this, () -> {
                            try {
                                this.icon = getServer().loadServerIcon(iconFile);
                            } catch (Exception e) {
                                getLogger().log(Level.WARNING, "Failed to load live server icon", e);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to write server-icon.png", e);
                success = false;
            }
        } else if (faviconBase64 == null) {
            // Cleared icon
            File iconFile = new File("server-icon.png");
            if (iconFile.exists()) {
                iconFile.delete();
            }
            this.icon = null;
        }

        return success;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public LocalWebServer getWebServer() {
        return webServer;
    }

    public @Nullable BukkitAudiences adventure() {
        return this.adventure;
    }

    public void sendMessage(@NotNull CommandSender sender, @NotNull Component message) {
        try {
            sender.sendMessage(message);
        } catch (Throwable t) {
            try {
                if (this.adventure != null) {
                    this.adventure.sender(sender).sendMessage(message);
                } else {
                    sender.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(message));
                }
            } catch (Throwable ignored) {
                sender.sendMessage(message.toString());
            }
        }
    }
}
