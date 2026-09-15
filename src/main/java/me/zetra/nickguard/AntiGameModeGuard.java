/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.GameMode
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerCommandPreprocessEvent
 *  org.bukkit.event.player.PlayerGameModeChangeEvent
 *  org.bukkit.event.server.ServerCommandEvent
 *  org.bukkit.plugin.Plugin
 */
package me.zetra.nickguard;

import java.util.Locale;
import me.zetra.nickguard.ConfigManager;
import me.zetra.nickguard.NickGuardPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.Plugin;

public final class AntiGameModeGuard
implements Listener {
    private final NickGuardPlugin plugin;
    private final ConfigManager config;

    public AntiGameModeGuard(NickGuardPlugin nickGuardPlugin, ConfigManager configManager) {
        this.plugin = nickGuardPlugin;
        this.config = configManager;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents((Listener)this, (Plugin)this.plugin);
    }

    public void reload() {
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onGameMode(PlayerGameModeChangeEvent playerGameModeChangeEvent) {
        if (!this.config.antiGameModeEnabled()) {
            return;
        }
        if (!(playerGameModeChangeEvent.getNewGameMode() != GameMode.CREATIVE && playerGameModeChangeEvent.getNewGameMode() != GameMode.SPECTATOR || this.config.isAntiGameModeAllowed(playerGameModeChangeEvent.getPlayer().getName()))) {
            playerGameModeChangeEvent.setCancelled(true);
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> playerGameModeChangeEvent.getPlayer().setGameMode(GameMode.SURVIVAL));
            this.plugin.getLogger().warning("[NickGuard] Gamemode bloqueado nick=" + playerGameModeChangeEvent.getPlayer().getName() + " modo=" + String.valueOf(playerGameModeChangeEvent.getNewGameMode()));
        }
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent playerCommandPreprocessEvent) {
        if (this.isDangerousGameModeCommand(playerCommandPreprocessEvent.getMessage(), playerCommandPreprocessEvent.getPlayer().getName())) {
            playerCommandPreprocessEvent.setCancelled(true);
            this.plugin.getLogger().warning("[NickGuard] Bloqueado comando gamemode executor=" + playerCommandPreprocessEvent.getPlayer().getName() + " comando=" + playerCommandPreprocessEvent.getMessage());
        }
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onServerCommand(ServerCommandEvent serverCommandEvent) {
        String string = "/" + serverCommandEvent.getCommand();
        String string2 = this.findTarget(string);
        if (this.isCreativeCommand(string) && (string2 == null || !this.config.isAntiGameModeAllowed(string2))) {
            serverCommandEvent.setCancelled(true);
            this.plugin.getLogger().warning("[NickGuard] Bloqueado comando gamemode executor=CONSOLE comando=" + string);
        }
    }

    private boolean isDangerousGameModeCommand(String string, String string2) {
        if (!this.config.antiGameModeEnabled() || this.config.isAntiGameModeAllowed(string2)) {
            return false;
        }
        return this.isCreativeCommand(string);
    }

    private boolean isCreativeCommand(String string) {
        String string2 = string.startsWith("/") ? string.substring(1) : string;
        String[] stringArray = string2.split("\\s+");
        if (stringArray.length == 0) {
            return false;
        }
        String string3 = this.stripNamespace(stringArray[0]);
        if (string3.equals("gmc")) {
            return true;
        }
        if (string3.equals("gm")) {
            return stringArray.length >= 2 && (stringArray[1].equalsIgnoreCase("1") || stringArray[1].equalsIgnoreCase("c") || stringArray[1].equalsIgnoreCase("creative") || stringArray[1].equalsIgnoreCase("spectator") || stringArray[1].equalsIgnoreCase("3"));
        }
        if (string3.equals("gamemode")) {
            return stringArray.length >= 2 && (stringArray[1].equalsIgnoreCase("creative") || stringArray[1].equalsIgnoreCase("spectator") || stringArray[1].equalsIgnoreCase("1") || stringArray[1].equalsIgnoreCase("3"));
        }
        return false;
    }

    private String findTarget(String string) {
        String string2 = string.startsWith("/") ? string.substring(1) : string;
        String[] stringArray = string2.split("\\s+");
        if (stringArray.length >= 3 && this.stripNamespace(stringArray[0]).equals("gamemode")) {
            return stringArray[2];
        }
        if (stringArray.length >= 3 && this.stripNamespace(stringArray[0]).equals("gm")) {
            return stringArray[2];
        }
        return null;
    }

    private String stripNamespace(String string) {
        String string2 = string.toLowerCase(Locale.ROOT);
        int n = string2.indexOf(58);
        return n >= 0 ? string2.substring(n + 1) : string2;
    }
}

