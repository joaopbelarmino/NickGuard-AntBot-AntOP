package me.zetra.nickguard;

import com.google.gson.Gson;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class AlertService {
    private final NickGuardPlugin plugin;
    private final ConfigManager config;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    // One gate per category: noisy sources (e.g. /pl spam) cannot consume the slot of 2FA/identity alerts.
    private final Map<String, Gate> gates = new ConcurrentHashMap<>();
    private final AtomicLong discordBlockedUntil = new AtomicLong();
    private final AtomicLong discordDropped = new AtomicLong();
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private volatile boolean discordEnabled;
    private volatile String webhook;
    private volatile long intervalMillis;

    public AlertService(NickGuardPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        reload();
    }

    public void reload() {
        discordEnabled = plugin.getConfig().getBoolean("alerts.discord.enabled", false);
        webhook = plugin.getConfig().getString("alerts.discord.webhook-url", "");
        intervalMillis = Math.max(1, plugin.getConfig().getInt("alerts.minimum-interval-seconds", 5)) * 1000L;
    }

    public void warn(String message) {
        warn("general", message);
    }

    public void warn(String category, String message) {
        String clean = message.replaceAll("[\\p{Cntrl}]", " ");
        if (clean.length() > 1600) clean = clean.substring(0, 1600);
        // The local log is the audit trail and is never rate limited.
        plugin.getLogger().warning("[NICKGUARD] " + clean);
        long count = admit(category, System.currentTimeMillis());
        if (count < 0) return;
        String text = clean + (count == 0 ? "" : " [+" + count + " alertas '" + category + "' suprimidos]");
        Runnable notify = () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (plugin.getAdmin2FA() != null && plugin.getAdmin2FA().isBlocked(player)) continue;
                if (config.isSecurityAdmin(player.getName()) || player.hasPermission("nickguard.admin"))
                    player.sendMessage(ConfigManager.color("&c[NickGuard] &f" + text));
            }
        };
        if (Bukkit.isPrimaryThread()) notify.run();
        else if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, notify);
        sendDiscord(text);
    }

    public void info(String message) { plugin.getLogger().info("[NICKGUARD] " + message); }

    /** Returns -1 when the category is rate limited, otherwise how many alerts of it were suppressed before. */
    long admit(String category, long now) {
        return gates.computeIfAbsent(category, key -> new Gate()).tryPass(now, intervalMillis);
    }

    static String discordPayload(String message) {
        return new Gson().toJson(Map.of("content", "[NICKGUARD] " + message,
                "allowed_mentions", Map.of("parse", List.of())));
    }

    private void sendDiscord(String message) {
        if (!discordEnabled || webhook == null || webhook.isBlank()) return;
        if (System.currentTimeMillis() < discordBlockedUntil.get() || !inFlight.compareAndSet(false, true)) {
            discordDropped.incrementAndGet();
            return;
        }
        long dropped = discordDropped.getAndSet(0);
        String content = message + (dropped == 0 ? "" : " [+" + dropped + " alertas nao enviados ao Discord; veja o log]");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhook))
                    .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(discordPayload(content))).build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
                inFlight.set(false);
                if (error != null) {
                    plugin.getLogger().warning("[NICKGUARD] Falha ao enviar alerta Discord.");
                } else if (response.statusCode() == 429) {
                    long seconds = 60;
                    try { seconds = Math.max(1, Long.parseLong(response.headers().firstValue("Retry-After").orElse("60"))); }
                    catch (NumberFormatException ignored) {}
                    long retry = System.currentTimeMillis() + Math.min(seconds, 3600) * 1000L;
                    discordBlockedUntil.accumulateAndGet(retry, Math::max);
                }
            });
        } catch (RuntimeException error) {
            inFlight.set(false);
            plugin.getLogger().warning("[NICKGUARD] Webhook invalido; confira alerts.discord.webhook-url.");
        }
    }

    private static final class Gate {
        private final AtomicLong next = new AtomicLong();
        private final AtomicLong suppressed = new AtomicLong();

        long tryPass(long now, long interval) {
            long current = next.get();
            if (now < current || !next.compareAndSet(current, now + interval)) {
                suppressed.incrementAndGet();
                return -1;
            }
            return suppressed.getAndSet(0);
        }
    }
}
