/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 */
package me.zetra.nickguard;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import me.zetra.nickguard.ConfigManager;
import me.zetra.nickguard.NickGuardPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class AlertService {
    private final NickGuardPlugin plugin;
    private final ConfigManager config;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public AlertService(NickGuardPlugin nickGuardPlugin, ConfigManager configManager) {
        this.plugin = nickGuardPlugin;
        this.config = configManager;
    }

    public void warn(String string) {
        this.plugin.getLogger().warning("[NICKGUARD] " + string);
        this.notifyAdmins("&c[NickGuard] &f" + string);
        this.sendDiscord(string);
    }

    public void info(String string) {
        this.plugin.getLogger().info("[NICKGUARD] " + string);
    }

    private void notifyAdmins(String string) {
        String string2 = ConfigManager.color(string);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!this.config.isSecurityAdmin(player.getName()) && !player.hasPermission("nickguard.admin")) continue;
            player.sendMessage(string2);
        }
    }

    private void sendDiscord(String string) {
        if (!this.plugin.getConfig().getBoolean("alerts.discord.enabled", false)) {
            return;
        }
        String string2 = this.plugin.getConfig().getString("alerts.discord.webhook-url", "");
        if (string2 == null || string2.isBlank()) {
            return;
        }
        String string3 = "{\"content\":\"" + this.escapeJson("[NICKGUARD] " + string) + "\"}";
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(string2)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(string3)).build();
        this.httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.discarding()).exceptionally(throwable -> {
            this.plugin.getLogger().warning("[NICKGUARD] Falha ao enviar alerta Discord: " + throwable.getMessage());
            return null;
        });
    }

    private String escapeJson(String string) {
        return string.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}

