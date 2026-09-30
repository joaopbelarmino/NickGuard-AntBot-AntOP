/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerCommandPreprocessEvent
 *  org.bukkit.event.server.ServerCommandEvent
 *  org.bukkit.plugin.Plugin
 */
package me.zetra.nickguard;

import java.util.Locale;
import java.util.Set;
import me.zetra.nickguard.ConfigManager;
import me.zetra.nickguard.NickGuardPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.Plugin;

public final class AntiLuckPermsGuard
implements Listener {
    // Labels declared in LuckPerms' plugin.yml; used when the command map cannot resolve the owner.
    static final Set<String> LUCKPERMS_LABELS = Set.of("luckperms", "lp", "perm", "perms", "permission", "permissions");
    private static final Set<String> PERMISSION_SUBCOMMANDS = Set.of("permission", "perm", "perms");
    private final NickGuardPlugin plugin;
    private final ConfigManager config;

    public AntiLuckPermsGuard(NickGuardPlugin nickGuardPlugin, ConfigManager configManager) {
        this.plugin = nickGuardPlugin;
        this.config = configManager;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents((Listener)this, (Plugin)this.plugin);
    }

    public void reload() {
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent playerCommandPreprocessEvent) {
        String string = playerCommandPreprocessEvent.getMessage();
        if (this.isDangerous(string, playerCommandPreprocessEvent.getPlayer().getName())) {
            playerCommandPreprocessEvent.setCancelled(true);
            this.log(playerCommandPreprocessEvent.getPlayer().getName(), string);
        }
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onServerCommand(ServerCommandEvent serverCommandEvent) {
        String string = "/" + serverCommandEvent.getCommand();
        if (this.isDangerous(string, "CONSOLE")) {
            serverCommandEvent.setCancelled(true);
            this.log("CONSOLE", string);
        }
    }

    boolean isDangerous(String string, String string2) {
        boolean bl;
        if (!this.config.antiLuckPermsEnabled()) {
            return false;
        }
        String string3 = string.startsWith("/") ? string.substring(1) : string;
        String[] stringArray = string3.split("\\s+");
        if (stringArray.length < 1) {
            return false;
        }
        if (!this.isLuckPermsCommand(stringArray[0])) {
            return false;
        }
        if (!string2.equals("CONSOLE") && !config.isAntiLuckAllowed(string2)) return true;
        if (stringArray.length < 6) return false;
        if (!PERMISSION_SUBCOMMANDS.contains(stringArray[3].toLowerCase(Locale.ROOT)) || !(stringArray[4].equalsIgnoreCase("set") || stringArray[4].equalsIgnoreCase("settemp"))) {
            return false;
        }
        if (stringArray.length > 6 && stringArray[6].equalsIgnoreCase("false")) return false;
        String string5 = stringArray[5].toLowerCase(Locale.ROOT);
        boolean bl2 = bl = this.config.blockedPermissions().contains(string5) || string5.contains("*") || string5.equals("minecraft.command.op") || string5.equals("bukkit.command.op");
        if (!bl) {
            return false;
        }
        String string6 = stringArray[1].toLowerCase(Locale.ROOT);
        String string7 = stringArray.length >= 3 ? stringArray[2] : "";
        boolean bl3 = !string2.equals("CONSOLE") && this.config.isAntiLuckAllowed(string2);
        boolean bl4 = string6.equals("user") && this.config.isAntiLuckAllowed(string7);
        return !bl3 || !bl4;
    }

    private boolean isLuckPermsCommand(String label) {
        String owner = null;
        try {
            Command command = Bukkit.getCommandMap().getCommand(label.toLowerCase(Locale.ROOT));
            if (command instanceof PluginIdentifiableCommand pluginCommand) owner = pluginCommand.getPlugin().getName();
        } catch (RuntimeException unavailable) {
            // No server (tests) or a broken command map: fall back to the known labels.
        }
        return isLuckPermsLabel(label, owner);
    }

    /** Owner-based match catches every alias (including custom ones); known labels cover an unresolved owner. */
    static boolean isLuckPermsLabel(String label, String owner) {
        if (owner != null && !owner.isBlank()) return owner.equalsIgnoreCase("LuckPerms");
        String lower = label.toLowerCase(Locale.ROOT);
        int n = lower.indexOf(58);
        return LUCKPERMS_LABELS.contains(n >= 0 ? lower.substring(n + 1) : lower);
    }

    private void log(String string, String string2) {
        this.plugin.getLogger().warning("[NickGuard] Bloqueado comando LuckPerms perigoso executor=" + string + " comando=" + string2);
    }
}
