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
            this.alerts.warn("Blocked command executor=" + player.getName() + " command=" + playerCommandPreprocessEvent.getMessage());
            return;
        }
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

    private String commandRoot(String string) {
        String string2 = string.startsWith("/") ? string.substring(1) : string;
        return string2.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
    }

    private String commandBase(String string) {
        int n = string.indexOf(58);
        return n >= 0 ? string.substring(n + 1) : string;
    }
}

