package com.cosmicraft.torneocosmic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Controla el ciclo de vida completo de un 1v1 del torneo: guardado y
 * restauración de estado, entrega de kits, secuencia de anuncios/animación,
 * el propio combate, y el cálculo del ganador.
 */
public class DuelManager {

    private final torneocosmic plugin;

    // Estado del duelo activo (solo puede haber uno a la vez)
    private boolean active = false;
    private boolean freeze = false;

    private Player fighter1;
    private Player fighter2;
    private PlayerSnapshot snapshot1;
    private PlayerSnapshot snapshot2;

    private final Map<UUID, Double> damageDealt = new HashMap<>();

    private BukkitTask timeoutTask;
    private BukkitTask actionBarTask;
    private final java.util.List<BukkitTask> introTasks = new java.util.ArrayList<>();

    private long fightEndsAtMillis;

    public DuelManager(torneocosmic plugin) {
        this.plugin = plugin;
    }

    // ==================== POSICIONES ====================

    public void setPosition(int index, Location location) {
        String path = "posiciones.pos" + index;
        plugin.getConfig().set(path, serializeLocation(location));
        plugin.saveConfig();
    }

    public Location getPosition(int index) {
        String raw = plugin.getConfig().getString("posiciones.pos" + index, "");
        return deserializeLocation(raw);
    }

    private String serializeLocation(Location loc) {
        return loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ()
                + "," + loc.getYaw() + "," + loc.getPitch();
    }

