/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.command.CommandSender
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.AsyncPlayerPreLoginEvent
 *  org.bukkit.event.player.AsyncPlayerPreLoginEvent$Result
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitTask
 */
package me.zetra.nickguard;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import me.zetra.nickguard.AlertService;
import me.zetra.nickguard.ConfigManager;
import me.zetra.nickguard.NickGuardPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class RaidProtection
implements Listener {
    private final NickGuardPlugin plugin;
    private final AlertService alerts;
    private final Map<String, Deque<Long>> ipAttempts = new ConcurrentHashMap<String, Deque<Long>>();
    private final Map<String, Deque<Long>> patternAttempts = new ConcurrentHashMap<String, Deque<Long>>();
    private final Map<String, Long> blockedIps = new ConcurrentHashMap<String, Long>();
    private final Map<String, Long> blockedPatterns = new ConcurrentHashMap<String, Long>();
    private final Deque<Long> globalAttempts = new ConcurrentLinkedDeque<Long>();
    private final Map<String, Long> recentIps = new ConcurrentHashMap<String, Long>();
    private BukkitTask cleanupTask;
    private boolean manualRaidMode;
    private volatile long raidModeUntil;

    public RaidProtection(NickGuardPlugin nickGuardPlugin, AlertService alertService) {
        this.plugin = nickGuardPlugin;
        this.alerts = alertService;
        this.loadBlocks();
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents((Listener)this, (Plugin)this.plugin);
        this.cleanupTask = Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, this::cleanup, 1200L, 1200L);
    }

    public void reload() {
        this.loadBlocks();
    }

    public void cancel() {
        if (this.cleanupTask != null) {
            this.cleanupTask.cancel();
        }
        this.saveBlocks();
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent asyncPlayerPreLoginEvent) {
        Long l;
        long l2 = System.currentTimeMillis();
        String string = this.formatIp(asyncPlayerPreLoginEvent.getAddress());
        String string2 = asyncPlayerPreLoginEvent.getName();
        String string3 = this.normalizeNick(string2);
        if (this.isIpWhitelisted(string) || this.shouldIgnoreProxyIp(string)) {
            return;
        }
        if (this.antiReloginEnabled() && this.isActiveBlock(l = this.blockedIps.get(string), l2)) {
            asyncPlayerPreLoginEvent.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, this.blockMessage());
            return;
        }
        if (this.similarNickEnabled() && this.isActiveBlock(l = this.blockedPatterns.get(string3), l2)) {
            asyncPlayerPreLoginEvent.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, this.blockMessage());
            return;
        }
        this.recordGlobal(l2);
        this.recordRecentIp(string, l2);
        if (this.checkAutoRaidMode(l2)) {
            this.activateRaidMode("connection spike", this.raidModeDurationSeconds());
        }
        if (this.antiReloginEnabled()) {
            int n3 = this.recordAndCount(this.ipAttempts, string, l2, (long)this.windowSeconds() * 1000L);
            int n2 = this.countWithin(this.ipAttempts.get(string), l2, (long)this.softLimitWindowSeconds() * 1000L);
            int n = this.recordAndCount(this.ipAttempts, string + "#burst", l2, 60000L);
            if (n >= this.blacklistAttempts()) {
                this.blockedIps.put(string, -1L);
                this.alerts.warn("Blocked IP " + string + " - reason: relogin flood blacklist - attempts: " + n + "/60s - duration: manual");
                this.saveBlocksAsync();
                asyncPlayerPreLoginEvent.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, this.blockMessage());
                return;
            }
            if (n3 >= this.maxAttempts()) {
                this.blockIp(string, this.temporaryBanSeconds(), "relogin flood - attempts: " + n3 + "/" + this.windowSeconds() + "s");
                asyncPlayerPreLoginEvent.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, this.blockMessage());
                return;
            }
            if (n2 >= this.softLimitAttempts()) {
                this.blockIp(string, this.softBlockSeconds(), "relogin flood soft-limit - attempts: " + n2 + "/" + this.softLimitWindowSeconds() + "s");
                asyncPlayerPreLoginEvent.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, this.blockMessage());
                return;
            }
        }
        if (this.similarNickEnabled()) {
            int n4 = this.recordAndCount(this.patternAttempts, string3, l2, (long)this.similarWindowSeconds() * 1000L);
            boolean keywordMatch = this.containsBlockedKeyword(string3);
            boolean strictRaid = this.isRaidModeActive() && (keywordMatch || n4 >= Math.max(2, this.similarMaxNicks() / 2));
            if (keywordMatch && this.blockKeywordsImmediately()) {
                this.blockPattern(string3, this.similarBlockDurationSeconds(), "blocked keyword - attempts: " + n4 + "/" + this.similarWindowSeconds() + "s");
                asyncPlayerPreLoginEvent.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, this.blockMessage());
                return;
            }
            if (n4 >= this.similarMaxNicks() || strictRaid) {
                int n6 = n4 >= this.similarMaxNicks() * 2 ? this.similarEscalationDurationSeconds() : this.similarBlockDurationSeconds();
                this.blockPattern(string3, n6, "similar nick raid - attempts: " + n4 + "/" + this.similarWindowSeconds() + "s");
                if (n4 >= this.raidSimilarThreshold()) {
                    this.activateRaidMode("similar nick spike", this.raidModeDurationSeconds());
                }
                asyncPlayerPreLoginEvent.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, this.blockMessage());
            }
        }
    }

    public void status(CommandSender commandSender) {
        this.cleanup();
        commandSender.sendMessage(ConfigManager.color("&b[NickGuard] Status"));
        commandSender.sendMessage(ConfigManager.color("&7raid-mode: &f" + (this.isRaidModeActive() ? "ativo" : "inativo")));
        commandSender.sendMessage(ConfigManager.color("&7ips bloqueados: &f" + this.blockedIps.size()));
        commandSender.sendMessage(ConfigManager.color("&7patterns bloqueados: &f" + this.blockedPatterns.size()));
        commandSender.sendMessage(ConfigManager.color("&7tentativas recentes: &f" + this.countWithin(this.globalAttempts, System.currentTimeMillis(), (long)this.raidGlobalWindowSeconds() * 1000L)));
    }

    public void blockIpManual(String string, long l) {
        this.blockIp(string, l, "manual block");
    }

    public boolean unblockIp(String string) {
        boolean bl = this.blockedIps.remove(string) != null;
        this.saveBlocksAsync();
        return bl;
    }

    public boolean unblockPattern(String string) {
        boolean bl = this.blockedPatterns.remove(this.normalizeNick(string)) != null;
        this.saveBlocksAsync();
        return bl;
    }

    public void setRaidMode(boolean bl) {
        this.manualRaidMode = bl;
        this.raidModeUntil = !bl ? 0L : System.currentTimeMillis() + (long)this.raidModeDurationSeconds() * 1000L;
        this.alerts.warn("Raid mode " + (bl ? "enabled manually" : "disabled manually"));
    }

    public boolean isRaidModeActive() {
        return this.manualRaidMode || this.raidModeUntil > System.currentTimeMillis();
    }

    private void blockIp(String string, long l, String string2) {
        long l2 = l <= 0L ? -1L : System.currentTimeMillis() + l * 1000L;
        this.blockedIps.put(string, l2);
        this.alerts.warn("Blocked IP " + string + " - reason: " + string2 + " - duration: " + this.formatDuration(l));
        this.saveBlocksAsync();
    }

    private void blockPattern(String string, long l, String string2) {
        long l2 = l <= 0L ? -1L : System.currentTimeMillis() + l * 1000L;
        this.blockedPatterns.put(string, l2);
        this.alerts.warn("Blocked nick pattern " + string + " - reason: " + string2 + " - duration: " + this.formatDuration(l));
        this.saveBlocksAsync();
    }

    private void activateRaidMode(String string, long l) {
        long l2 = System.currentTimeMillis() + l * 1000L;
        if (l2 <= this.raidModeUntil || this.manualRaidMode) {
            return;
        }
        this.raidModeUntil = l2;
        this.alerts.warn("Raid mode enabled - reason: " + string + " - duration: " + this.formatDuration(l));
    }

    private boolean checkAutoRaidMode(long l) {
        if (!this.plugin.getConfig().getBoolean("raid-mode.enabled", true)) {
            return false;
        }
        int n = this.countWithin(this.globalAttempts, l, (long)this.raidGlobalWindowSeconds() * 1000L);
        long l3 = this.recentIps.values().stream().filter(l2 -> l - l2 <= (long)this.raidSuspiciousWindowSeconds() * 1000L).count();
        return n >= this.raidGlobalConnections() || l3 >= (long)this.raidSuspiciousIps();
    }

    private void recordGlobal(long l) {
        this.globalAttempts.addLast(l);
        this.countWithin(this.globalAttempts, l, (long)this.raidGlobalWindowSeconds() * 1000L);
    }

    private void recordRecentIp(String string, long l) {
        this.recentIps.put(string, l);
        long l2 = (long)this.raidSuspiciousWindowSeconds() * 1000L;
        this.recentIps.entrySet().removeIf(entry -> l - (Long)entry.getValue() > l2);
    }

    private int recordAndCount(Map<String, Deque<Long>> map, String string2, long l, long l2) {
        Deque deque = map.computeIfAbsent(string2, string -> new ConcurrentLinkedDeque());
        deque.addLast(l);
        return this.countWithin(deque, l, l2);
    }

    private int countWithin(Deque<Long> deque, long l, long l2) {
        Long l3;
        if (deque == null) {
            return 0;
        }
        while ((l3 = deque.peekFirst()) != null && l - l3 > l2) {
            deque.pollFirst();
        }
        return deque.size();
    }

    private boolean isActiveBlock(Long l, long l2) {
        if (l == null) {
            return false;
        }
        if (l == -1L) {
            return true;
        }
        return l > l2;
    }

    private void cleanup() {
        long l = System.currentTimeMillis();
        this.blockedIps.entrySet().removeIf(entry -> (Long)entry.getValue() != -1L && (Long)entry.getValue() <= l);
        this.blockedPatterns.entrySet().removeIf(entry -> (Long)entry.getValue() != -1L && (Long)entry.getValue() <= l);
        this.ipAttempts.entrySet().removeIf(entry -> this.countWithin((Deque)entry.getValue(), l, 60000L) == 0);
        this.patternAttempts.entrySet().removeIf(entry -> this.countWithin((Deque)entry.getValue(), l, (long)this.similarWindowSeconds() * 1000L) == 0);
        this.recentIps.entrySet().removeIf(entry -> l - (Long)entry.getValue() > (long)this.raidSuspiciousWindowSeconds() * 1000L);
        this.saveBlocks();
    }

    private void loadBlocks() {
        this.blockedIps.clear();
        this.blockedPatterns.clear();
        this.loadMap(this.blockedFile("blocked-ips.yml"), this.blockedIps);
        this.loadMap(this.blockedFile("blocked-patterns.yml"), this.blockedPatterns);
        this.cleanup();
    }

    private void loadMap(File file, Map<String, Long> map) {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration((File)file);
        ConfigurationSection configurationSection = yamlConfiguration.getConfigurationSection("blocked");
        if (configurationSection == null) {
            return;
        }
        long l = System.currentTimeMillis();
        for (String string : configurationSection.getKeys(false)) {
            long l2 = configurationSection.getLong(string + ".expires-at", 0L);
            if (l2 != -1L && l2 <= l) continue;
            map.put(this.decodeKey(string), l2);
        }
    }

    private void saveBlocksAsync() {
        Bukkit.getScheduler().runTaskAsynchronously((Plugin)this.plugin, this::saveBlocks);
    }

    private void saveBlocks() {
        this.saveMap(this.blockedFile("blocked-ips.yml"), this.blockedIps);
        this.saveMap(this.blockedFile("blocked-patterns.yml"), this.blockedPatterns);
    }

    private void saveMap(File file, Map<String, Long> map) {
        YamlConfiguration yamlConfiguration = new YamlConfiguration();
        for (Map.Entry<String, Long> entry : map.entrySet()) {
            yamlConfiguration.set("blocked." + this.encodeKey(entry.getKey()) + ".expires-at", (Object)entry.getValue());
            yamlConfiguration.set("blocked." + this.encodeKey(entry.getKey()) + ".value", (Object)entry.getKey());
        }
        try {
            yamlConfiguration.save(file);
        }
        catch (IOException iOException) {
            this.plugin.getLogger().warning("[NICKGUARD] Falha ao salvar " + file.getName() + ": " + iOException.getMessage());
        }
    }

    private File blockedFile(String string) {
        File file = new File(this.plugin.getDataFolder(), string);
        File file2 = file.getParentFile();
        if (file2 != null) {
            file2.mkdirs();
        }
        return file;
    }

    private String encodeKey(String string) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(string.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeKey(String string) {
        try {
            return new String(Base64.getUrlDecoder().decode(string), StandardCharsets.UTF_8);
        }
        catch (IllegalArgumentException illegalArgumentException) {
            return string;
        }
    }

    private String normalizeNick(String string) {
        String string2;
        String string3 = string2 = string == null ? "" : string;
        if (this.plugin.getConfig().getBoolean("similar-nick-protection.normalize.lowercase", true)) {
            string2 = string2.toLowerCase(Locale.ROOT);
        }
        if (this.plugin.getConfig().getBoolean("similar-nick-protection.normalize.remove-numbers", true)) {
            string2 = string2.replaceAll("[0-9]", "");
        }
        if (this.plugin.getConfig().getBoolean("similar-nick-protection.normalize.remove-symbols", true)) {
            string2 = string2.replaceAll("[^a-zA-Z]", "");
        }
        return string2;
    }

    private boolean containsBlockedKeyword(String string) {
        for (String string2 : this.plugin.getConfig().getStringList("similar-nick-protection.blocked-keywords")) {
            if (string2.isBlank() || !string.contains(string2.toLowerCase(Locale.ROOT))) continue;
            return true;
        }
        return false;
    }

    private boolean isIpWhitelisted(String string) {
        return new HashSet(this.plugin.getConfig().getStringList("anti-relogin-flood.whitelist-ips")).contains(string);
    }

    private boolean shouldIgnoreProxyIp(String string) {
        if (!this.plugin.getConfig().getBoolean("anti-relogin-flood.ignore-proxy-ip", false)) {
            return false;
        }
        return string.startsWith("127.") || string.equals("localhost") || string.equals("0:0:0:0:0:0:0:1") || string.equals("::1");
    }

    private String formatIp(InetAddress inetAddress) {
        return inetAddress == null ? "unknown" : inetAddress.getHostAddress();
    }

    private String formatDuration(long l) {
        if (l <= 0L) {
            return "manual";
        }
        Duration duration = Duration.ofSeconds(l);
        if (duration.toHours() > 0L) {
            return duration.toHours() + "h";
        }
        if (duration.toMinutes() > 0L) {
            return duration.toMinutes() + "m";
        }
        return l + "s";
    }

    private String blockMessage() {
        return ConfigManager.color(this.plugin.getConfig().getString("raid-kick-message", "&cConexao bloqueada temporariamente pelo NickGuard."));
    }

    private boolean antiReloginEnabled() {
        return this.plugin.getConfig().getBoolean("anti-relogin-flood.enabled", true);
    }

    private boolean similarNickEnabled() {
        return this.plugin.getConfig().getBoolean("similar-nick-protection.enabled", true);
    }

    private int windowSeconds() {
        return this.plugin.getConfig().getInt("anti-relogin-flood.window-seconds", 10);
    }

    private int maxAttempts() {
        return this.plugin.getConfig().getInt("anti-relogin-flood.max-attempts", 10);
    }

    private int temporaryBanSeconds() {
        return this.plugin.getConfig().getInt("anti-relogin-flood.temporary-ban-seconds", 3600);
    }

    private int softLimitAttempts() {
        return this.plugin.getConfig().getInt("anti-relogin-flood.soft-limit-attempts", 3);
    }

    private int softLimitWindowSeconds() {
        return this.plugin.getConfig().getInt("anti-relogin-flood.soft-limit-window-seconds", 2);
    }

    private int softBlockSeconds() {
        return this.plugin.getConfig().getInt("anti-relogin-flood.soft-block-seconds", 300);
    }

    private int blacklistAttempts() {
        return this.plugin.getConfig().getInt("anti-relogin-flood.blacklist-attempts", 50);
    }

    private int similarWindowSeconds() {
        return this.plugin.getConfig().getInt("similar-nick-protection.window-seconds", 10);
    }

    private int similarMaxNicks() {
        return this.plugin.getConfig().getInt("similar-nick-protection.max-similar-nicks", 5);
    }

    private int similarBlockDurationSeconds() {
        return this.plugin.getConfig().getInt("similar-nick-protection.block-duration-seconds", 300);
    }

    private int similarEscalationDurationSeconds() {
        return this.plugin.getConfig().getInt("similar-nick-protection.escalation-duration-seconds", 3600);
    }

    private boolean blockKeywordsImmediately() {
        return this.plugin.getConfig().getBoolean("similar-nick-protection.block-keywords-immediately", true);
    }

    private int raidGlobalConnections() {
        return this.plugin.getConfig().getInt("raid-mode.global-connections", 20);
    }

    private int raidGlobalWindowSeconds() {
        return this.plugin.getConfig().getInt("raid-mode.global-window-seconds", 5);
    }

    private int raidSimilarThreshold() {
        return this.plugin.getConfig().getInt("raid-mode.similar-nicks", 10);
    }

    private int raidSuspiciousIps() {
        return this.plugin.getConfig().getInt("raid-mode.suspicious-ips", 10);
    }

    private int raidSuspiciousWindowSeconds() {
        return this.plugin.getConfig().getInt("raid-mode.suspicious-window-seconds", 10);
    }

    private int raidModeDurationSeconds() {
        return this.plugin.getConfig().getInt("raid-mode.duration-seconds", 300);
    }
}
