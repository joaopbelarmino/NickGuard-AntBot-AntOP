/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.ConsoleCommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerCommandPreprocessEvent
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.permissions.PermissionAttachmentInfo
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitTask
 */
package me.zetra.nickguard;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import me.zetra.nickguard.AlertService;
import me.zetra.nickguard.ConfigManager;
import me.zetra.nickguard.NickGuardPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class DangerousPermissionGuard
implements Listener {
    private final NickGuardPlugin plugin;
    private final ConfigManager config;
    private final AlertService alerts;
    private BukkitTask scanTask;

    public DangerousPermissionGuard(NickGuardPlugin nickGuardPlugin, ConfigManager configManager, AlertService alertService) {
        this.plugin = nickGuardPlugin;
        this.config = configManager;
        this.alerts = alertService;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents((Listener)this, (Plugin)this.plugin);
        this.scanTask = Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, this::scanOnlinePlayers, 600L, 600L);
    }

    public void reload() {
        this.scanOnlinePlayers();
    }

    public void cancel() {
        if (this.scanTask != null) {
            this.scanTask.cancel();
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent playerJoinEvent) {
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> this.check(playerJoinEvent.getPlayer(), "join"), 20L);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onCommand(PlayerCommandPreprocessEvent playerCommandPreprocessEvent) {
        this.check(playerCommandPreprocessEvent.getPlayer(), "command");
    }

    private void scanOnlinePlayers() {
        if (!this.enabled()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.check(player, "periodic-scan");
        }
    }

    private void check(Player player, String string) {
        if (!this.enabled() || this.config.isSecurityAdmin(player.getName())) {
            return;
        }
        String string2 = this.detectDangerousPermission(player);
        boolean bl = player.isOp();
        if (!bl && string2 == null) {
            return;
        }
        if (bl && this.removeOp()) {
            player.setOp(false);
        }
        String string3 = string2 == null ? "op" : string2;
        this.runConfiguredCommands(player, string3);
        this.quarantine(player);
        this.alerts.warn("Dangerous permission detected for " + player.getName() + ": " + string3 + " - action: " + this.actionSummary() + " - source: " + string);
        if (this.kickPlayer()) {
            player.kickPlayer(ConfigManager.color(this.plugin.getConfig().getString("dangerous-permissions.kick-message", "&cPermissao administrativa perigosa detectada.")));
        }
    }

    private String detectDangerousPermission(Player player) {
        Set<String> set = this.lowerSet(this.plugin.getConfig().getStringList("dangerous-permissions.permissions"));
        Set<String> set2 = this.lowerSet(this.plugin.getConfig().getStringList("dangerous-permissions.contains-patterns"));
        for (String string : set) {
            if (!this.matchesPermissionCheck(player, string)) continue;
            return string;
        }
        for (PermissionAttachmentInfo string : player.getEffectivePermissions()) {
            if (!string.getValue()) continue;
            String string2 = string.getPermission().toLowerCase(Locale.ROOT);
            if (this.matchesDangerous(string2, set)) {
                return string2;
            }
            if (this.plugin.getConfig().getBoolean("dangerous-permissions.detect-any-wildcard", false) && string2.contains("*")) {
                return string2;
            }
            for (String string3 : set2) {
                if (string3.equals("*") || string3.isBlank() || !string2.contains(string3)) continue;
                return string2;
            }
        }
        return null;
    }

    private boolean matchesPermissionCheck(Player player, String string) {
        if (string.isBlank()) {
            return false;
        }
        if (string.contains("*")) {
            return this.hasExactEffectivePermission(player, string);
        }
        return player.hasPermission(string);
    }

    private boolean matchesDangerous(String string, Set<String> set) {
        for (String string2 : set) {
            if (!string2.equals(string)) continue;
            return true;
        }
        return false;
    }

    private boolean hasExactEffectivePermission(Player player, String string) {
        for (PermissionAttachmentInfo permissionAttachmentInfo : player.getEffectivePermissions()) {
            if (!permissionAttachmentInfo.getValue() || !permissionAttachmentInfo.getPermission().equalsIgnoreCase(string)) continue;
            return true;
        }
        return false;
    }

    private void runConfiguredCommands(Player player, String string) {
        ConsoleCommandSender consoleCommandSender = Bukkit.getConsoleSender();
        for (String string2 : this.plugin.getConfig().getStringList("dangerous-permissions.command-on-detect")) {
            if (string2 == null || string2.isBlank()) continue;
            String string3 = string2.replace("{player}", player.getName()).replace("{permission}", string);
            Bukkit.dispatchCommand((CommandSender)consoleCommandSender, (String)(string3.startsWith("/") ? string3.substring(1) : string3));
        }
    }

    private void quarantine(Player player) {
        String string = this.plugin.getConfig().getString("dangerous-permissions.quarantine-group", "");
        if (string == null || string.isBlank()) {
            return;
        }
        Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), (String)("lp user " + player.getName() + " parent set " + string));
    }

    private Set<String> lowerSet(Iterable<String> iterable) {
        HashSet<String> hashSet = new HashSet<String>();
        for (String string : iterable) {
            if (string == null || string.isBlank()) continue;
            hashSet.add(string.toLowerCase(Locale.ROOT));
        }
        return hashSet;
    }

    private String actionSummary() {
        StringBuilder stringBuilder = new StringBuilder();
        if (this.removeOp()) {
            stringBuilder.append("removed op");
        }
        if (this.kickPlayer()) {
            if (!stringBuilder.isEmpty()) {
                stringBuilder.append(" + ");
            }
            stringBuilder.append("kicked");
        }
        if (stringBuilder.isEmpty()) {
            return "alerted";
        }
        return stringBuilder.toString();
    }

    private boolean enabled() {
        return this.plugin.getConfig().getBoolean("dangerous-permissions.enabled", true);
    }

    private boolean removeOp() {
        return this.plugin.getConfig().getBoolean("dangerous-permissions.actions.remove-op", true);
    }

    private boolean kickPlayer() {
        return this.plugin.getConfig().getBoolean("dangerous-permissions.actions.kick-player", true);
    }
}
