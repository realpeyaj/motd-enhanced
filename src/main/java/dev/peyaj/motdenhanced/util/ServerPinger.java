package dev.peyaj.motdenhanced.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.stream.Collectors;

public class ServerPinger {

    public static class PingResult {
        public final boolean success;
        public final String motd;
        public final String favicon;
        public final String version;
        public final int onlinePlayers;
        public final int maxPlayers;
        public final String error;

        public PingResult(boolean success, String motd, String favicon, String version, int onlinePlayers, int maxPlayers, String error) {
            this.success = success;
            this.motd = motd != null ? motd : "";
            this.favicon = favicon;
            this.version = version;
            this.onlinePlayers = onlinePlayers;
            this.maxPlayers = maxPlayers;
            this.error = error;
        }

        public static PingResult fail(String error) {
            return new PingResult(false, null, null, null, 0, 0, error);
        }
    }

    public static PingResult ping(String rawAddress) {
        if (rawAddress == null || rawAddress.trim().isEmpty()) {
            return PingResult.fail("Server address cannot be empty");
        }

        String cleaned = rawAddress.trim()
                .replaceFirst("^[a-zA-Z]+://", "")
                .split("/")[0]
                .trim();

        String host = cleaned;
        int port = 25565;

        if (cleaned.contains(":")) {
            String[] parts = cleaned.split(":");
            host = parts[0];
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return PingResult.fail("Invalid port number");
            }
        } else {
            InetSocketAddress srvResolved = resolveSrv(host);
            if (srvResolved != null) {
                host = srvResolved.getHostString();
                port = srvResolved.getPort();
            }
        }

