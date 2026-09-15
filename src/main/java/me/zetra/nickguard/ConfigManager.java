/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.file.FileConfiguration
 */
package me.zetra.nickguard;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import me.zetra.nickguard.NickGuardPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public final class ConfigManager {
    private static final List<String> DEFAULT_ADMINS = List.of("jaozinmDKK", "jaozinmdk", "LordeGTR");
    private final NickGuardPlugin plugin;
    private Set<String> adm2FA = Set.of();
    private Set<String> securityAdmins = Set.of();
    private Set<String> antiOpAllowed = Set.of();
    private Set<String> antiLuckAllowed = Set.of();
    private Set<String> antiGameModeAllowed = Set.of();
    private Set<String> blockedPermissions = Set.of();

    public ConfigManager(NickGuardPlugin nickGuardPlugin) {
        this.plugin = nickGuardPlugin;
        this.reload();
    }

    public void reload() {
        this.plugin.reloadConfig();
        FileConfiguration fileConfiguration = this.plugin.getConfig();
        this.adm2FA = this.lowerConfiguredSet(fileConfiguration, "ADM_2FA", DEFAULT_ADMINS);
        this.securityAdmins = this.lowerConfiguredSet(fileConfiguration, "security-admins", DEFAULT_ADMINS);
        this.antiOpAllowed = this.lowerConfiguredSet(fileConfiguration, "anti-op.allowed", DEFAULT_ADMINS);
        this.antiLuckAllowed = this.lowerConfiguredSet(fileConfiguration, "anti-luckperms-wildcard.allowed", DEFAULT_ADMINS);
        this.antiGameModeAllowed = this.lowerConfiguredSet(fileConfiguration, "anti-gamemode-creative.allowed", DEFAULT_ADMINS);
        this.blockedPermissions = this.lowerSet(fileConfiguration.getStringList("anti-luckperms-wildcard.blocked-permissions"));
    }

    public boolean isIdentityGuardEnabled() {
        return this.plugin.getConfig().getBoolean("enabled", true);
    }

    public String identityKickMessage() {
        return ConfigManager.color(this.plugin.getConfig().getString("kick-message", "Este nickname ja pertence a outra conta."));
    }

    public boolean admin2FAEnabled() {
        return this.plugin.getConfig().getBoolean("admin-2fa.enabled", true);
    }

    public boolean blockUntilVerified() {
        return this.plugin.getConfig().getBoolean("admin-2fa.block-until-verified", true);
    }

    public String issuer() {
        return this.plugin.getConfig().getString("admin-2fa.issuer", "ZetraMC 2FA");
    }

    public String getCodeCommand() {
        return this.plugin.getConfig().getString("admin-2fa.code-command", "2fa").toLowerCase(Locale.ROOT);
    }

    public String getResetCommand() {
        return this.plugin.getConfig().getString("admin-2fa.reset-command", "redefine").toLowerCase(Locale.ROOT);
    }

    public boolean setupUrlMessage() {
        return this.plugin.getConfig().getBoolean("admin-2fa.setup-url-message", true);
    }

    public boolean kickOnTimeout() {
        return this.plugin.getConfig().getBoolean("admin-2fa.kick-on-timeout", false);
    }

    public boolean antiOpEnabled() {
        return this.plugin.getConfig().isConfigurationSection("anti-op") && this.plugin.getConfig().getBoolean("anti-op.enabled", true);
    }

    public boolean antiLuckPermsEnabled() {
        return this.plugin.getConfig().isConfigurationSection("anti-luckperms-wildcard") && this.plugin.getConfig().getBoolean("anti-luckperms-wildcard.enabled", true);
    }

    public boolean antiGameModeEnabled() {
        return this.plugin.getConfig().isConfigurationSection("anti-gamemode-creative") && this.plugin.getConfig().getBoolean("anti-gamemode-creative.enabled", true);
    }

    public boolean isAdmin2FA(String string) {
        return this.adm2FA.contains(ConfigManager.lower(string));
    }

    public boolean isSecurityAdmin(String string) {
        return this.securityAdmins.contains(ConfigManager.lower(string));
    }

    public boolean isAntiOpAllowed(String string) {
        return this.antiOpAllowed.contains(ConfigManager.lower(string));
    }

    public boolean isAntiLuckAllowed(String string) {
        return this.antiLuckAllowed.contains(ConfigManager.lower(string));
    }

    public boolean isAntiGameModeAllowed(String string) {
        return this.antiGameModeAllowed.contains(ConfigManager.lower(string));
    }

    public Set<String> blockedPermissions() {
        return this.blockedPermissions;
    }

    public static String lower(String string) {
        return string == null ? "" : string.toLowerCase(Locale.ROOT);
    }

    public static String color(String string) {
        return string == null ? "" : string.replace("&", "\u00a7");
    }

    private Set<String> lowerSet(List<String> list) {
        if (list == null) {
            return new HashSet<String>();
        }
        return list.stream().filter(string -> string != null && !string.isBlank()).map(ConfigManager::lower).collect(Collectors.toCollection(HashSet::new));
    }

    private Set<String> lowerConfiguredSet(FileConfiguration fileConfiguration, String string, List<String> list) {
        Set<String> set = this.lowerSet(fileConfiguration.getStringList(string));
        if (!set.isEmpty()) {
            return set;
        }
        return this.lowerSet(list);
    }
}

