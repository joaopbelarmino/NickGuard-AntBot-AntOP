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
    private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final Map<String, Long> lockedUntil = new ConcurrentHashMap<>();
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
        Bukkit.getOnlinePlayers().forEach(this::requireIfNeeded);
    }

    public void reload() {
        this.loadSecrets();
        this.pending.clear();
        this.verified.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.requireIfNeeded(player);
        }
    }

    public void save() {
        if (this.secretsConfig == null || this.secretsFile == null) {
            return;
        }
        try {
            AtomicFiles.write(this.secretsFile.toPath(), this.secretsConfig.saveToString());
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
    }

    public boolean handleCodeCommand(CommandSender commandSender, String[] stringArray) {
        AdminSecret adminSecret;
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage(ConfigManager.color("&cApenas jogadores podem validar 2FA."));
            return true;
        }
        Player player = (Player)commandSender;
        String account = ConfigManager.lower(player.getName());
        String accountPath = "players." + account + ".";
        if (!config.matchesPinnedUuid(player)) {
            player.sendMessage("UUID administrativo nao autorizado. Contate o console.");
            return true;
        }
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
        Long l2 = this.lockedUntil.get(account);
        if (l2 != null && l2 > l) {
            long l3 = Math.max(1L, (l2 - l) / 1000L);
            player.sendMessage(ConfigManager.color("&cMuitas tentativas de 2FA. Aguarde &e" + l3 + "s&c."));
            return true;
        }
        if (l2 != null) {
            this.lockedUntil.remove(account);
            if (l2 > 0) this.failedAttempts.remove(account);
        }
        if ((adminSecret = this.secrets.get(ConfigManager.lower(player.getName()))) == null || !adminSecret.enabled()) {
            player.sendMessage("2FA nao cadastrado. Solicite cadastro pelo console.");
            return true;
        }
        long step = TotpUtil.matchingStep(adminSecret.secret(), stringArray[0], l,
                secretsConfig.getLong(accountPath + "last-used-step", -1));
        if (step >= 0) {
            secretsConfig.set(accountPath + "last-used-step", step);
            failedAttempts.remove(account);
            lockedUntil.remove(account);
            if (!saveAccountState(account)) {
                player.sendMessage("Falha ao persistir 2FA; acesso permanece bloqueado.");
                return true;
            }
            this.pending.remove(player.getUniqueId());
            this.verified.add(player.getUniqueId());
            player.sendMessage(ConfigManager.color("&a2FA validado com sucesso."));
        } else {
            int n;
            int n2 = this.failedAttempts.merge(account, 1, Integer::sum);
            if (n2 >= (n = Math.max(1, this.plugin.getConfig().getInt("admin-2fa.max-failed-attempts", 5)))) {
                int n3 = Math.max(1, this.plugin.getConfig().getInt("admin-2fa.lockout-seconds", 60));
                this.lockedUntil.put(account, System.currentTimeMillis() + (long)n3 * 1000L);
                this.failedAttempts.put(account, 0);
                player.sendMessage(ConfigManager.color("&cCodigo 2FA invalido. Muitas tentativas, aguarde &e" + n3 + "s&c."));
                this.plugin.getAlertService().warn("2fa", "2FA bloqueado temporariamente nick=" + player.getName() + " tentativas=" + n2);
            } else {
                player.sendMessage(ConfigManager.color("&cCodigo 2FA invalido. Tentativas: &e" + n2 + "&c/&e" + n));
            }
            saveAccountState(account);
        }
        return true;
    }

    public boolean handleResetCommand(CommandSender commandSender, String[] stringArray) {
        if (!(commandSender instanceof ConsoleCommandSender)) {
            commandSender.sendMessage("Cadastro e reset 2FA somente pelo console.");
            return true;
        }
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
        if (!config.isAdmin2FA(string)) {
            commandSender.sendMessage("O nick precisa estar listado em ADM_2FA.");
            return true;
        }
        if (!this.setNewSecret(string)) {
            commandSender.sendMessage("Falha ao salvar secret. Cadastro nao confirmado; verifique o disco.");
            return true;
        }
        commandSender.sendMessage("Novo secret salvo em plugins/NickGuard/admin2fa.yml, players."
                + ConfigManager.lower(string) + ".secret. Entregue ao titular por canal privado.");
        Player player = Bukkit.getPlayerExact((String)string);
        if (player != null) {
            this.pending.add(player.getUniqueId());
            this.verified.remove(player.getUniqueId());
            player.sendMessage("2FA redefinido pelo console. Solicite o secret ao responsavel por canal privado.");
        }
        return true;
    }

    private void requireIfNeeded(Player player) {
        if (!this.config.admin2FAEnabled() || !this.config.isAdmin2FA(player.getName())) {
            this.pending.remove(player.getUniqueId());
            this.verified.add(player.getUniqueId());
            return;
        }
        this.verified.remove(player.getUniqueId());
        this.pending.add(player.getUniqueId());
        player.closeInventory();
        if (config.kickOnTimeout()) {
            int seconds = Math.max(10, plugin.getConfig().getInt("admin-2fa.timeout-seconds", 120));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && isBlocked(player)) player.kickPlayer("Tempo para validar 2FA esgotado.");
            }, seconds * 20L);
        }
        AdminSecret adminSecret = this.secrets.get(ConfigManager.lower(player.getName()));
        if (adminSecret == null || !adminSecret.enabled()) {
            player.sendMessage("2FA nao cadastrado. O console deve usar redefine " + player.getName() + ".");
        } else {
            player.sendMessage(ConfigManager.color("&cZetraMC 2FA: use &e/" + this.config.getCodeCommand() + " <codigo> &cpara liberar sua conta."));
        }
    }

    public boolean isVerified(UUID uuid) { return verified.contains(uuid); }

    public boolean isBlocked(Player player) {
        return pending.contains(player.getUniqueId());
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
        if (playerCommandPreprocessEvent.getMessage().matches("(?i)^/(?:nickguard:)?2fa(?:\\s.*)?$")) {
            playerCommandPreprocessEvent.setCancelled(true);
            String[] parts = playerCommandPreprocessEvent.getMessage().trim().split("\\s+");
            handleCodeCommand(playerCommandPreprocessEvent.getPlayer(), java.util.Arrays.copyOfRange(parts, 1, parts.length));
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
            failedAttempts.put(ConfigManager.lower(string), secretsConfig.getInt(string2 + "failed-attempts", 0));
            lockedUntil.put(ConfigManager.lower(string), secretsConfig.getLong(string2 + "locked-until", 0));
        }
    }

    private boolean setNewSecret(String string) {
        String string2 = TotpUtil.generateSecret();
        String string3 = ConfigManager.lower(string);
        this.secrets.put(string3, new AdminSecret(string2, true));
        this.secretsConfig.set("players." + string3 + ".secret", (Object)string2);
        this.secretsConfig.set("players." + string3 + ".enabled", (Object)true);
        this.secretsConfig.set("players." + string3 + ".last-used-step", -1L);
        failedAttempts.remove(string3);
        lockedUntil.remove(string3);
        boolean saved = saveAccountState(string3);
        if (!saved) this.secrets.remove(string3);
        return saved;
    }


    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onEntityInteract(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        if (isBlocked(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onEntityInteractAt(org.bukkit.event.player.PlayerInteractAtEntityEvent e) {
        if (isBlocked(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && isBlocked(p)) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onOpen(org.bukkit.event.inventory.InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p && isBlocked(p)) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onReceiveDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && isBlocked(p)) e.setCancelled(true);
        if (e instanceof EntityDamageByEntityEvent hit
                && hit.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player p && isBlocked(p)) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onSwap(org.bukkit.event.player.PlayerSwapHandItemsEvent e) {
        if (isBlocked(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onConsume(org.bukkit.event.player.PlayerItemConsumeEvent e) {
        if (isBlocked(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onBook(org.bukkit.event.player.PlayerEditBookEvent e) {
        if (isBlocked(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onSign(org.bukkit.event.block.SignChangeEvent e) {
        if (isBlocked(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onCommands(org.bukkit.event.player.PlayerCommandSendEvent e) {
        if (isBlocked(e.getPlayer())) e.getCommands().removeIf(s -> !s.equals("2fa") && !s.equals("nickguard:2fa"));
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onTab(org.bukkit.event.server.TabCompleteEvent e) {
        if (e.getSender() instanceof Player p && isBlocked(p)) e.setCancelled(true);
    }

    private record AdminSecret(String secret, boolean enabled) {
    }

    private boolean saveAccountState(String account) {
        String root = "players." + account + ".";
        secretsConfig.set(root + "failed-attempts", failedAttempts.getOrDefault(account, 0));
        secretsConfig.set(root + "locked-until", lockedUntil.getOrDefault(account, 0L));
        try {
            AtomicFiles.write(secretsFile.toPath(), secretsConfig.saveToString());
            return true;
        } catch (IOException error) {
            plugin.getLogger().severe("Falha ao persistir estado 2FA.");
            return false;
        }
    }
}
