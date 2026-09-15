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
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.event.server.ServerCommandEvent
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitTask
 */
package me.zetra.nickguard;

import java.util.Locale;
import me.zetra.nickguard.ConfigManager;
import me.zetra.nickguard.NickGuardPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class AntiOpGuard
implements Listener {
    private final NickGuardPlugin plugin;
    private final ConfigManager config;
    private BukkitTask task;

    public AntiOpGuard(NickGuardPlugin nickGuardPlugin, ConfigManager configManager) {
        this.plugin = nickGuardPlugin;
        this.config = configManager;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents((Listener)this, (Plugin)this.plugin);
        this.startTask();
    }

    public void reload() {
        this.cancel();
        this.startTask();
    }

    public void cancel() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent playerJoinEvent) {
        this.checkPlayer(playerJoinEvent.getPlayer());
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent playerCommandPreprocessEvent) {
        if (this.isOpCommand(playerCommandPreprocessEvent.getMessage()) && !this.config.isAntiOpAllowed(playerCommandPreprocessEvent.getPlayer().getName())) {
            playerCommandPreprocessEvent.setCancelled(true);
            this.log("Bloqueado comando OP executor=" + playerCommandPreprocessEvent.getPlayer().getName() + " comando=" + playerCommandPreprocessEvent.getMessage());
        }
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onServerCommand(ServerCommandEvent serverCommandEvent) {
        if (!this.config.antiOpEnabled()) {
            return;
        }
        if (this.isOpCommand(serverCommandEvent.getCommand())) {
            serverCommandEvent.setCancelled(true);
            this.log("Bloqueado comando OP executor=CONSOLE comando=/" + serverCommandEvent.getCommand());
        }
    }

    private void startTask() {
        this.task = Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, () -> Bukkit.getOnlinePlayers().forEach(this::checkPlayer), 40L, 100L);
    }

    private void checkPlayer(Player player) {
        if (!this.config.antiOpEnabled() || !player.isOp() || this.config.isAntiOpAllowed(player.getName())) {
            return;
        }
        player.setOp(false);
        this.log("OP removido nick=" + player.getName());
        this.notifyAdmins("&c[NickGuard] OP removido de &e" + player.getName());
    }

    private boolean isOpCommand(String string) {
        if (!this.config.antiOpEnabled()) {
            return false;
        }
        String string2 = this.root(string);
        return string2.equals("op");
    }

    private String root(String string) {
        String string2 = string.startsWith("/") ? string.substring(1) : string;
        String string3 = string2.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int n = string3.indexOf(58);
        return n >= 0 ? string3.substring(n + 1) : string3;
    }

    private void notifyAdmins(String string) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("nickguard.admin") && !this.config.isSecurityAdmin(player.getName())) continue;
            player.sendMessage(ConfigManager.color(string));
        }
    }

    private void log(String string) {
        this.plugin.getLogger().warning("[NickGuard] " + string);
    }
}

