/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerRegisterChannelEvent
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.messaging.PluginMessageListener
 */
package me.zetra.nickguard;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import me.zetra.nickguard.AlertService;
import me.zetra.nickguard.ConfigManager;
import me.zetra.nickguard.NickGuardPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class ClientSignatureGuard
implements Listener,
PluginMessageListener {
    private final NickGuardPlugin plugin;
    private final ConfigManager config;
    private final AlertService alerts;

    public ClientSignatureGuard(NickGuardPlugin nickGuardPlugin, ConfigManager configManager, AlertService alertService) {
        this.plugin = nickGuardPlugin;
        this.config = configManager;
        this.alerts = alertService;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents((Listener)this, (Plugin)this.plugin);
        this.registerIncoming("minecraft:brand");
        this.registerIncoming("MC|Brand");
    }

    public void unregister() {
        Bukkit.getMessenger().unregisterIncomingPluginChannel((Plugin)this.plugin);
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onRegisterChannel(PlayerRegisterChannelEvent playerRegisterChannelEvent) {
        if (!this.enabled() || this.config.isSecurityAdmin(playerRegisterChannelEvent.getPlayer().getName())) {
            return;
        }
        String string = playerRegisterChannelEvent.getChannel();
        if (this.matchesList(string, "client-signature-guard.blocked-channels") || this.matchesList(string, "client-signature-guard.blocked-patterns")) {
            this.punish(playerRegisterChannelEvent.getPlayer(), "channel", string);
        }
    }

    public void onPluginMessageReceived(String string, Player player, byte[] byArray) {
        if (!this.enabled() || this.config.isSecurityAdmin(player.getName())) {
            return;
        }
        String string2 = this.decodePayload(byArray);
        if (this.matchesList(string, "client-signature-guard.blocked-channels") || this.matchesList(string2, "client-signature-guard.blocked-brands") || this.matchesList(string2, "client-signature-guard.blocked-patterns")) {
            this.punish(player, "brand/payload " + string, string2);
        }
    }

    private void punish(Player player, String string, String string2) {
        this.alerts.warn("Blocked client signature player=" + player.getName() + " source=" + string + " value=" + this.sanitize(string2));
        if (this.plugin.getConfig().getBoolean("client-signature-guard.kick-player", true)) {
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> player.kickPlayer(ConfigManager.color(this.plugin.getConfig().getString("client-signature-guard.kick-message", "&cCliente nao permitido no servidor."))));
        }
    }

    private boolean matchesList(String string, String string2) {
        String string3 = this.normalize(string);
        if (string3.isBlank()) {
            return false;
        }
        for (String string4 : this.plugin.getConfig().getStringList(string2)) {
            String string5 = this.normalize(string4);
            if (string5.isBlank() || !string3.equals(string5) && !string3.contains(string5)) continue;
            return true;
        }
        return false;
    }

    private String decodePayload(byte[] byArray) {
        if (byArray == null || byArray.length == 0) {
            return "";
        }
        String string = new String(byArray, StandardCharsets.UTF_8);
        return string.replaceAll("[^\\p{Print}]", "");
    }

    private String normalize(String string) {
        if (string == null) {
            return "";
        }
        return string.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(".", "").replace(":", "");
    }

    private String sanitize(String string) {
        String string2 = string == null ? "" : string.replaceAll("[\\r\\n\\t]", " ");
        return string2.length() > 80 ? string2.substring(0, 80) + "..." : string2;
    }

    private void registerIncoming(String string) {
        try {
            Bukkit.getMessenger().registerIncomingPluginChannel((Plugin)this.plugin, string, (PluginMessageListener)this);
        }
        catch (RuntimeException runtimeException) {
            this.plugin.getLogger().warning("[NICKGUARD] Nao foi possivel registrar canal " + string + ": " + runtimeException.getMessage());
        }
    }

    private boolean enabled() {
        return this.plugin.getConfig().getBoolean("client-signature-guard.enabled", true);
    }
}

