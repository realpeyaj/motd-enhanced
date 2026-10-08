package dev.peyaj.motdenhanced.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.peyaj.motdenhanced.MOTDEnhancedPlugin;
import dev.peyaj.motdenhanced.util.ServerPinger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class LocalWebServer {
    private final MOTDEnhancedPlugin plugin;
    private final String bindAddress;
    private final int port;
    private HttpServer server;
    private ExecutorService executor;
    private byte[] cachedIndexHtml;
    private byte[] cachedDefaultIconSvg;
    private byte[] cachedMinecraftWoff2;
    private byte[] cachedMinecraftBoldWoff2;
    private final Gson gson = new Gson();

    public LocalWebServer(MOTDEnhancedPlugin plugin, String bindAddress, int port) {
        this.plugin = plugin;
        this.bindAddress = (bindAddress == null || bindAddress.trim().isEmpty()) ? "0.0.0.0" : bindAddress.trim();
        this.port = port;
    }

    public synchronized void start() throws IOException {
        loadResources();
        InetSocketAddress address = new InetSocketAddress(bindAddress, port);
        server = HttpServer.create(address, 0);

        executor = Executors.newFixedThreadPool(4);
        server.setExecutor(executor);

        server.createContext("/", new IndexHandler());
        server.createContext("/default-server-icon.svg", new SvgHandler());
        server.createContext("/favicon.ico", new SvgHandler());
        server.createContext("/minecraft.woff2", new FontHandler());
        server.createContext("/minecraft-bold.woff2", new FontBoldHandler());
        server.createContext("/api/data", new DataHandler());
        server.createContext("/api/save", new SaveHandler());
        server.createContext("/api/query", new QueryHandler());

        server.start();
        plugin.getLogger().info("Local MOTD web editor listening on " + bindAddress + ":" + port);
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
        plugin.getLogger().info("Local MOTD web editor stopped");
    }

    private void loadResources() {
        cachedIndexHtml = loadResourceBytes("/web/index.html");
        cachedDefaultIconSvg = loadResourceBytes("/web/default-server-icon.svg");
        cachedMinecraftWoff2 = loadResourceBytes("/web/minecraft.woff2");
        cachedMinecraftBoldWoff2 = loadResourceBytes("/web/minecraft-bold.woff2");
    }

    private byte[] loadResourceBytes(String resourcePath) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                return new byte[0];
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
            return out.toByteArray();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load resource " + resourcePath, e);
            return new byte[0];
        }
    }

    private class IndexHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed".getBytes(StandardCharsets.UTF_8), "text/plain");
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            sendResponse(exchange, 200, cachedIndexHtml, "text/html; charset=UTF-8");
        }
    }

    private class SvgHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed".getBytes(StandardCharsets.UTF_8), "text/plain");
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", "image/svg+xml; charset=UTF-8");
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
            sendResponse(exchange, 200, cachedDefaultIconSvg, "image/svg+xml; charset=UTF-8");
        }
    }

    private class FontHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed".getBytes(StandardCharsets.UTF_8), "text/plain");
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", "font/woff2");
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=604800");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            sendResponse(exchange, 200, cachedMinecraftWoff2, "font/woff2");
        }
    }

    private class FontBoldHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed".getBytes(StandardCharsets.UTF_8), "text/plain");
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", "font/woff2");
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=604800");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            sendResponse(exchange, 200, cachedMinecraftBoldWoff2, "font/woff2");
        }
    }

    private class DataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed".getBytes(StandardCharsets.UTF_8), "text/plain");
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI());
            String token = query.get("token");

            if (!plugin.getSessionManager().validateSession(token)) {
                JsonObject error = new JsonObject();
                error.addProperty("success", false);
                error.addProperty("message", "Invalid or expired session token");
                sendJsonResponse(exchange, 401, error);
                return;
            }

            JsonObject data = new JsonObject();
            data.addProperty("success", true);
            data.addProperty("text", plugin.getMotdText());
            data.addProperty("favicon", plugin.getServerIconBase64());
            data.addProperty("serverName", plugin.getServer().getName());
            data.addProperty("maxPlayers", plugin.getServer().getMaxPlayers());
            data.addProperty("rotationEnabled", plugin.getConfig().getBoolean("rotation.enabled", false));
            data.addProperty("maintenanceEnabled", plugin.getConfig().getBoolean("maintenance.enabled", false));
            data.addProperty("maintenanceMotd", plugin.getConfig().getString("maintenance.motd",
                    "&c&lMAINTENANCE &8» &7Server is currently undergoing maintenance.\n&eFollow updates on our Discord: &b/discord"));
            data.addProperty("maintenanceVersion", plugin.getConfig().getString("maintenance.version-string", "&cMaintenance"));
            data.addProperty("versionStringEnabled", plugin.getConfig().getBoolean("version-string.enabled", false));
            data.addProperty("versionString", plugin.getConfig().getString("version-string.text", "&b1.20 - 1.21.x"));
            data.addProperty("modifyMaxPlayers", plugin.getConfig().getBoolean("player-count.modify-max", false));
            data.addProperty("customMaxPlayers", plugin.getConfig().getInt("player-count.max-players", plugin.getServer().getMaxPlayers()));
            data.addProperty("plusOnePlayer", plugin.getConfig().getBoolean("player-count.plus-one", false));

            JsonArray rotationArr = new JsonArray();
            for (String r : plugin.getConfig().getStringList("rotation.motds")) {
                rotationArr.add(r);
            }
            data.add("rotationMotds", rotationArr);

            JsonArray hoverArr = new JsonArray();
            for (String h : plugin.getConfig().getStringList("hover-text")) {
                hoverArr.add(h);
            }
            data.add("hoverText", hoverArr);

            sendJsonResponse(exchange, 200, data);
        }
    }

    private class SaveHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed".getBytes(StandardCharsets.UTF_8), "text/plain");
                return;
            }

            InputStream is = exchange.getRequestBody();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int read;
            while ((read = is.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            String body = baos.toString(StandardCharsets.UTF_8);

            try {
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                String token = json.has("token") ? json.get("token").getAsString() : null;

                if (!plugin.getSessionManager().validateSession(token)) {
                    JsonObject error = new JsonObject();
                    error.addProperty("success", false);
                    error.addProperty("message", "Invalid or expired session token");
                    sendJsonResponse(exchange, 401, error);
                    return;
                }

                String text = json.has("text") ? json.get("text").getAsString() : "";
                String favicon = json.has("favicon") && !json.get("favicon").isJsonNull() ? json.get("favicon").getAsString() : null;

                boolean success = plugin.applyMotd(text, favicon);

                if (json.has("rotationEnabled")) {
                    plugin.getConfig().set("rotation.enabled", json.get("rotationEnabled").getAsBoolean());
                }
                if (json.has("rotationMotds") && json.get("rotationMotds").isJsonArray()) {
                    java.util.List<String> list = new java.util.ArrayList<>();
                    json.get("rotationMotds").getAsJsonArray().forEach(el -> list.add(el.getAsString()));
                    plugin.getConfig().set("rotation.motds", list);
                }
                if (json.has("hoverText") && json.get("hoverText").isJsonArray()) {
                    java.util.List<String> list = new java.util.ArrayList<>();
                    json.get("hoverText").getAsJsonArray().forEach(el -> list.add(el.getAsString()));
                    plugin.getConfig().set("hover-text", list);
                }
                if (json.has("maintenanceEnabled")) {
                    plugin.getConfig().set("maintenance.enabled", json.get("maintenanceEnabled").getAsBoolean());
                }
                if (json.has("maintenanceMotd")) {
                    plugin.getConfig().set("maintenance.motd", json.get("maintenanceMotd").getAsString());
                }
                if (json.has("maintenanceVersion")) {
                    plugin.getConfig().set("maintenance.version-string", json.get("maintenanceVersion").getAsString());
                }
                if (json.has("versionStringEnabled")) {
                    plugin.getConfig().set("version-string.enabled", json.get("versionStringEnabled").getAsBoolean());
                }
                if (json.has("versionString")) {
                    plugin.getConfig().set("version-string.text", json.get("versionString").getAsString());
                }
                if (json.has("modifyMaxPlayers")) {
                    plugin.getConfig().set("player-count.modify-max", json.get("modifyMaxPlayers").getAsBoolean());
                }
                if (json.has("customMaxPlayers")) {
                    plugin.getConfig().set("player-count.max-players", json.get("customMaxPlayers").getAsInt());
                }
                if (json.has("plusOnePlayer")) {
                    plugin.getConfig().set("player-count.plus-one", json.get("plusOnePlayer").getAsBoolean());
                }
                plugin.saveConfig();

                JsonObject response = new JsonObject();
                response.addProperty("success", success);
                if (success) {
                    response.addProperty("message", "MOTD and server icon saved and applied");
                    sendJsonResponse(exchange, 200, response);
                } else {
                    response.addProperty("message", "Failed to apply MOTD to server");
                    sendJsonResponse(exchange, 500, response);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to parse save payload", e);
                JsonObject error = new JsonObject();
                error.addProperty("success", false);
                error.addProperty("message", "Malformed request payload");
                sendJsonResponse(exchange, 400, error);
            }
        }
    }

    private class QueryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed".getBytes(StandardCharsets.UTF_8), "text/plain");
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI());
            String token = query.get("token");

            if (!plugin.getSessionManager().validateSession(token)) {
                JsonObject error = new JsonObject();
                error.addProperty("success", false);
                error.addProperty("message", "Invalid or expired session token");
                sendJsonResponse(exchange, 401, error);
                return;
            }

            String targetServer = query.get("server");
            if (targetServer == null || targetServer.trim().isEmpty()) {
                JsonObject error = new JsonObject();
                error.addProperty("success", false);
                error.addProperty("message", "Server address cannot be empty");
                sendJsonResponse(exchange, 400, error);
                return;
            }

            ServerPinger.PingResult result = ServerPinger.ping(targetServer);
            JsonObject res = new JsonObject();
            res.addProperty("success", result.success);
            if (result.success) {
                res.addProperty("motd", result.motd);
                if (result.favicon != null) {
                    res.addProperty("favicon", result.favicon);
                }
                if (result.version != null) {
                    res.addProperty("version", result.version);
                }
                res.addProperty("onlinePlayers", result.onlinePlayers);
                res.addProperty("maxPlayers", result.maxPlayers);
                sendJsonResponse(exchange, 200, res);
            } else {
                res.addProperty("message", result.error != null ? result.error : "Failed to ping server");
                sendJsonResponse(exchange, 200, res);
            }
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, JsonObject json) throws IOException {
        byte[] bytes = gson.toJson(json).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        sendResponse(exchange, statusCode, bytes, "application/json; charset=UTF-8");
    }

    private void sendResponse(HttpExchange exchange, int statusCode, byte[] bytes, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, String> parseQuery(URI uri) {
        Map<String, String> result = new HashMap<>();
        String query = uri.getQuery();
        if (query == null || query.isEmpty()) {
            return result;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0 && idx < pair.length() - 1) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                result.put(key, val);
            } else if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                result.put(key, "");
            }
        }
        return result;
    }
}
