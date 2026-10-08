package dev.peyaj.motdenhanced.web;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();
    private final long lifetimeMillis;

    public SessionManager(int lifetimeMinutes) {
        this.lifetimeMillis = Math.max(1, lifetimeMinutes) * 60L * 1000L;
    }

    public String createSession(String creator) {
        cleanExpiredSessions();
        String token = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();
        sessions.put(token, new SessionData(creator, now + lifetimeMillis));
        return token;
    }

    public boolean validateSession(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        cleanExpiredSessions();
        SessionData data = sessions.get(token);
        if (data == null) {
            return false;
        }
        if (System.currentTimeMillis() > data.expiresAt) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    private void cleanExpiredSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> now > entry.getValue().expiresAt);
    }

    public void clear() {
        sessions.clear();
    }

    public static class SessionData {
        public final String creator;
        public final long expiresAt;

        public SessionData(String creator, long expiresAt) {
            this.creator = creator;
            this.expiresAt = expiresAt;
        }
    }
}
