package me.zetra.nickguard;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

/** Existing ambiguous identities are blocked until an administrator resolves the case. */
public final class IdentityGuard implements Listener {
    private final NickGuardPlugin plugin;
    private final ConfigManager config;
    private final Map<String, Set<UUID>> known = new HashMap<>();
    private final Map<String, Reservation> reservations = new HashMap<>();
    private final Set<UUID> ignored = new HashSet<>();
    private final java.io.File exclusions;

    public IdentityGuard(NickGuardPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        exclusions = new java.io.File(plugin.getDataFolder(), "identity-exclusions.yml");
        for (String value : YamlConfiguration.loadConfiguration(exclusions).getStringList("uuids")) {
            try { ignored.add(UUID.fromString(value)); }
            catch (IllegalArgumentException invalid) { plugin.getLogger().warning("UUID invalido em identity-exclusions.yml."); }
        }
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() != null && !ignored.contains(player.getUniqueId()))
                known.computeIfAbsent(ConfigManager.lower(player.getName()), key -> new HashSet<>()).add(player.getUniqueId());
        }
        plugin.getLogger().info("NickGuard identity cache: " + known.size() + " nicks.");
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::expireReservations, 1200L, 1200L);
    }

    public void reload() { expireReservations(); }

    private synchronized void expireReservations() {
        long now = System.currentTimeMillis();
        reservations.values().removeIf(value -> value.expiresAt < now);
    }

    public synchronized int removeUuid(UUID uuid) {
        // Tombstones prevent the server's in-memory usercache from importing the removed UUID again.
        Set<UUID> updated = new HashSet<>(ignored);
        updated.add(uuid);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("uuids", updated.stream().map(UUID::toString).sorted().toList());
        try { AtomicFiles.write(exclusions.toPath(), yaml.saveToString()); }
        catch (java.io.IOException error) { throw new IllegalStateException("Nao foi possivel salvar exclusao de identidade.", error); }
        ignored.add(uuid);
        int changed = 0;
        for (Set<UUID> identities : known.values()) if (identities.remove(uuid)) changed++;
        known.values().removeIf(Set::isEmpty);
        reservations.values().removeIf(value -> value.uuid.equals(uuid));
        return changed;
    }

    private synchronized boolean reserve(String nick, UUID uuid) {
        if (ignored.contains(uuid)) return false;
        String key = ConfigManager.lower(nick);
        Set<UUID> identities = known.get(key);
        if (identities != null) return identities.size() == 1 && identities.contains(uuid);
        long now = System.currentTimeMillis();
        Reservation reservation = reservations.get(key);
        if (reservation != null && reservation.expiresAt > now && !reservation.uuid.equals(uuid)) return false;
        reservations.put(key, new Reservation(uuid, now + 60000));
        return true;
    }

    private synchronized void release(String name, UUID uuid) {
        String key = ConfigManager.lower(name);
        Reservation value = reservations.get(key);
        if (value != null && value.uuid.equals(uuid)) reservations.remove(key);
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!config.isIdentityGuardEnabled() || event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        if (!reserve(event.getName(), event.getUniqueId())) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, config.identityKickMessage());
            plugin.getAlertService().warn("identity", "Identity bloqueada nick=" + event.getName() + " uuid_tentativa=" + event.getUniqueId()
                    + " reason=UUID diferente, removido ou historico ambiguo");
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onPreLoginResult(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) release(event.getName(), event.getUniqueId());
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        if (!config.isIdentityGuardEnabled() || event.getResult() != PlayerLoginEvent.Result.ALLOWED) return;
        if (!reserve(event.getPlayer().getName(), event.getPlayer().getUniqueId()))
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, config.identityKickMessage());
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onLoginResult(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED)
            release(event.getPlayer().getName(), event.getPlayer().getUniqueId());
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public synchronized void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!ignored.contains(uuid)) known.computeIfAbsent(ConfigManager.lower(event.getPlayer().getName()), key -> new HashSet<>()).add(uuid);
        release(event.getPlayer().getName(), uuid);
    }

    private record Reservation(UUID uuid, long expiresAt) {}
}
