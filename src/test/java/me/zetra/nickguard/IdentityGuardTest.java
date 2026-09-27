package me.zetra.nickguard;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityGuardTest {
    @TempDir Path dir;
    private IdentityGuard create(OfflinePlayer... existing) {
        NickGuardPlugin plugin = mock(NickGuardPlugin.class);
        ConfigManager config = mock(ConfigManager.class);
        when(plugin.getDataFolder()).thenReturn(dir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getAlertService()).thenReturn(mock(AlertService.class));
        when(config.isIdentityGuardEnabled()).thenReturn(true);
        when(config.identityKickMessage()).thenReturn("Blocked");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOfflinePlayers).thenReturn(existing);
            return new IdentityGuard(plugin, config);
        }
    }
    private OfflinePlayer offline(UUID uuid) {
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getName()).thenReturn("Test");
        when(player.getUniqueId()).thenReturn(uuid);
        return player;
    }
    private AsyncPlayerPreLoginEvent attempt(UUID uuid) {
        try (var bukkit = mockStatic(Bukkit.class)) {
            var profile = mock(com.destroystokyo.paper.profile.PlayerProfile.class);
            when(profile.getId()).thenReturn(uuid);
            when(profile.getName()).thenReturn("TEST");
            bukkit.when(() -> Bukkit.createProfile(uuid, "TEST"))
                    .thenReturn(profile);
            return new AsyncPlayerPreLoginEvent("TEST", InetAddress.getLoopbackAddress(), uuid);
        }
    }
    @Test void conflictingReservationBlocksButRejectedAttemptReleases() {
        IdentityGuard guard = create();
        var first = attempt(UUID.randomUUID());
        var second = attempt(UUID.randomUUID());
        guard.onAsyncPreLogin(first);
        guard.onAsyncPreLogin(second);
        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, first.getLoginResult());
        assertNotEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, second.getLoginResult());
        first.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "Other plugin");
        guard.onPreLoginResult(first);
        var retry = attempt(second.getUniqueId());
        guard.onAsyncPreLogin(retry);
        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, retry.getLoginResult());
    }
    @Test void ambiguityFailsClosedAndExclusionSurvivesRestart() {
        UUID original = UUID.randomUUID(), duplicate = UUID.randomUUID();
        OfflinePlayer a = offline(original), b = offline(duplicate);
        IdentityGuard guard = create(a, b);
        var ambiguous = attempt(original);
        guard.onAsyncPreLogin(ambiguous);
        assertNotEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, ambiguous.getLoginResult());
        assertEquals(1, guard.removeUuid(duplicate));
        guard = create(a, b);
        var allowed = attempt(original);
        var denied = attempt(duplicate);
        guard.onAsyncPreLogin(allowed);
        guard.onAsyncPreLogin(denied);
        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, allowed.getLoginResult());
        assertNotEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, denied.getLoginResult());
    }
}
