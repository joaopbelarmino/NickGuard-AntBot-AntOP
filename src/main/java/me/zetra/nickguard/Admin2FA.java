/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.event.player.AsyncChatEvent
 *  org.bukkit.Bukkit
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.ConsoleCommandSender
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.BlockBreakEvent
 *  org.bukkit.event.block.BlockPlaceEvent
 *  org.bukkit.event.entity.EntityDamageByEntityEvent
 *  org.bukkit.event.entity.EntityPickupItemEvent
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.player.PlayerCommandPreprocessEvent
 *  org.bukkit.event.player.PlayerDropItemEvent
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.event.player.PlayerMoveEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.plugin.Plugin
 */
package me.zetra.nickguard;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.zetra.nickguard.ConfigManager;
import me.zetra.nickguard.NickGuardPlugin;
import me.zetra.nickguard.TotpUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public final class Admin2FA
implements Listener {
    private final NickGuardPlugin plugin;
    private final ConfigManager config;
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final Set<UUID> verified = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> failedAttempts = new ConcurrentHashMap<UUID, Integer>();
    private final Map<UUID, Long> lockedUntil = new ConcurrentHashMap<UUID, Long>();
    private final Map<String, AdminSecret> secrets = new ConcurrentHashMap<String, AdminSecret>();
    private File secretsFile;
    private FileConfiguration secretsConfig;

    public Admin2FA(NickGuardPlugin nickGuardPlugin, ConfigManager configManager) {
        this.plugin = nickGuardPlugin;
        this.config = configManager;
        this.loadSecrets();
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents((Listener)this, (Plugin)this.plugin);
    }

    public void reload() {
        this.loadSecrets();
        this.pending.clear();
        this.verified.clear();
        this.failedAttempts.clear();
        this.lockedUntil.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.requireIfNeeded(player);
        }
    }

    public void save() {
        if (this.secretsConfig == null || this.secretsFile == null) {
            return;
        }
        try {
            this.secretsConfig.save(this.secretsFile);
        }
        catch (IOException iOException) {
            this.plugin.getLogger().warning("Nao foi possivel salvar admin2fa.yml: " + iOException.getMessage());
        }
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent playerJoinEvent) {
        this.requireIfNeeded(playerJoinEvent.getPlayer());
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent playerQuitEvent) {
        this.pending.remove(playerQuitEvent.getPlayer().getUniqueId());
        this.verified.remove(playerQuitEvent.getPlayer().getUniqueId());
        this.failedAttempts.remove(playerQuitEvent.getPlayer().getUniqueId());
        this.lockedUntil.remove(playerQuitEvent.getPlayer().getUniqueId());
    }

    public boolean handleCodeCommand(CommandSender commandSender, String[] stringArray) {
        AdminSecret adminSecret;
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage(ConfigManager.color("&cApenas jogadores podem validar 2FA."));
            return true;
        }
        Player player = (Player)commandSender;
        if (stringArray.length != 1) {
            player.sendMessage(ConfigManager.color("&eUse /" + this.config.getCodeCommand() + " <codigo>"));
            return true;
        }
        if (!this.config.isAdmin2FA(player.getName()) || this.verified.contains(player.getUniqueId())) {
            player.sendMessage(ConfigManager.color("&aSeu 2FA ja esta validado."));
            return true;
        }
        this.pending.add(player.getUniqueId());
        long l = System.currentTimeMillis();
        Long l2 = this.lockedUntil.get(player.getUniqueId());
        if (l2 != null && l2 > l) {
            long l3 = Math.max(1L, (l2 - l) / 1000L);
            player.sendMessage(ConfigManager.color("&cMuitas tentativas de 2FA. Aguarde &e" + l3 + "s&c."));
            return true;
        }
        if (l2 != null) {
            this.lockedUntil.remove(player.getUniqueId());
            this.failedAttempts.remove(player.getUniqueId());
        }
        if ((adminSecret = this.secrets.get(ConfigManager.lower(player.getName()))) == null || !adminSecret.enabled()) {
            this.sendSetup(player, true);
            return true;
        }
        if (TotpUtil.verify(adminSecret.secret(), stringArray[0])) {
            this.pending.remove(player.getUniqueId());
            this.verified.add(player.getUniqueId());
            this.failedAttempts.remove(player.getUniqueId());
            this.lockedUntil.remove(player.getUniqueId());
            player.sendMessage(ConfigManager.color("&a2FA validado com sucesso."));
        } else {
            int n;
            int n2 = this.failedAttempts.merge(player.getUniqueId(), 1, Integer::sum);
            if (n2 >= (n = this.plugin.getConfig().getInt("admin-2fa.max-failed-attempts", 5))) {
                int n3 = this.plugin.getConfig().getInt("admin-2fa.lockout-seconds", 60);
                this.lockedUntil.put(player.getUniqueId(), System.currentTimeMillis() + (long)n3 * 1000L);
                this.failedAttempts.put(player.getUniqueId(), 0);
                player.sendMessage(ConfigManager.color("&cCodigo 2FA invalido. Muitas tentativas, aguarde &e" + n3 + "s&c."));
                this.plugin.getLogger().warning("[NickGuard] 2FA bloqueado temporariamente nick=" + player.getName() + " tentativas=" + n2);
            } else {
                player.sendMessage(ConfigManager.color("&cCodigo 2FA invalido. Tentativas: &e" + n2 + "&c/&e" + n));
            }
        }
        return true;
    }

    public boolean handleResetCommand(CommandSender commandSender, String[] stringArray) {
        String string;
        if (stringArray.length < 1) {
            commandSender.sendMessage(ConfigManager.color("&eUse /" + this.config.getResetCommand() + " 2FA [nick] &7ou &e/" + this.config.getResetCommand() + " <nick>"));
            return true;
        }
        if (!(commandSender instanceof ConsoleCommandSender) && !commandSender.hasPermission("nickguard.admin")) {
            commandSender.sendMessage(ConfigManager.color("&cVoce nao tem permissao para redefinir 2FA."));
            return true;
        }
        if (this.plugin.getConfig().getBoolean("admin-2fa.reset-console-only", false) && !(commandSender instanceof ConsoleCommandSender)) {
            commandSender.sendMessage(ConfigManager.color("&cEste reset de 2FA so pode ser executado pelo console."));
            return true;
        }
        if (stringArray[0].equalsIgnoreCase("2FA") && stringArray.length >= 2) {
            string = stringArray[1];
        } else if (!stringArray[0].equalsIgnoreCase("2FA")) {
            string = stringArray[0];
        } else if (commandSender instanceof Player) {
            Player player = (Player)commandSender;
            string = player.getName();
        } else {
            commandSender.sendMessage(ConfigManager.color("&cConsole precisa informar o nick: /" + this.config.getResetCommand() + " <nick>"));
            return true;
        }
        String string2 = this.setNewSecret(string);
        commandSender.sendMessage(ConfigManager.color("&aNovo secret 2FA para &e" + string + "&a: &f" + string2));
        Player player = Bukkit.getPlayerExact((String)string);
        if (player != null) {
            this.pending.add(player.getUniqueId());
            this.verified.remove(player.getUniqueId());
            this.sendSetup(player, true);
        }
        return true;
    }

    private void requireIfNeeded(Player player) {
        if (!this.config.admin2FAEnabled() || !this.config.isAdmin2FA(player.getName())) {
            this.pending.remove(player.getUniqueId());
            this.verified.add(player.getUniqueId());
            return;
        }
        if (!this.config.blockUntilVerified()) {
            return;
        }
        this.verified.remove(player.getUniqueId());
        this.pending.add(player.getUniqueId());
        AdminSecret adminSecret = this.secrets.get(ConfigManager.lower(player.getName()));
        if (adminSecret == null || !adminSecret.enabled()) {
            this.setNewSecret(player.getName());
            this.sendSetup(player, true);
        } else {
            player.sendMessage(ConfigManager.color("&cZetraMC 2FA: use &e/" + this.config.getCodeCommand() + " <codigo> &cpara liberar sua conta."));
        }
    }

    private boolean isBlocked(Player player) {
        return this.config.admin2FAEnabled() && this.config.blockUntilVerified() && this.config.isAdmin2FA(player.getName()) && !this.verified.contains(player.getUniqueId());
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onMove(PlayerMoveEvent playerMoveEvent) {
        if (this.isBlocked(playerMoveEvent.getPlayer()) && playerMoveEvent.getTo() != null && (!playerMoveEvent.getFrom().getWorld().equals((Object)playerMoveEvent.getTo().getWorld()) || playerMoveEvent.getFrom().distanceSquared(playerMoveEvent.getTo()) > 0.0)) {
            playerMoveEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onChat(AsyncChatEvent asyncChatEvent) {
        if (this.isBlocked(asyncChatEvent.getPlayer())) {
            asyncChatEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onCommand(PlayerCommandPreprocessEvent playerCommandPreprocessEvent) {
        if (!this.isBlocked(playerCommandPreprocessEvent.getPlayer())) {
            return;
        }
        String string = this.commandRoot(playerCommandPreprocessEvent.getMessage());
        if (string.equals(this.config.getCodeCommand())) {
            return;
        }
        playerCommandPreprocessEvent.setCancelled(true);
        playerCommandPreprocessEvent.getPlayer().sendMessage(ConfigManager.color("&cValide o 2FA primeiro: &e/" + this.config.getCodeCommand() + " <codigo>"));
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onBreak(BlockBreakEvent blockBreakEvent) {
        if (this.isBlocked(blockBreakEvent.getPlayer())) {
            blockBreakEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onPlace(BlockPlaceEvent blockPlaceEvent) {
        if (this.isBlocked(blockPlaceEvent.getPlayer())) {
            blockPlaceEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onInteract(PlayerInteractEvent playerInteractEvent) {
        if (this.isBlocked(playerInteractEvent.getPlayer())) {
            playerInteractEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onInventory(InventoryClickEvent inventoryClickEvent) {
        Player player;
        HumanEntity humanEntity = inventoryClickEvent.getWhoClicked();
        if (humanEntity instanceof Player && this.isBlocked(player = (Player)humanEntity)) {
            inventoryClickEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onDamage(EntityDamageByEntityEvent entityDamageByEntityEvent) {
        Player player;
        Entity entity = entityDamageByEntityEvent.getDamager();
        if (entity instanceof Player && this.isBlocked(player = (Player)entity)) {
            entityDamageByEntityEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onDrop(PlayerDropItemEvent playerDropItemEvent) {
        if (this.isBlocked(playerDropItemEvent.getPlayer())) {
            playerDropItemEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onPickup(EntityPickupItemEvent entityPickupItemEvent) {
        Player player;
        LivingEntity livingEntity = entityPickupItemEvent.getEntity();
        if (livingEntity instanceof Player && this.isBlocked(player = (Player)livingEntity)) {
            entityPickupItemEvent.setCancelled(true);
        }
    }

    private void loadSecrets() {
        this.secrets.clear();
        this.secretsFile = new File(this.plugin.getDataFolder(), "admin2fa.yml");
        if (!this.secretsFile.exists()) {
            this.plugin.saveResource("admin2fa.yml", false);
        }
        this.secretsConfig = YamlConfiguration.loadConfiguration((File)this.secretsFile);
        ConfigurationSection configurationSection = this.secretsConfig.getConfigurationSection("players");
        if (configurationSection == null) {
            return;
        }
        for (String string : configurationSection.getKeys(false)) {
            String string2 = "players." + string + ".";
            String string3 = this.secretsConfig.getString(string2 + "secret", "");
            boolean bl = this.secretsConfig.getBoolean(string2 + "enabled", true);
            if (string3.isBlank()) continue;
            this.secrets.put(ConfigManager.lower(string), new AdminSecret(string3, bl));
        }
    }

    private String setNewSecret(String string) {
        String string2 = TotpUtil.generateSecret();
        String string3 = ConfigManager.lower(string);
        this.secrets.put(string3, new AdminSecret(string2, true));
        this.secretsConfig.set("players." + string3 + ".secret", (Object)string2);
        this.secretsConfig.set("players." + string3 + ".enabled", (Object)true);
        this.save();
        return string2;
    }

    private void sendSetup(Player player, boolean bl) {
        AdminSecret adminSecret = this.secrets.get(ConfigManager.lower(player.getName()));
        if (adminSecret == null) {
            return;
        }
        String string = TotpUtil.otpauthUrl(this.config.issuer(), player.getName(), adminSecret.secret());
        player.sendMessage(ConfigManager.color("&cZetraMC 2FA: configure seu autenticador e use &e/" + this.config.getCodeCommand() + " <codigo>&c."));
        if (this.config.setupUrlMessage()) {
            this.sendClickableUrl(player, string);
        }
        if (bl) {
            player.sendMessage(ConfigManager.color("&7Secret: &f" + adminSecret.secret()));
            player.sendMessage(ConfigManager.color("&7URL: &f" + string));
        }
    }

    private String commandRoot(String string) {
        String string2 = string.startsWith("/") ? string.substring(1) : string;
        String string3 = string2.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int n = string3.indexOf(58);
        return n >= 0 ? string3.substring(n + 1) : string3;
    }

    private void sendClickableUrl(Player player, String string) {
        String string2 = "[{\"text\":\"Clique aqui para adicionar o 2FA\",\"color\":\"aqua\",\"underlined\":true,\"clickEvent\":{\"action\":\"open_url\",\"value\":\"" + this.escapeJson(string) + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"contents\":\"Abrir autenticador\"}}]";
        Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), (String)("tellraw " + player.getName() + " " + string2));
    }

    private String escapeJson(String string) {
        return string.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record AdminSecret(String secret, boolean enabled) {
    }
}

