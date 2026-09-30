/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerCommandPreprocessEvent
 *  org.bukkit.plugin.Plugin
 */
package me.zetra.nickguard;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.zetra.nickguard.AlertService;
import me.zetra.nickguard.ConfigManager;
import me.zetra.nickguard.NickGuardPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

public final class BlockedCommandsGuard
implements Listener {
    private final NickGuardPlugin plugin;
    private final ConfigManager config;
    private final AlertService alerts;
    // Per-player throttle so spamming a blocked command cannot flood the log or the alert channels.
    private final Map<UUID, Long> nextAlert = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> suppressedAttempts = new ConcurrentHashMap<>();

    public BlockedCommandsGuard(NickGuardPlugin nickGuardPlugin, ConfigManager configManager, AlertService alertService) {
        this.plugin = nickGuardPlugin;
        this.config = configManager;
        this.alerts = alertService;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents((Listener)this, (Plugin)this.plugin);
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onCommand(PlayerCommandPreprocessEvent playerCommandPreprocessEvent) {
        if (!this.plugin.getConfig().getBoolean("blocked-commands.enabled", true)) {
            return;
        }
        Player player = playerCommandPreprocessEvent.getPlayer();
        if (this.config.isSecurityAdmin(player.getName())) {
            return;
        }
        String string = this.commandRoot(playerCommandPreprocessEvent.getMessage());
        String string2 = this.commandBase(string);
        for (String string3 : this.plugin.getConfig().getStringList("blocked-commands.commands")) {
            String string4 = string3.toLowerCase(Locale.ROOT).trim();
            if (string4.isBlank() || !this.matches(string, string2, string4)) continue;
            playerCommandPreprocessEvent.setCancelled(true);
            player.sendMessage(ConfigManager.color(this.plugin.getConfig().getString("blocked-commands.message", "&cComando bloqueado pelo NickGuard.")));
            this.reportBlocked(player, playerCommandPreprocessEvent.getMessage());
            return;
        }
    }

    private void reportBlocked(Player player, String command) {
        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        if (now < nextAlert.getOrDefault(id, 0L)) {
            suppressedAttempts.merge(id, 1, Integer::sum);
            return;
        }
        long cooldown = Math.max(0, plugin.getConfig().getInt("blocked-commands.alert-cooldown-seconds", 10)) * 1000L;
        nextAlert.put(id, now + cooldown);
        Integer skipped = suppressedAttempts.remove(id);
        alerts.warn("blocked-command", "Blocked command executor=" + player.getName() + " command=" + command
                + (skipped == null ? "" : " (+" + skipped + " tentativas suprimidas)"));
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        nextAlert.remove(event.getPlayer().getUniqueId());
        suppressedAttempts.remove(event.getPlayer().getUniqueId());
    }

    private boolean matches(String string, String string2, String string3) {
        if (string3.endsWith(":*")) {
            return string.startsWith(string3.substring(0, string3.length() - 1));
        }
        if (string3.endsWith("*")) {
            return string.startsWith(string3.substring(0, string3.length() - 1));
        }
        return string.equals(string3) || string2.equals(string3);
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onSendCommands(org.bukkit.event.player.PlayerCommandSendEvent event) {
        if (!plugin.getConfig().getBoolean("blocked-commands.enabled", true) || config.isSecurityAdmin(event.getPlayer().getName())) return;
        event.getCommands().removeIf(command -> plugin.getConfig().getStringList("blocked-commands.commands").stream()
                .anyMatch(rule -> matches(commandRoot(command), commandBase(commandRoot(command)), rule.toLowerCase(Locale.ROOT).trim())));
    }

    private String commandRoot(String string) {
        String string2 = string.startsWith("/") ? string.substring(1) : string;
        return string2.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
    }

    private String commandBase(String string) {
        int n = string.indexOf(58);
        return n >= 0 ? string.substring(n + 1) : string;
    }
}
