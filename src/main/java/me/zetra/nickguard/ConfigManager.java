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
        this.adm2FA = this.lowerSet(fileConfiguration.getStringList("ADM_2FA"));
        this.securityAdmins = this.lowerSet(fileConfiguration.getStringList("security-admins"));
        this.antiOpAllowed = this.lowerSet(fileConfiguration.getStringList("anti-op.allowed"));
        this.antiLuckAllowed = this.lowerSet(fileConfiguration.getStringList("anti-luckperms-wildcard.allowed"));
        this.antiGameModeAllowed = this.lowerSet(fileConfiguration.getStringList("anti-gamemode-creative.allowed"));
        this.blockedPermissions = this.lowerSet(fileConfiguration.getStringList("anti-luckperms-wildcard.blocked-permissions"));
        this.warnExceptionsWithout2FA();
    }

    private void warnExceptionsWithout2FA() {
        List<String> names = exceptionsWithout2FA(admin2FAEnabled() ? adm2FA : Set.of(),
                securityAdmins, antiOpAllowed, antiLuckAllowed, antiGameModeAllowed);
        if (names.isEmpty()) return;
        String effect = requireTwoFactorForExceptions()
                ? "e NAO recebem excecao administrativa (require-2fa-for-exceptions: true)."
                : "e sao liberados apenas pelo nick. Adicione-os em ADM_2FA ou ative require-2fa-for-exceptions.";
        plugin.getLogger().warning("[NickGuard] Nicks com excecao administrativa sem 2FA: " + names + " " + effect);
    }

    /** Names that hold an administrative exception but are not protected by the 2FA module. */
    @SafeVarargs
    static List<String> exceptionsWithout2FA(Set<String> twoFactor, Set<String>... allowlists) {
        return java.util.Arrays.stream(allowlists).flatMap(Set::stream)
                .filter(name -> !twoFactor.contains(name)).distinct().sorted().toList();
    }

    public boolean requireTwoFactorForExceptions() {
        return this.plugin.getConfig().getBoolean("require-2fa-for-exceptions", false);
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
        return "2fa";
    }

    public String getResetCommand() {
        return "redefine";
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
        return this.securityAdmins.contains(ConfigManager.lower(string)) && sessionAuthorized(string);
    }

    public boolean isAntiOpAllowed(String string) {
        return this.antiOpAllowed.contains(ConfigManager.lower(string)) && sessionAuthorized(string);
    }

    public boolean isAntiLuckAllowed(String string) {
        return this.antiLuckAllowed.contains(ConfigManager.lower(string)) && sessionAuthorized(string);
    }

    public boolean isAntiGameModeAllowed(String string) {
        return this.antiGameModeAllowed.contains(ConfigManager.lower(string)) && sessionAuthorized(string);
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

    private boolean sessionAuthorized(String name) {
        org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayerExact(name);
        if (player == null || !matchesPinnedUuid(player)) return false;
        if (!admin2FAEnabled() || !isAdmin2FA(name)) return !requireTwoFactorForExceptions();
        return plugin.getAdmin2FA() != null && plugin.getAdmin2FA().isVerified(player.getUniqueId());
    }

    public boolean matchesPinnedUuid(org.bukkit.entity.Player player) {
        String uuid = plugin.getConfig().getString("admin-identities." + lower(player.getName()), "");
        return uuid.isBlank() ? !plugin.getConfig().getBoolean("require-admin-uuid", false)
                : uuid.equalsIgnoreCase(player.getUniqueId().toString());
    }
}
