package me.zetra.nickguard;

import java.util.List;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HardeningTest {
    @Test void luckPermsAliasesAreRecognized() {
        for (String label : List.of("lp", "luckperms", "perm", "perms", "permission", "permissions", "luckperms:perm", "LP"))
            assertTrue(AntiLuckPermsGuard.isLuckPermsLabel(label, null), label);
        assertTrue(AntiLuckPermsGuard.isLuckPermsLabel("customalias", "LuckPerms"));
        assertFalse(AntiLuckPermsGuard.isLuckPermsLabel("perm", "OtherPlugin"));
        assertFalse(AntiLuckPermsGuard.isLuckPermsLabel("gamemode", null));
    }

    @Test void luckPermsAliasesCannotBypassGuard() {
        ConfigManager config = mock(ConfigManager.class);
        when(config.antiLuckPermsEnabled()).thenReturn(true);
        when(config.isAntiLuckAllowed("Admin")).thenReturn(true);
        when(config.blockedPermissions()).thenReturn(Set.of("*"));
        AntiLuckPermsGuard guard = new AntiLuckPermsGuard(mock(NickGuardPlugin.class), config);

        assertTrue(guard.isDangerous("/perm user Mod parent set admin", "Mod"));
        assertTrue(guard.isDangerous("/perms editor", "Mod"));
        assertTrue(guard.isDangerous("/luckperms:permissions user Mod info", "Mod"));
        assertTrue(guard.isDangerous("/perm user Mod permission set * true", "CONSOLE"));
        assertTrue(guard.isDangerous("/lp user Mod perm set * true", "Admin"));
        assertFalse(guard.isDangerous("/perm user Mod permission set essentials.fly true", "CONSOLE"));
        assertFalse(guard.isDangerous("/perms user Mod info", "Admin"));
        assertFalse(guard.isDangerous("/spawn", "Mod"));
    }

    @Test void noisyAlertCategoryDoesNotMaskOthers() {
        NickGuardPlugin plugin = mock(NickGuardPlugin.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        AlertService alerts = new AlertService(plugin, mock(ConfigManager.class));

        assertEquals(0, alerts.admit("blocked-command", 0));
        assertEquals(-1, alerts.admit("blocked-command", 100));
        assertEquals(-1, alerts.admit("blocked-command", 200));
        assertEquals(0, alerts.admit("2fa", 300));
        assertEquals(0, alerts.admit("identity", 400));
        assertEquals(2, alerts.admit("blocked-command", 5000));
    }

    @Test void exceptionsOutside2FAAreReported() {
        Set<String> twoFactor = Set.of("owner");
        assertEquals(List.of("helper", "mod"), ConfigManager.exceptionsWithout2FA(twoFactor,
                Set.of("owner", "mod"), Set.of("mod", "helper"), Set.of()));
        assertTrue(ConfigManager.exceptionsWithout2FA(twoFactor, Set.of("owner")).isEmpty());
        assertEquals(List.of("owner"), ConfigManager.exceptionsWithout2FA(Set.of(), Set.of("owner")));
    }
}