    private Location deserializeLocation(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            String[] parts = raw.split(",");
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                return null;
            }
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = Float.parseFloat(parts[4]);
            float pitch = Float.parseFloat(parts[5]);
            return new Location(world, x, y, z, yaw, pitch);
        } catch (Exception ex) {
            return null;
        }
    }

    // ==================== ESTADO ====================

    public boolean isActive() {
        return active;
    }

    public boolean isFreeze() {
        return freeze;
    }

    public boolean isFighter(Player player) {
        if (!active) {
            return false;
        }
        return player.equals(fighter1) || player.equals(fighter2);
    }

    public Player getOpponent(Player player) {
        if (player.equals(fighter1)) {
            return fighter2;
        }
        if (player.equals(fighter2)) {
            return fighter1;
        }
        return null;
    }

    public void addDamage(Player attacker, double amount) {
        if (!isFighter(attacker)) {
            return;
        }
        damageDealt.merge(attacker.getUniqueId(), amount, Double::sum);
    }

    // ==================== INICIO DEL DUELO ====================

    public enum StartResult {
        OK,
        YA_HAY_DUELO,
        FALTAN_POSICIONES,
        JUGADOR_NO_CONECTADO,
        MISMO_JUGADOR
    }

    public StartResult start(Player p1, Player p2, String fase) {
        if (active) {
            return StartResult.YA_HAY_DUELO;
        }
        if (p1 == null || p2 == null || !p1.isOnline() || !p2.isOnline()) {
            return StartResult.JUGADOR_NO_CONECTADO;
        }
        if (p1.equals(p2)) {
            return StartResult.MISMO_JUGADOR;
        }

        Location pos1 = getPosition(1);
        Location pos2 = getPosition(2);
        if (pos1 == null || pos2 == null) {
            return StartResult.FALTAN_POSICIONES;
        }

        this.active = true;
        this.freeze = true;
        this.fighter1 = p1;
        this.fighter2 = p2;
        this.snapshot1 = new PlayerSnapshot(p1);
        this.snapshot2 = new PlayerSnapshot(p2);
        this.damageDealt.clear();

        // Orientar a los jugadores para que se miren de frente al llegar.
        Location tp1 = pos1.clone();
        Location tp2 = pos2.clone();
        tp1.setDirection(pos2.toVector().subtract(pos1.toVector()));
        tp2.setDirection(pos1.toVector().subtract(pos2.toVector()));

        p1.teleport(tp1);
        p2.teleport(tp2);

        plugin.getKitManager().applyKit(p1);
        plugin.getKitManager().applyKit(p2);
        p1.setHealth(p1.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                ? p1.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() : 20.0D);
        p2.setHealth(p2.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                ? p2.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() : 20.0D);
        p1.setFoodLevel(20);
        p2.setFoodLevel(20);
        p1.setSaturation(20f);
        p2.setSaturation(20f);
        p1.setExp(0f);
        p2.setExp(0f);
        p1.setLevel(0);
        p2.setLevel(0);
        p1.setFireTicks(0);
        p2.setFireTicks(0);

        runIntroSequence(p1, p2, fase);
        return StartResult.OK;
    }

    private void runIntroSequence(Player p1, Player p2, String fase) {
        String faseTexto = displayFase(fase);
        String vsLine = ChatColor.YELLOW + p1.getName() + ChatColor.GRAY + " VS " + ChatColor.YELLOW + p2.getName();

        broadcast(ChatColor.GOLD + "" + ChatColor.BOLD + "===================================");
        if (faseTexto != null) {
            broadcast(ChatColor.GOLD + "" + ChatColor.BOLD + faseTexto);
        }
        broadcast(vsLine);
        broadcast(ChatColor.GOLD + "" + ChatColor.BOLD + "===================================");

        long tick = 0L;

        if (faseTexto != null) {
            schedule(tick, () -> showTitle(faseTexto, p1.getName() + " VS " + p2.getName(), NamedTextColor.GOLD));
            tick += 50L;
        }

        final long t1 = tick;
        schedule(t1, () -> {
            showTitle("", "¡Prepárense en sus posiciones!", NamedTextColor.WHITE);
            broadcast(ChatColor.AQUA + "¡Prepárense en sus posiciones!");
        });
        tick += 40L;

        final long t2 = tick;
        schedule(t2, () -> {
            showTitle("", "Por un lado tenemos a " + ChatColor.YELLOW + p1.getName() + ChatColor.RESET + "!!!!", NamedTextColor.GRAY);
            broadcast(ChatColor.GRAY + "Por un lado tenemos a " + ChatColor.YELLOW + p1.getName() + ChatColor.GRAY + "!!!!");
        });
        tick += 35L;

        final long t3 = tick;
        schedule(t3, () -> {
            showTitle("", "Y por el otro tenemos a " + ChatColor.YELLOW + p2.getName() + ChatColor.RESET + "!!!!", NamedTextColor.GRAY);
            broadcast(ChatColor.GRAY + "Y por el otro tenemos a " + ChatColor.YELLOW + p2.getName() + ChatColor.GRAY + "!!!!");
        });
        tick += 35L;

        final long t4 = tick;
        schedule(t4, () -> bigCountdown("3", NamedTextColor.RED));
        tick += 20L;

        final long t5 = tick;
        schedule(t5, () -> bigCountdown("2", NamedTextColor.RED));
        tick += 20L;

        final long t6 = tick;
        schedule(t6, () -> bigCountdown("1", NamedTextColor.RED));
        tick += 20L;

        final long t7 = tick;
        schedule(t7, () -> {
            bigCountdown("¡PELEA!", NamedTextColor.GREEN);
            beginCombat();
        });
    }

    private void schedule(long delayTicks, Runnable runnable) {
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active) {
                runnable.run();
            }
        }, delayTicks);
        introTasks.add(task);
    }

    private void showTitle(String main, String sub, NamedTextColor color) {
        Component mainComponent = Component.text(main).color(color).decorate(TextDecoration.BOLD);
        Component subComponent = Component.text(sub).color(NamedTextColor.WHITE);
        Title title = Title.title(mainComponent, subComponent,
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1800), Duration.ofMillis(250)));
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showTitle(title);
        }
    }

    private void bigCountdown(String text, NamedTextColor color) {
        Component mainComponent = Component.text(text).color(color).decorate(TextDecoration.BOLD);
        Title title = Title.title(mainComponent, Component.empty(),
                Title.Times.times(Duration.ofMillis(50), Duration.ofMillis(700), Duration.ofMillis(150)));
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showTitle(title);
        }
    }

    private String displayFase(String fase) {
        if (fase == null || fase.isBlank()) {
            return null;
        }
        String normalized = fase.trim().toLowerCase();
        switch (normalized) {
            case "final":
                return "¡GRAN FINAL!";
            case "semis":
            case "semifinal":
            case "semifinales":
                return "SEMIFINAL";
            case "cuartos":
            case "cuartosdefinal":
                return "CUARTOS DE FINAL";
            case "octavos":
            case "octavosdefinal":
                return "OCTAVOS DE FINAL";
            case "dieciseisavos":
                return "DIECISEISAVOS DE FINAL";
            default:
                return fase.toUpperCase();
        }
    }

    private void broadcast(String message) {
        Bukkit.broadcastMessage(message);
    }

    // ==================== COMBATE ====================

    private void beginCombat() {
        if (!active) {
            return;
        }
        this.freeze = false;

        int durationSeconds = plugin.getConfig().getInt("duelo.duracion-segundos", 300);
        this.fightEndsAtMillis = System.currentTimeMillis() + (durationSeconds * 1000L);

        timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, this::endByTimeout, durationSeconds * 20L);

        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long remaining = Math.max(0, (fightEndsAtMillis - System.currentTimeMillis()) / 1000L);
            String formatted = formatTime(remaining);
            Component bar = Component.text("⏱ ").color(NamedTextColor.GOLD)
                    .append(Component.text(formatted).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            if (fighter1 != null && fighter1.isOnline()) {
                fighter1.sendActionBar(bar);
            }
            if (fighter2 != null && fighter2.isOnline()) {
                fighter2.sendActionBar(bar);
            }
        }, 0L, 20L);
    }

    private String formatTime(long totalSeconds) {
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * Llamado por el listener cuando un peleador recibiría un golpe letal.
     * Se cancela la muerte real y se declara ganador al otro peleador.
     */
    public void endMatchByDeath(Player loser) {
        if (!active || !isFighter(loser)) {
            return;
        }
        Player winner = getOpponent(loser);
        finish(winner, loser, "muerte");
    }

    private void endByTimeout() {
        if (!active) {
            return;
        }

        double dmg1 = damageDealt.getOrDefault(fighter1.getUniqueId(), 0.0D);
        double dmg2 = damageDealt.getOrDefault(fighter2.getUniqueId(), 0.0D);

        if (dmg1 > dmg2) {
            finish(fighter1, fighter2, "tiempo");
        } else if (dmg2 > dmg1) {
            finish(fighter2, fighter1, "tiempo");
        } else {
            double hp1 = fighter1.getHealth();
            double hp2 = fighter2.getHealth();
            if (hp1 > hp2) {
                finish(fighter1, fighter2, "tiempo");
            } else if (hp2 > hp1) {
                finish(fighter2, fighter1, "tiempo");
            } else {
                finishDraw();
            }
        }
    }

    private void finish(Player winner, Player loser, String motivo) {
        cancelTasks();

        String winnerName = winner.getName();
        broadcast(ChatColor.GOLD + "" + ChatColor.BOLD + "===================================");
        if (motivo.equals("muerte")) {
            broadcast(ChatColor.GREEN + "" + ChatColor.BOLD + winnerName + " gana el combate por eliminación!");
        } else {
            broadcast(ChatColor.GREEN + "" + ChatColor.BOLD + winnerName + " gana el combate por tiempo (mayor daño/vida)!");
        }
        broadcast(ChatColor.GOLD + "" + ChatColor.BOLD + "===================================");

        Component mainComponent = Component.text("¡" + winnerName + " GANA!").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD);
        Title title = Title.title(mainComponent, Component.text("Fin del combate").color(NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2500), Duration.ofMillis(500)));
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showTitle(title);
        }

        restoreFighters();
        resetState();
    }

    private void finishDraw() {
        cancelTasks();
        broadcast(ChatColor.YELLOW + "" + ChatColor.BOLD + "¡Empate! Ambos peleadores terminaron con el mismo daño y la misma vida.");
        broadcast(ChatColor.YELLOW + "Un administrador debe repetir la ronda con /tournament pvp de nuevo.");

        Component mainComponent = Component.text("¡EMPATE!").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD);
        Title title = Title.title(mainComponent, Component.text("Se repetirá la ronda").color(NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2500), Duration.ofMillis(500)));
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showTitle(title);
        }

        restoreFighters();
        resetState();
    }

    public boolean stopDuel(CommandSender sender, String reason) {
        if (!active) {
            return false;
        }
        cancelTasks();
        broadcast(ChatColor.RED + "" + ChatColor.BOLD + "El combate ha sido detenido por un administrador.");
        restoreFighters();
        resetState();
        return true;
    }

    private void restoreFighters() {
        if (fighter1 != null && fighter1.isOnline() && snapshot1 != null) {
            snapshot1.restore(fighter1);
        }
        if (fighter2 != null && fighter2.isOnline() && snapshot2 != null) {
            snapshot2.restore(fighter2);
        }
    }

    private void cancelTasks() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;
        }
        for (BukkitTask task : introTasks) {
            task.cancel();
        }
        introTasks.clear();
    }

    private void resetState() {
        this.active = false;
        this.freeze = false;
        this.fighter1 = null;
        this.fighter2 = null;
        this.snapshot1 = null;
        this.snapshot2 = null;
        this.damageDealt.clear();
    }

    /**
     * Para uso del listener: si un peleador se desconecta durante el combate.
     */
    public void handleFighterQuit(Player quitter) {
        if (!active || !isFighter(quitter)) {
            return;
        }
        Player winner = getOpponent(quitter);
        cancelTasks();
        broadcast(ChatColor.RED + quitter.getName() + " se desconectó. " + winner.getName() + " gana el combate por abandono.");

        // Restaurar al que se queda; el que se fue se restaura guardando su
        // snapshot para aplicarlo cuando vuelva a conectarse (ver DuelListener).
        if (winner != null && winner.isOnline()) {
            PlayerSnapshot winnerSnapshot = winner.equals(fighter1) ? snapshot1 : snapshot2;
            if (winnerSnapshot != null) {
                winnerSnapshot.restore(winner);
            }
        }

        PlayerSnapshot quitterSnapshot = quitter.equals(fighter1) ? snapshot1 : snapshot2;
        if (quitterSnapshot != null) {
            plugin.getPendingRestores().put(quitter.getUniqueId(), quitterSnapshot);
        }

        resetState();
    }
}