        try {
            return pingTcp(host, port);
        } catch (Exception tcpEx) {
            if (!isPrivateOrLocal(host)) {
                try {
                    return pingViaApi(cleaned);
                } catch (Exception ignored) {
                }
            }
            return PingResult.fail("Could not connect to server: " + tcpEx.getMessage());
        }
    }

    private static PingResult pingTcp(String host, int port) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3500);
            socket.setSoTimeout(3500);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream handshake = new DataOutputStream(b);
            writeVarInt(handshake, 0x00);
            writeVarInt(handshake, -1);
            byte[] hostBytes = host.getBytes(StandardCharsets.UTF_8);
            writeVarInt(handshake, hostBytes.length);
            handshake.write(hostBytes);
            handshake.writeShort(port);
            writeVarInt(handshake, 1);

            writeVarInt(out, b.size());
            out.write(b.toByteArray());

            out.writeByte(0x01);
            out.writeByte(0x00);
            out.flush();

            readVarInt(in);
            int packetId = readVarInt(in);
            if (packetId != 0x00) {
                throw new IOException("Unexpected packet id: " + packetId);
            }

            int stringLength = readVarInt(in);
            byte[] jsonBytes = new byte[stringLength];
            in.readFully(jsonBytes);

            String jsonStr = new String(jsonBytes, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();

            String motd = "";
            if (root.has("description")) {
                motd = parseDescription(root.get("description"));
            }

            String favicon = root.has("favicon") && !root.get("favicon").isJsonNull()
                    ? root.get("favicon").getAsString()
                    : null;

            String version = null;
            if (root.has("version") && root.get("version").isJsonObject()) {
                JsonObject vObj = root.getAsJsonObject("version");
                if (vObj.has("name")) {
                    version = vObj.get("name").getAsString();
                }
            }

            int online = 0;
            int max = 0;
            if (root.has("players") && root.get("players").isJsonObject()) {
                JsonObject pObj = root.getAsJsonObject("players");
                if (pObj.has("online")) online = pObj.get("online").getAsInt();
                if (pObj.has("max")) max = pObj.get("max").getAsInt();
            }

            return new PingResult(true, motd, favicon, version, online, max, null);
        }
    }

    private static PingResult pingViaApi(String hostAndPort) throws IOException {
        URL url = URI.create("https://api.mcstatus.io/v2/status/java/" + hostAndPort).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(4000);
        conn.setRequestProperty("User-Agent", "motd-enhanced/1.0");
        conn.connect();

        if (conn.getResponseCode() != 200) {
            throw new IOException("API HTTP " + conn.getResponseCode());
        }

        try (InputStream is = conn.getInputStream();
             InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(isr)) {
            String jsonStr = br.lines().collect(Collectors.joining("\n"));
            JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();

            if (!json.has("online") || !json.get("online").getAsBoolean()) {
                throw new IOException("Server is offline");
            }

            String motd = "";
            if (json.has("motd") && json.get("motd").isJsonObject()) {
                JsonObject motdObj = json.getAsJsonObject("motd");
                if (motdObj.has("raw")) {
                    motd = motdObj.get("raw").getAsString().replace('§', '&');
                } else if (motdObj.has("clean")) {
                    motd = motdObj.get("clean").getAsString();
                }
            }

            String icon = json.has("icon") && !json.get("icon").isJsonNull()
                    ? json.get("icon").getAsString()
                    : null;

            String version = null;
            if (json.has("version") && json.get("version").isJsonObject()) {
                JsonObject v = json.getAsJsonObject("version");
                if (v.has("name_clean")) version = v.get("name_clean").getAsString();
            }

            int online = 0;
            int max = 0;
            if (json.has("players") && json.get("players").isJsonObject()) {
                JsonObject p = json.getAsJsonObject("players");
                if (p.has("online")) online = p.get("online").getAsInt();
                if (p.has("max")) max = p.get("max").getAsInt();
            }

            return new PingResult(true, motd, icon, version, online, max, null);
        }
    }

    private static String parseDescription(JsonElement element) {
        if (element == null || element.isJsonNull()) return "";
        if (element.isJsonPrimitive()) {
            return element.getAsString().replace('§', '&');
        }
        if (element.isJsonObject()) {
            StringBuilder sb = new StringBuilder();
            parseComponent(element.getAsJsonObject(), sb);
            return sb.toString().replace('§', '&');
        }
        if (element.isJsonArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonElement item : element.getAsJsonArray()) {
                sb.append(parseDescription(item));
            }
            return sb.toString().replace('§', '&');
        }
        return "";
    }

    private static void parseComponent(JsonObject obj, StringBuilder sb) {
        if (obj.has("color")) {
            String color = obj.get("color").getAsString();
            String code = colorToCode(color);
            if (code != null) sb.append(code);
        }
        if (obj.has("bold") && obj.get("bold").getAsBoolean()) sb.append("&l");
        if (obj.has("italic") && obj.get("italic").getAsBoolean()) sb.append("&o");
        if (obj.has("underlined") && obj.get("underlined").getAsBoolean()) sb.append("&n");
        if (obj.has("strikethrough") && obj.get("strikethrough").getAsBoolean()) sb.append("&m");
        if (obj.has("obfuscated") && obj.get("obfuscated").getAsBoolean()) sb.append("&k");

        if (obj.has("text")) {
            sb.append(obj.get("text").getAsString());
        }

        if (obj.has("extra") && obj.get("extra").isJsonArray()) {
            for (JsonElement child : obj.get("extra").getAsJsonArray()) {
                if (child.isJsonObject()) {
                    parseComponent(child.getAsJsonObject(), sb);
                } else if (child.isJsonPrimitive()) {
                    sb.append(child.getAsString());
                }
            }
        }
    }

    private static String colorToCode(String color) {
        if (color == null || color.isEmpty()) return null;
        if (color.startsWith("#")) {
            return "&#" + color.substring(1);
        }
        switch (color.toLowerCase()) {
            case "black": return "&0";
            case "dark_blue": return "&1";
            case "dark_green": return "&2";
            case "dark_aqua": return "&3";
            case "dark_red": return "&4";
            case "dark_purple": return "&5";
            case "gold": return "&6";
            case "gray": return "&7";
            case "dark_gray": return "&8";
            case "blue": return "&9";
            case "green": return "&a";
            case "aqua": return "&b";
            case "red": return "&c";
            case "light_purple": return "&d";
            case "yellow": return "&e";
            case "white": return "&f";
            case "reset": return "&r";
            default:
                if (color.length() == 1) return "&" + color;
                return null;
        }
    }

    private static InetSocketAddress resolveSrv(String host) {
        if (host == null || host.isEmpty() || host.equalsIgnoreCase("localhost") || host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            return null;
        }
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes("_minecraft._tcp." + host, new String[]{"SRV"});
            Attribute srv = attrs.get("SRV");
            if (srv != null && srv.size() > 0) {
                String[] parts = srv.get(0).toString().split(" ");
                int port = Integer.parseInt(parts[2]);
                String target = parts[3].endsWith(".") ? parts[3].substring(0, parts[3].length() - 1) : parts[3];
                return new InetSocketAddress(target, port);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean isPrivateOrLocal(String host) {
        if (host == null) return false;
        String h = host.toLowerCase().trim();
        return h.equals("localhost") || h.equals("127.0.0.1") || h.startsWith("192.168.") || h.startsWith("10.") || h.startsWith("172.");
    }

    public static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.writeByte(value);
                return;
            } else {
                out.writeByte((value & 0x7F) | 0x80);
                value >>>= 7;
            }
        }
    }

    public static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int length = 0;
        byte currentByte;
        while (true) {
            currentByte = in.readByte();
            value |= (currentByte & 0x7F) << (length++ * 7);
            if (length > 5) throw new IOException("VarInt too big");
            if ((currentByte & 0x80) != 0x80) break;
        }
        return value;
    }
}
