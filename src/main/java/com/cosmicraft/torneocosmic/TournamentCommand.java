package com.cosmicraft.torneocosmic;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TournamentCommand implements CommandExecutor, TabCompleter {

    private static final List<String> FASES = Arrays.asList(
            "final", "semis", "cuartos", "octavos", "dieciseisavos");

    private final torneocosmic plugin;

    public TournamentCommand(torneocosmic plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "No tienes permiso para usar este comando. Solo operadores (ops).");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set":
                return handleSet(sender, args);
            case "pvp":
                return handlePvp(sender, args);
            case "setabsolutewinner":
                return handleSetAbsoluteWinner(sender, args);
            case "removeabsolutewinner":
                return handleRemoveAbsoluteWinner(sender, args);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== TorneoCosmic ===");
        sender.sendMessage(ChatColor.YELLOW + "/tournament set pos1" + ChatColor.GRAY + " - Fija la posición 1 del 1v1 donde estás parado.");
        sender.sendMessage(ChatColor.YELLOW + "/tournament set pos2" + ChatColor.GRAY + " - Fija la posición 2 del 1v1 donde estás parado.");
        sender.sendMessage(ChatColor.YELLOW + "/tournament pvp <jugador1> <jugador2> [fase]" + ChatColor.GRAY + " - Inicia un 1v1.");
        sender.sendMessage(ChatColor.YELLOW + "/tournament pvp stop" + ChatColor.GRAY + " - Detiene el 1v1 en curso.");
        sender.sendMessage(ChatColor.DARK_GRAY + "Fases disponibles: final, semis, cuartos, octavos, dieciseisavos (o cualquier texto libre).");
        sender.sendMessage(ChatColor.YELLOW + "/tournament setabsolutewinner <jugador>" + ChatColor.GRAY + " - Da +1 corazón permanente.");
        sender.sendMessage(ChatColor.YELLOW + "/tournament removeabsolutewinner <jugador>" + ChatColor.GRAY + " - Quita el corazón extra.");
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo un jugador dentro del juego puede fijar posiciones.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /tournament set <pos1|pos2>");
            return true;
        }

        String target = args[1].toLowerCase();
        int index;
        if (target.equals("pos1")) {
            index = 1;
        } else if (target.equals("pos2")) {
            index = 2;
        } else {
            sender.sendMessage(ChatColor.RED + "Uso: /tournament set <pos1|pos2>");
            return true;
        }

        plugin.getDuelManager().setPosition(index, player.getLocation());
        sender.sendMessage(ChatColor.GREEN + "Posición " + index + " guardada en tu ubicación actual.");
        return true;
    }

    private boolean handlePvp(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /tournament pvp <jugador1> <jugador2> [fase]  |  /tournament pvp stop");
            return true;
        }

        if (args[1].equalsIgnoreCase("stop")) {
            boolean stopped = plugin.getDuelManager().stopDuel(sender, "Detenido manualmente.");
            if (stopped) {
                sender.sendMessage(ChatColor.GREEN + "El combate en curso fue detenido y los peleadores volvieron a su estado anterior.");
            } else {
                sender.sendMessage(ChatColor.RED + "No hay ningún combate en curso.");
            }
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /tournament pvp <jugador1> <jugador2> [fase]");
            return true;
        }

        Player p1 = Bukkit.getPlayerExact(args[1]);
        Player p2 = Bukkit.getPlayerExact(args[2]);

        String fase = null;
        if (args.length > 3) {
            StringBuilder sb = new StringBuilder();
            for (int i = 3; i < args.length; i++) {
                sb.append(args[i]).append(" ");
            }
            fase = sb.toString().trim();
        }

        DuelManager.StartResult result = plugin.getDuelManager().start(p1, p2, fase);
        switch (result) {
            case OK:
                sender.sendMessage(ChatColor.GREEN + "¡Combate iniciado entre " + p1.getName() + " y " + p2.getName() + "!");
                break;
            case YA_HAY_DUELO:
                sender.sendMessage(ChatColor.RED + "Ya hay un combate en curso. Usa /tournament pvp stop para detenerlo primero.");
                break;
            case FALTAN_POSICIONES:
                sender.sendMessage(ChatColor.RED + "Faltan posiciones por configurar. Usa /tournament set pos1 y /tournament set pos2 primero.");
                break;
            case JUGADOR_NO_CONECTADO:
                sender.sendMessage(ChatColor.RED + "Uno o ambos jugadores no están conectados.");
                break;
            case MISMO_JUGADOR:
                sender.sendMessage(ChatColor.RED + "No puedes poner al mismo jugador contra sí mismo.");
                break;
        }
        return true;
    }

    private boolean handleSetAbsoluteWinner(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /tournament setabsolutewinner <jugador>");
            return true;
        }
        
        // Usamos OfflinePlayer para poder dárselo incluso si está desconectado
        org.bukkit.OfflinePlayer targetOffline = Bukkit.getOfflinePlayer(args[1]);
        if (targetOffline == null || targetOffline.getUniqueId() == null) {
            sender.sendMessage(ChatColor.RED + "No se pudo encontrar al jugador.");
            return true;
        }

        plugin.addAbsoluteWinner(targetOffline.getUniqueId());

        if (targetOffline.isOnline() && targetOffline.getPlayer() != null) {
            Player p = targetOffline.getPlayer();
            plugin.updateAbsoluteWinnerHealth(p);
            p.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "¡Felicidades! Has sido declarado Ganador Absoluto y has recibido 1 corazón extra permanentemente.");
        }

        sender.sendMessage(ChatColor.GREEN + "¡" + targetOffline.getName() + " ahora es un Ganador Absoluto (+1 Corazón)!");
        return true;
    }

    private boolean handleRemoveAbsoluteWinner(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /tournament removeabsolutewinner <jugador>");
            return true;
        }

        org.bukkit.OfflinePlayer targetOffline = Bukkit.getOfflinePlayer(args[1]);
        plugin.removeAbsoluteWinner(targetOffline.getUniqueId());

        if (targetOffline.isOnline() && targetOffline.getPlayer() != null) {
            plugin.updateAbsoluteWinnerHealth(targetOffline.getPlayer());
        }

        sender.sendMessage(ChatColor.YELLOW + "Se ha removido el título de Ganador Absoluto a " + targetOffline.getName() + ".");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (!sender.isOp()) {
            return options;
        }

        if (args.length == 1) {
            options.addAll(Arrays.asList("set", "pvp", "setabsolutewinner", "removeabsolutewinner"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            options.addAll(Arrays.asList("pos1", "pos2"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("pvp") || args[0].equalsIgnoreCase("setabsolutewinner") || args[0].equalsIgnoreCase("removeabsolutewinner"))) {
            if (args[0].equalsIgnoreCase("pvp")) options.add("stop");
            for (Player p : Bukkit.getOnlinePlayers()) {
                options.add(p.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("pvp") && !args[1].equalsIgnoreCase("stop")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                options.add(p.getName());
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("pvp")) {
            options.addAll(FASES);
        }

        String current = args[args.length - 1].toLowerCase();
        options.removeIf(opt -> !opt.toLowerCase().startsWith(current));
        return options;
    }
}
