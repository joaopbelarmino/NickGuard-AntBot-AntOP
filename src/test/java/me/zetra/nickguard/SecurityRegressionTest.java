package me.zetra.nickguard;

import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityRegressionTest {
    @TempDir Path dir;
    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test void totpRfcVectorAndReplay() {
        assertEquals(1, TotpUtil.matchingStep(SECRET, "287082", 59000, -1));
        assertEquals(-1, TotpUtil.matchingStep(SECRET, "287082", 59000, 1));
        assertEquals(1, TotpUtil.matchingStep(SECRET, "287082", 60000, -1));
        assertEquals(-1, TotpUtil.matchingStep(SECRET, "287082", 120000, -1));
    }
    @Test void malformedCodesAndSecretsFailClosed() {
        assertEquals(-1, TotpUtil.matchingStep(SECRET, "abcdef", 59000, -1));
        assertEquals(-1, TotpUtil.matchingStep("INVALID0", "287082", 59000, -1));
        for (int i = 0; i < 100; i++) assertTrue(TotpUtil.generateSecret().matches("[A-Z2-7]+"));
    }
    @Test void similarNamesAreDistinctAndExpire() {
        SimilarNickWindow window = new SimilarNickWindow();
        assertEquals(1, window.record("Test1", 0, 10000));
        assertEquals(1, window.record("TEST1", 100, 10000));
        assertEquals(2, window.record("Test2", 200, 10000));
        assertEquals(1, window.record("Test3", 11000, 10000));
        assertTrue(window.expire(22000, 10000));
    }
    @Test void numericNamesDoNotShareEmptySignature() {
        assertEquals("mainraid", SimilarNickWindow.normalize("MainRAID_123", true, true, true));
        assertNotEquals(SimilarNickWindow.normalize("123", true, true, true),
                SimilarNickWindow.normalize("456", true, true, true));
    }
    @Test void discordJsonEscapesAndDisablesMentions() {
        String message = "@everyone \"test\"\n\r\t\\";
        var json = JsonParser.parseString(AlertService.discordPayload(message)).getAsJsonObject();
        assertEquals("[NICKGUARD] " + message, json.get("content").getAsString());
        assertTrue(json.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").isEmpty());
    }
    @Test void atomicFileReplacementLeavesNoTemporaryFiles() throws Exception {
        Path file = dir.resolve("state.yml");
        AtomicFiles.write(file, "first");
        AtomicFiles.write(file, "second");
        assertEquals("second", Files.readString(file));
        try (var files = Files.list(dir)) { assertEquals(1, files.count()); }
    }
    @Test void failedAttemptsSurviveRestartAndLockOut() throws Exception {
        Files.writeString(dir.resolve("admin2fa.yml"), "players:\n  admin:\n    secret: " + SECRET
                + "\n    enabled: true\n    failed-attempts: 3\n    locked-until: 0\n");
        NickGuardPlugin plugin = mock(NickGuardPlugin.class);
        ConfigManager config = mock(ConfigManager.class);
        Player player = mock(Player.class);
        when(plugin.getDataFolder()).thenReturn(dir.toFile());
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getAlertService()).thenReturn(mock(AlertService.class));
        when(player.getName()).thenReturn("Admin");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(config.matchesPinnedUuid(player)).thenReturn(true);
        when(config.isAdmin2FA("Admin")).thenReturn(true);
        new Admin2FA(plugin, config).handleCodeCommand(player, new String[]{"invalid"});
        assertEquals(4, YamlConfiguration.loadConfiguration(dir.resolve("admin2fa.yml").toFile())
                .getInt("players.admin.failed-attempts"));
        Admin2FA restarted = new Admin2FA(plugin, config);
        restarted.handleCodeCommand(player, new String[]{"invalid"});
        long lock = YamlConfiguration.loadConfiguration(dir.resolve("admin2fa.yml").toFile())
                .getLong("players.admin.locked-until");
        assertTrue(lock > System.currentTimeMillis());
        assertFalse(restarted.isVerified(player.getUniqueId()));
        restarted.handleResetCommand(player, new String[]{"Admin"});
        assertEquals(SECRET, YamlConfiguration.loadConfiguration(dir.resolve("admin2fa.yml").toFile())
                .getString("players.admin.secret"));
    }
}
