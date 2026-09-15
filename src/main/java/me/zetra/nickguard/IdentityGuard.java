/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.AsyncPlayerPreLoginEvent
 *  org.bukkit.event.player.AsyncPlayerPreLoginEvent$Result
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.plugin.Plugin
 */
package me.zetra.nickguard;

import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.zetra.nickguard.ConfigManager;
import me.zetra.nickguard.NickGuardPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

public final class IdentityGuard
implements Listener {
    private final NickGuardPlugin plugin;
    private final ConfigManager config;
    private final Map<String, KnownIdentity> knownIdentities = new ConcurrentHashMap<String, KnownIdentity>();

    public IdentityGuard(NickGuardPlugin nickGuardPlugin, ConfigManager configManager) {
        this.plugin = nickGuardPlugin;
        this.config = configManager;
        this.reload();
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents((Listener)this, (Plugin)this.plugin);
    }

    public void reload() {
        this.rebuildKnownIdentities();
    }

    public int removeUuid(UUID uUID) {
        int n = this.knownIdentities.size();
        this.knownIdentities.entrySet().removeIf(entry -> ((KnownIdentity)entry.getValue()).uuid().equals(uUID));
        return n - this.knownIdentities.size();
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent asyncPlayerPreLoginEvent) {
        if (!this.config.isIdentityGuardEnabled()) {
            return;
        }
        String string = asyncPlayerPreLoginEvent.getName();
        String string2 = ConfigManager.lower(string);
        UUID uUID = asyncPlayerPreLoginEvent.getUniqueId();
        KnownIdentity knownIdentity = this.knownIdentities.get(string2);
        if (knownIdentity == null) {
            KnownIdentity knownIdentity2 = this.knownIdentities.putIfAbsent(string2, new KnownIdentity(string, uUID));
            if (knownIdentity2 != null && !knownIdentity2.uuid().equals(uUID)) {
                this.block(asyncPlayerPreLoginEvent, string, knownIdentity2.uuid(), uUID);
            }
            return;
        }
        UUID uUID2 = knownIdentity.uuid();
        if (uUID2.equals(uUID)) {
            return;
        }
        this.block(asyncPlayerPreLoginEvent, string, uUID2, uUID);
    }

    private void block(AsyncPlayerPreLoginEvent asyncPlayerPreLoginEvent, String string, UUID uUID, UUID uUID2) {
        this.plugin.getLogger().warning("[NickGuard] Bloqueado nick=" + string + " uuid_existente=" + String.valueOf(uUID) + " uuid_tentativa=" + String.valueOf(uUID2) + " ip=" + this.formatIp(asyncPlayerPreLoginEvent.getAddress()));
        asyncPlayerPreLoginEvent.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, this.config.identityKickMessage());
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent playerJoinEvent) {
        this.knownIdentities.putIfAbsent(ConfigManager.lower(playerJoinEvent.getPlayer().getName()), new KnownIdentity(playerJoinEvent.getPlayer().getName(), playerJoinEvent.getPlayer().getUniqueId()));
    }

    private void rebuildKnownIdentities() {
        this.knownIdentities.clear();
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            String string = offlinePlayer.getName();
            if (string == null) continue;
            this.knownIdentities.putIfAbsent(ConfigManager.lower(string), new KnownIdentity(string, offlinePlayer.getUniqueId()));
        }
        this.plugin.getLogger().info("NickGuard identity cache: " + this.knownIdentities.size() + " nicks.");
    }

    private String formatIp(InetAddress inetAddress) {
        return inetAddress == null ? "unknown" : inetAddress.getHostAddress();
    }

    private record KnownIdentity(String nick, UUID uuid) {
    }
}

