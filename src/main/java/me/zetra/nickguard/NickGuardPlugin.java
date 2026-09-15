/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParseException
 *  com.google.gson.JsonParser
 *  org.bukkit.Bukkit
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.World
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.java.JavaPlugin
 */
package me.zetra.nickguard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import me.zetra.nickguard.Admin2FA;
import me.zetra.nickguard.AlertService;
import me.zetra.nickguard.AntiGameModeGuard;
import me.zetra.nickguard.AntiLuckPermsGuard;
import me.zetra.nickguard.AntiOpGuard;
import me.zetra.nickguard.BlockedCommandsGuard;
import me.zetra.nickguard.ClientSignatureGuard;
import me.zetra.nickguard.ConfigManager;
import me.zetra.nickguard.DangerousPermissionGuard;
import me.zetra.nickguard.IdentityGuard;
import me.zetra.nickguard.RaidProtection;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class NickGuardPlugin
extends JavaPlugin {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private ConfigManager configManager;
    private IdentityGuard identityGuard;
    private Admin2FA admin2FA;
    private AntiOpGuard antiOpGuard;
    private AntiLuckPermsGuard antiLuckPermsGuard;
    private AntiGameModeGuard antiGameModeGuard;
    private AlertService alertService;
    private RaidProtection raidProtection;
    private DangerousPermissionGuard dangerousPermissionGuard;
    private BlockedCommandsGuard blockedCommandsGuard;
    private ClientSignatureGuard clientSignatureGuard;

    public void onEnable() {
        this.saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        this.alertService = new AlertService(this, this.configManager);
        this.identityGuard = new IdentityGuard(this, this.configManager);
        this.admin2FA = new Admin2FA(this, this.configManager);
        this.antiOpGuard = new AntiOpGuard(this, this.configManager);
        this.antiLuckPermsGuard = new AntiLuckPermsGuard(this, this.configManager);
        this.antiGameModeGuard = new AntiGameModeGuard(this, this.configManager);
        this.raidProtection = new RaidProtection(this, this.alertService);
        this.dangerousPermissionGuard = new DangerousPermissionGuard(this, this.configManager, this.alertService);
        this.blockedCommandsGuard = new BlockedCommandsGuard(this, this.configManager, this.alertService);
        this.clientSignatureGuard = new ClientSignatureGuard(this, this.configManager, this.alertService);
        this.identityGuard.register();
        this.admin2FA.register();
        this.antiOpGuard.register();
        this.antiLuckPermsGuard.register();
        this.antiGameModeGuard.register();
        this.raidProtection.register();
        this.dangerousPermissionGuard.register();
        this.blockedCommandsGuard.register();
        this.clientSignatureGuard.register();
        this.getLogger().info("NickGuard habilitado.");
    }

    public void onDisable() {
        if (this.antiOpGuard != null) {
            this.antiOpGuard.cancel();
        }
        if (this.admin2FA != null) {
            this.admin2FA.save();
        }
        if (this.raidProtection != null) {
            this.raidProtection.cancel();
        }
        if (this.dangerousPermissionGuard != null) {
            this.dangerousPermissionGuard.cancel();
        }
        if (this.clientSignatureGuard != null) {
            this.clientSignatureGuard.unregister();
        }
    }

    public IdentityGuard getIdentityGuard() {
        return this.identityGuard;
    }

    public Admin2FA getAdmin2FA() {
        return this.admin2FA;
    }

    public boolean onCommand(CommandSender commandSender, Command command, String string, String[] stringArray) {
        String string2 = command.getName().toLowerCase(Locale.ROOT);
        if (string2.equals("nickguard")) {
            return this.handleNickGuard(commandSender, stringArray);
        }
        if (string2.equals("verificar")) {
            return this.handleVerify(commandSender, stringArray);
        }
        if (string2.equals("remover")) {
            return this.handleRemoveUuid(commandSender, stringArray);
        }
        if (string2.equals(this.configManager.getCodeCommand())) {
            return this.admin2FA.handleCodeCommand(commandSender, stringArray);
        }
        if (string2.equals(this.configManager.getResetCommand())) {
            return this.admin2FA.handleResetCommand(commandSender, stringArray);
        }
        return false;
    }

    private boolean handleNickGuard(CommandSender commandSender, String[] stringArray) {
        if (!commandSender.hasPermission("nickguard.admin")) {
            commandSender.sendMessage(ConfigManager.color("&cVoce nao tem permissao para usar este comando."));
            return true;
        }
        if (stringArray.length == 1 && stringArray[0].equalsIgnoreCase("reload")) {
            this.reloadConfig();
            this.configManager.reload();
            this.identityGuard.reload();
            this.admin2FA.reload();
            this.antiOpGuard.reload();
            this.antiLuckPermsGuard.reload();
            this.antiGameModeGuard.reload();
            this.raidProtection.reload();
            this.dangerousPermissionGuard.reload();
            commandSender.sendMessage(ConfigManager.color("&aNickGuard recarregado."));
            return true;
        }
        if (stringArray.length == 1 && stringArray[0].equalsIgnoreCase("status")) {
            this.raidProtection.status(commandSender);
            return true;
        }
        if (stringArray.length == 2 && stringArray[0].equalsIgnoreCase("unblockip")) {
            boolean bl = this.raidProtection.unblockIp(stringArray[1]);
            commandSender.sendMessage(ConfigManager.color(bl ? "&aIP desbloqueado." : "&eIP nao estava bloqueado."));
            return true;
        }
        if (stringArray.length == 3 && stringArray[0].equalsIgnoreCase("blockip")) {
            long l = this.parseDurationSeconds(stringArray[2]);
            this.raidProtection.blockIpManual(stringArray[1], l);
            commandSender.sendMessage(ConfigManager.color("&aIP bloqueado por &f" + l + "s&a."));
            return true;
        }
        if (stringArray.length == 2 && stringArray[0].equalsIgnoreCase("unblockpattern")) {
            boolean bl = this.raidProtection.unblockPattern(stringArray[1]);
            commandSender.sendMessage(ConfigManager.color(bl ? "&aPattern desbloqueado." : "&ePattern nao estava bloqueado."));
            return true;
        }
        if (stringArray.length == 2 && stringArray[0].equalsIgnoreCase("raidmode")) {
            if (stringArray[1].equalsIgnoreCase("on")) {
                this.raidProtection.setRaidMode(true);
                commandSender.sendMessage(ConfigManager.color("&aRaid mode ativado."));
                return true;
            }
            if (stringArray[1].equalsIgnoreCase("off")) {
                this.raidProtection.setRaidMode(false);
                commandSender.sendMessage(ConfigManager.color("&aRaid mode desativado."));
                return true;
            }
        }
        commandSender.sendMessage(ConfigManager.color("&eUse /nickguard reload|status|unblockip <ip>|blockip <ip> <tempo>|unblockpattern <pattern>|raidmode on/off"));
        return true;
    }

    private long parseDurationSeconds(String string) {
        String string2 = string.toLowerCase(Locale.ROOT).trim();
        try {
            if (string2.endsWith("s")) {
                return Long.parseLong(string2.substring(0, string2.length() - 1));
            }
            if (string2.endsWith("m")) {
                return Long.parseLong(string2.substring(0, string2.length() - 1)) * 60L;
            }
            if (string2.endsWith("h")) {
                return Long.parseLong(string2.substring(0, string2.length() - 1)) * 3600L;
            }
            return Long.parseLong(string2);
        }
        catch (NumberFormatException numberFormatException) {
            return 300L;
        }
    }

    private boolean handleVerify(CommandSender commandSender, String[] stringArray) {
        if (!commandSender.hasPermission("nickguard.admin")) {
            commandSender.sendMessage(ConfigManager.color("&cVoce nao tem permissao para usar este comando."));
            return true;
        }
        if (stringArray.length != 1 || stringArray[0].isBlank()) {
            commandSender.sendMessage(ConfigManager.color("&eUse /verificar <nome> &7ou &e/verificar all"));
            return true;
        }
        if (stringArray[0].equalsIgnoreCase("all")) {
            return this.handleVerifyAll(commandSender);
        }
        String string = stringArray[0];
        String string2 = string.toLowerCase(Locale.ROOT);
        ArrayList<OfflinePlayer> arrayList = new ArrayList<OfflinePlayer>();
        for (OfflinePlayer offlinePlayer2 : Bukkit.getOfflinePlayers()) {
            String string3 = offlinePlayer2.getName();
            if (string3 == null || !string3.toLowerCase(Locale.ROOT).equals(string2)) continue;
            arrayList.add(offlinePlayer2);
        }
        arrayList.sort(Comparator.comparing((OfflinePlayer offlinePlayer) -> offlinePlayer.getName() == null ? "" : offlinePlayer.getName().toLowerCase(Locale.ROOT)).thenComparing(offlinePlayer -> offlinePlayer.getUniqueId().toString()));
        commandSender.sendMessage(ConfigManager.color("&b[NickGuard] Verificacao de nick"));
        commandSender.sendMessage(ConfigManager.color("&7nick_lowercase: &f" + string2));
        commandSender.sendMessage(ConfigManager.color("&7registros encontrados: &f" + arrayList.size()));
        if (arrayList.isEmpty()) {
            commandSender.sendMessage(ConfigManager.color("&cNenhum player conhecido com esse nick."));
            return true;
        }
        for (OfflinePlayer offlinePlayer3 : arrayList) {
            String string4 = offlinePlayer3.getName() == null ? string : offlinePlayer3.getName();
            commandSender.sendMessage(ConfigManager.color("&e- nick=&f" + string4 + " &7lower=&f" + string4.toLowerCase(Locale.ROOT) + " &7uuid=&f" + String.valueOf(offlinePlayer3.getUniqueId())));
        }
        return true;
    }

    private boolean handleVerifyAll(CommandSender commandSender) {
        LinkedHashMap<String, List<OfflinePlayer>> linkedHashMap = new LinkedHashMap<>();
        for (OfflinePlayer object22 : Bukkit.getOfflinePlayers()) {
            String playerName = object22.getName();
            if (playerName == null || playerName.isBlank()) continue;
            linkedHashMap.computeIfAbsent(playerName.toLowerCase(Locale.ROOT), string -> new ArrayList<>()).add(object22);
        }
        List<Map.Entry<String, List<OfflinePlayer>>> list = linkedHashMap.entrySet().stream().filter(entry -> entry.getValue().stream().map(OfflinePlayer::getUniqueId).distinct().count() > 1L).sorted(Map.Entry.comparingByKey()).toList();
        commandSender.sendMessage(ConfigManager.color("&b[NickGuard] Verificacao geral de duplicados"));
        commandSender.sendMessage(ConfigManager.color("&7nicks duplicados encontrados: &f" + list.size()));
        if (list.isEmpty()) {
            commandSender.sendMessage(ConfigManager.color("&aNenhum nick duplicado encontrado."));
            return true;
        }
        int n = 0;
        int n2 = 20;
        for (Map.Entry<String, List<OfflinePlayer>> object : list) {
            if (n >= n2) break;
            List<OfflinePlayer> list2 = object.getValue().stream().sorted(Comparator.comparing((OfflinePlayer offlinePlayer) -> offlinePlayer.getName() == null ? "" : offlinePlayer.getName()).thenComparing(offlinePlayer -> offlinePlayer.getUniqueId().toString())).toList();
            commandSender.sendMessage(ConfigManager.color("&e" + object.getKey() + " &7(" + list2.size() + " registros)"));
            for (OfflinePlayer offlinePlayer2 : list2) {
                String string2 = offlinePlayer2.getName() == null ? object.getKey() : offlinePlayer2.getName();
                commandSender.sendMessage(ConfigManager.color("&7- &fnick=" + string2 + " &7uuid=&f" + String.valueOf(offlinePlayer2.getUniqueId())));
            }
            ++n;
        }
        if (list.size() > n2) {
            commandSender.sendMessage(ConfigManager.color("&7Mostrando &f" + n2 + " &7de &f" + list.size() + "&7. Use &e/verificar <nick> &7para ver um caso especifico."));
        }
        return true;
    }

    private boolean handleRemoveUuid(CommandSender commandSender, String[] stringArray) {
        UUID uUID;
        if (!commandSender.hasPermission("nickguard.admin")) {
            commandSender.sendMessage(ConfigManager.color("&cVoce nao tem permissao para usar este comando."));
            return true;
        }
        if (stringArray.length < 1 || stringArray.length > 2) {
            commandSender.sendMessage(ConfigManager.color("&eUse /remover <uuid> &7para revisar."));
            commandSender.sendMessage(ConfigManager.color("&eUse /remover <uuid> confirmar &7para mover os dados vanilla para backup."));
            return true;
        }
        try {
            uUID = UUID.fromString(stringArray[0]);
        }
        catch (IllegalArgumentException illegalArgumentException) {
            commandSender.sendMessage(ConfigManager.color("&cUUID invalido: &f" + stringArray[0]));
            return true;
        }
        if (stringArray.length == 1 || !stringArray[1].equalsIgnoreCase("confirmar")) {
            List<Path> list = this.findVanillaUuidFiles(uUID);
            commandSender.sendMessage(ConfigManager.color("&b[NickGuard] Remocao segura por UUID"));
            commandSender.sendMessage(ConfigManager.color("&7uuid: &f" + String.valueOf(uUID)));
            commandSender.sendMessage(ConfigManager.color("&7arquivos vanilla encontrados: &f" + list.size()));
            commandSender.sendMessage(ConfigManager.color("&7Isto move playerdata/stats/advancements, remove usercache e limpa cache do NickGuard."));
            commandSender.sendMessage(ConfigManager.color("&7Nao apaga dados de plugins como LuckPerms/nLogin/economia."));
            commandSender.sendMessage(ConfigManager.color("&eConfirmar: /remover " + String.valueOf(uUID) + " confirmar"));
            return true;
        }
        Player player = Bukkit.getPlayer((UUID)uUID);
        if (player != null) {
            player.kickPlayer(ConfigManager.color("&cSeus dados foram removidos por um administrador."));
        }
        String string = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now()) + "_" + String.valueOf(uUID);
        Path path = new File(this.getDataFolder(), "removed-uuid-backups/" + string).toPath();
        ArrayList<Path> arrayList = new ArrayList<Path>();
        for (Path path2 : this.findVanillaUuidFiles(uUID)) {
            try {
                Path path3 = path.resolve(this.safeRelativePath(path2));
                Files.createDirectories(path3.getParent(), new FileAttribute[0]);
                Files.move(path2, path3, StandardCopyOption.REPLACE_EXISTING);
                arrayList.add(path2);
            }
            catch (IOException iOException) {
                commandSender.sendMessage(ConfigManager.color("&cFalha ao mover &f" + String.valueOf(path2) + "&c: " + iOException.getMessage()));
                this.getLogger().warning("[NickGuard] Falha ao remover uuid=" + String.valueOf(uUID) + " arquivo=" + String.valueOf(path2) + " erro=" + iOException.getMessage());
            }
        }
        int n = this.removeUserCacheEntries(uUID, path);
        int n2 = this.identityGuard.removeUuid(uUID);
        commandSender.sendMessage(ConfigManager.color("&a[NickGuard] Remocao concluida."));
        commandSender.sendMessage(ConfigManager.color("&7uuid: &f" + String.valueOf(uUID)));
        commandSender.sendMessage(ConfigManager.color("&7arquivos movidos: &f" + arrayList.size()));
        commandSender.sendMessage(ConfigManager.color("&7entradas removidas do usercache: &f" + n));
        commandSender.sendMessage(ConfigManager.color("&7entradas removidas do cache NickGuard: &f" + n2));
        commandSender.sendMessage(ConfigManager.color("&7backup: &f" + String.valueOf(path)));
        this.getLogger().info("[NickGuard] Remocao UUID uuid=" + String.valueOf(uUID) + " arquivos=" + arrayList.size() + " usercache=" + n + " cache=" + n2 + " backup=" + String.valueOf(path));
        return true;
    }

    private List<Path> findVanillaUuidFiles(UUID uUID) {
        ArrayList<Path> arrayList = new ArrayList<Path>();
        String string = uUID.toString();
        for (World world : Bukkit.getWorlds()) {
            File file = world.getWorldFolder();
            this.addIfExists(arrayList, file.toPath().resolve("playerdata").resolve(string + ".dat"));
            this.addIfExists(arrayList, file.toPath().resolve("playerdata").resolve(string + ".dat_old"));
            this.addIfExists(arrayList, file.toPath().resolve("stats").resolve(string + ".json"));
            this.addIfExists(arrayList, file.toPath().resolve("advancements").resolve(string + ".json"));
        }
        return arrayList;
    }

    private void addIfExists(List<Path> list, Path path) {
        if (Files.isRegularFile(path, new LinkOption[0])) {
            list.add(path);
        }
    }

    private Path safeRelativePath(Path path) {
        Path path2;
        Path path3 = path.toAbsolutePath().normalize();
        if (path3.startsWith(path2 = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize())) {
            return path2.relativize(path3);
        }
        return Path.of(path3.getFileName().toString(), new String[0]);
    }

    private int removeUserCacheEntries(UUID uUID, Path path) {
        Path path2 = Bukkit.getWorldContainer().toPath().resolve("usercache.json").toAbsolutePath().normalize();
        if (!Files.isRegularFile(path2, new LinkOption[0])) {
            return 0;
        }
        try {
            String string = Files.readString(path2, StandardCharsets.UTF_8);
            JsonElement jsonElement = JsonParser.parseString((String)string);
            if (!jsonElement.isJsonArray()) {
                this.getLogger().warning("[NickGuard] usercache.json nao e um array JSON; arquivo nao alterado.");
                return 0;
            }
            String string2 = uUID.toString();
            JsonArray jsonArray = new JsonArray();
            int n = 0;
            for (JsonElement jsonElement2 : jsonElement.getAsJsonArray()) {
                if (this.isUserCacheEntryForUuid(jsonElement2, string2)) {
                    ++n;
                    continue;
                }
                jsonArray.add(jsonElement2);
            }
            if (n == 0) {
                return 0;
            }
            Path path3 = path.resolve("usercache.json.backup");
            Files.createDirectories(path3.getParent(), new FileAttribute[0]);
            Files.copy(path2, path3, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(path2, (CharSequence)(GSON.toJson((JsonElement)jsonArray) + "\n"), StandardCharsets.UTF_8, new OpenOption[0]);
            return n;
        }
        catch (JsonParseException | IOException | IllegalStateException throwable) {
            this.getLogger().warning("[NickGuard] Falha ao editar usercache uuid=" + String.valueOf(uUID) + " erro=" + throwable.getMessage());
            return 0;
        }
    }

    private boolean isUserCacheEntryForUuid(JsonElement jsonElement, String string) {
        if (!jsonElement.isJsonObject()) {
            return false;
        }
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        JsonElement jsonElement2 = jsonObject.get("uuid");
        return jsonElement2 != null && jsonElement2.isJsonPrimitive() && string.equalsIgnoreCase(jsonElement2.getAsString());
    }
}
